# Dot-source: PATH + $CrmPhpExe + $CrmPhpIni + $CrmPhpExtDir (long path, forward slashes).
# NOTE: Do not use 8.3 paths with "~" in extension_dir — PHP truncates at ~ on Windows.
$machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$env:Path = $machinePath + ';' + $userPath

function Get-RegistryPhpExe {
    $roots = @(
        'HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*',
        'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*',
        'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*'
    )
    foreach ($root in $roots) {
        $items = Get-ItemProperty -Path $root -ErrorAction SilentlyContinue
        foreach ($item in $items) {
            $name = "$($item.DisplayName)"
            if ($name -notmatch '(?i)^php(\s|$)') { continue }
            $installLocation = "$($item.InstallLocation)"
            if (-not [string]::IsNullOrWhiteSpace($installLocation)) {
                $exe = Join-Path $installLocation 'php.exe'
                if (Test-Path -LiteralPath $exe) { return $exe }
            }
            $installSource = "$($item.InstallSource)"
            if (-not [string]::IsNullOrWhiteSpace($installSource)) {
                $exe = Join-Path $installSource 'php.exe'
                if (Test-Path -LiteralPath $exe) { return $exe }
            }
        }
    }
    return $null
}

function Get-WingetPackagePhpExe {
    $packagesRoot = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages'
    if (-not (Test-Path -LiteralPath $packagesRoot)) { return $null }

    $candidates = Get-ChildItem -LiteralPath $packagesRoot -Directory -Filter 'PHP.PHP*' -ErrorAction SilentlyContinue |
        ForEach-Object {
            @(
                (Join-Path $_.FullName 'php.exe'),
                (Join-Path $_.FullName 'php\php.exe')
            )
        } |
        Where-Object { Test-Path -LiteralPath $_ }

    return ($candidates | Select-Object -First 1)
}

function Get-WherePhpExe {
    $whereExe = Join-Path $env:SystemRoot 'System32\where.exe'
    if (-not (Test-Path -LiteralPath $whereExe)) { return $null }
    $out = & $whereExe php 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $out) { return $null }
    foreach ($line in $out) {
        $p = $line.Trim()
        if ($p -and (Test-Path -LiteralPath $p)) { return $p }
    }
    return $null
}

$CrmPhpExe = $null
$phpCmd = Get-Command php -ErrorAction SilentlyContinue
if ($phpCmd -and (Test-Path -LiteralPath $phpCmd.Source)) {
    $CrmPhpExe = $phpCmd.Source
}
if (-not $CrmPhpExe) { $CrmPhpExe = Get-WherePhpExe }
if (-not $CrmPhpExe) { $CrmPhpExe = Get-WingetPackagePhpExe }
if (-not $CrmPhpExe) { $CrmPhpExe = Get-RegistryPhpExe }

if ($CrmPhpExe -and (Test-Path -LiteralPath $CrmPhpExe)) {
    $phpDir = Split-Path $CrmPhpExe -Parent
    $env:Path = $phpDir + ';' + $machinePath + ';' + $userPath
}

$CrmPhpIni = $null
$CrmPhpExtDir = $null
if ($CrmPhpExe -and (Test-Path -LiteralPath $CrmPhpExe)) {
    $phpDir = Split-Path $CrmPhpExe -Parent
    $iniCandidate = Join-Path $phpDir 'php.ini'
    if (Test-Path -LiteralPath $iniCandidate) {
        $CrmPhpIni = $iniCandidate
    }
    $extLong = Join-Path $phpDir 'ext'
    if (Test-Path -LiteralPath $extLong) {
        $CrmPhpExtDir = $extLong.Replace('\', '/')
    }
}
