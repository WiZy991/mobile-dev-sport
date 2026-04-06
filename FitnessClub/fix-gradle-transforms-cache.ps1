# Reset broken transforms-4 cache (metadata.bin / FileNotFoundException).
# Run:  powershell -ExecutionPolicy Bypass -File .\fix-gradle-transforms-cache.ps1
# Close Android Studio / Cursor first so files are not locked.

$ErrorActionPreference = 'Continue'
Set-Location -LiteralPath $PSScriptRoot

Write-Host 'Stopping Gradle daemons...'
& (Join-Path $PSScriptRoot 'gradlew.bat') --stop 2>$null
Start-Sleep -Seconds 3

$transforms = Join-Path $env:USERPROFILE '.gradle\caches\transforms-4'
if (-not (Test-Path -LiteralPath $transforms)) {
    Write-Host "No transforms-4 folder at: $transforms - nothing to delete."
    exit 0
}

Write-Host "Removing: $transforms"
try {
    Remove-Item -LiteralPath $transforms -Recurse -Force -ErrorAction Stop
    Write-Host 'Done. Run gradlew.bat or open the project again.'
    exit 0
}
catch {
    Write-Host 'Could not delete (files locked). Options:'
    Write-Host '  A) Close all IDEs and Java apps, run this script again, or reboot once.'
    Write-Host '  B) Use a fresh Gradle home for this project only:'
    Write-Host '       .\gradlew-local.cmd help'
    Write-Host '     In Android Studio: Settings - Gradle - Gradle user home:'
    $localHome = Join-Path $PSScriptRoot '.gradle-user-home'
    Write-Host "     $localHome"
    exit 1
}
