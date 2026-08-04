@echo off
title FitnessClub Agent - Setup 1.0.2
cd /d "%~dp0"
set "ZIP=FitnessClubAgent-Setup-1.0.2.zip"
if not exist "%ZIP%" (
    echo Missing %ZIP% in this folder.
    pause
    exit /b 1
)
echo.
echo Need Python 3 once (python.org, Add to PATH) — Setup will build EXE then install.
echo.
set "DEST=%TEMP%\FitnessClubAgent-setup"
if exist "%DEST%" rmdir /s /q "%DEST%"
mkdir "%DEST%"
powershell -NoProfile -Command "Expand-Archive -LiteralPath '%CD%\%ZIP%' -DestinationPath '%DEST%' -Force"
cd /d "%DEST%"
call install.bat
