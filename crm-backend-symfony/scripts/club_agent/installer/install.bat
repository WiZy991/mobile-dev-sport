@echo off
chcp 65001 >nul
title FitnessClub Agent - install
cd /d "%~dp0"
echo.
echo  FitnessClub Agent - install
echo  ==========================
echo.

if not exist "%~dp0FitnessClubAgent.exe" (
  if exist "%~dp0build_and_install.ps1" (
    echo EXE missing - building with Python...
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_and_install.ps1"
    if errorlevel 1 (
      echo.
      echo Build failed. Use the prebuilt ZIP from GitHub Actions, or install Python 3 and retry.
      pause
      exit /b 1
    )
  ) else (
    echo Missing FitnessClubAgent.exe next to install.bat.
    echo Download the prebuilt Setup ZIP from GitHub Actions artifact FitnessClubAgent-Setup.
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
