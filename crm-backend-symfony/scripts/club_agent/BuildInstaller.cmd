@echo off
title FitnessClub Agent - build installer 1.0.1
cd /d "%~dp0"
echo.
echo Building FitnessClubAgent.exe + Setup ZIP...
echo Requires: Python 3 from python.org (with "Add to PATH")
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_installer.ps1"
if errorlevel 1 (
  echo.
  echo BUILD FAILED.
  pause
  exit /b 1
)
echo.
echo Open folder: installer\output
explorer "%~dp0installer\output"
pause
