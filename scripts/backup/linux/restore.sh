#!/bin/bash
set -euo pipefail

# ── Validación de Dependencias (Local) ─────────────────────────
if [ "${RUNNING_IN_DOCKER:-false}" != "true" ]; then
    if ! command -v pg_restore >/dev/null 2>&1; then
        echo "ERROR: La dependencia 'pg_restore' no fue encontrada en el PATH."
        exit 1
    fi

    if ! command -v psql >/dev/null 2>&1; then
        echo "ERROR: La dependencia 'psql' no fue encontrada en el PATH."
        exit 1
    fi
fi

usage() {
    echo "USO:"
    echo "  bash scripts/backup/restore.sh --full   <archivo.dump>"
    echo "  bash scripts/backup/restore.sh --tenant <archivo.sql>"
    exit 1
}

MODE=""
BACKUP_FILE=""
FORCE=false

while [[ $# -gt 0 ]]; do
  case $1 in
    --full)
      MODE="full"
      BACKUP_FILE="$2"
      shift 2
      ;;
    --tenant)
      MODE="tenant"
      BACKUP_FILE="$2"
      shift 2
      ;;
    --force)
      FORCE=true
      shift
      ;;
    *)
      usage
      ;;
  esac
done

if [ "$MODE" = "" ] || [ "$BACKUP_FILE" = "" ]; then usage; fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

if [[ "$BACKUP_FILE" != /* ]]; then BACKUP_FILE="$REPO_ROOT/$BACKUP_FILE"; fi

if [ "${RUNNING_IN_DOCKER:-false}" = "true" ]; then
    DB_HOST="${DB_HOST:-postgres}"
    DB_CONN_PORT=5432
else
    ENV_FILE="$REPO_ROOT/.env"
    if [ ! -f "$ENV_FILE" ]; then
        echo "ERROR: No se encontró .env"
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

DB_NAME="${DB_NAME:-sgd_hc}"
for var in DB_USERNAME DB_PASSWORD DB_NAME; do
    if [ -z "${!var:-}" ]; then
        echo "ERROR: La variable '$var' no está definida."
        exit 1
    fi
done

if [ ! -f "$BACKUP_FILE" ]; then
    echo "ERROR: El archivo de backup no existe: $BACKUP_FILE"
    exit 1
fi

if [ "$MODE" = "full" ]; then
    if [ "$FORCE" = "false" ]; then
        echo "  Para continuar escribe exactamente: RESTAURAR"
        read -rp "  Confirmación: " CONFIRM
        if [ "$CONFIRM" != "RESTAURAR" ]; then exit 0; fi
    fi

    PGPASSWORD="$DB_PASSWORD" pg_restore -h "$DB_HOST" -p "$DB_CONN_PORT" -U "$DB_USERNAME" -d "$DB_NAME" --clean --if-exists --no-owner --no-privileges --single-transaction --verbose "$BACKUP_FILE"
    echo "✓ Restauración completa finalizada."
fi

if [ "$MODE" = "tenant" ]; then
    FILENAME=$(basename "$BACKUP_FILE" .sql)
    SLUG=$(echo "$FILENAME" | sed 's/^backup_tenant_//' | sed 's/_[0-9]\{8\}$//')
    TENANT_ID=$(grep "^-- tenant_id" "$BACKUP_FILE" | head -1 | awk '{print $NF}')

    if [ "$FORCE" = "false" ]; then
        echo "  Para continuar escribe exactamente: $SLUG"
        read -rp "  Confirmación: " CONFIRM
        if [ "$CONFIRM" != "$SLUG" ]; then exit 0; fi
    fi

    TEMP_SQL=$(mktemp /tmp/sgd_restore_XXXXXX.sql)
    trap 'rm -f "$TEMP_SQL"' EXIT

    cat > "$TEMP_SQL" << SQL
BEGIN;

-- ── Drops de triggers que bloquean DELETE en document_versions ──
DROP TRIGGER IF EXISTS trg_document_versions_no_delete ON document_versions;
DROP TRIGGER IF EXISTS trg_document_versions_no_update ON document_versions;

-- ── DELETE en orden inverso de dependencias FK ──

-- Workflows (hijos primero)
DELETE FROM workflow_events    WHERE tenant_id = '$TENANT_ID';
DELETE FROM workflow_comments  WHERE tenant_id = '$TENANT_ID';
DELETE FROM workflow_documents WHERE workflow_id IN (SELECT id FROM workflows WHERE tenant_id = '$TENANT_ID');
DELETE FROM review_tasks       WHERE tenant_id = '$TENANT_ID';
DELETE FROM workflows          WHERE tenant_id = '$TENANT_ID';

-- Documents y versiones
DELETE FROM document_ocr_metadata WHERE document_id IN (SELECT id FROM documents WHERE tenant_id = '$TENANT_ID');
DELETE FROM document_versions  WHERE tenant_id = '$TENANT_ID';
DELETE FROM documents           WHERE tenant_id = '$TENANT_ID';
DELETE FROM document_templates  WHERE tenant_id = '$TENANT_ID';
DELETE FROM report_templates    WHERE tenant_id = '$TENANT_ID';

-- DICOM
DELETE FROM dicom_instances WHERE tenant_id = '$TENANT_ID';
DELETE FROM dicom_series    WHERE tenant_id = '$TENANT_ID';
DELETE FROM dicom_studies   WHERE tenant_id = '$TENANT_ID';

-- Clinical
DELETE FROM clinical_histories WHERE tenant_id = '$TENANT_ID';

-- Users y roles
DELETE FROM notifications     WHERE tenant_id = '$TENANT_ID';
DELETE FROM task_delegations  WHERE tenant_id = '$TENANT_ID';
DELETE FROM backup_history    WHERE tenant_id = '$TENANT_ID';
DELETE FROM api_call_usage    WHERE tenant_id = '$TENANT_ID';
DELETE FROM user_push_tokens  WHERE user_id IN (SELECT id FROM users WHERE tenant_id = '$TENANT_ID');
DELETE FROM role_user         WHERE user_id IN (SELECT id FROM users WHERE tenant_id = '$TENANT_ID');
DELETE FROM role_permission   WHERE role_id IN (SELECT id FROM roles WHERE tenant_id = '$TENANT_ID');
DELETE FROM patients          WHERE tenant_id = '$TENANT_ID';
DELETE FROM users             WHERE tenant_id = '$TENANT_ID';
DELETE FROM roles             WHERE tenant_id = '$TENANT_ID';

-- Parent
DELETE FROM tenants WHERE id = '$TENANT_ID';

COMMIT;
SQL

    cat "$BACKUP_FILE" >> "$TEMP_SQL"
    PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_CONN_PORT" -U "$DB_USERNAME" -d "$DB_NAME" --no-psqlrc -v ON_ERROR_STOP=1 -f "$TEMP_SQL"
    echo "✓ Restauración del tenant finalizada."
fi
