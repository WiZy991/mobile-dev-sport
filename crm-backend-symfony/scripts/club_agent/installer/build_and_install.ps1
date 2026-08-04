# Сборка FitnessClubAgent.exe (если нет) и подготовка папки для install.ps1.
# Запускается из распакованного Setup-пакета на Windows.
$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
Set-Location $Root

$exe = Join-Path $Root "FitnessClubAgent.exe"
if (-not (Test-Path $exe)) {
    Write-Host "=== Building FitnessClubAgent.exe (Python + PyInstaller) ===" -ForegroundColor Cyan
    $buildPs1 = Join-Path $Root "build.ps1"
    if (-not (Test-Path $buildPs1)) {
        throw "Missing build.ps1 next to install scripts. Broken package."
    }
    & $buildPs1
    $built = Join-Path $Root "dist\FitnessClubAgent.exe"
    if (-not (Test-Path $built)) {
        throw "Build finished but dist\FitnessClubAgent.exe not found."
    }
    Copy-Item -Force $built $exe
    Write-Host "EXE ready: $exe" -ForegroundColor Green
} else {
    Write-Host "FitnessClubAgent.exe already present — skip build." -ForegroundColor Gray
}

if (-not (Test-Path (Join-Path $Root "config\agent_config.json"))) {
    $example = Join-Path $Root "agent_config.example.json"
    if (Test-Path $example) {
        New-Item -ItemType Directory -Force -Path (Join-Path $Root "config") | Out-Null
        Copy-Item -Force $example (Join-Path $Root "config\agent_config.json")
    }
}
