# =============================================================
# Backup Script para Windows (PowerShell)
# Nivel 2 y Nivel 3
# =============================================================
param(
    [switch]$FullOnly,
    [string]$TenantSlug = ""
)

$ErrorActionPreference = "Stop"

# 1. Validar dependencias
if (-not (Get-Command "pg_dump" -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: La dependencia 'pg_dump' no fue encontrada en el PATH." -ForegroundColor Red
    Write-Host "Asegurate de tener instaladas las herramientas de cliente de PostgreSQL."
    exit 1
}
if (-not (Get-Command "psql" -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: La dependencia 'psql' no fue encontrada en el PATH." -ForegroundColor Red
    exit 1
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RepoRoot = Resolve-Path "$ScriptDir\..\..\.."
$BackupDir = "$RepoRoot\backups"
$LogDir = "$BackupDir\logs"
$EnvFile = "$RepoRoot\.env"

if (-not (Test-Path $EnvFile)) {
    Write-Host "ERROR: No se encontro el archivo .env en $EnvFile" -ForegroundColor Red
    exit 1
}

# Leer .env
Get-Content $EnvFile | Where-Object { $_ -match "^[^#\s]+=" } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    $value = $value.Trim('"', "'")
    [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim())
}

$DB_HOST = [Environment]::GetEnvironmentVariable("DB_HOST")
if (-not $DB_HOST) { $DB_HOST = "localhost" }
$DB_PORT = [Environment]::GetEnvironmentVariable("DB_PORT")
if (-not $DB_PORT) { $DB_PORT = "5433" }
$DB_NAME = [Environment]::GetEnvironmentVariable("DB_NAME")
if (-not $DB_NAME) { $DB_NAME = "sgd_hc" }
$DB_USERNAME = [Environment]::GetEnvironmentVariable("DB_USERNAME")
$DB_PASSWORD = [Environment]::GetEnvironmentVariable("DB_PASSWORD")

if (-not $DB_USERNAME -or -not $DB_PASSWORD) {
    Write-Host "ERROR: Credenciales no definidas en el .env" -ForegroundColor Red
    exit 1
}

[Environment]::SetEnvironmentVariable("PGPASSWORD", $DB_PASSWORD)

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

$DateStr = Get-Date -Format "yyyyMMdd"
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$LogFile = "$LogDir\backup_$Timestamp.log"

function Log-Message([string]$msg) {
    $formatted = "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] $msg"
    Write-Host $formatted
    Add-Content -Path $LogFile -Value $formatted
}

Log-Message "Iniciando backup SGD-HC"

if ($TenantSlug -eq "") {
    Log-Message "--- NIVEL 2: Backup completo ---"
    $FullBackupFile = "$BackupDir\backup_completo_$DateStr.dump"
    & pg_dump -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME --format=custom --compress=9 --no-password --file="$FullBackupFile" 2>> $LogFile
}

if ($FullOnly) {
    Log-Message "Modo full-only completado. Saliendo."
    exit 0
}

Log-Message "--- NIVEL 3: Consultando tenants ---"
if ($TenantSlug -ne "") {
    $TenantsOutput = & psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME --no-psqlrc -t -A -c "SELECT id || '|' || slug FROM tenants WHERE slug = '$TenantSlug' ORDER BY id"
} else {
    $TenantsOutput = & psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME --no-psqlrc -t -A -c "SELECT id || '|' || slug FROM tenants ORDER BY id"
}

if ($TenantsOutput) {
    foreach ($line in $TenantsOutput) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $parts = $line.Split('|')
        $tenant_id = $parts[0]
        $slug = $parts[1]
        
        $TenantFile = "$BackupDir\backup_tenant_${slug}_${DateStr}.sql"
        Log-Message "Procesando tenant: $slug"
        
        @"
-- Backup Tenant : $slug
-- tenant_id     : $tenant_id
BEGIN;

"@ | Set-Content -Path $TenantFile

        $colsTenant = & psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME --no-psqlrc -t -A -c "SELECT string_agg(column_name, ', ' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'tenants'"
        if ($colsTenant) {
            Add-Content -Path $TenantFile -Value "COPY tenants ($colsTenant) FROM stdin;"
            & psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME --no-psqlrc -q -c "COPY (SELECT * FROM tenants WHERE id = '$tenant_id') TO STDOUT" | Add-Content -Path $TenantFile
            Add-Content -Path $TenantFile -Value "\."
        }

        $tables = "users","roles","patients","documents","document_templates","report_templates","dicom_studies","dicom_series","dicom_instances"
        foreach ($t in $tables) {
            $cols = & psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME --no-psqlrc -t -A -c "SELECT string_agg(column_name, ', ' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = '$t'"
            if ($cols) {
                Add-Content -Path $TenantFile -Value "COPY $t ($cols) FROM stdin;"
                & psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME --no-psqlrc -q -c "COPY (SELECT * FROM $t WHERE tenant_id = '$tenant_id') TO STDOUT" | Add-Content -Path $TenantFile
                Add-Content -Path $TenantFile -Value "\."
            }
        }

        $joinTables = @(
            @{ Table="role_user"; Where="user_id IN (SELECT id FROM users WHERE tenant_id = '$tenant_id')" },
            @{ Table="document_ocr_metadata"; Where="document_id IN (SELECT id FROM documents WHERE tenant_id = '$tenant_id')" }
        )
        foreach ($jt in $joinTables) {
            $cols = & psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME --no-psqlrc -t -A -c "SELECT string_agg(column_name, ', ' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = '$($jt.Table)'"
            if ($cols) {
                Add-Content -Path $TenantFile -Value "COPY $($jt.Table) ($cols) FROM stdin;"
                & psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME --no-psqlrc -q -c "COPY (SELECT * FROM $($jt.Table) WHERE $($jt.Where)) TO STDOUT" | Add-Content -Path $TenantFile
                Add-Content -Path $TenantFile -Value "\."
            }
        }

        $newTenantTables = @(
            @{ Table="document_versions"; Where="tenant_id = '$tenant_id'" },
            @{ Table="clinical_histories"; Where="tenant_id = '$tenant_id'" },
            @{ Table="workflows"; Where="tenant_id = '$tenant_id'" },
            @{ Table="review_tasks"; Where="tenant_id = '$tenant_id'" },
            @{ Table="workflow_events"; Where="tenant_id = '$tenant_id'" },
            @{ Table="workflow_comments"; Where="tenant_id = '$tenant_id'" },
            @{ Table="task_delegations"; Where="tenant_id = '$tenant_id'" },
            @{ Table="notifications"; Where="tenant_id = '$tenant_id'" },
            @{ Table="api_call_usage"; Where="tenant_id = '$tenant_id'" },
            @{ Table="backup_history"; Where="tenant_id = '$tenant_id'" }
        )
        foreach ($nt in $newTenantTables) {
            $cols = & psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME --no-psqlrc -t -A -c "SELECT string_agg(column_name, ', ' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = '$($nt.Table)'"
            if ($cols) {
                Add-Content -Path $TenantFile -Value "COPY $($nt.Table) ($cols) FROM stdin;"
                & psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME --no-psqlrc -q -c "COPY (SELECT * FROM $($nt.Table) WHERE $($nt.Where)) TO STDOUT" | Add-Content -Path $TenantFile
                Add-Content -Path $TenantFile -Value "\."
            }
        }

        $newJoinTables = @(
            @{ Table="workflow_documents"; Where="workflow_id IN (SELECT id FROM workflows WHERE tenant_id = '$tenant_id')" },
            @{ Table="user_push_tokens"; Where="user_id IN (SELECT id FROM users WHERE tenant_id = '$tenant_id')" },
            @{ Table="role_permission"; Where="role_id IN (SELECT id FROM roles WHERE tenant_id = '$tenant_id')" },
            @{ Table="plan_limits"; Where="plan_id IN (SELECT id FROM plans)" },
            @{ Table="plan_features"; Where="plan_id IN (SELECT id FROM plans)" }
        )
        foreach ($jt in $newJoinTables) {
            $cols = & psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME --no-psqlrc -t -A -c "SELECT string_agg(column_name, ', ' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = '$($jt.Table)'"
            if ($cols) {
                Add-Content -Path $TenantFile -Value "COPY $($jt.Table) ($cols) FROM stdin;"
                & psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME --no-psqlrc -q -c "COPY (SELECT * FROM $($jt.Table) WHERE $($jt.Where)) TO STDOUT" | Add-Content -Path $TenantFile
                Add-Content -Path $TenantFile -Value "\."
            }
        }

        $globalTables = @("permissions","plans")
        foreach ($gt in $globalTables) {
            $cols = & psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME --no-psqlrc -t -A -c "SELECT string_agg(column_name, ', ' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = '$gt'"
            if ($cols) {
                Add-Content -Path $TenantFile -Value "COPY $gt ($cols) FROM stdin;"
                & psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME --no-psqlrc -q -c "COPY (SELECT * FROM $gt) TO STDOUT" | Add-Content -Path $TenantFile
                Add-Content -Path $TenantFile -Value "\."
            }
        }

        Add-Content -Path $TenantFile -Value "COMMIT;"
        Log-Message "  ✓ tenant '$slug' completado"
    }
}

Log-Message "Limpieza finalizada. Backup Completado."
