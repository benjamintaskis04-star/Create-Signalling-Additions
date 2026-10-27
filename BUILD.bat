@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "GRADLE_VERSION=8.10"
set "TOOLS_DIR=%~dp0.gradle-bootstrap"
set "GRADLE_HOME=%TOOLS_DIR%\gradle-%GRADLE_VERSION%"
set "GRADLE_ZIP=%TOOLS_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
set "GRADLE_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"
set "ORIGINAL_DIR=%CD%"

echo ===============================================
echo  Create: Signalling Additions 1.5.0 Alpha 1 local development build
echo  Internal mod version: 1.5.0-alpha.1
echo  Minecraft 1.21.1 / NeoForge 21.1.243
echo  Create 6.0.10 / Gradle %GRADLE_VERSION%
echo ===============================================
echo.

java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java could not be found.
    echo This project requires JDK 21.
    echo.
    pause
    exit /b 1
)

set "RESOURCE_BUNDLE=%~dp0build-support\static-resources.zip"
if not exist "%RESOURCE_BUNDLE%" (
    echo ERROR: Missing build-support\static-resources.zip
    echo Re-extract the complete source package and try again.
    echo.
    pause
    exit /b 1
)
echo Using prepacked static resource bundle.
echo.

if not exist "%GRADLE_HOME%\bin\gradle.bat" (
    echo Gradle %GRADLE_VERSION% is not installed for this project yet.
    echo Downloading it now - this only needs to happen once.
    echo.
    if not exist "%TOOLS_DIR%" mkdir "%TOOLS_DIR%"
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri '%GRADLE_URL%' -OutFile '%GRADLE_ZIP%'"
    if errorlevel 1 (
        echo.
        echo ERROR: Could not download Gradle from:
        echo %GRADLE_URL%
        echo Check your internet connection and try again.
        echo.
        pause
        exit /b 1
    )
    echo Extracting Gradle...
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "Expand-Archive -Path '%GRADLE_ZIP%' -DestinationPath '%TOOLS_DIR%' -Force"
    if errorlevel 1 (
        echo.
        echo ERROR: Gradle downloaded but could not be extracted.
        echo.
        pause
        exit /b 1
    )
    del /q "%GRADLE_ZIP%" >nul 2>&1
)

echo Preparing a local build workspace from:
echo   %ORIGINAL_DIR%
echo.
echo Gradle output is LIVE below.
echo ===============================================
echo.

rem Build from a short local temporary path. Java/Gradle cannot reliably read
rem OneDrive placeholder files directly, even when PowerShell can read them.
set "CSA_BUILD_DIR=%TEMP%\CSA-Alpha1-%RANDOM%-%RANDOM%"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop'; New-Item -ItemType Directory -Path $env:CSA_BUILD_DIR -Force | Out-Null; Copy-Item -LiteralPath 'src','build-support','build.gradle','settings.gradle','gradle.properties' -Destination $env:CSA_BUILD_DIR -Recurse -Force"
if errorlevel 1 exit /b 1
call "%GRADLE_HOME%\bin\gradle.bat" -p "%CSA_BUILD_DIR%" clean build --console=plain --no-build-cache --no-watch-fs --info
set "BUILD_RESULT=%ERRORLEVEL%"
if "%BUILD_RESULT%"=="0" (
    if not exist "%ORIGINAL_DIR%\build\libs" mkdir "%ORIGINAL_DIR%\build\libs"
    copy /y "%CSA_BUILD_DIR%\build\libs\*.jar" "%ORIGINAL_DIR%\build\libs\" >nul
    if errorlevel 1 set "BUILD_RESULT=1"
)
echo Build workspace retained at: %CSA_BUILD_DIR%

echo.
if "%BUILD_RESULT%"=="0" (
    echo ===============================================
    echo BUILD SUCCEEDED
    echo Look in: build\libs\
    echo ===============================================
) else (
    echo ===============================================
    echo BUILD FAILED OR WAS CANCELLED
    echo Copy the visible Gradle error/output and send it to me.
    echo ===============================================
)

echo.
pause
exit /b %BUILD_RESULT%
