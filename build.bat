@echo off
setlocal
cd /d "%~dp0"
set "GRADLE_VERSION=9.2.1"
set "CACHE_DIR=%USERPROFILE%\.gradle\wheat-sweep"
set "GRADLE_HOME=%CACHE_DIR%\gradle-%GRADLE_VERSION%"
set "ARCHIVE=%CACHE_DIR%\gradle-%GRADLE_VERSION%-bin.zip"

where java >nul 2>nul
if errorlevel 1 (
  echo Java не найдена. Установи JDK 21 и повтори build.bat.
  exit /b 1
)

if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
  echo Скачиваю Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ARCHIVE%'; Expand-Archive -Path '%ARCHIVE%' -DestinationPath '%CACHE_DIR%' -Force; Remove-Item '%ARCHIVE%'"
  if errorlevel 1 (
    echo Не удалось скачать или распаковать Gradle.
    exit /b 1
  )
)

call "%GRADLE_HOME%\bin\gradle.bat" build %*
exit /b %ERRORLEVEL%
