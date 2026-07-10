# FitnessClub Agent - Windows install script
# Usage: powershell -ExecutionPolicy Bypass -File install.ps1 [-InstallDir path] [-AddFirewallRule] [-Launch] [-Silent]
param(
    [string]$InstallDir = "",
    [switch]$AddFirewallRule,
    [switch]$Launch,
    [switch]$Silent
)

$ErrorActionPreference = "Stop"
$AppName = "FitnessClub Agent"
$ExeName = "FitnessClubAgent.exe"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

if (-not $InstallDir) {
    $InstallDir = Join-Path $env:LOCALAPPDATA "FitnessClubAgent"
}

$SourceExe = Join-Path $ScriptDir $ExeName
if (-not (Test-Path $SourceExe)) {
    throw "Missing $ExeName next to install.ps1. Run from distribution folder."
}

function Write-Step([string]$Msg) {
    if (-not $Silent) { Write-Host $Msg }
}

Write-Step "=== $AppName - install ==="
Write-Step "Target: $InstallDir"

Get-Process -Name "FitnessClubAgent" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 1

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
$configDir = Join-Path $InstallDir "config"
New-Item -ItemType Directory -Force -Path $configDir | Out-Null

Copy-Item -Force $SourceExe (Join-Path $InstallDir $ExeName)

$exampleConfig = Join-Path $ScriptDir "config\agent_config.json"
$targetConfig = Join-Path $configDir "agent_config.json"
if ((Test-Path $exampleConfig) -and -not (Test-Path $targetConfig)) {
    Copy-Item -Force $exampleConfig $targetConfig
    Write-Step "Config created: $targetConfig"
} elseif (-not (Test-Path $targetConfig)) {
    $appdataConfig = Join-Path $env:APPDATA "FitnessClubAgent\agent_config.json"
    if (Test-Path $appdataConfig) {
        Copy-Item -Force $appdataConfig $targetConfig
        Write-Step "Config copied from $appdataConfig"
    }
}

$uninstallSrc = Join-Path $ScriptDir "uninstall.ps1"
if (Test-Path $uninstallSrc) {
    Copy-Item -Force $uninstallSrc (Join-Path $InstallDir "uninstall.ps1")
}

$Wsh = New-Object -ComObject WScript.Shell
$desktop = [Environment]::GetFolderPath("Desktop")
$startMenu = Join-Path ([Environment]::GetFolderPath("Programs")) "FitnessClub Agent"
New-Item -ItemType Directory -Force -Path $startMenu | Out-Null

foreach ($pair in @(
    @{ Path = (Join-Path $desktop "FitnessClub Agent.lnk"); Desc = "FitnessClub club agent" },
    @{ Path = (Join-Path $startMenu "FitnessClub Agent.lnk"); Desc = "FitnessClub club agent" }
)) {
    $s = $Wsh.CreateShortcut($pair.Path)
    $s.TargetPath = Join-Path $InstallDir $ExeName
    $s.WorkingDirectory = $InstallDir
    $s.Description = $pair.Desc
    $s.Save()
}

$uninstallPs1 = Join-Path $InstallDir "uninstall.ps1"
$s = $Wsh.CreateShortcut((Join-Path $startMenu "Uninstall FitnessClub Agent.lnk"))
$s.TargetPath = "powershell.exe"
$s.Arguments = "-ExecutionPolicy Bypass -File `"$uninstallPs1`""
$s.WorkingDirectory = $InstallDir
$s.Save()

$regPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\FitnessClubAgent"
New-Item -Path $regPath -Force | Out-Null
Set-ItemProperty -Path $regPath -Name "DisplayName" -Value $AppName
$verFile = Join-Path $ScriptDir "VERSION.txt"
$ver = if (Test-Path $verFile) { (Get-Content $verFile -Raw).Trim() } else { "1.0.0" }
Set-ItemProperty -Path $regPath -Name "DisplayVersion" -Value $ver
Set-ItemProperty -Path $regPath -Name "Publisher" -Value "FitnessClub"
Set-ItemProperty -Path $regPath -Name "InstallLocation" -Value $InstallDir
Set-ItemProperty -Path $regPath -Name "UninstallString" -Value "powershell.exe -ExecutionPolicy Bypass -File `"$uninstallPs1`""
Set-ItemProperty -Path $regPath -Name "DisplayIcon" -Value (Join-Path $InstallDir $ExeName)

if ($AddFirewallRule) {
    $ruleName = "FitnessClub Agent C01 (TCP 8765)"
    if (-not (Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue)) {
        try {
            New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Protocol TCP -LocalPort 8765 -Action Allow | Out-Null
            Write-Step "Firewall: inbound TCP 8765 allowed"
        } catch {
            Write-Step "Firewall: skipped (need admin): $_"
        }
    }
}

Write-Step ""
Write-Step "Done."
Write-Step "  App:    $InstallDir\$ExeName"
Write-Step "  Config: $targetConfig"
Write-Step "  Next: open agent, set CRM and equipment, Save all."

if ($Launch) {
    Start-Process (Join-Path $InstallDir $ExeName)
}

if (-not $Silent) {
    Write-Host ""
    Read-Host "Press Enter to exit"
}
