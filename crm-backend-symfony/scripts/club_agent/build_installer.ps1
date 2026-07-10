# Сборка дистрибутива FitnessClub Agent (exe + установщик)
$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
$InstallerDir = Join-Path $Root "installer"
$StagingDir = Join-Path $InstallerDir "staging"
$OutputDir = Join-Path $InstallerDir "output"
$Version = (Get-Content (Join-Path $InstallerDir "VERSION.txt") -Raw).Trim()

Write-Host "=== FitnessClub Agent installer build v$Version ===" -ForegroundColor Cyan

# 1. PyInstaller
& (Join-Path $Root "build.ps1")

$exe = Join-Path $Root "dist\FitnessClubAgent.exe"
if (-not (Test-Path $exe)) {
    throw "Build failed: missing $exe"
}

# 2. Staging
if (Test-Path $StagingDir) { Remove-Item -Recurse -Force $StagingDir }
New-Item -ItemType Directory -Force -Path (Join-Path $StagingDir "config") | Out-Null
Copy-Item -Force $exe (Join-Path $StagingDir "FitnessClubAgent.exe")
Copy-Item -Force (Join-Path $Root "agent_config.example.json") (Join-Path $StagingDir "config\agent_config.json")
Copy-Item -Force (Join-Path $InstallerDir "install.ps1") $StagingDir
Copy-Item -Force (Join-Path $InstallerDir "uninstall.ps1") $StagingDir
Copy-Item -Force (Join-Path $InstallerDir "VERSION.txt") $StagingDir
Copy-Item -Force (Join-Path $InstallerDir "install.bat") $StagingDir

$installTxt = Join-Path $OutputDir "INSTALL.txt"
if (Test-Path $installTxt) { Copy-Item -Force $installTxt $StagingDir }

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

# 3. ZIP (всегда)
$zipName = "FitnessClubAgent-Setup-$Version.zip"
$zipPath = Join-Path $OutputDir $zipName
if (Test-Path $zipPath) { Remove-Item -Force $zipPath }
Compress-Archive -Path (Join-Path $StagingDir "*") -DestinationPath $zipPath -Force
Write-Host "ZIP: $zipPath" -ForegroundColor Green

$setupCmd = Join-Path $OutputDir "Setup.cmd"
@"
@echo off
title FitnessClub Agent - Setup
cd /d "%~dp0"
set "ZIP=$zipName"
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
"@ | Set-Content -Path $setupCmd -Encoding ASCII
Write-Host "Launcher: $setupCmd" -ForegroundColor Green

# 4. Inno Setup (если установлен)
$iscc = @(
    "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
    "${env:ProgramFiles}\Inno Setup 6\ISCC.exe",
    "${env:ProgramFiles(x86)}\Inno Setup 5\ISCC.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1

$setupExe = Join-Path $OutputDir "FitnessClubAgent-Setup.exe"
if ($iscc) {
    Write-Host "Inno Setup: $iscc" -ForegroundColor Gray
    Push-Location $InstallerDir
    try {
        & $iscc "FitnessClubAgent.iss"
        if (Test-Path (Join-Path $OutputDir "FitnessClubAgent-Setup.exe")) {
            $versioned = Join-Path $OutputDir "FitnessClubAgent-Setup-$Version.exe"
            Copy-Item -Force $setupExe $versioned
            Write-Host "Setup EXE: $versioned" -ForegroundColor Green
        }
    } finally {
        Pop-Location
    }
} else {
    Write-Host "Inno Setup not found - ZIP + install.bat only" -ForegroundColor Yellow
    Write-Host "  Or run Setup.cmd next to the ZIP file" -ForegroundColor Yellow
    Write-Host "  Or install Inno Setup 6 and re-run build_installer.ps1" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Done." -ForegroundColor Green
Write-Host "  Deploy: FitnessClubAgent-Setup.exe or install.bat from ZIP" -ForegroundColor Green
