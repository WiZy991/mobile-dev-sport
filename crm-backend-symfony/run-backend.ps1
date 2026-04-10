# One-command backend launcher for Windows.
# First run (fresh machine):   .\run-backend.ps1 -Init
# Next runs:                   .\run-backend.ps1

param(
    [switch]$Init,
    [switch]$SkipSeed
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Update-PhpContext {
    . (Join-Path $PSScriptRoot '_prepend-winget-php.ps1')
}

function Install-PhpIfMissing {
    Update-PhpContext
    if ($CrmPhpExe -and (Test-Path -LiteralPath $CrmPhpExe)) {
        return
    }

    $winget = Get-Command winget -ErrorAction SilentlyContinue
    if (-not $winget) {
        Write-Error "PHP not found and winget is unavailable. Install PHP 8.5+ manually, then re-run .\run-backend.ps1"
        exit 1
    }

    Write-Host 'PHP not found. Installing via winget (PHP.PHP.8.5)...' -ForegroundColor Yellow
    & $winget.Source install PHP.PHP.8.5 --accept-package-agreements --accept-source-agreements --disable-interactivity

    Update-PhpContext
    if (-not $CrmPhpExe -or -not (Test-Path -LiteralPath $CrmPhpExe)) {
        Write-Error "PHP install did not finish correctly. Reopen terminal and run .\run-backend.ps1 again."
        exit 1
    }
}

Install-PhpIfMissing

if (-not $CrmPhpIni) {
    Write-Host 'Configuring php.ini for Symfony/SQLite...' -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot 'configure-php-win.ps1')
    Update-PhpContext
}

$autoload = Join-Path $PSScriptRoot 'vendor\autoload.php'
if ($Init -or -not (Test-Path -LiteralPath $autoload)) {
    Write-Host 'Running first-time local setup...' -ForegroundColor Yellow
    if ($SkipSeed) {
        & (Join-Path $PSScriptRoot 'setup-local-win.ps1') -SkipSeed
    } else {
        & (Join-Path $PSScriptRoot 'setup-local-win.ps1')
    }
}

Write-Host ''
Write-Host 'Starting backend server...' -ForegroundColor Green
& (Join-Path $PSScriptRoot 'dev-server.ps1')
