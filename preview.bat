@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0preview.ps1"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Standalone preview exited with error code %ERRORLEVEL%.
    pause
)
