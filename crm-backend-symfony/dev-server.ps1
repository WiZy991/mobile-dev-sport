# Локальный веб-сервер для Symfony (корень — public).
# Запускает именно WinGet php.exe (не "php" из PATH — иначе часто C:\php без pdo_sqlite).
# Если порт 8000 уже занят другим php.exe без SQLite, в браузере будет «could not find driver» при том что diagnose-php-db.ps1 «всё ок».
# Порт занят: .\dev-server.ps1 -KillListener  (завершит процесс, который слушает выбранный порт)
# Другой порт: $env:CRM_DEV_PORT='8010'; .\dev-server.ps1

param(
    [switch]$KillListener
)

. (Join-Path $PSScriptRoot '_prepend-winget-php.ps1')

function Get-CrmDatabaseUrlFromEnvFiles {
    param([string]$Root)
    $url = ''
    foreach ($name in @('.env', '.env.local')) {
        $path = Join-Path $Root $name
        if (-not (Test-Path -LiteralPath $path)) { continue }
        foreach ($line in Get-Content -LiteralPath $path) {
            if ($line -match '^\s*#' ) { continue }
            if ($line -match '^\s*DATABASE_URL\s*=\s*(.+)$') {
                $v = $Matches[1].Trim()
                if (($v.StartsWith('"') -and $v.EndsWith('"')) -or ($v.StartsWith("'") -and $v.EndsWith("'"))) {
                    $v = $v.Substring(1, $v.Length - 2)
                }
                $url = $v
            }
        }
    }
    return $url
}

function Get-DevPortListenerPids {
    param([int]$Port)
    try {
        Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop |
            Select-Object -ExpandProperty OwningProcess -Unique
    } catch {
        @()
    }
}

function Show-DevPortListeners {
    param([int]$Port)
    $pids = @(Get-DevPortListenerPids -Port $Port)
    if ($pids.Count -eq 0) {
        Write-Host "  (could not resolve PID via Get-NetTCPConnection; try: netstat -ano | findstr :$Port )" -ForegroundColor DarkGray
        return
    }
    foreach ($procId in $pids) {
        $p = Get-CimInstance Win32_Process -Filter "ProcessId=$procId" -ErrorAction SilentlyContinue
        $exe = if ($p) { $p.ExecutablePath } else { '?' }
        $cmd = if ($p) { $p.CommandLine } else { '' }
        Write-Host "  PID $procId  $exe" -ForegroundColor Gray
        if ($cmd) {
            Write-Host "           $cmd" -ForegroundColor DarkGray
            if ($cmd -match '(?i)php(\.exe)?' -and $cmd -match '-S\s' -and $cmd -notmatch '\s-c\s') {
                Write-Host '           ^ php -S without -c php.ini (Cursor/VS Code PHP Server, task, or manual) -> site: could not find driver' -ForegroundColor Red
            }
        }
    }
}

function Clear-DevPortListeners {
    param([int]$Port, [int]$MaxRounds = 20)
    $taskkill = Join-Path $env:SystemRoot 'System32\taskkill.exe'
    for ($round = 0; $round -lt $MaxRounds; $round++) {
        if (-not (Test-DevPortInUse -Port $Port)) {
            return $true
        }
        $pids = @(Get-DevPortListenerPids -Port $Port)
        foreach ($procId in $pids) {
            Write-Host "Stopping PID $procId (attempt $($round + 1)/$MaxRounds)..." -ForegroundColor Yellow
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
            if (Test-Path -LiteralPath $taskkill) {
                & $taskkill /F /PID $procId 2>&1 | Out-Null
            }
        }
        Start-Sleep -Milliseconds 450
    }
    return -not (Test-DevPortInUse -Port $Port)
}

function Test-DevPortInUse {
    param([string]$HostName = '127.0.0.1', [int]$Port = 8000)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $iar.AsyncWaitHandle.WaitOne(400, $false)) { return $false }
        $client.EndConnect($iar)
        return $client.Connected
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

if (-not $CrmPhpExe -or -not (Test-Path -LiteralPath $CrmPhpExe)) {
    Write-Error 'PHP not found. Install: winget install PHP.PHP.8.5'
    exit 1
}

if (-not $CrmPhpIni) {
    Write-Error 'php.ini missing next to php.exe. Run: .\configure-php-win.ps1'
    exit 1
}

# Не передаём -d extension_dir=... с абсолютным путём: кириллица в профиле ломает загрузку DLL.
# В php.ini после configure-php-win.ps1 должно быть extension_dir = "ext".

# -c forces this ini for built-in server (avoids "could not find driver" when ini is skipped).
$mods = & $CrmPhpExe -c $CrmPhpIni -m 2>&1 | Out-String
$dbUrl = Get-CrmDatabaseUrlFromEnvFiles -Root $PSScriptRoot
$useMysql = $dbUrl -match '(?i)mysql:'
$useSqlite = $dbUrl -match '(?i)sqlite:'
if ([string]::IsNullOrWhiteSpace($dbUrl)) {
    $useMysql = $true
}
if ($useMysql) {
    if ($mods -notmatch '(?m)^pdo_mysql\s*$') {
        Write-Host "pdo_mysql missing (DATABASE_URL uses MySQL). Run: .\configure-php-win.ps1"
        Write-Host "Using: $CrmPhpExe"
        exit 1
    }
    $pdoMysqlCheck = Join-Path $PSScriptRoot 'bin\pdo-mysql-check.php'
    $pdoMysqlOut = & $CrmPhpExe -c $CrmPhpIni $pdoMysqlCheck 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host $pdoMysqlOut
        Write-Host "PDO MySQL self-test failed. Run: .\configure-php-win.ps1"
        exit 1
    }
}
if ($useSqlite) {
    if ($mods -notmatch '(?m)^pdo_sqlite\s*$') {
        Write-Host "pdo_sqlite missing (DATABASE_URL uses SQLite). Run: .\configure-php-win.ps1"
        Write-Host "Using: $CrmPhpExe"
        exit 1
    }
    $pdoSqliteCheck = Join-Path $PSScriptRoot 'bin\pdo-sqlite-check.php'
    $pdoSqliteOut = & $CrmPhpExe -c $CrmPhpIni $pdoSqliteCheck 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host $pdoSqliteOut
        Write-Host "PDO SQLite self-test failed. Run: .\configure-php-win.ps1"
        exit 1
    }
}

Set-Location $PSScriptRoot

$DevPort = 8000
if ($env:CRM_DEV_PORT) {
    $parsedPort = 0
    if (-not [int]::TryParse($env:CRM_DEV_PORT, [ref]$parsedPort) -or $parsedPort -lt 1 -or $parsedPort -gt 65535) {
        Write-Error "CRM_DEV_PORT must be 1-65535, got: $($env:CRM_DEV_PORT)"
        exit 1
    }
    $DevPort = $parsedPort
}

if (Test-DevPortInUse -Port $DevPort) {
    # ASCII only: Write-Error + Cyrillic mojibreaks in Windows PowerShell 5.1 default code pages.
    Write-Host ''
    Write-Host "Port $DevPort is already in use (often another 'php -S' still running)." -ForegroundColor Yellow
    Write-Host 'Listeners:' -ForegroundColor Yellow
    Show-DevPortListeners -Port $DevPort
    Write-Host ''
    Write-Host 'If you see TWO php.exe: one is often an IDE auto-started server (127.0.0.1, no -c). Disable that extension/task or use CRM_DEV_PORT=8010 for this project only.' -ForegroundColor DarkYellow
    Write-Host ''
    Write-Host 'Stop that PID (close its terminal), Task Manager, or run:' -ForegroundColor Yellow
    Write-Host "  .\dev-server.ps1 -KillListener" -ForegroundColor Cyan
    Write-Host 'Or another port:' -ForegroundColor Yellow
    Write-Host "  `$env:CRM_DEV_PORT='8010'; .\dev-server.ps1" -ForegroundColor Cyan
    Write-Host ''

    if ($KillListener) {
        $cleared = Clear-DevPortListeners -Port $DevPort
        if (-not $cleared) {
            Write-Host "Port $DevPort still in use after -KillListener (something may be respawning php -S every few ms)." -ForegroundColor Red
            Write-Host 'Turn off Cursor/VS Code "PHP Server" / Symfony auto-server, or use: $env:CRM_DEV_PORT=''8010''; .\dev-server.ps1' -ForegroundColor Yellow
            exit 1
        }
        Write-Host "Port $DevPort is free. Starting server..." -ForegroundColor Green
        Write-Host ''
    } else {
        exit 1
    }
}

# Только наш dev-server подставляет это — public/check-pdo.php отвечает 404 без переменной.
$env:CRM_WINGET_DEVCHECK = '1'

Write-Host "PHP:  $CrmPhpExe"
Write-Host "INI:  $CrmPhpIni"
Write-Host "PORT: $DevPort"
Write-Host "Admin:     http://127.0.0.1:$DevPort/admin"
Write-Host "PDO check: http://127.0.0.1:$DevPort/check-pdo.php"
# 0.0.0.0 so IPv4 localhost and LAN work.
& $CrmPhpExe -c $CrmPhpIni -S "0.0.0.0:$DevPort" -t public
