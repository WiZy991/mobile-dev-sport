@echo off
chcp 65001 >nul
title FitnessClub Agent - install
cd /d "%~dp0"
echo.
echo  FitnessClub Agent - install
echo  ==========================
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" -AddFirewallRule -Launch
if errorlevel 1 (
    echo.
    echo Install failed.
    pause
    exit /b 1
)
