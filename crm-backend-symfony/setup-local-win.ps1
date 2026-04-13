# First-time Windows setup after: git clone ... && cd crm-backend-symfony
# Requires: winget install PHP.PHP.8.5 (and network for Composer).
# WARNING: recreates SQLite DB (drops all local data in var/data_*.db).
# Run: powershell -ExecutionPolicy Bypass -File .\setup-local-win.ps1

param(
    [string] $AdminEmail = 'admin@localhost',
    [string] $AdminPassword = 'admin',
    [string] $AdminName = 'Administrator',
    [switch] $SkipSeed
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

. (Join-Path $PSScriptRoot '_prepend-winget-php.ps1')
if (-not $CrmPhpExe -or -not (Test-Path -LiteralPath $CrmPhpExe)) {
    Write-Error 'PHP not found. Install: winget install PHP.PHP.8.5 --accept-package-agreements'
    exit 1
}

Write-Host '>>> configure-php-win.ps1 (extensions; extension_dir = ext)'
& (Join-Path $PSScriptRoot 'configure-php-win.ps1')
. (Join-Path $PSScriptRoot '_prepend-winget-php.ps1')

# APP_SECRET required; .env in repo may leave it empty
$envLocal = Join-Path $PSScriptRoot '.env.local'
$needSecret = $true
if (Test-Path -LiteralPath $envLocal) {
    if ((Get-Content -LiteralPath $envLocal -Raw) -match '(?m)^APP_SECRET=\S+') { $needSecret = $false }
}
if ($needSecret) {
    $b = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    $sec = [Convert]::ToBase64String($b)
    if (Test-Path -LiteralPath $envLocal) {
        Add-Content -LiteralPath $envLocal -Value "`nAPP_SECRET=$sec`n"
    } else {
        Set-Content -LiteralPath $envLocal -Value "APP_SECRET=$sec`n"
    }
    Write-Host '>>> wrote APP_SECRET to .env.local'
}

Write-Host '>>> composer install (setup-deps.ps1)'
& (Join-Path $PSScriptRoot 'setup-deps.ps1')
if (-not $?) { exit 1 }

$console = Join-Path $PSScriptRoot 'bin\console'
$iniOpt = @()
if ($CrmPhpIni) { $iniOpt += '-c', $CrmPhpIni }

Write-Host '>>> SQLite database (schema from entities; migrations marked applied)'
& $CrmPhpExe @iniOpt $console doctrine:schema:drop --full-database --force --no-interaction
& $CrmPhpExe @iniOpt $console doctrine:schema:create --no-interaction
& $CrmPhpExe @iniOpt $console doctrine:migrations:sync-metadata-storage --no-interaction
& $CrmPhpExe @iniOpt $console doctrine:migrations:version --add --all --no-interaction

Write-Host ">>> CRM super admin: $AdminEmail"
& $CrmPhpExe @iniOpt $console app:create-staff-user $AdminEmail $AdminPassword "--name=$AdminName" --no-interaction
if ($LASTEXITCODE -ne 0) {
    Write-Host '(If "already exists", login with that email or pick another -AdminEmail.)'
}

if (-not $SkipSeed) {
    Write-Host '>>> optional test data (app:seed-data)'
    & $CrmPhpExe @iniOpt $console app:seed-data --no-interaction
}

Write-Host ''
Write-Host '=== Done ==='
Write-Host 'Start server:  .\dev-server.ps1'
Write-Host 'Open:          http://127.0.0.1:8000/admin'
Write-Host "Login CRM:     $AdminEmail / $AdminPassword"
Write-Host 'API base:      http://127.0.0.1:8000/api/v1/'
