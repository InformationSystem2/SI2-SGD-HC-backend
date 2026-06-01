

# =============================================================
# Nivel 2: Backup completo con pg_dump (formato custom comprimido)
# Nivel 3: Backup por tenant individual (formato SQL importable)
#
# Ejecución manual (desde la raíz del repo):
#   bash scripts/backup/backup.sh
#
# Ejecución automática:
#   El servicio 'backup' en docker-compose.dev.yml ejecuta este
#   script cada noche via cron (ver Lección 04).
# =============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ "${RUNNING_IN_DOCKER:-false}" = "true" ]; then
    # ── Entorno Docker ──────────────────────────────────────────
    # Las variables de .env ya están inyectadas por env_file en docker-compose.
    # Los volúmenes están montados en rutas absolutas definidas en docker-compose.
    BACKUP_DIR="/backups"
    DB_HOST="${DB_HOST:-postgres}"    # nombre del servicio en la red Docker
    DB_CONN_PORT=5432                 # puerto interno del contenedor postgres
else
# ── Ejecución manual ────────────────────────────────────────
    # SCRIPT_DIR = {repo}/scripts/backup
    # REPO_ROOT  = {repo}  (dos niveles arriba)
    REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
    BACKUP_DIR="$REPO_ROOT/backups"

    ENV_FILE="$REPO_ROOT/.env"
    if [ ! -f "$ENV_FILE" ]; then
        echo "ERROR: No se encontró el archivo .env en: $ENV_FILE"
        echo "Asegúrate de ejecutar este script desde la raíz del repositorio."
        exit 1
    fi
    # set -a: exporta automáticamente todas las variables definidas con 'source'
    # set +a: desactiva ese modo al terminar
    set -a; source "$ENV_FILE"; set +a

    DB_HOST="${DB_HOST:-localhost}"
    # En local, el postgres de Docker está mapeado al puerto 5433 del host
    DB_CONN_PORT="${DB_PORT:-5433}"
fi


# Si DB_NAME no está en .env, lo extraemos desde DB_URL (formato JDBC).
# Ejemplo: jdbc:postgresql://localhost:5432/sgd_hc → sgd_hc
if [ -z "${DB_NAME:-}" ] && [ -n "${DB_URL:-}" ]; then
    DB_NAME="$(echo "$DB_URL" | sed 's|.*\/||' | sed 's|?.*||')"
fi
DB_NAME="${DB_NAME:-sgd_hc}"

# ── Validar variables críticas ──────────────────────────────────
for var in DB_USERNAME DB_PASSWORD DB_NAME; do
    if [ -z "${!var:-}" ]; then
        echo "ERROR: La variable '$var' no está definida. Revisa tu archivo .env"
        exit 1
    fi
done


# ──────────────────────────────────────────────────────────────
# SECCIÓN 2 — INICIALIZACIÓN
# ──────────────────────────────────────────────────────────────
LOG_DIR="$BACKUP_DIR/logs"
DATE=$(date +%Y%m%d)
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_FILE="$LOG_DIR/backup_${TIMESTAMP}.log"
RETENTION_DAYS=30

mkdir -p "$BACKUP_DIR" "$LOG_DIR"

# log() — escribe en pantalla Y en el archivo de log simultáneamente
log() {
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] $1"
    echo "$msg"
    echo "$msg" >> "$LOG_FILE"
}

log "═══════════════════════════════════════════════════════"
log "Iniciando backup SGD-HC"
log "Host DB  : $DB_HOST:$DB_CONN_PORT"
log "Base     : $DB_NAME"
log "Carpeta  : $BACKUP_DIR"
log "Log      : $LOG_FILE"
log "═══════════════════════════════════════════════════════"


# ──────────────────────────────────────────────────────────────
# SECCIÓN 3 — NIVEL 2: BACKUP COMPLETO (pg_dump custom format)
# ──────────────────────────────────────────────────────────────
FULL_BACKUP_FILE="$BACKUP_DIR/backup_completo_${DATE}.dump"

log "--- NIVEL 2: Backup completo ---"
log "Destino: $FULL_BACKUP_FILE"

PGPASSWORD="$DB_PASSWORD" pg_dump \
    -h "$DB_HOST" \
    -p "$DB_CONN_PORT" \
    -U "$DB_USERNAME" \
    -d "$DB_NAME" \
    --format=custom \
    --compress=9 \
    --no-password \
    --file="$FULL_BACKUP_FILE" \
    2>> "$LOG_FILE"

log "Backup completo OK: $(du -sh "$FULL_BACKUP_FILE" | cut -f1)"


# ──────────────────────────────────────────────────────────────
# SECCIÓN 4 — NIVEL 3: BACKUP POR TENANT
# ──────────────────────────────────────────────────────────────

# dump_table_tenant — Exporta UNA tabla filtrada al archivo SQL de destino.
#
# El bloque generado tiene este formato (importable con psql -f):
#   COPY tablename (col1, col2, ...) FROM stdin;
#   valor1	valor2	...
#   \.
#
# Argumentos:
#   $1  nombre de la tabla en PostgreSQL
#   $2  cláusula WHERE completa (puede ser subquery)
#   $3  ruta al archivo .sql de destino
dump_table_tenant() {
    local table="$1"
    local where_clause="$2"
    local output_file="$3"

    # Obtener columnas en orden de definición para declarar en COPY
    local cols
    cols=$(PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" -p "$DB_CONN_PORT" \
        -U "$DB_USERNAME" -d "$DB_NAME" \
        --no-psqlrc -t -A \
        -c "SELECT string_agg(column_name, ', ' ORDER BY ordinal_position)
            FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = '$table'" \
        2>> "$LOG_FILE")

    if [ -z "$cols" ]; then
        log "  ADVERTENCIA: tabla '$table' no encontrada, se omite"
        return 0
    fi

    {
        echo ""
        echo "-- ─── Tabla: $table ───────────────────────────────────────"
        echo "COPY $table ($cols) FROM stdin;"

        # Exportar filas: formato texto PostgreSQL (tabulador como separador, \N = NULL)
        PGPASSWORD="$DB_PASSWORD" psql \
            -h "$DB_HOST" -p "$DB_CONN_PORT" \
            -U "$DB_USERNAME" -d "$DB_NAME" \
            --no-psqlrc -q \
            -c "COPY (SELECT * FROM $table WHERE $where_clause) TO STDOUT" \
            2>> "$LOG_FILE"

        # Terminador obligatorio del bloque COPY
        echo "\."
    } >> "$output_file"
}

# ── Consultar tenants activos ───────────────────────────────────
log "--- NIVEL 3: Consultando tenants en la base de datos ---"

TENANTS=$(PGPASSWORD="$DB_PASSWORD" psql \
    -h "$DB_HOST" -p "$DB_CONN_PORT" \
    -U "$DB_USERNAME" -d "$DB_NAME" \
    --no-psqlrc -t -A \
    -c "SELECT id || '|' || slug FROM tenants ORDER BY id" \
    2>> "$LOG_FILE")

if [ -z "$TENANTS" ]; then
    log "ADVERTENCIA: No se encontraron tenants. Se omite el Nivel 3."
else
    while IFS='|' read -r tenant_id slug; do
        # Saltar líneas vacías (puede aparecer una al final del output de psql)
        [ -z "$tenant_id" ] && continue

        TENANT_FILE="$BACKUP_DIR/backup_tenant_${slug}_${DATE}.sql"
        log "Procesando tenant: $slug (id=$tenant_id)"

        # ── Encabezado del archivo SQL ────────────────────────────
        cat > "$TENANT_FILE" << HEADER
-- =============================================================
-- Backup Tenant : $slug
-- tenant_id     : $tenant_id
-- Base de datos : $DB_NAME @ $DB_HOST
-- Generado      : $(date '+%Y-%m-%d %H:%M:%S')
-- =============================================================
-- CÓMO RESTAURAR (usa restore.sh para el proceso completo):
--   psql -h HOST -U USER -d DBNAME -f este_archivo.sql
--
-- ADVERTENCIA: Este archivo INSERTA datos. Si los registros ya
-- existen habrá errores de clave duplicada. El restore.sh se
-- encarga de manejar eso correctamente.
-- =============================================================
BEGIN;

HEADER

        # ── Tablas con tenant_id DIRECTO ──────────────────────────
        for table in users roles patients documents document_templates \
                     report_templates dicom_studies dicom_series dicom_instances; do
            dump_table_tenant \
                "$table" \
                "tenant_id = '$tenant_id'" \
                "$TENANT_FILE"
        done

        # ── Tablas con tenant_id INDIRECTO ────────────────────────
        # role_user: tabla de unión users ↔ roles (sin tenant_id propio)
        dump_table_tenant \
            "role_user" \
            "user_id IN (SELECT id FROM users WHERE tenant_id = '$tenant_id')" \
            "$TENANT_FILE"

        # document_ocr_metadata: ligada a documents (sin tenant_id propio)
        dump_table_tenant \
            "document_ocr_metadata" \
            "document_id IN (SELECT id FROM documents WHERE tenant_id = '$tenant_id')" \
            "$TENANT_FILE"

        echo "" >> "$TENANT_FILE"
        echo "COMMIT;" >> "$TENANT_FILE"

        log "  ✓ tenant '$slug' completado: $(du -sh "$TENANT_FILE" | cut -f1)"

    done <<< "$TENANTS"
fi


# ──────────────────────────────────────────────────────────────
# SECCIÓN 5 — LIMPIEZA DE ARCHIVOS ANTIGUOS
# ──────────────────────────────────────────────────────────────
log "--- Limpieza: eliminando backups con más de $RETENTION_DAYS días ---"
DELETED_COUNT=0

# 'find -mtime +N' encuentra archivos modificados hace más de N días
while IFS= read -r old_file; do
    log "  Eliminando: $old_file"
    rm -f "$old_file"
    DELETED_COUNT=$((DELETED_COUNT + 1))
done < <(find "$BACKUP_DIR" -maxdepth 1 \
    \( -name "backup_completo_*.dump" -o -name "backup_tenant_*.sql" \) \
    -mtime "+$RETENTION_DAYS" 2>/dev/null)

log "Archivos eliminados: $DELETED_COUNT"

# ──────────────────────────────────────────────────────────────
# SECCIÓN 6 — UPLOAD A AZURE BLOB STORAGE
# ──────────────────────────────────────────────────────────────
# Esta sección solo se ejecuta si AZURE_STORAGE_SAS_URL está definida.
# Si la variable no está en el .env, los backups se guardan solo en local.
#
# Formato de AZURE_STORAGE_SAS_URL:
#   https://<cuenta>.blob.core.windows.net/<contenedor>?<sas-token>
#
# El SAS Token debe tener permisos: Read, Write, Create (sobre Object/Blob).

subir_a_azure() {
    local archivo="$1"
    local nombre_blob="$2"

    # Separar la base URL (antes del ?) del token SAS (después del ?)
    local base_url="${AZURE_STORAGE_SAS_URL%%\?*}"
    local sas_token="${AZURE_STORAGE_SAS_URL#*\?}"

    # URL final: base_url/nombre_blob?sas_token
    local upload_url="${base_url}/${nombre_blob}?${sas_token}"

    log "  Subiendo: $nombre_blob"

    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" \
        -X PUT \
        -H "x-ms-blob-type: BlockBlob" \
        --data-binary @"$archivo" \
        "$upload_url")

    if [ "$http_code" = "201" ]; then
        log "  ✓ Azure OK: $nombre_blob (HTTP 201)"
    else
        log "  ✗ Azure FALLO: $nombre_blob (HTTP $http_code)"
        # No abortamos el script — el backup local ya existe
    fi
}

if [ -n "${AZURE_STORAGE_SAS_URL:-}" ]; then
    log "--- AZURE: Subiendo backups a Azure Blob Storage ---"

    # Subir el backup completo
    if [ -f "$FULL_BACKUP_FILE" ]; then
        subir_a_azure "$FULL_BACKUP_FILE" "$(basename "$FULL_BACKUP_FILE")"
    fi

    # Subir los backups por tenant generados en esta ejecución
    for sql_file in "$BACKUP_DIR"/backup_tenant_*_${DATE}.sql; do
        [ -f "$sql_file" ] && subir_a_azure "$sql_file" "$(basename "$sql_file")"
    done

    log "--- AZURE: Upload finalizado ---"
else
    log "AZURE_STORAGE_SAS_URL no configurada — backup solo local"
fi


# ──────────────────────────────────────────────────────────────
# SECCIÓN 7 — RESUMEN FINAL
# ──────────────────────────────────────────────────────────────
log "═══════════════════════════════════════════════════════"
log "Backup completado exitosamente"
log "Log guardado en: $LOG_FILE"
log "═══════════════════════════════════════════════════════"