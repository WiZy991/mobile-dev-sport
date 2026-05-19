# Сборка FitnessClubAgent.exe (Windows)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# py -3 — надёжнее, чем python (часто это stub из Microsoft Store без реального интерпретатора)
$pipInstall = @("-m", "pip", "install", "-q", "-r", "requirements.txt", "pyinstaller")
$pyInstaller = @("-m", "PyInstaller", "--noconfirm", "club_agent.spec")

if (Get-Command py -ErrorAction SilentlyContinue) {
    & py -3 @pipInstall
    if (Test-Path dist) { Remove-Item -Recurse -Force dist }
    if (Test-Path build) { Remove-Item -Recurse -Force build }
    & py -3 @pyInstaller
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    & python @pipInstall
    if (Test-Path dist) { Remove-Item -Recurse -Force dist }
    if (Test-Path build) { Remove-Item -Recurse -Force build }
    & python @pyInstaller
} else {
    throw "Python не найден. Установите с python.org и добавьте в PATH, либо установите Python Launcher (py)."
}

Write-Host ""
Write-Host "Done: $PSScriptRoot\dist\FitnessClubAgent.exe" -ForegroundColor Green
