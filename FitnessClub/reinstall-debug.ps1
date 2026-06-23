# Удалить битую установку и поставить свежий debug APK на эмулятор.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    Write-Error "adb не найден: $adb`nУкажите ANDROID_HOME или откройте проект в Android Studio."
}

Write-Host "Сборка debug..."
& .\gradlew.bat :app:assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { Write-Error "APK не найден: $apk" }

Write-Host "Удаление старой версии..."
& $adb uninstall ru.worldcashfit.app 2>$null

Write-Host "Установка $apk ..."
& $adb install -r $apk
if ($LASTEXITCODE -eq 0) {
    Write-Host "OK. Запустите приложение на эмуляторе."
} else {
    Write-Error "Установка не удалась"
}
