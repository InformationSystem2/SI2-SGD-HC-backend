#!/bin/bash
# =============================================================
# restore.sh — Restauración de Backups SGD-HC
#
# Modo 1 (restauración completa):
#   bash scripts/backup/restore.sh --full backups/backup_completo_20260601.dump
#
# Modo 2 (restauración por tenant):
#   bash scripts/backup/restore.sh --tenant backups/backup_tenant_system_20260601.sql
#
# ADVERTENCIA: La restauración completa BORRA todos los datos
# actuales de la base antes de restaurar. Úsala con cuidado.
# =============================================================


set -euo pipefail

# ──────────────────────────────────────────────────────────────
# SECCIÓN 1 — COLORES PARA LA TERMINAL
# ──────────────────────────────────────────────────────────────
# Solo activar colores si la salida es una terminal real (no un pipe/log)
if [ -t 1 ]; then
    RED='\033[0;31m'
    YELLOW='\033[1;33m'
    GREEN='\033[0;32m'
    CYAN='\033[0;36m'
    BOLD='\033[1m'
    NC='\033[0m'  # No Color (reset)
else
    RED='' YELLOW='' GREEN='' CYAN='' BOLD='' NC=''
fi


# ──────────────────────────────────────────────────────────────
# SECCIÓN 2 — AYUDA
# ──────────────────────────────────────────────────────────────
usage() {
    echo ""
    echo -e "${BOLD}USO:${NC}"
    echo "  bash scripts/backup/restore.sh --full   <archivo.dump>"
    echo "  bash scripts/backup/restore.sh --tenant <archivo.sql>"
    echo ""
    echo -e "${BOLD}OPCIONES:${NC}"
    echo "  --full    Restauración completa de la base de datos"
    echo "            Requiere: un archivo .dump generado por backup.sh (Nivel 2)"
    echo ""
    echo "  --tenant  Restauración de un tenant individual"
    echo "            Requiere: un archivo .sql generado por backup.sh (Nivel 3)"
    echo ""
    echo -e "${BOLD}EJEMPLOS:${NC}"
    echo "  bash scripts/backup/restore.sh --full backups/backup_completo_20260601.dump"
    echo "  bash scripts/backup/restore.sh --tenant backups/backup_tenant_system_20260601.sql"
    echo ""
    exit 1
}

# ──────────────────────────────────────────────────────────────
# SECCIÓN 3 — PARSEAR ARGUMENTOS
# ──────────────────────────────────────────────────────────────
MODE=""
BACKUP_FILE=""

if [ $# -lt 2 ]; then
    echo -e "${RED}ERROR: Faltan argumentos.${NC}"
    usage
fi

case "$1" in
    --full)   MODE="full"   ;;
    --tenant) MODE="tenant" ;;
    *)
        echo -e "${RED}ERROR: Opción desconocida: $1${NC}"
        usage
        ;;
esac

BACKUP_FILE="$2"

# Resolver ruta relativa desde la raíz del repo si no es absoluta
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

if [[ "$BACKUP_FILE" != /* ]]; then
    BACKUP_FILE="$REPO_ROOT/$BACKUP_FILE"
fi


# ──────────────────────────────────────────────────────────────
# SECCIÓN 4 — DETECCIÓN DE ENTORNO Y VARIABLES
# ──────────────────────────────────────────────────────────────
if [ "${RUNNING_IN_DOCKER:-false}" = "true" ]; then
    DB_HOST="${DB_HOST:-postgres}"
    DB_CONN_PORT=5432
else
    ENV_FILE="$REPO_ROOT/.env"
    if [ ! -f "$ENV_FILE" ]; then
        echo -e "${RED}ERROR: No se encontró el archivo .env en: $ENV_FILE${NC}"
        exit 1
    fi
    set -a; source "$ENV_FILE"; set +a
    DB_HOST="${DB_HOST:-localhost}"
    DB_CONN_PORT="${DB_PORT:-5433}"
fi

# Resolver DB_NAME desde DB_URL si no está definido directamente
if [ -z "${DB_NAME:-}" ] && [ -n "${DB_URL:-}" ]; then
    DB_NAME="$(echo "$DB_URL" | sed 's|.*\/||' | sed 's|?.*||')"
fi
DB_NAME="${DB_NAME:-sgd_hc}"

for var in DB_USERNAME DB_PASSWORD DB_NAME; do
    if [ -z "${!var:-}" ]; then
        echo -e "${RED}ERROR: La variable '$var' no está definida. Revisa tu archivo .env${NC}"
        exit 1
    fi
done

# ──────────────────────────────────────────────────────────────
# SECCIÓN 5 — VALIDAR QUE EL ARCHIVO EXISTE
# ──────────────────────────────────────────────────────────────
if [ ! -f "$BACKUP_FILE" ]; then
    echo -e "${RED}ERROR: El archivo de backup no existe: $BACKUP_FILE${NC}"
    echo ""
    echo "Backups disponibles en $REPO_ROOT/backups/:"
    ls -lh "$REPO_ROOT/backups/"*.dump "$REPO_ROOT/backups/"*.sql 2>/dev/null || echo "  (ninguno encontrado)"
    echo ""
    exit 1
fi

FILE_SIZE=$(du -sh "$BACKUP_FILE" | cut -f1)
FILE_DATE=$(date -r "$BACKUP_FILE" '+%Y-%m-%d %H:%M:%S' 2>/dev/null || stat -c '%y' "$BACKUP_FILE" 2>/dev/null | cut -d'.' -f1)


# ──────────────────────────────────────────────────────────────
# SECCIÓN 6 — MODO 1: RESTAURACIÓN COMPLETA
# ──────────────────────────────────────────────────────────────
if [ "$MODE" = "full" ]; then

    # Validar extensión
    if [[ "$BACKUP_FILE" != *.dump ]]; then
        echo -e "${YELLOW}ADVERTENCIA: Se esperaba un archivo .dump para restauración completa.${NC}"
        echo "El archivo seleccionado es: $BACKUP_FILE"
        echo ""
    fi

    # ── Resumen de la operación ─────────────────────────────────
    echo ""
    echo -e "${BOLD}${RED}╔══════════════════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}${RED}║          ⚠  RESTAURACIÓN COMPLETA ⚠                  ║${NC}"
    echo -e "${BOLD}${RED}╚══════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "  Base de datos : ${CYAN}$DB_NAME${NC} en $DB_HOST:$DB_CONN_PORT"
    echo -e "  Archivo       : ${CYAN}$(basename "$BACKUP_FILE")${NC}"
    echo -e "  Tamaño        : $FILE_SIZE   |   Fecha: $FILE_DATE"
    echo ""
    echo -e "${RED}  ESTA OPERACIÓN ELIMINARÁ TODOS LOS DATOS ACTUALES"
    echo -e "  DE LA BASE '$DB_NAME' ANTES DE RESTAURAR.${NC}"
    echo ""
    echo -e "  Para continuar escribe exactamente: ${BOLD}RESTAURAR${NC}"
    echo -e "  Para cancelar presiona Ctrl+C o escribe cualquier otra cosa."
    echo ""
    read -rp "  Confirmación: " CONFIRM

    if [ "$CONFIRM" != "RESTAURAR" ]; then
        echo ""
        echo "Operación cancelada. No se realizaron cambios."
        exit 0
    fi

    echo ""
    echo -e "${YELLOW}Iniciando restauración completa...${NC}"
    echo ""

    # pg_restore flags:
    #   --clean        → DROP y CREATE de cada objeto antes de restaurar
    #   --if-exists    → no falla si un objeto a borrar no existe
    #   --no-owner     → no intenta asignar ownership (evita errores de permisos)
    #   --no-privileges → no restaura GRANTs/REVOKEs
    #   --single-transaction → todo o nada (rollback si algo falla)
    #   --verbose      → muestra progreso en stderr
    PGPASSWORD="$DB_PASSWORD" pg_restore \
        -h "$DB_HOST" \
        -p "$DB_CONN_PORT" \
        -U "$DB_USERNAME" \
        -d "$DB_NAME" \
        --clean \
        --if-exists \
        --no-owner \
        --no-privileges \
        --single-transaction \
        --verbose \
        "$BACKUP_FILE"

    echo ""
    echo -e "${GREEN}✓ Restauración completa finalizada exitosamente.${NC}"
    echo ""
fi


# ──────────────────────────────────────────────────────────────
# SECCIÓN 7 — MODO 2: RESTAURACIÓN POR TENANT
# ──────────────────────────────────────────────────────────────
if [ "$MODE" = "tenant" ]; then

    # Validar extensión
    if [[ "$BACKUP_FILE" != *.sql ]]; then
        echo -e "${YELLOW}ADVERTENCIA: Se esperaba un archivo .sql para restauración por tenant.${NC}"
        echo "El archivo seleccionado es: $BACKUP_FILE"
        echo ""
    fi

    # Extraer el slug del nombre de archivo (backup_tenant_{slug}_{fecha}.sql)
    FILENAME=$(basename "$BACKUP_FILE" .sql)
    SLUG=$(echo "$FILENAME" | sed 's/^backup_tenant_//' | sed 's/_[0-9]\{8\}$//')

    # Extraer tenant_id del encabezado del archivo SQL
    TENANT_ID=$(grep "^-- tenant_id" "$BACKUP_FILE" | head -1 | awk '{print $NF}')

    # ── Resumen de la operación ─────────────────────────────────
    echo ""
    echo -e "${BOLD}${YELLOW}╔══════════════════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}${YELLOW}║         ⚠  RESTAURACIÓN DE TENANT ⚠                  ║${NC}"
    echo -e "${BOLD}${YELLOW}╚══════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "  Base de datos : ${CYAN}$DB_NAME${NC} en $DB_HOST:$DB_CONN_PORT"
    echo -e "  Tenant (slug) : ${CYAN}$SLUG${NC}"
    echo -e "  tenant_id     : ${CYAN}${TENANT_ID:-no detectado}${NC}"
    echo -e "  Archivo       : ${CYAN}$(basename "$BACKUP_FILE")${NC}"
    echo -e "  Tamaño        : $FILE_SIZE   |   Fecha: $FILE_DATE"
    echo ""
    echo -e "${YELLOW}  Esta operación BORRARÁ los datos actuales del tenant"
    echo -e "  '$SLUG' y los reemplazará con los del archivo de backup.${NC}"
    echo ""
    echo -e "  Para continuar escribe exactamente el slug del tenant: ${BOLD}$SLUG${NC}"
    echo -e "  Para cancelar presiona Ctrl+C o escribe cualquier otra cosa."
    echo ""
    read -rp "  Confirmación: " CONFIRM

    if [ "$CONFIRM" != "$SLUG" ]; then
        echo ""
        echo "Operación cancelada. No se realizaron cambios."
        exit 0
    fi

    echo ""
    echo -e "${YELLOW}Iniciando restauración del tenant '$SLUG'...${NC}"
    echo ""

    if [ -z "$TENANT_ID" ]; then
        echo -e "${RED}ERROR: No se pudo extraer el tenant_id del archivo de backup.${NC}"
        echo "Asegúrate de usar un archivo generado por backup.sh."
        exit 1
    fi

    # ── Estrategia: borrar → reinsertar en transacción ─────────
    # Generamos un script SQL temporal que:
    #   1. Dentro de una transacción, borra los datos del tenant en orden
    #      inverso a las FK (hijos antes que padres) para evitar errores
    #      de integridad referencial.
    #   2. A continuación ejecuta el archivo .sql de backup (COPY ... FROM stdin).
    #
    # El orden de borrado es el inverso del orden de inserción del backup:
    #   Inserción: tenants → users/roles → patients → documents → dicom_studies
    #              → dicom_series → dicom_instances → role_user
    #              → document_ocr_metadata
    #   Borrado:   document_ocr_metadata → role_user → dicom_instances
    #              → dicom_series → dicom_studies → documents
    #              → document_templates → report_templates
    #              → patients → users → roles

    TEMP_SQL=$(mktemp /tmp/sgd_restore_XXXXXX.sql)
    trap 'rm -f "$TEMP_SQL"' EXIT  # Limpiar el temporal al salir

    cat > "$TEMP_SQL" << SQL
-- Script de restauración generado automáticamente por restore.sh
-- tenant_id: $TENANT_ID  |  slug: $SLUG
-- $(date '+%Y-%m-%d %H:%M:%S')

BEGIN;

-- ── Paso 1: Borrar datos del tenant en orden inverso de FK ──────────────────

-- Tablas con tenant_id INDIRECTO (se borran primero)
DELETE FROM document_ocr_metadata
    WHERE document_id IN (
        SELECT id FROM documents WHERE tenant_id = '$TENANT_ID'
    );

DELETE FROM role_user
    WHERE user_id IN (
        SELECT id FROM users WHERE tenant_id = '$TENANT_ID'
    );

-- Tablas DICOM (hijos antes que padres, ON DELETE CASCADE en FK pero por claridad)
DELETE FROM dicom_instances WHERE tenant_id = '$TENANT_ID';
DELETE FROM dicom_series    WHERE tenant_id = '$TENANT_ID';
DELETE FROM dicom_studies   WHERE tenant_id = '$TENANT_ID';

-- Tablas de documentos
DELETE FROM documents          WHERE tenant_id = '$TENANT_ID';
DELETE FROM document_templates WHERE tenant_id = '$TENANT_ID';
DELETE FROM report_templates   WHERE tenant_id = '$TENANT_ID';

-- Tablas base del tenant
DELETE FROM patients WHERE tenant_id = '$TENANT_ID';
DELETE FROM users    WHERE tenant_id = '$TENANT_ID';
DELETE FROM roles    WHERE tenant_id = '$TENANT_ID';

COMMIT;


SQL

    # Agregar el contenido del backup (ya incluye su propio BEGIN/COMMIT)
    cat "$BACKUP_FILE" >> "$TEMP_SQL"

    # Ejecutar el script completo
    PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_CONN_PORT" \
        -U "$DB_USERNAME" \
        -d "$DB_NAME" \
        --no-psqlrc \
        -v ON_ERROR_STOP=1 \
        -f "$TEMP_SQL"

    echo ""
    echo -e "${GREEN}✓ Restauración del tenant '$SLUG' finalizada exitosamente.${NC}"
    echo ""
fi