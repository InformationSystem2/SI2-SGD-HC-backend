# =============================================================
# Restore Script para Windows (PowerShell)
# =============================================================
param(
    [Parameter(Mandatory=$true)]
    [string]$Mode,
    
    [Parameter(Mandatory=$true)]
    [string]$BackupFile,

    [switch]$Force
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command "pg_restore" -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: La dependencia 'pg_restore' no fue encontrada en el PATH." -ForegroundColor Red
    exit 1
}
if (-not (Get-Command "psql" -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: La dependencia 'psql' no fue encontrada en el PATH." -ForegroundColor Red
    exit 1
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RepoRoot = Resolve-Path "$ScriptDir\..\..\.."
$EnvFile = "$RepoRoot\.env"

if (-not (Test-Path $EnvFile)) {
    Write-Host "ERROR: No se encontro el archivo .env" -ForegroundColor Red
    exit 1
}

Get-Content $EnvFile | Where-Object { $_ -match "^[^#\s]+=" } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim('"', "'").Trim())
}

$DB_HOST = [Environment]::GetEnvironmentVariable("DB_HOST")
if (-not $DB_HOST) { $DB_HOST = "localhost" }
$DB_PORT = [Environment]::GetEnvironmentVariable("DB_PORT")
if (-not $DB_PORT) { $DB_PORT = "5433" }
$DB_NAME = [Environment]::GetEnvironmentVariable("DB_NAME")
if (-not $DB_NAME) { $DB_NAME = "sgd_hc" }
$DB_USERNAME = [Environment]::GetEnvironmentVariable("DB_USERNAME")
$DB_PASSWORD = [Environment]::GetEnvironmentVariable("DB_PASSWORD")
[Environment]::SetEnvironmentVariable("PGPASSWORD", $DB_PASSWORD)

if (-not (Test-Path $BackupFile)) {
    Write-Host "ERROR: El archivo de backup no existe: $BackupFile" -ForegroundColor Red
    exit 1
}

if ($Mode -eq "-full") {
    if (-not $Force) {
        Write-Host "ADVERTENCIA: Se eliminaran todos los datos de '$DB_NAME'." -ForegroundColor Yellow
        $Confirm = Read-Host "Escribe exactamente RESTAURAR para continuar"
        if ($Confirm -ne "RESTAURAR") { exit 0 }
    }

    & pg_restore -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME --clean --if-exists --no-owner --no-privileges --single-transaction --verbose $BackupFile
    Write-Host "✓ Restauracion completa finalizada." -ForegroundColor Green
}
elseif ($Mode -eq "-tenant") {
    if (-not $Force) {
        $Confirm = Read-Host "Escribe SI para continuar la restauracion del tenant"
        if ($Confirm -ne "SI") { exit 0 }
    }
    
    # Extraer tenant_id
    $Header = Get-Content $BackupFile -TotalCount 20
    $TenantId = ($Header | Select-String "^-- tenant_id\s+:\s+(.*)$").Matches.Groups[1].Value.Trim()
    
    if (-not $TenantId) {
        Write-Host "No se encontro tenant_id en el backup" -ForegroundColor Red
        exit 1
    }

    $TempSql = [System.IO.Path]::GetTempFileName()
    @"
BEGIN;
DELETE FROM document_ocr_metadata WHERE document_id IN (SELECT id FROM documents WHERE tenant_id = '$TenantId');
DELETE FROM role_user WHERE user_id IN (SELECT id FROM users WHERE tenant_id = '$TenantId');
DELETE FROM dicom_instances WHERE tenant_id = '$TenantId';
DELETE FROM dicom_series    WHERE tenant_id = '$TenantId';
DELETE FROM dicom_studies   WHERE tenant_id = '$TenantId';
DELETE FROM documents          WHERE tenant_id = '$TenantId';
DELETE FROM document_templates WHERE tenant_id = '$TenantId';
DELETE FROM report_templates   WHERE tenant_id = '$TenantId';
DELETE FROM patients WHERE tenant_id = '$TenantId';
DELETE FROM users    WHERE tenant_id = '$TenantId';
DELETE FROM roles    WHERE tenant_id = '$TenantId';
DELETE FROM tenants  WHERE id = '$TenantId';
COMMIT;
"@ | Set-Content -Path $TempSql
    
    Add-Content -Path $TempSql -Value (Get-Content $BackupFile -Raw)
    
    & psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME --no-psqlrc -v ON_ERROR_STOP=1 -f $TempSql
    Remove-Item $TempSql
    Write-Host "✓ Restauracion del tenant finalizada." -ForegroundColor Green
}
else {
    Write-Host "Modo invalido: $Mode" -ForegroundColor Red
    exit 1
}
