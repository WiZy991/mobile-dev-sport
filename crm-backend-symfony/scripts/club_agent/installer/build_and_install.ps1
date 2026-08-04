# Build FitnessClubAgent.exe (if missing) and prepare folder for install.ps1.
# Runs from extracted Setup package on Windows.
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
    $built = Join-Path $Root (Join-Path "dist" "FitnessClubAgent.exe")
    if (-not (Test-Path $built)) {
        throw "Build finished but dist\FitnessClubAgent.exe not found."
    }
    Copy-Item -Force $built $exe
    Write-Host "EXE ready: $exe" -ForegroundColor Green
} else {
    Write-Host "FitnessClubAgent.exe already present - skip build." -ForegroundColor Gray
}

$configPath = Join-Path $Root (Join-Path "config" "agent_config.json")
if (-not (Test-Path $configPath)) {
    $example = Join-Path $Root "agent_config.example.json"
    if (Test-Path $example) {
        New-Item -ItemType Directory -Force -Path (Join-Path $Root "config") | Out-Null
        Copy-Item -Force $example $configPath
    }
}
