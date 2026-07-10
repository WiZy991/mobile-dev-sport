# Uninstall FitnessClub Agent
param([switch]$Silent)

$ErrorActionPreference = "Stop"
$InstallDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Get-Process -Name "FitnessClubAgent" -ErrorAction SilentlyContinue | Stop-Process -Force

$paths = @(
    (Join-Path ([Environment]::GetFolderPath("Desktop")) "FitnessClub Agent.lnk"),
    (Join-Path ([Environment]::GetFolderPath("Programs")) "FitnessClub Agent")
)
foreach ($p in $paths) {
    if (Test-Path $p) { Remove-Item -Recurse -Force $p }
}

Remove-Item -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\FitnessClubAgent" -Recurse -Force -ErrorAction SilentlyContinue

if ($Silent) {
    $answer = "y"
} else {
    $answer = Read-Host "Remove program folder ($InstallDir)? [y/N]"
}
if ($answer -match '^[yY]') {
    if ($InstallDir -and (Test-Path $InstallDir)) {
        Remove-Item -Recurse -Force $InstallDir
    }
}

if (-not $Silent) {
    Write-Host "Uninstalled."
    Read-Host "Press Enter"
}
