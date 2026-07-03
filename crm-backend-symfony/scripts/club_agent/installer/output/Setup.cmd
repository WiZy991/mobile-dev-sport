@echo off
title FitnessClub Agent - Setup
cd /d "%~dp0"
set "ZIP=FitnessClubAgent-Setup-1.0.0.zip"
if not exist "%ZIP%" (
    echo Missing %ZIP% in this folder.
    pause
    exit /b 1
)
set "DEST=%TEMP%\FitnessClubAgent-setup"
if exist "%DEST%" rmdir /s /q "%DEST%"
mkdir "%DEST%"
powershell -NoProfile -Command "Expand-Archive -LiteralPath '%CD%\%ZIP%' -DestinationPath '%DEST%' -Force"
cd /d "%DEST%"
call install.bat
