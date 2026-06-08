#!/bin/bash
# =============================================================
# Nivel 2: Backup completo con pg_dump (formato custom comprimido)
# Nivel 3: Backup por tenant individual (formato SQL importable)
# =============================================================

set -euo pipefail

FULL_ONLY=false
TARGET_TENANT_SLUG=""

while [[ $# -gt 0 ]]; do
  case $1 in
    --full-only)
      FULL_ONLY=true
      shift
      ;;
    --tenant-slug)
      TARGET_TENANT_SLUG="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done

# ── Validación de Dependencias (Local) ─────────────────────────
if [ "${RUNNING_IN_DOCKER:-false}" != "true" ]; then
    if ! command -v pg_dump >/dev/null 2>&1; then
        echo "ERROR: La dependencia 'pg_dump' no fue encontrada en el PATH."
        echo "Asegúrate de tener instaladas las herramientas de cliente de PostgreSQL."
        exit 1
    fi

    if ! command -v psql >/dev/null 2>&1; then
        echo "ERROR: La dependencia 'psql' no fue encontrada en el PATH."
        echo "Asegúrate de tener instaladas las herramientas de cliente de PostgreSQL."
        exit 1
    fi
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ "${RUNNING_IN_DOCKER:-false}" = "true" ]; then
    BACKUP_DIR="/backups"
    DB_HOST="${DB_HOST:-postgres}"
    DB_CONN_PORT=5432
else
    REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
    BACKUP_DIR="$REPO_ROOT/backups"

    ENV_FILE="$REPO_ROOT/.env"
    if [ ! -f "$ENV_FILE" ]; then
        echo "ERROR: No se encontró el archivo .env en: $ENV_FILE"
        exit 1
    fi
    set -a; source "$ENV_FILE"; set +a

    DB_HOST="${DB_HOST:-localhost}"
    # Extraer puerto de DB_URL si DB_PORT no está definido
    if [ -z "${DB_PORT:-}" ] && [ -n "${DB_URL:-}" ]; then
        DB_PORT=$(echo "$DB_URL" | sed -n 's|.*://[^:]*:\([0-9]*\)/.*|\1|p')
    fi
    DB_CONN_PORT="${DB_PORT:-5432}"
fi

if [ -z "${DB_NAME:-}" ] && [ -n "${DB_URL:-}" ]; then
    DB_NAME="$(echo "$DB_URL" | sed 's|.*\/||' | sed 's|?.*||')"
fi
DB_NAME="${DB_NAME:-sgd_hc}"

for var in DB_USERNAME DB_PASSWORD DB_NAME; do
    if [ -z "${!var:-}" ]; then
        echo "ERROR: La variable '$var' no está definida."
        exit 1
    fi
done

LOG_DIR="$BACKUP_DIR/logs"
DATE=$(date +%Y%m%d)
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_FILE="$LOG_DIR/backup_${TIMESTAMP}.log"
RETENTION_DAYS=30

mkdir -p "$BACKUP_DIR" "$LOG_DIR"

log() {
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] $1"
    echo "$msg"
    echo "$msg" >> "$LOG_FILE"
}

log "═══════════════════════════════════════════════════════"
log "Iniciando backup SGD-HC"
log "Host DB  : $DB_HOST:$DB_CONN_PORT"
log "Base     : $DB_NAME"
log "═══════════════════════════════════════════════════════"

if [ "$TARGET_TENANT_SLUG" = "" ]; then
    FULL_BACKUP_FILE="$BACKUP_DIR/backup_completo_${DATE}.dump"
    log "--- NIVEL 2: Backup completo ---"
    PGPASSWORD="$DB_PASSWORD" pg_dump -h "$DB_HOST" -p "$DB_CONN_PORT" -U "$DB_USERNAME" -d "$DB_NAME" --format=custom --compress=9 --no-password --file="$FULL_BACKUP_FILE" 2>> "$LOG_FILE"
    log "Backup completo OK: $(du -sh "$FULL_BACKUP_FILE" | cut -f1)"
fi

if [ "$FULL_ONLY" = "true" ]; then
    log "Modo full-only completado. Saliendo."
    exit 0
fi

dump_table_tenant() {
    local table="$1"
    local where_clause="$2"
    local output_file="$3"

    local cols
    cols=$(PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_CONN_PORT" -U "$DB_USERNAME" -d "$DB_NAME" --no-psqlrc -t -A -c "SELECT string_agg(column_name, ', ' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = '$table'" 2>> "$LOG_FILE")

    if [ -z "$cols" ]; then return 0; fi

    {
        echo ""
        echo "-- ─── Tabla: $table ───────────────────────────────────────"
        echo "COPY $table ($cols) FROM stdin;"
        PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_CONN_PORT" -U "$DB_USERNAME" -d "$DB_NAME" --no-psqlrc -q -c "COPY (SELECT * FROM $table WHERE $where_clause) TO STDOUT" 2>> "$LOG_FILE"
        echo "\."
    } >> "$output_file"
}

log "--- NIVEL 3: Consultando tenants ---"
if [ "$TARGET_TENANT_SLUG" != "" ]; then
    TENANTS=$(PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_CONN_PORT" -U "$DB_USERNAME" -d "$DB_NAME" --no-psqlrc -t -A -c "SELECT id || '|' || slug FROM tenants WHERE slug = '$TARGET_TENANT_SLUG' ORDER BY id" 2>> "$LOG_FILE")
else
    TENANTS=$(PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_CONN_PORT" -U "$DB_USERNAME" -d "$DB_NAME" --no-psqlrc -t -A -c "SELECT id || '|' || slug FROM tenants ORDER BY id" 2>> "$LOG_FILE")
fi

if [ -n "$TENANTS" ]; then
    while IFS='|' read -r tenant_id slug; do
        [ -z "$tenant_id" ] && continue
        TENANT_FILE="$BACKUP_DIR/backup_tenant_${slug}_${DATE}.sql"
        log "Procesando tenant: $slug"
        
        cat > "$TENANT_FILE" << HEADER
-- Backup Tenant : $slug
-- tenant_id     : $tenant_id
BEGIN;
HEADER

        dump_table_tenant "tenants" "id = '$tenant_id'" "$TENANT_FILE"

        for table in users roles patients documents document_templates report_templates dicom_studies dicom_series dicom_instances; do
            dump_table_tenant "$table" "tenant_id = '$tenant_id'" "$TENANT_FILE"
        done

        dump_table_tenant "role_user" "user_id IN (SELECT id FROM users WHERE tenant_id = '$tenant_id')" "$TENANT_FILE"
        dump_table_tenant "document_ocr_metadata" "document_id IN (SELECT id FROM documents WHERE tenant_id = '$tenant_id')" "$TENANT_FILE"

        echo "COMMIT;" >> "$TENANT_FILE"
        log "  ✓ tenant '$slug' completado"
    done <<< "$TENANTS"
fi

log "--- Limpieza ---"
find "$BACKUP_DIR" -maxdepth 1 \( -name "backup_completo_*.dump" -o -name "backup_tenant_*.sql" \) -mtime "+$RETENTION_DAYS" -exec rm -f {} \; 2>/dev/null

if [ -n "${AZURE_STORAGE_SAS_URL:-}" ]; then
    log "--- AZURE: Subiendo backups ---"
    base_url="${AZURE_STORAGE_SAS_URL%%\?*}"
    sas_token="${AZURE_STORAGE_SAS_URL#*\?}"

    for f in ${FULL_BACKUP_FILE:-} "$BACKUP_DIR"/backup_tenant_*_${DATE}.sql; do
        [ -f "$f" ] || continue
        nombre_blob="$(basename "$f")"
        curl -s -o /dev/null -w "%{http_code}" -X PUT -H "x-ms-blob-type: BlockBlob" --data-binary @"$f" "${base_url}/${nombre_blob}?${sas_token}"
    done
fi

log "Backup finalizado"
