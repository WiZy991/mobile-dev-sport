# Build FitnessClubAgent.exe (if missing) and prepare folder for install.ps1.
# Runs from extracted Setup package on Windows.
$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
Set-Location -LiteralPath $Root

$exe = Join-Path $Root "FitnessClubAgent.exe"
if (-not (Test-Path -LiteralPath $exe)) {
    Write-Host "=== Building FitnessClubAgent.exe (Python + PyInstaller) ===" -ForegroundColor Cyan
    $buildPs1 = Join-Path $Root "build.ps1"
    if (-not (Test-Path -LiteralPath $buildPs1)) {
        throw "Missing build.ps1 next to install scripts. Broken package."
    }
    & $buildPs1
    if ($LASTEXITCODE -ne 0 -and -not $?) {
        throw "build.ps1 failed."
    }

    $built = Join-Path $Root (Join-Path "dist" "FitnessClubAgent.exe")
    if (-not (Test-Path -LiteralPath $built)) {
        $alt = Get-ChildItem -LiteralPath $Root -Filter "FitnessClubAgent.exe" -Recurse -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($alt) {
            $built = $alt.FullName
        }
    }
    if (-not (Test-Path -LiteralPath $built)) {
        throw "Build finished but FitnessClubAgent.exe not found under dist\."
    }
    Copy-Item -Force -LiteralPath $built -Destination $exe
    Write-Host "EXE ready: $exe" -ForegroundColor Green
} else {
    Write-Host "FitnessClubAgent.exe already present - skip build." -ForegroundColor Gray
}

$configPath = Join-Path $Root (Join-Path "config" "agent_config.json")
if (-not (Test-Path -LiteralPath $configPath)) {
    $example = Join-Path $Root "agent_config.example.json"
    if (Test-Path -LiteralPath $example) {
        New-Item -ItemType Directory -Force -Path (Join-Path $Root "config") | Out-Null
        Copy-Item -Force -LiteralPath $example -Destination $configPath
    }
}
