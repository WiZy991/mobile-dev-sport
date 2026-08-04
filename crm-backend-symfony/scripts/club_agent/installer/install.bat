@echo off
chcp 65001 >nul
title FitnessClub Agent - install
cd /d "%~dp0"
echo.
echo  FitnessClub Agent - install
echo  ==========================
echo.

if exist "%~dp0build_and_install.ps1" (
  echo Preparing EXE...
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_and_install.ps1"
  if errorlevel 1 (
    echo.
    echo Build/prepare failed. Install Python 3 from python.org with "Add to PATH", then retry.
    pause
    exit /b 1
  )
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" -AddFirewallRule -Launch
if errorlevel 1 (
    echo.
    echo Install failed.
    pause
    exit /b 1
)
