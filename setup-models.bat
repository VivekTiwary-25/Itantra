@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-models.ps1"
set EXITCODE=%ERRORLEVEL%
echo.
if not "%EXITCODE%"=="0" (
  echo setup-models.ps1 failed with exit code %EXITCODE%.
) else (
  echo Model setup completed successfully.
)
pause
exit /b %EXITCODE%
