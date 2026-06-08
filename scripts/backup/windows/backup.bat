@echo off
REM Lanzador de Backup para Windows
echo Iniciando script de Backup de SGD-HC...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0backup.ps1"
if %errorlevel% neq 0 (
    echo.
    echo ERROR: El script de backup ha fallado con el codigo %errorlevel%
    pause
    exit /b %errorlevel%
)
echo.
pause
