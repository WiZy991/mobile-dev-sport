# Shows why SQLite may fail (paths, DLLs, PDO drivers). Run from crm-backend-symfony.

$ErrorActionPreference = 'Continue'
. (Join-Path $PSScriptRoot '_prepend-winget-php.ps1')

Write-Host '--- PHP executable ---'
Write-Host $CrmPhpExe
Write-Host '--- php.ini ---'
Write-Host $CrmPhpIni
$extLong = Join-Path (Split-Path $CrmPhpExe -Parent) 'ext'
Write-Host '--- ext folder (long path) ---'
Write-Host $extLong
if (Test-Path -LiteralPath $extLong) {
    $dll = Join-Path $extLong 'php_pdo_sqlite.dll'
    Write-Host "php_pdo_sqlite.dll exists: $(Test-Path -LiteralPath $dll)"
    Get-ChildItem -LiteralPath $extLong -Filter '*sqlite*' | ForEach-Object { Write-Host "  $($_.Name)" }
} else {
    Write-Host 'EXT FOLDER MISSING — reinstall PHP or use full zip from windows.php.net'
}

if ($CrmPhpIni -and (Test-Path -LiteralPath $CrmPhpIni)) {
    Write-Host '--- extension_dir lines in php.ini ---'
    Select-String -LiteralPath $CrmPhpIni -Pattern 'extension_dir' | ForEach-Object { $_.Line }
}

$dExt = @()
if ($CrmPhpExtDir) { $dExt = @('-d', "extension_dir=$CrmPhpExtDir") }

$printPdo = Join-Path $PSScriptRoot 'bin\print-pdo-drivers.php'
if ($CrmPhpExe -and $CrmPhpIni -and (Test-Path -LiteralPath $CrmPhpIni)) {
    Write-Host '--- php -m (pdo*) ---'
    & $CrmPhpExe -c $CrmPhpIni @dExt -m 2>&1 | Select-String -Pattern 'pdo|sqlite'
    Write-Host '--- PDO::getAvailableDrivers() ---'
    & $CrmPhpExe -c $CrmPhpIni @dExt $printPdo
}
