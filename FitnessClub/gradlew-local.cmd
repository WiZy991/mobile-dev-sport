@echo off
REM Обход битого %USERPROFILE%\.gradle\caches\transforms-4: отдельный GRADLE_USER_HOME только для этого проекта.
set "GRADLE_USER_HOME=%~dp0.gradle-user-home"
if not exist "%GRADLE_USER_HOME%" mkdir "%GRADLE_USER_HOME%" 2>nul
call "%~dp0gradlew.bat" %*
