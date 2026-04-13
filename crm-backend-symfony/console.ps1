# Symfony CLI с тем же php.exe и php.ini, что и dev-server.ps1.
# Не используйте голый "php bin/console" из PATH — без -c и с битым extension_dir будет could not find driver.
# Примеры:
#   .\console.ps1 doctrine:schema:validate
#   .\console.ps1 doctrine:migrations:status
#   composer run db-reset-dev   (альтернатива полному сбросу БД)

param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $CommandArgs
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_prepend-winget-php.ps1')

if (-not $CrmPhpExe -or -not (Test-Path -LiteralPath $CrmPhpExe)) {
    Write-Error 'PHP not found. Install: winget install PHP.PHP.8.5'
    exit 1
}
if (-not $CrmPhpIni -or -not (Test-Path -LiteralPath $CrmPhpIni)) {
    Write-Error 'php.ini missing next to php.exe. Run: .\configure-php-win.ps1'
    exit 1
}

Set-Location $PSScriptRoot
$console = Join-Path $PSScriptRoot 'bin\console'
& $CrmPhpExe -c $CrmPhpIni $console @CommandArgs
exit $LASTEXITCODE
