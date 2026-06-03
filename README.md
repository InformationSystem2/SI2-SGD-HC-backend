# SGD-HC — Sistema de Gestión Documental y Clínico

**Sistemas de Información II — Universidad Autónoma Gabriel René Moreno (UAGRM)**

## Entregables

| Recurso | Enlace |
|---|---|
| Documento técnico (PDF) | [`docs/refactoring_permissions.md`](docs/refactoring_permissions.md) |
| Repositorio público | https://github.com/evert-aov/SI2-SGD-HC-backend |

---

## Información del Proyecto

SGD-HC (Sistema de Gestión Documental y Clínico) es una plataforma empresarial robusta y multitenant diseñada para la digitalización, centralización y control estricto de historias clínicas, plantillas médicas, imágenes diagnósticas en formato **DICOM** y documentos clínicos con soporte para procesamiento **OCR**.

El sistema se enfoca en resolver el aislamiento absoluto de datos entre clínicas (Multitenancy) y la protección granular de información sensible mediante un motor dinámico de control de acceso basado en roles y permisos (RBAC) a nivel de atributos de datos, garantizando que el personal médico acceda únicamente a los campos autorizados de la historia clínica.

---

## Arquitectura General

```
  Petición HTTP (Cliente) 
         │  
         ▼
  JwtAuthenticationFilter ──► Extrae username/tenantId del JWT
         │
         ▼
  TenantContext / FilterAspect ──► Establece Tenant ID en ThreadLocal y activa filtro Hibernate
         │
         ├─ Spring Security ──► Verifica permisos de endpoints (@PreAuthorize)
         │
  Controllers / Services ──► Valida permisos de escritura a nivel de datos (SecurityUtils)
         │
  Mappers (toResponseDto) ──► Filtra y oculta atributos sin permiso de lectura (JsonInclude)
         │
  PostgreSQL / Redis ──► Acceso a datos aislado y caché de sesiones
```

---

## Estructura del Proyecto

```
sgd_spring-boot/
├── src/main/java/com/sgd_hc/
│   ├── DataInitializer.java        # Inicializador y seed del sistema (clínicas extra y pacientes)
│   ├── dicom/                      # Módulo de Imágenes Diagnósticas (DICOM)
│   │   ├── controller/
│   │   ├── entity/
│   │   ├── repository/
│   │   └── service/                # Parser y almacenamiento de metadatos DICOM
│   ├── documents/                  # Módulo de Documentos y Plantillas Clínicas
│   │   ├── controller/             # DocumentTemplateController, DocumentController, HistorialController
│   │   ├── dto/                    # Schemas de petición y respuesta (con JsonInclude)
│   │   ├── entity/                 # Document, DocumentTemplate, DocumentOcrMetadata
│   │   ├── mapper/                 # DocumentMapper y DocumentTemplateMapper (filtrado por permisos)
│   │   ├── repository/
│   │   └── service/                # Lógica de documentos, estados, OCR y almacenamiento
│   ├── patients/                   # Módulo de Pacientes
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/                 # Patient
│   │   ├── mapper/                 # PatientMapper (filtrado por permisos)
│   │   └── service/                # Lógica de pacientes e historial clínico
│   ├── security/                   # Módulo de Autenticación, JWT y Aislamiento Multitenant
│   │   ├── config/                 # Aspectos y filtros de Hibernate para Tenants
│   │   ├── details/                # SecurityUser (carga dinámica de roles y permisos desde BD)
│   │   ├── filter/                 # JwtAuthenticationFilter
│   │   ├── service/                # JwtService, AuthService, UserDetailsServiceImpl
│   │   └── utils/                  # SecurityUtils (métodos estáticos de autorización centralizados)
│   ├── tenants/                    # Módulo de Gestión de Tenants y Suscripciones
│   │   ├── controller/             # TenantController (onboarding, renovación, planes)
│   │   ├── dto/
│   │   ├── entity/                 # Tenant
│   │   ├── mapper/
│   │   └── service/                # TenantService (onboarding público y gestión superadmin)
│   └── users/                      # Módulo de Usuarios y Permisos
│       ├── controller/             # UserController, RoleController, PermissionController
│       ├── dto/                    # UserCreateDto, RoleCreateDto, PermissionResponseDto
│       ├── entity/                 # User, Role, Permission (relaciones con cascada)
│       ├── mapper/
│       └── service/
│
├── src/main/resources/
│   ├── db/migration/               # Migraciones de base de datos Flyway
│   │   ├── V1__core_tenants_users.sql
│   │   ├── V10__refactor_roles_permissions_serial.sql
│   │   ├── V11__insert_default_permissions.sql  # Script SQL de inicialización de permisos
│   │   └── ...
│   ├── application.properties      # Configuración de puerto, DB, Redis, Azure y Seguridad
│   └── default-branding.json
│
├── docs/
│   └── refactoring_permissions.md  # Documentación detallada de permisos y JWT
├── build.gradle                    # Archivo de construcción y dependencias del sistema
└── README.md
```

---

## Tecnologías

### Backend & Core
| Tecnología | Versión | Uso |
|---|---|---|
| Java | 21 / 25 | Lenguaje de programación principal |
| Spring Boot | 4.0.5 | Framework base de la aplicación |
| Spring Security | Incluido | Autenticación y control de accesos por JWT |
| Hibernate / JPA | Incluido | ORM y filtrado de datos por inquilino (Tenant) |
| Flyway | Incluido | Migración y control de versiones de BD |
| PostgreSQL | 18 | Base de datos relacional principal |
| Redis | — | Caché de sesiones y tokens |

### Librerías Especializadas e Integraciones
| Tecnología | Versión | Uso |
|---|---|---|
| io.jsonwebtoken (JJWT)| 0.12.5 | Generación y lectura de tokens JWT compactos |
| dcm4che | 5.34.3 | Manipulación y parsing de metadatos de archivos médicos DICOM |
| Azure Storage Blob | 12.29.0 | Almacenamiento seguro de archivos clínicos en la nube |
| Net Datafaker | 2.4.2 | Generación sintética de datos clínicos de prueba |

---

## Instalación y Ejecución

### 1. Requisitos Previos
* PostgreSQL (v15 o superior) en ejecución.
* Redis Server en ejecución.
* Java Development Kit (JDK) 21 instalado.

### 2. Configurar Variables de Entorno
Cree un archivo `.env` en la raíz del proyecto basándose en `.env.example`:

```env
PORT=8080
DB_URL=jdbc:postgresql://localhost:5432/sgd_hc
DB_USERNAME=postgres
DB_PASSWORD=postgres
JWT_SECRET=tu_clave_secreta_super_larga_en_base64_aqui
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000
REDIS_URL=redis://localhost:6379
OCR_SERVICE_URL=http://localhost:8001
UPLOAD_DIR=uploads
AZURE_STORAGE_CONNECTION_STRING=tu_connection_string_azure
AZURE_STORAGE_CONTAINER_NAME=uploads
```

### 3. Compilar e Iniciar la Aplicación

Compilar el proyecto omitiendo las pruebas unitarias:
```bash
./gradlew build -x test
```

Iniciar el servidor de desarrollo Spring Boot:
```bash
./gradlew bootRun
```

La API estará disponible en: `http://localhost:8080`  
La documentación interactiva de la API (Swagger UI): `http://localhost:8080/swagger-ui.html`

---

## Endpoints Principales

### Autenticación y Onboarding
| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/auth/login` | Inicio de sesión de usuarios y devolución de JWT optimizado |
| `POST` | `/api/auth/refresh` | Renovación de tokens de acceso usando Refresh Token |
| `POST` | `/api/tenants/public/register` | Registro público de una nueva clínica (Tenant) |
| `POST` | `/api/tenants/public/pay` | Procesamiento simulado de pago para activar la suscripción |

### Gestión de Tenants (Inquilinos)
| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/tenants/current/info` | Retorna los datos básicos de la clínica actual del usuario |
| `PUT` | `/api/tenants/current/info` | Actualiza la información básica del Tenant actual |
| `GET` | `/api/tenants/admin/list` | Listado paginado de inquilinos (exclusivo Superusuario) |
| `GET` | `/api/tenants/admin/{id}` | Detalle completo de un Tenant específico (exclusivo Superusuario) |

### Documentos Clínicos
| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/documents` | Crea un nuevo documento clínico basado en plantilla |
| `POST` | `/api/documents/external` | Registra un documento clínico de fuente externa (PDF/Imagen adjunta) |
| `GET` | `/api/documents/{id}` | Recupera el detalle de un documento médico (filtrado por permisos) |
| `PUT` | `/api/documents/{id}` | Actualiza metadatos o contenido clínico |
| `PATCH` | `/api/documents/{id}/status` | Cambia el estado en el flujo del documento (Borrador -> Revisión -> Finalizado) |

---

## Módulo de Seguridad: algoritmos y políticas

### Aislamiento por Tenant (Multitenancy)
La base de datos relacional utiliza una columna `tenant_id` para aislar las tablas. A través de un Aspecto AOP (`TenantFilterAspect.java`), se interceptan las solicitudes autorizadas, se extrae el ID de la clínica del JWT y se inyecta en un filtro nativo de Hibernate (`TenantContext`). Cualquier consulta SQL resultante se limita de forma transparente al contexto de la clínica del usuario activo.

### Permisos Dinámicos y JWT Optimizado
* **JWT Liviano**: Para evitar que la carga útil del token sea extremadamente grande (debido a los más de 100 permisos de atributos), el token JWT solo viaja con el listado de roles asignados.
* **Carga en Memoria de Spring Security**: Al realizarse una petición, el filtro de Spring Security invoca dinámicamente el método `SecurityUser.getAuthorities()`, resolviendo y aplanando en tiempo de ejecución los permisos de cada rol directamente de la base de datos.
* **Seguridad de Atributos**: El componente `SecurityUtils.java` expone validaciones estáticas reutilizables para garantizar que la API deniegue la lectura o edición de cualquier atributo no autorizado.
* **Exclusión de Campos Nulos**: Todas las respuestas serializadas ocultan dinámicamente los campos nulos que han sido rechazados por permisos de lectura, mediante la anotación `@JsonInclude`.

---

## Por qué control de accesos a nivel de atributos y no de endpoints simple

| Tipo de Control | Permite ocultar campos sensibles | Flexibilidad por Rol | Complejidad de API |
|---|---|---|---|
| **Control por Endpoint (`/paciente/{id}`)** | No (Retorna todo el objeto o nada) | Baja | Baja |
| **Control a nivel de Atributo (SGD-HC)** | **Sí** (Oculta dirección, diagnóstico, etc.) | **Alta** (Granular por permiso) | Media (Mapeo dinámico) |

Al gestionar historias clínicas y datos médicos protegidos por leyes de privacidad (ej. HIPAA), un médico general debe poder ver el historial clínico completo de un paciente, pero un recepcionista solo debe poder visualizar los datos demográficos básicos (como nombre y teléfono), bloqueando campos sensibles como diagnósticos o recetas médicas en el mismo endpoint `/api/patients/{id}`.

---

## Documentación Técnica

- [`docs/refactoring_permissions.md`](docs/refactoring_permissions.md) — Análisis arquitectónico de la refactorización de permisos, diseño del JWT optimizado y guías de uso de `SecurityUtils`.

---

## Equipo

| Integrante | Rol |
|---|---|
| **Evert Rodríguez Araúz** | Backend Developer / Arquitecto de Software |
| *[Integrante 2]* | *[Rol]* |
| *[Integrante 3]* | *[Rol]* |

---

*Proyecto desarrollado para la materia de Sistemas de Información II — UAGRM*
