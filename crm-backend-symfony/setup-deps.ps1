# Installs PHP deps: downloads composer.phar if needed, runs composer install.
# Run: powershell -ExecutionPolicy Bypass -File .\setup-deps.ps1

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

. (Join-Path $PSScriptRoot '_prepend-winget-php.ps1')
if (-not $CrmPhpExe -or -not (Test-Path -LiteralPath $CrmPhpExe)) {
    Write-Error 'PHP not found. Install: winget install PHP.PHP.8.5'
    exit 1
}

$phar = Join-Path $PSScriptRoot 'composer.phar'
if (-not (Test-Path -LiteralPath $phar) -or ((Get-Item $phar).Length -lt 2MB)) {
    Write-Host 'Downloading composer.phar...'
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    try {
        Invoke-WebRequest -Uri 'https://getcomposer.org/download/latest-stable/composer.phar' -OutFile $phar -UseBasicParsing
    } catch {
        Write-Host 'Invoke-WebRequest failed, trying curl...'
        Remove-Item -Force $phar -ErrorAction SilentlyContinue
        & curl.exe -fsSL 'https://getcomposer.org/download/latest-stable/composer.phar' -o $phar
    }
}

if (-not (Test-Path -LiteralPath $phar) -or ((Get-Item $phar).Length -lt 2MB)) {
    Write-Error 'Could not download composer.phar. Install Composer from https://getcomposer.org/download/ then run: composer install'
    exit 1
}

Write-Host 'Running composer install...'
if ($CrmPhpIni) {
    & $CrmPhpExe -c $CrmPhpIni composer.phar install --no-interaction
} else {
    & $CrmPhpExe composer.phar install --no-interaction
}
Write-Host 'Done. Start server: .\dev-server.ps1'
