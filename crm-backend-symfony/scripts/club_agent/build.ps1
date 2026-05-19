# Сборка FitnessClubAgent.exe (Windows)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

python -m pip install -q -r requirements.txt pyinstaller

if (Test-Path dist) { Remove-Item -Recurse -Force dist }
if (Test-Path build) { Remove-Item -Recurse -Force build }

python -m PyInstaller --noconfirm club_agent.spec

Write-Host ""
Write-Host "Done: $PSScriptRoot\dist\FitnessClubAgent.exe" -ForegroundColor Green
