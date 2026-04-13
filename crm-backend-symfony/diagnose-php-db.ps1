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
    $dllSqlite = Join-Path $extLong 'php_pdo_sqlite.dll'
    $dllMysql = Join-Path $extLong 'php_pdo_mysql.dll'
    Write-Host "php_pdo_sqlite.dll exists: $(Test-Path -LiteralPath $dllSqlite)"
    Write-Host "php_pdo_mysql.dll exists:  $(Test-Path -LiteralPath $dllMysql)"
    Get-ChildItem -LiteralPath $extLong -Filter '*sqlite*' -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "  $($_.Name)" }
    Get-ChildItem -LiteralPath $extLong -Filter '*mysql*' -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "  $($_.Name)" }
} else {
    Write-Host 'EXT FOLDER MISSING — reinstall PHP or use full zip from windows.php.net'
}

if ($CrmPhpIni -and (Test-Path -LiteralPath $CrmPhpIni)) {
    Write-Host '--- extension_dir lines in php.ini ---'
    Select-String -LiteralPath $CrmPhpIni -Pattern 'extension_dir' | ForEach-Object { $_.Line }
}

$printPdo = Join-Path $PSScriptRoot 'bin\print-pdo-drivers.php'
if ($CrmPhpExe -and $CrmPhpIni -and (Test-Path -LiteralPath $CrmPhpIni)) {
    Write-Host '--- php -m (pdo*) ---'
    & $CrmPhpExe -c $CrmPhpIni -m 2>&1 | Select-String -Pattern 'pdo|sqlite|mysql'
    Write-Host '--- PDO::getAvailableDrivers() ---'
    & $CrmPhpExe -c $CrmPhpIni $printPdo
}
