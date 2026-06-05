# Documentación del Sistema de Auditoría — SI2-SGD-HC

## Arquitectura General

El sistema de auditoría captura automáticamente todas las operaciones que modifican el estado de la aplicación (creaciones, actualizaciones, eliminaciones), almacena los registros en una base de datos PostgreSQL independiente con cifrado AES-256-GCM en reposo y verificación de integridad HMAC-SHA256, y expone una interfaz web de consulta exclusiva para el equipo de desarrollo con rol SUPERUSER.

La arquitectura sigue un enfoque híbrido de captura: un filtro global intercepta todas las peticiones HTTP mutantes desde la capa de seguridad de Spring, y una anotación opcional permite capturar el estado anterior y posterior de entidades específicas a nivel de servicio. Un mecanismo de deduplicación basado en ThreadLocal evita que un mismo evento se registre dos veces cuando ambas vías coinciden.

---

## Separación de Bases de Datos

La aplicación utiliza dos bases de datos PostgreSQL completamente independientes. La base principal aloja todas las tablas del negocio (tenants, usuarios, roles, pacientes, documentos, DICOM). La base de auditoría contiene exclusivamente la tabla `audit_log`.

Cada base tiene su propio pool de conexiones HikariCP, su propio EntityManagerFactory de Hibernate, su propio gestor de transacciones y su propia instancia de Flyway para migraciones. Esto garantiza que las escrituras de auditoría nunca compitan por conexiones o bloqueos con las operaciones del negocio, y que la base de auditoría pueda crecer, respaldarse y monitorearse de forma independiente.

La base principal se configura en la clase `MainDbConfig` (anotada como primaria), mientras que la base de auditoría se configura en `AuditDbConfig`. La aplicación principal excluye explícitamente los repositorios del paquete de auditoría de su escaneo JPA, y el `AuditDbConfig` escanea exclusivamente ese paquete.

Flyway ejecuta migraciones separadas para cada base. Las migraciones de la base principal residen en `db/migration/main/` (versiones V1 a V8 del negocio), y la migración de auditoría reside en `db/migration/audit/` (V1 con la creación de la tabla `audit_log`). No hay solapamiento ni conflicto de versiones.

---

## Captura Global mediante Filtro HTTP

### Posicionamiento en la cadena de seguridad

El componente principal de captura es un filtro que extiende `OncePerRequestFilter` de Spring y se inserta en la cadena de seguridad inmediatamente después del filtro de autenticación JWT, pero antes de que el controlador procese la petición. Esta posición es deliberada: cuando el filtro de auditoría se ejecuta, el usuario ya está autenticado, su identidad está disponible en el contexto de seguridad, y el tenant está identificado, pero el cuerpo de la petición aún no ha sido consumido por el controlador.

### Criterios de selección

El filtro solo procesa métodos HTTP que modifican estado: POST, PUT, PATCH y DELETE. También verifica que la ruta comience con `/api/` y que no pertenezca a la lista de exclusiones. Las rutas excluidas son las de autenticación (login, refresh de token), documentación de API (Swagger, OpenAPI), health checks, métricas y estáticos. Esto evita ruido en los logs y la captura de credenciales en tránsito durante el login.

### Cacheo del cuerpo de la petición

El flujo estándar de servlets consume el `InputStream` del request una sola vez. Si el filtro de auditoría leyera el cuerpo directamente, el controlador lo recibiría vacío. Para resolver esto, el filtro envuelve el request original en una clase `CachedBodyHttpServletRequest`, que extiende `HttpServletRequestWrapper`. Al construirse, esta clase lee la totalidad del `InputStream` y almacena los bytes en un buffer interno. Sus métodos `getInputStream()` y `getReader()` devuelven streams que leen del buffer en lugar del stream original ya consumido. Tanto el filtro como el controlador reciben el cuerpo completo.

### Extracción de información

Una vez que el controlador procesa la petición y la cadena de filtros retorna, el filtro construye una entidad `AuditLog` con:

- **Método HTTP y ruta**: obtenidos directamente del request.
- **Dirección IP del cliente**: se lee el encabezado `X-Forwarded-For`. Si no hay proxies de confianza configurados, se toma la primera IP de la lista (la más cercana al cliente original). Si hay proxies configurados, se recorre la lista de derecha a izquierda descartando las IPs conocidas hasta encontrar la IP real del cliente.
- **User-Agent**: del encabezado estándar.
- **Tipo de recurso**: extraído del primer segmento de la ruta después de `/api/`. Por ejemplo, `/api/module_users/patients/abc123` produce `MODULE_USERS`.
- **Identificador del recurso**: se escanean los segmentos de la ruta buscando un UUID válido. Si se encuentra, se registra como identificador del recurso afectado.
- **Tipo de acción**: inferido del método HTTP. POST se traduce a CREATE, PUT y PATCH a UPDATE, DELETE a DELETE.
- **Usuario y tenant**: obtenidos del `SecurityContextHolder`. Si el principal autenticado es un `SecurityUser`, se extraen su identificador, correo electrónico, nombre completo y el identificador del tenant al que pertenece.
- **Código de estado HTTP y mensaje de error**: si la cadena de filtros lanzó una excepción, se registra el error.
- **Duración**: calculada como la diferencia de tiempo entre el inicio y el fin del procesamiento.

### Mecanismo de deduplicación

Existe una clase `AuditContext` que mantiene un `ThreadLocal<Boolean>`. Cuando el aspecto `@Auditable` procesa un método anotado, activa este flag antes de guardar el log y lo limpia al terminar. Al regresar del `filterChain.doFilter()`, el filtro consulta `AuditContext.isAspectActive()`. Si el aspecto ya registró la operación, el filtro omite su propia captura. Esto evita entradas duplicadas cuando un controlador con método POST invoca un servicio anotado con `@Auditable`.

---

## Captura Detallada mediante Anotación @Auditable

### Propósito

Mientras que el filtro global captura el contexto HTTP de toda operación mutante, la anotación `@Auditable` proporciona una capa adicional de detalle para métodos específicos de la capa de servicio. Su valor principal reside en la capacidad de capturar el estado anterior y posterior de una entidad cuando se modifica, así como los argumentos exactos con los que se invocó el método.

### Estructura de la anotación

La anotación se aplica a métodos y acepta tres parámetros: el tipo de recurso (nombre de la entidad, como `PATIENT` o `DOCUMENT`), el tipo de acción (del enum `ActionType`), y opcionalmente el nombre del parámetro del método que contiene el identificador del recurso (por defecto `id`).

### Comportamiento del aspecto

Un aspecto AOP con la directiva `@Around` intercepta cualquier método anotado. Su flujo es el siguiente:

1. **Extracción de argumentos**: todos los parámetros del método se serializan a un mapa de clave-valor usando Jackson. Cada valor se inspecciona recursivamente: los campos `password`, `passwordConfirm`, `currentPassword` y `newPassword` se reemplazan por el texto `***ENMASCARADO***` tanto en el nivel raíz como en objetos anidados. Esta sanitización es independiente de la que realiza el servicio de cifrado — defensa en profundidad.

2. **Extracción del identificador**: se busca entre los parámetros del método aquel cuyo nombre coincida con `idParamName`. Este valor se usará como `resourceId` en el registro de auditoría.

3. **Captura del estado anterior**: si la acción es de tipo UPDATE y se encontró un identificador, el aspecto consulta la base de datos usando el `EntityManager`. Recorre el metamodelo de JPA buscando una entidad cuyo nombre coincida con el `resourceType` especificado en la anotación. Si la encuentra, ejecuta `EntityManager.find()` con el identificador extraído, obtiene la entidad en su estado actual (antes de la modificación), la desacopla del contexto de persistencia para evitar efectos secundarios, la serializa a mapa y la sanitiza.

4. **Ejecución del método**: invoca `joinPoint.proceed()` para ejecutar la lógica real del servicio. Si el método retorna exitosamente, el valor de retorno se captura como `changesAfter`. Si lanza una excepción, se captura el mensaje de error y se marca el registro con código 500.

5. **Construcción y envío**: se construye una entidad `AuditLog` con el tipo de acción y recurso de la anotación, el identificador extraído, los argumentos sanitizados como `requestBody`, el estado anterior (si aplica) como `changesBefore`, y el resultado como `changesAfter`. Se activa el flag de `AuditContext` para que el filtro global no duplique la entrada, se invoca `auditLogService.logAction()` de forma asíncrona, y se limpia el contexto.

### Servicios anotados

Actualmente, la anotación `@Auditable` está aplicada en los siguientes servicios:

- `PatientService`: métodos `updatePatient` y `deletePatient`
- `UserService`: métodos `updateUser` y `deleteUser`
- `DocumentService`: métodos `update` y `delete`
- `RoleService`: método `updateRole` (usando `PERMISSION_CHANGE` como tipo de acción)

---

## Persistencia Asíncrona y Cifrado

### Procesamiento en segundo plano

Cuando cualquier componente (filtro, aspecto o código programático) invoca `AuditLogService.logAction()`, la operación no se ejecuta en el hilo de la petición HTTP. El método está anotado con `@Async` y utiliza un `ThreadPoolTaskExecutor` dedicado con entre 4 y 8 hilos y una cola de 1000 tareas. Si la cola se satura, la política de rechazo `CallerRunsPolicy` hace que el hilo invocante ejecute la tarea directamente, actuando como contrapresión natural. Esto garantiza que una base de datos de auditoría lenta o fallida nunca degrade el tiempo de respuesta de las peticiones de los usuarios.

El executor de tareas asíncronas está configurado con el nombre `auditTaskExecutor` y asigna a sus hilos el prefijo `audit-`, lo que facilita su identificación en logs y herramientas de monitoreo.

### Cifrado AES-256-GCM

Antes de persistir el registro, el servicio aplica cifrado a los tres campos que pueden contener datos sensibles: `requestBody`, `changesBefore` y `changesAfter`. El algoritmo utilizado es AES-256 en modo GCM (Galois/Counter Mode).

El modo GCM fue elegido específicamente porque proporciona simultáneamente confidencialidad y autenticación. A diferencia de otros modos como CBC, GCM no requiere un mecanismo separado de HMAC para verificar la integridad del texto cifrado; el propio descifrado falla si el ciphertext fue alterado, gracias a su etiqueta de autenticación de 128 bits.

Para cada operación de cifrado se genera un vector de inicialización aleatorio de 12 bytes usando `SecureRandom`. El resultado final es la concatenación del IV seguido del ciphertext (que incluye la etiqueta de autenticación GCM). Esta estructura permite que el descifrado extraiga primero el IV de los primeros 12 bytes y luego procese el resto.

La clave de cifrado se obtiene de la propiedad de configuración `audit.encryption.key`, que debe ser una cadena de 32 bytes codificada en Base64. En tiempo de inicialización, el servicio decodifica esta cadena y la convierte en una `SecretKey` de AES.

### Sanitización de contraseñas

Antes de cifrar, el contenido JSON de los campos se analiza y se reemplazan las contraseñas. Los campos `password`, `passwordConfirm`, `currentPassword` y `newPassword` se sustituyen por el texto `***ENMASCARADO***`. Esta sanitización es recursiva: si un campo contiene un objeto anidado que a su vez contiene estos campos, también se enmascaran. Si el contenido no es JSON válido, se preserva el texto plano. Esta sanitización ocurre tanto en el filtro (para `requestBody`) como en el aspecto (para argumentos y estados), constituyendo defensa en profundidad.

---

## Integridad mediante HMAC-SHA256

### Cálculo del hash

Cada registro de auditoría incluye un campo `integrityHash` que actúa como sello de integridad. El hash se calcula después de la primera persistencia (que asigna el identificador UUID a la entidad) pero antes de una segunda persistencia que almacena el hash.

El proceso construye un mapa ordenado alfabéticamente conteniendo los campos identificativos del registro: identificador único, tenant, usuario, correo, tipo de acción, tipo de recurso, identificador del recurso, fecha de creación y dirección IP. Para los campos que contienen datos cifrados (`requestBody`, `changesBefore`, `changesAfter`), no se incluye su valor directamente sino el hash SHA-256 de sus bytes. Esto significa que el sello de integridad cubre incluso los campos cifrados sin necesidad de descifrarlos.

El mapa ordenado se serializa a su representación textual y sobre ella se calcula un HMAC-SHA256 usando una clave secreta independiente de la clave de cifrado. El resultado es una cadena hexadecimal de 64 caracteres.

### Verificación

La verificación de un registro recalcula el hash siguiendo exactamente el mismo procedimiento: se toma el registro, se anula temporalmente su campo `integrityHash`, se genera el hash, se restaura el valor original y se compara. Si los hashes no coinciden, se considera que el registro fue manipulado.

La verificación se ejecuta automáticamente cada vez que se consulta un registro individual. Si se detecta manipulación, se emite una advertencia en los logs del servidor y el campo `valid` del DTO de respuesta se establece en `false`. El frontend muestra esta condición como un banner rojo de advertencia en el modal de detalle.

Además, existe un endpoint específico para verificar la integridad de un registro individual y otro para verificación en lote. El endpoint por lotes recorre los registros más recientes (hasta un límite configurable, por defecto 100), verifica cada uno y retorna el resultado individual junto con un conteo de manipulaciones detectadas.

---

## Almacenamiento en Base de Datos de Auditoría

### Estructura de la tabla

La tabla `audit_log` en la base de datos de auditoría contiene los siguientes grupos de columnas:

- **Identidad**: identificador único UUID versión 7 (generado por Hibernate antes de la inserción), marcas de tiempo de creación y actualización (heredadas de la superclase `RootEntity`). El uso de UUIDv7 como clave primaria proporciona ordenamiento temporal aproximado y evita colisiones en entornos distribuidos.
- **Contexto multi-tenant y usuario**: identificador del tenant, identificador del usuario, correo electrónico y nombre del usuario. Estos campos permanecen en texto plano para permitir consultas y filtrados sin necesidad de descifrar.
- **Acción y recurso**: tipo de acción (enum con 11 valores), tipo de recurso (nombre de la entidad afectada), identificador y nombre del recurso.
- **Contexto HTTP**: método y ruta de la petición.
- **Red y cliente**: dirección IP (soporta IPv4 e IPv6 hasta 45 caracteres) y cadena User-Agent.
- **Datos sensibles cifrados**: cuerpo de la petición, estado anterior y estado posterior. Estos tres campos son de tipo `BYTEA` (binario) y contienen el resultado del cifrado AES-256-GCM. Son completamente ilegibles sin la clave de cifrado.
- **Respuesta**: código de estado HTTP y mensaje de error (en texto plano).
- **Integridad**: hash HMAC-SHA256 de 64 caracteres.

### Índices

Se crearon siete índices para optimizar las consultas más frecuentes:

- Índice compuesto por tenant y fecha descendente: optimiza las consultas que filtran por organización clínica y ordenan por antigüedad.
- Índice compuesto por usuario y fecha descendente: optimiza el historial de acciones de un usuario específico.
- Índice compuesto por tipo de recurso e identificador: permite consultar el historial completo de modificaciones de una entidad concreta (por ejemplo, un paciente específico).
- Índice por tipo de acción: acelera el filtrado por operación (todos los CREATE, todos los DELETE, etc.).
- Índice por dirección IP: útil en investigaciones forenses cuando se rastrea actividad desde una IP específica.
- Índice por fecha descendente: optimiza el ordenamiento cronológico inverso, que es el criterio por defecto.
- Índice único por hash de integridad: garantiza que no pueda haber dos registros con el mismo sello de integridad y acelera las búsquedas por hash.

---

## API REST de Consulta

### Control de acceso

El acceso a todos los endpoints bajo `/api/audit` está restringido exclusivamente a usuarios que posean el rol `ROLE_SUPERUSER`. Esta restricción se aplica en dos niveles: en la configuración de seguridad de Spring a nivel de patrón de URL, y mediante la anotación `@PreAuthorize` en la clase del controlador como defensa en profundidad.

El rol `ROLE_SUPERUSER` es creado automáticamente por el inicializador de datos de la aplicación durante el arranque. El superusuario del sistema ya lo posee. Los administradores de tenant sin este rol reciben un error HTTP 403 si intentan acceder.

El aspecto de filtro multi-tenant (`TenantFilterAspect`) excluye explícitamente al `AuditLogRepository` de su interceptación. Esto significa que las consultas de auditoría no están sujetas al filtro de tenant de Hibernate, permitiendo que el SUPERUSER vea los registros de todas las organizaciones clínicas.

### Endpoints disponibles

El controlador expone cinco endpoints:

- **Listado paginado con filtros**: soporta filtros por tenant, usuario, tipo de acción, tipo de recurso y rango de fechas. Los parámetros de fecha se reciben como fecha local (día, mes, año) y se convierten internamente a marcas de tiempo con zona horaria UTC. La respuesta es una página de Spring Data con contenido, total de elementos, total de páginas y número de página actual. Los campos cifrados se descifran automáticamente en el DTO de respuesta.

- **Detalle individual**: retorna un registro completo con todos los campos descifrados y el resultado de la verificación de integridad. Si el registro fue manipulado, se emite una advertencia en los logs.

- **Verificación de integridad individual**: endpoint dedicado que retorna el identificador del registro, si es válido o no, y un mensaje descriptivo.

- **Verificación de integridad por lotes**: recibe un parámetro de límite y verifica los registros más recientes. Retorna una lista de resultados individuales y registra un conteo total en los logs.

- **Exportación CSV**: aplica los mismos filtros que el listado pero retorna un archivo CSV con encabezados de columna y una columna adicional que indica si cada registro pasó la verificación de integridad. El nombre del archivo incluye la fecha actual. El límite máximo es de 100.000 registros.

---

## Logging en Google Cloud

El sistema está preparado para integración con Google Cloud Logging. Un archivo `logback-spring.xml` configura dos appenders: uno de consola siempre activo y uno de Cloud Logging condicionado a la activación del perfil Spring `gcp`.

Cuando el perfil `gcp` está activo, una dependencia de Google Cloud Logging para Logback envía automáticamente las líneas de log a GCP. Los logs de auditoría se emiten con el nombre de log `audit-log` y un formato estructurado que incluye tenant, usuario, acción, recurso, estado HTTP e identificador del registro.

Cuando el perfil `gcp` no está activo (comportamiento por defecto), los logs de auditoría se envían únicamente a la consola, lo que permite desarrollo y pruebas sin depender de infraestructura cloud.

---

## Buffer de Emergencia y Tolerancia a Fallos

Si la base de datos de auditoría no está disponible en el momento de persistir un registro, el servicio no pierde el dato. En su lugar, coloca la entidad en una cola concurrente en memoria con capacidad máxima configurable (por defecto 1000 entradas). Si la cola alcanza su capacidad máxima, se descarta la entrada más antigua (política FIFO).

Un proceso programado despierta cada 30 segundos, drena la cola e intenta persistir cada entrada pendiente. Si el reintento tiene éxito, el registro queda correctamente almacenado con su hash de integridad. Si falla nuevamente, la entrada se reencola para el siguiente ciclo. Este mecanismo proporciona una ventana de tolerancia frente a caídas temporales de la base de datos de auditoría sin afectar la operación normal de la aplicación.

---

## Interfaz Web de Auditoría (Frontend Angular)

### Arquitectura del módulo

El frontend de auditoría es un módulo independiente bajo `features/audit/` que sigue el patrón de componentes standalone de Angular. Se carga de forma diferida (lazy loading) y solo está accesible para usuarios con el rol `ROLE_SUPERUSER`. Las rutas de la aplicación principal incluyen la ruta `/audit` protegida por el guarda de permisos correspondiente, y el sidebar muestra el ítem "Auditoria" únicamente a usuarios autorizados.

### Modelo de datos y servicio HTTP

Las interfaces TypeScript reflejan exactamente los DTOs del backend. El servicio HTTP expone cinco métodos que mapean uno a uno con los endpoints de la API REST. Utiliza `HttpParams` para construir los parámetros de consulta de forma tipada y segura. Las llamadas se realizan a través del interceptor de autenticación global, que añade automáticamente el token JWT y el encabezado de tenant.

### Gestión de estado reactiva

En lugar de usar un store global como NgRx o un hook de React como SWR, el módulo utiliza un servicio inyectable con señales de Angular para gestionar el estado reactivo. Este servicio expone:

- Una señal de filtros que contiene el estado actual de todos los criterios de búsqueda. Cualquier modificación de un filtro reinicia automáticamente la página a cero.
- Un efecto que reacciona a cambios en los filtros y dispara automáticamente una nueva consulta a la API.
- Señales computadas derivadas de la página de resultados: lista de registros, total de elementos, total de páginas, página actual, y si hay filtros activos.
- Una señal para el registro seleccionado que controla la visibilidad del modal de detalle.
- Una señal para mostrar u ocultar el panel de filtros avanzados.

Este enfoque elimina la necesidad de gestionar suscripciones y ciclos de vida manualmente: las señales se limpian automáticamente cuando el componente se destruye.

### Componentes de interfaz

La página de auditoría se compone de cuatro componentes:

- **Página contenedora**: orquesta los subcomponentes, muestra el encabezado con título y subtítulo, y gestiona los estados de carga y error.

- **Panel de filtros**: ofrece seis campos de búsqueda (entidad, operación, ID de usuario, ID de tenant, fecha desde y fecha hasta). El panel es colapsable y muestra un indicador visual cuando hay filtros activos. Incluye un botón para limpiar todos los filtros y un botón de exportación CSV que descarga el archivo respetando los filtros actuales y muestra una animación de carga durante la descarga.

- **Tabla de resultados**: implementa un diseño dual responsivo. En pantallas de escritorio muestra una tabla HTML con ocho columnas: fecha, usuario, operación, entidad, identificador del registro, IP, código de estado HTTP y botón de detalle. En dispositivos móviles, los mismos datos se presentan como tarjetas apiladas con información condensada. Las operaciones se codifican con colores semánticos: verde para creación, ámbar para actualización, rojo para eliminación, azul para autenticación. La paginación permite navegar entre páginas con botones de anterior y siguiente.

- **Modal de detalle**: se abre al hacer clic en el botón de ver detalle. Muestra un diseño de dos columnas. La columna izquierda presenta metadata (identificador, fecha, método, ruta, código de estado), información del actor (usuario, IP, tenant) y una alerta roja de advertencia cuando la verificación de integridad falla. La columna derecha muestra el cuerpo de la petición, el estado anterior y el estado nuevo en formato JSON con resaltado de sintaxis, así como el User-Agent. El modal se cierra al hacer clic en el botón de cierre o en el fondo oscuro.

---

## Configuración de Seguridad y Permisos

### Cadena de filtros de Spring Security

La configuración de seguridad se modificó para añadir el filtro de auditoría en la posición correcta de la cadena. El orden es:

1. Filtros de infraestructura de Spring Security (codificación, CORS, cabeceras)
2. Filtro JWT de autenticación (extrae el token, valida, establece el contexto de seguridad)
3. Filtro de auditoría (captura la operación ya autenticada)
4. Filtro de autorización (verifica roles y permisos)
5. Resto de filtros hasta llegar al controlador

El filtro de auditoría se añade con la directiva `addFilterAfter`, que lo coloca inmediatamente después del filtro JWT. Su registro como bean de servlet está deshabilitado para evitar una doble ejecución.

### Exclusión del filtro multi-tenant

El aspecto `TenantFilterAspect` intercepta todos los repositorios Spring Data JPA para activar el filtro de tenant de Hibernate, excepto dos: `TenantRepository` (que consulta la tabla de tenants, naturalmente no filtrable por tenant) y `AuditLogRepository`. Esta exclusión es necesaria porque:

- Los registros de auditoría no heredan de `BaseEntity` y por lo tanto no tienen el filtro `@Filter` de Hibernate.
- Las operaciones de auditoría se ejecutan en hilos asíncronos donde el `TenantContext` (que es un `ThreadLocal`) está vacío.
- El SUPERUSER debe poder consultar registros de todos los tenants sin restricciones.

### Sanitización de proxies

El filtro de auditoría soporta la configuración de una lista de proxies de confianza para la correcta resolución de direcciones IP. Sin proxies configurados, se toma la IP más a la izquierda del encabezado `X-Forwarded-For`. Con proxies configurados, se recorre la lista de derecha a izquierda descartando las IPs de proxies conocidos hasta encontrar la IP real del cliente. La lista de proxies se configura mediante una propiedad que acepta direcciones IP separadas por comas.

---

## Monitoreo de Rendimiento

El sistema incluye un interceptor de Spring MVC que registra el tiempo de inicio de cada petición en un atributo del request y, al finalizar, calcula la duración. Si una petición excede los 5 segundos, se emite una advertencia en los logs con el método, la ruta, la duración y el código de estado. Esto proporciona visibilidad sobre posibles cuellos de botella sin afectar la funcionalidad de auditoría.

El interceptor está registrado para todas las rutas bajo `/api/`, excluyendo documentación, health checks y métricas, y cumple una función complementaria al filtro de auditoría sin interferir con él.

---

## Flujo Completo de una Operación Auditada

### Escritura (captura)

1. Un usuario autenticado realiza una petición HTTP mutante (por ejemplo, `PUT /api/module_users/patients/abc123`).
2. El filtro JWT valida el token, extrae la identidad del usuario y su tenant, y establece el contexto de seguridad.
3. El filtro de auditoría determina que la petición debe ser auditada (método PUT, ruta bajo `/api/`).
4. El filtro envuelve el request en `CachedBodyHttpServletRequest`, cacheando el cuerpo para lectura dual.
5. La cadena de filtros continúa. El controlador recibe la petición y la despacha al servicio correspondiente.
6. Si el método del servicio está anotado con `@Auditable`:
   a. El aspecto extrae los argumentos y los sanitiza.
   b. Consulta la base de datos para obtener el estado anterior de la entidad.
   c. Invoca el método real del servicio.
   d. Captura el resultado como estado posterior.
   e. Construye un registro de auditoría con toda esta información.
   f. Activa el flag de `AuditContext` y envía el registro al servicio de logs.
7. Al retornar al filtro de auditoría después del `filterChain.doFilter()`:
   a. El filtro verifica `AuditContext`. Si el aspecto ya registró la operación, omite la suya.
   b. Si no, construye su propio registro con el contexto HTTP.
8. El servicio de logs recibe el registro, sanitiza contraseñas en los datos, cifra los campos sensibles con AES-256-GCM, persiste en la base de datos de auditoría, recalcula y guarda el hash de integridad HMAC-SHA256, y emite una línea de log estructurada. Todo esto ocurre en un hilo del pool asíncrono.
9. Si la base de datos de auditoría no está disponible, el registro se encola en el buffer de emergencia para reintento posterior.

### Lectura (consulta)

1. Un SUPERUSER navega a la página de auditoría en el frontend.
2. Al cargar, el estado reactivo dispara una consulta `GET /api/audit?page=0&size=20` con el token JWT.
3. El controlador recibe la petición, verifica el rol SUPERUSER y construye los filtros.
4. El servicio de logs consulta el repositorio de auditoría usando especificaciones JPA dinámicas.
5. Las entidades recuperadas se mapean a DTOs, descifrando los campos BLOB mediante AES-256-GCM.
6. La respuesta paginada se retorna como JSON.
7. El frontend actualiza sus señales y renderiza la tabla con los resultados.
8. Si el usuario hace clic en "Ver Detalle", se muestra el modal con todos los campos, incluyendo el estado de integridad. Si el registro fue manipulado, aparece una alerta roja.
9. El usuario puede exportar los resultados actuales como archivo CSV.

---

## Consideraciones de Seguridad

### Cifrado en reposo
Todos los datos sensibles almacenados en la base de auditoría están cifrados con AES-256-GCM. Sin la clave de cifrado, los campos `requestBody`, `changesBefore` y `changesAfter` son binarios opacos. Un administrador de base de datos con acceso directo a PostgreSQL no puede leer el contenido de estos campos.

### Integridad verificable
Cada registro incluye un sello HMAC-SHA256 que cubre su identidad y el hash de sus datos cifrados. Cualquier modificación — ya sea de un campo en texto plano o de los datos cifrados — es detectable recalcular el hash. La verificación está integrada en cada consulta de detalle y disponible como endpoint independiente.

### Sanitización de credenciales
Las contraseñas se enmascaran en dos puntos independientes del código: en el aspecto `@Auditable` (al serializar argumentos) y en el servicio de logs (al procesar el cuerpo de la petición). Esto garantiza que incluso si un desarrollador anota un método que recibe credenciales, éstas no quedarán registradas.

### Aislamiento de base de datos
La base de datos de auditoría es físicamente independiente de la base de datos principal. Un fallo en la base de auditoría no afecta la operación normal de la aplicación. Las conexiones, transacciones y migraciones son completamente separadas.

### Acceso restringido
Solo los usuarios con el rol `ROLE_SUPERUSER` pueden acceder a los endpoints de auditoría. Este rol es asignado exclusivamente al equipo de desarrollo. Los administradores de tenant y el personal médico no tienen visibilidad de estos registros.

### Protección contra duplicación
El mecanismo de `AuditContext` basado en `ThreadLocal` garantiza que cuando un método anotado con `@Auditable` es invocado desde un controlador, solo se genera un registro de auditoría (el del aspecto, que es más detallado), evitando entradas duplicadas.

---

## Tabla Resumen de Componentes

| Componente | Ubicación | Función Principal |
|---|---|---|
| `AuditLog` | Backend - Entity | Modelo de datos de la tabla `audit_log` |
| `ActionType` | Backend - Enum | 11 tipos de acciones registrables |
| `AuditLogFilter` | Backend - Filter | Captura global de peticiones HTTP mutantes |
| `CachedBodyHttpServletRequest` | Backend - Filter | Cacheo del cuerpo para lectura múltiple |
| `AuditContext` | Backend - Filter | Flag ThreadLocal para deduplicación |
| `@Auditable` | Backend - Annotation | Marca métodos de servicio para captura detallada |
| `AuditAspect` | Backend - Aspect | Captura before/after con sanitización |
| `AuditLogService` | Backend - Service | Persistencia asíncrona, cifrado, buffer, consultas |
| `AuditEncryptionService` | Backend - Service | AES-256-GCM con IV aleatorio |
| `AuditHashService` | Backend - Service | HMAC-SHA256 con SHA-256 de BLOBs |
| `AuditController` | Backend - Controller | 5 endpoints REST protegidos |
| `MainDbConfig` | Backend - Config | DataSource, EMF, Flyway de la DB principal |
| `AuditDbConfig` | Backend - Config | DataSource, EMF, Flyway de la DB de auditoría |
| `AuditAsyncConfig` | Backend - Config | Pool de hilos para persistencia asíncrona |
| `TenantFilterAspect` | Backend - Security | Excluye `AuditLogRepository` del filtro multi‑tenant |
| `AuditService` | Frontend - Service | Cliente HTTP para los 5 endpoints |
| `AuditState` | Frontend - Service | Estado reactivo con señales de Angular |
| `AuditPageComponent` | Frontend - Page | Contenedor principal de la interfaz |
| `AuditFiltersComponent` | Frontend - Component | 6 campos de filtro + exportación CSV |
| `AuditTableComponent` | Frontend - Component | Tabla desktop + tarjetas mobile + paginación |
| `AuditDetailModalComponent` | Frontend - Component | Modal de detalle con verificación de integridad |
