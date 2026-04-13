# Запуск Composer без глобальной установки (как console.ps1 для PHP).
# Первый раз скачает composer.phar рядом с проектом (см. .gitignore).
#
# Примеры:
#   .\composer.ps1 install
#   .\composer.ps1 update symfony/mime
#   .\composer.ps1 require symfony/mime:8.0.*
#
# PhpSpreadsheet требует ext-gd. Если GD ещё не включён:
#   .\configure-php-win.ps1
# Временно обновить lock без GD в CLI:
#   .\composer.ps1 update --no-interaction --ignore-platform-req=ext-gd

param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $ComposerArgs
)

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
        Remove-Item -Force $phar -ErrorAction SilentlyContinue
        & curl.exe -fsSL 'https://getcomposer.org/download/latest-stable/composer.phar' -o $phar
    }
}
if (-not (Test-Path -LiteralPath $phar) -or ((Get-Item $phar).Length -lt 2MB)) {
    Write-Error 'Could not download composer.phar. See https://getcomposer.org/download/'
    exit 1
}

$dExt = @()
if ($CrmPhpExtDir) { $dExt = @('-d', "extension_dir=$CrmPhpExtDir") }

if ($CrmPhpIni) {
    & $CrmPhpExe -c $CrmPhpIni @dExt $phar @ComposerArgs
} else {
    & $CrmPhpExe @dExt $phar @ComposerArgs
}
exit $LASTEXITCODE
