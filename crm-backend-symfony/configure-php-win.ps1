# One-time: copy php.ini-development to php.ini and enable extensions (Composer + Symfony).
# extension_dir = full path with forward slashes (NOT 8.3 with ~ — PHP breaks on ~ on Windows).
# extension=php_*.dll — explicit names for Windows.
# Run after: winget install PHP.PHP.8.5

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_prepend-winget-php.ps1')
if (-not $CrmPhpExe -or -not (Test-Path -LiteralPath $CrmPhpExe)) {
    Write-Error 'PHP not found. Install: winget install PHP.PHP.8.5'
    exit 1
}
$phpRoot = Split-Path $CrmPhpExe -Parent
$ini = Join-Path $phpRoot 'php.ini'
$dev = Join-Path $phpRoot 'php.ini-development'
if (-not (Test-Path -LiteralPath $dev)) {
    Write-Error "Not found: $dev"
    exit 1
}
if (-not (Test-Path -LiteralPath $ini)) {
    Copy-Item $dev $ini
    Write-Host "Created $ini"
}

$extPathLong = Join-Path $phpRoot 'ext'
if (-not (Test-Path -LiteralPath $extPathLong)) {
    Write-Error "PHP ext folder missing: $extPathLong. Reinstall PHP or use full TS x64 ZIP from https://windows.php.net/download/"
    exit 1
}

$pdoDll = Join-Path $extPathLong 'php_pdo_sqlite.dll'
if (-not (Test-Path -LiteralPath $pdoDll)) {
    Write-Error "Missing php_pdo_sqlite.dll. WinGet PHP may be incomplete. Download Thread Safe x64 ZIP from https://windows.php.net/download/ and extract into:`n$phpRoot"
    exit 1
}

$extAbs = $extPathLong.Replace('\', '/')
Write-Host "extension_dir: $extAbs"

$c = Get-Content -LiteralPath $ini -Raw -Encoding UTF8

$c = [regex]::Replace($c, '(?m)^\s*;?\s*extension_dir\s*=.*\r?\n?', '')
$c = [regex]::Replace($c, '(\[PHP\]\r?\n)', "`$1extension_dir = `"$extAbs`"`r`n", 1)

$replacements = @{
    ';extension=curl'       = 'extension=php_curl.dll'
    ';extension=gd'        = 'extension=php_gd.dll'
    ';extension=fileinfo'   = 'extension=php_fileinfo.dll'
    ';extension=intl'       = 'extension=php_intl.dll'
    ';extension=mbstring'   = 'extension=php_mbstring.dll'
    ';extension=openssl'    = 'extension=php_openssl.dll'
    ';extension=pdo_sqlite' = 'extension=php_pdo_sqlite.dll'
    ';extension=sqlite3'    = 'extension=php_sqlite3.dll'
    ';extension=zip'        = 'extension=php_zip.dll'
}
foreach ($kv in $replacements.GetEnumerator()) {
    $c = $c.Replace($kv.Key, $kv.Value)
}

$c = $c.Replace('extension=pdo_sqlite', 'extension=php_pdo_sqlite.dll')
$c = $c.Replace('extension=sqlite3', 'extension=php_sqlite3.dll')

# Fix leftover bare names (from older php.ini) — PHP looks for wrong files
$bare = [ordered]@{
    '(?m)^\s*extension\s*=\s*curl\s*$'       = 'extension=php_curl.dll'
    '(?m)^\s*extension\s*=\s*fileinfo\s*$'   = 'extension=php_fileinfo.dll'
    '(?m)^\s*extension\s*=\s*intl\s*$'      = 'extension=php_intl.dll'
    '(?m)^\s*extension\s*=\s*mbstring\s*$'  = 'extension=php_mbstring.dll'
    '(?m)^\s*extension\s*=\s*openssl\s*$'  = 'extension=php_openssl.dll'
    '(?m)^\s*extension\s*=\s*zip\s*$'       = 'extension=php_zip.dll'
    '(?m)^\s*extension\s*=\s*gd\s*$'        = 'extension=php_gd.dll'
}
foreach ($kv in $bare.GetEnumerator()) {
    $c = [regex]::Replace($c, $kv.Key, $kv.Value)
}

$utf8Bom = New-Object System.Text.UTF8Encoding $true
[System.IO.File]::WriteAllText($ini, $c, $utf8Bom)
# Built-in server uses SAPI "cli-server"; if php-cli-server.ini exists, PHP uses it instead of php.ini.
$cliServerIni = Join-Path $phpRoot 'php-cli-server.ini'
[System.IO.File]::WriteAllText($cliServerIni, $c, $utf8Bom)

Write-Host 'OK. Run: .\diagnose-php-db.ps1  then  .\dev-server.ps1'
