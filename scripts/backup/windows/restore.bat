@echo off
REM Lanzador de Restore para Windows
echo Iniciando script de Restauracion de SGD-HC...

if "%~1"=="" (
    echo.
    echo USO:
    echo   backup\restore.bat -full   "..\backups\archivo.dump"
    echo   backup\restore.bat -tenant "..\backups\archivo.sql"
    echo.
    pause
    exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0restore.ps1" %*
if %errorlevel% neq 0 (
    echo.
    echo ERROR: El script de restauracion ha fallado con el codigo %errorlevel%
    pause
    exit /b %errorlevel%
)
echo.
pause
