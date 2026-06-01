# 🐳 Docker Setup - Proyecto SGD HC

## Descripción General
Este repositorio utiliza Docker para orquestar un entorno de microservicios. La configuración está dividida en dos perfiles de ejecución aislados:

- `docker-compose.dev.yml`: Optimizado para **Desarrollo** (Hot-reload, depuración).
- `docker-compose.deploy.yml`: Optimizado para **Producción** (Nginx, Seguridad, JVM Tuneada).

---

## 🏗️ Arquitectura de Servicios

### Entorno de Desarrollo (sgd-hc-dev)
| Servicio | Container Name | Host Port | Container Port | Volumen (Source) |
|----------|----------------|-----------|----------------|-------------------|
| Frontend | `sgd-hc-frontend` | `4200` | `4200` | `./src -> /app/src` |
| Backend | `sgd-hc-backend` | `8081` | `8080` | `./src -> /app/src` |
| Database | `sgd-hc-postgres` | `5433` | `5432` | `postgres_data` |

### Entorno de Producción (sgd-hc-prod)
| Servicio | Container Name | Host Port | Puerto Int. | Servidor / Runtime |
|----------|----------------|-----------|-------------|--------------------|
| Frontend | `sgd-hc-frontend-prod` | `8080` | `8080` | Nginx (Alpine) |
| Backend | `sgd-hc-backend-prod` | `8081` | `8080` | JRE 26 (Distroless-like) |
| Database | `sgd-hc-postgres-prod` | `5432` | `5432` | PostgreSQL 16 |

---

## 🚀 Flujo de Trabajo (CLI)

### 1. Desarrollo Ágil
```bash
# Iniciar servicios con recreación de imágenes
docker compose -f docker-compose.dev.yml up -d --build

# Ver estados de salud
docker compose -f docker-compose.dev.yml ps
```

### 2. Inspección y Logs
```bash
# Debug en tiempo real
docker compose -f docker-compose.dev.yml logs -f              # Todos
docker compose -f docker-compose.dev.yml logs -f backend      # Solo API
docker compose -f docker-compose.dev.yml logs -f frontend     # Solo Angular

# Acceso directo a Base de Datos
docker compose -f docker-compose.dev.yml exec postgres psql -U superuser -d sgd_hc
```

---

## ⚙️ Configuración de Variables (.env)

El sistema lee las variables del archivo `.env` en la raíz (o usa defaults):

```env
# Puertos (Host)
FRONTEND_PORT=4200
BACKEND_PORT=8081
DB_PORT=5433

# Credenciales DB
DB_NAME=sgd_hc
DB_USERNAME=superuser
DB_PASSWORD=superuser

# URL interna para el Frontend (Inyectada en Build-time en Prod)
API_URL=http://localhost:8081/api
```

---

## 🚢 Despliegue y Optimización

### Backend (`Dockerfile.deploy`)
- **JVM Tuneada**: Se utiliza `UseSerialGC` y límites de RAM dinámicos (`MaxRAMPercentage=75.0`).
- **Parsing de DATABASE_URL**: El entrypoint incluye lógica para parsear URLs complejas de proveedores como Railway o Cloud Run:
  ```bash
  # Lógica interna del Entrypoint
  export DB_URL="jdbc:postgresql://${HOST_DB%/*}/${HOST_DB##*/}"
  ```
- **Seguridad**: Ejecución con usuario `spring` (no-root).

### Frontend (`Dockerfile.deploy`)
- **Nginx Templates**: Utiliza plantillas de Nginx para manejar el puerto dinámico de Cloud Run.
- **Inyección via SED**: El build de Angular se precede por una inyección física de la `API_URL` en el archivo `environment.prod.ts`.

---

## 🔄 Comparativa de Entornos

| Aspecto | Desarrollo | Producción |
|---------|-----------|----------------|
| **Backend** | Gradle `bootRun` | Jar Launcher (Layered) |
| **Frontend** | Angular Dev Server | Nginx Static Serve |
| **Hot-reload** | ✅ Sí (via Volúmenes) | ❌ No (Estático) |
| **Aislamiento** | Project: `sgd-hc-dev` | Project: `sgd-hc-prod` |
| **Imágenes** | `node:22`, `jdk:26` | `nginx:alpine`, `jre:26` |

---

## 🆘 Resolución de Problemas (Troubleshooting)

### Conflicto de Nombres
Si Docker reporta que el nombre de red o contenedor ya existe:
`docker rm -f sgd-hc-postgres sgd-hc-backend sgd-hc-frontend`

### Limpiar Datos
Para resetear la base de datos completamente en dev:
`docker compose -f docker-compose.dev.yml down -v`

### Sincronización de Dependencias
Si modificas `package.json` o `build.gradle`, debes forzar el build sin cache:
`docker compose -f docker-compose.dev.yml build --no-cache`