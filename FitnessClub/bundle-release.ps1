# Сборка release AAB для Google Play (ru.worldcashfit.app)
# Версия: versionCode / versionName из app/build.gradle.kts

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$javaHome = "C:\Users\anton\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma"
if (Test-Path "$javaHome\bin\java.exe") {
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$javaHome\bin;" + $env:PATH
}

Write-Host "Сборка bundleRelease..." -ForegroundColor Cyan
.\gradlew-local.cmd bundleRelease
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$gradleKts = Get-Content "app\build.gradle.kts" -Raw
$versionCode = if ($gradleKts -match 'versionCode\s*=\s*(\d+)') { $Matches[1] } else { "?" }
$versionName = if ($gradleKts -match 'versionName\s*=\s*"([^"]+)"') { $Matches[1] } else { "?" }

$src = "app\build\outputs\bundle\release\app-release.aab"
$destDir = "release"
New-Item -ItemType Directory -Force -Path $destDir | Out-Null

$signed = Test-Path "keystore.properties"
$suffix = if ($signed) { "signed" } else { "unsigned" }
$dest = Join-Path $destDir "worldcashfit-$versionName-$versionCode-$suffix.aab"
Copy-Item $src $dest -Force

Write-Host ""
Write-Host "Готово: $dest" -ForegroundColor Green
Write-Host "versionCode=$versionCode  versionName=$versionName" -ForegroundColor Gray

if (-not $signed) {
    Write-Host ""
    Write-Host "AAB собран БЕЗ релизной подписи — в Play Console загружать нельзя." -ForegroundColor Yellow
    Write-Host "Подпись:" -ForegroundColor Yellow
    Write-Host "  1) Android Studio -> Build -> Generate Signed App Bundle / APK"
    Write-Host "  2) Или скопируйте keystore.properties.example -> keystore.properties,"
    Write-Host "     укажите upload-keystore от первой публикации и запустите скрипт снова."
}
