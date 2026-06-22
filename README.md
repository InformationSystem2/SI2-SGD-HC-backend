# SGD-HC — Backend · Spring Boot

**Sistema de Gestión Documental y Clínico**  
Sistemas de Información II · Universidad Autónoma Gabriel René Moreno (UAGRM)

| Recurso | Enlace |
|---|---|
| Repositorio público | https://github.com/evert-aov/SI2-SGD-HC-backend |
| Documentación de permisos | [`DOCKER.md`](DOCKER.md) |
| Documentos técnicos | [`documents/`](../documents/) |

---

## Descripción

SGD-HC es una plataforma **multitenant** para la digitalización y control de historias clínicas, plantillas médicas, imágenes diagnósticas **DICOM** y documentos clínicos con soporte **OCR**. Cada clínica (tenant) opera en total aislamiento de datos garantizado por filtros Hibernate a nivel de base de datos.

---

## Arquitectura

```
Petición HTTP
      │
      ▼
JwtAuthenticationFilter          ← Extrae username + tenantId del JWT
      │
      ▼
TenantFilterAspect (AOP)         ← Inyecta tenant_id en ThreadLocal → filtro Hibernate
      │
      ├─ Spring Security          → @PreAuthorize por endpoint
      │
      ▼
Controllers → Services           → Lógica de negocio + validación de permisos
      │
      ▼
PostgreSQL / Redis                → Datos aislados por tenant · caché de sesiones
```

---

## Módulos

```
src/main/java/com/sgd_hc/
├── audit/           # Registro de auditoría de acciones (AuditLog)
├── backups/         # Gestión y descarga de respaldos de base de datos
├── config/          # Configuración global (CORS, OpenAPI, Multipart)
├── dicom/           # Imágenes diagnósticas: parser de metadatos DICOM (dcm4che)
├── documents/       # Documentos clínicos, plantillas, OCR, estados y versiones
├── notifications/   # Notificaciones push (FCM) y gestión de tokens de dispositivo
├── patients/        # Pacientes e historial clínico
├── security/        # JWT, Spring Security, multitenancy, UserDetails dinámico
├── tenants/         # Onboarding de clínicas, planes, suscripciones y branding
├── users/           # Usuarios, roles y permisos (RBAC)
└── workflow/        # Flujos de revisión, tareas, delegaciones y comentarios
```

### Recursos

```
src/main/resources/
├── db/migration/
│   ├── audit/       # V1__create_audit_log.sql
│   └── main/        # V1 … V28+ (esquema completo y migraciones incrementales)
├── application.properties
└── default-branding.json
```

---

## Tecnologías

| Tecnología | Versión | Rol |
|---|---|---|
| Java | 21 | Lenguaje principal |
| Spring Boot | 4.x | Framework base |
| Spring Security | — | Autenticación JWT + RBAC |
| Hibernate / JPA | — | ORM + filtrado multitenant |
| Flyway | — | Versionado de esquema SQL |
| PostgreSQL | 15+ | Base de datos relacional |
| Redis | — | Caché de sesiones y tokens |
| JJWT | 0.12.x | Firma y lectura de JWT |
| dcm4che | 5.34.x | Parser de archivos DICOM |
| Azure Storage Blob | 12.29.x | Almacenamiento de archivos en nube |
| Net Datafaker | 2.4.x | Datos sintéticos de prueba |

---

## Puesta en marcha

### Requisitos previos

- JDK 21
- PostgreSQL 15+
- Redis

### Variables de entorno

Crear `.env` en la raíz a partir de `.env.example`:

```env
PORT=8080
DB_URL=jdbc:postgresql://localhost:5432/sgd_hc
DB_USERNAME=postgres
DB_PASSWORD=postgres
JWT_SECRET=<clave_base64_larga>
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000
REDIS_URL=redis://localhost:6379
OCR_SERVICE_URL=http://localhost:8001
UPLOAD_DIR=uploads
AZURE_STORAGE_CONNECTION_STRING=<connection_string>
AZURE_STORAGE_CONTAINER_NAME=uploads
STRIPE_PUBLISHABLE_KEY=tu_key
STRIPE_SECRET_KEY=tu_key
APP_PUBLIC_URL=http://172.17.0.1:8080
ONLYOFFICE_DS_URL=http://localhost:8088
ONLYOFFICE_JWT_SECRET=<mima_jwt>
```

### Compilar y ejecutar

```bash
# Compilar omitiendo tests
./gradlew build -x test

# Iniciar servidor
./gradlew bootRun
```

| Recurso | URL |
|---|---|
| API REST | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |

> Para despliegue con Docker ver [`DOCKER.md`](DOCKER.md).

---

## Endpoints principales

### Autenticación

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/auth/login` | Inicio de sesión → devuelve JWT + refresh token |
| `POST` | `/api/auth/refresh` | Renovación de access token |
| `POST` | `/api/auth/forgot-password` | Solicitud de recuperación de contraseña |
| `POST` | `/api/auth/reset-password` | Restablecimiento con código de verificación |

### Tenants (Clínicas)

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/tenants/public/register` | Registro público de nueva clínica |
| `POST` | `/api/tenants/public/pay` | Procesamiento de pago de suscripción |
| `GET` | `/api/tenants/current/info` | Datos de la clínica del usuario autenticado |
| `PUT` | `/api/tenants/current/info` | Actualización de datos y branding |
| `GET` | `/api/tenants/admin/list` | Listado de todos los tenants (solo superusuario) |

### Documentos

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/documents` | Crear documento a partir de plantilla |
| `POST` | `/api/documents/external` | Registrar documento externo (PDF/imagen) |
| `GET` | `/api/documents/{id}` | Detalle de documento |
| `PUT` | `/api/documents/{id}` | Actualizar contenido o metadatos |
| `PATCH` | `/api/documents/{id}/status` | Cambio de estado del flujo documental |

### Plantillas

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/templates` | Listar plantillas del tenant |
| `POST` | `/api/templates` | Crear plantilla personalizada |
| `PUT` | `/api/templates/{id}` | Actualizar plantilla |

### Workflows

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/workflows` | Listar flujos activos del tenant |
| `POST` | `/api/workflows` | Crear flujo de revisión |
| `POST` | `/api/workflows/{id}/tasks` | Asignar tarea a usuario |
| `PATCH` | `/api/workflows/{id}/tasks/{taskId}` | Completar / delegar tarea |

---

## Seguridad y multitenancy

### Aislamiento de datos (Multitenancy)
Cada tabla tiene columna `tenant_id`. El aspecto AOP `TenantFilterAspect` extrae el tenant del JWT y lo registra en `TenantContext` (ThreadLocal). Hibernate aplica un filtro global que restringe **todas** las consultas al tenant activo de forma transparente.

### Permisos (RBAC)
- **JWT liviano**: el token solo contiene roles, no los permisos individuales.
- **Carga dinámica**: `SecurityUser.getAuthorities()` resuelve permisos desde base de datos en cada petición.
- **`@PreAuthorize`**: control declarativo por endpoint.
- **`SecurityUtils`**: validaciones de escritura reutilizables en servicios.

### Seeding de plantillas
Las plantillas por defecto se siembran automáticamente al registrar una nueva clínica mediante `TemplateDataSeeder`, inyectado en `TenantService.createTenantWithAdmin()`. Para datos de prueba adicionales (pacientes, clínicas extra) se usa el script Python `sgd_fastapi/seed_db.py`.

---

## Equipo

| Integrante | Rol |
|---|---|
| **Evert Rodríguez Araúz** | Backend Developer / Arquitecto de Software |

---

*Sistemas de Información II · UAGRM*
