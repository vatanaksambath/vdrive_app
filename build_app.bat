@echo off
setlocal

echo ================================================================
echo   KMAutoNav - Quick Build APK (Debug)
echo ================================================================

if "%JAVA_HOME%"=="" (
    set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
)
if "%ANDROID_HOME%"=="" (
    set "ANDROID_HOME=C:\Users\samba\AppData\Local\Android\Sdk"
)

set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

call gradlew.bat assembleDebug

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ================================================================
    echo   BUILD SUCCESSFUL!
    echo   APK Location: app\build\outputs\apk\debug\app-debug.apk
    echo ================================================================
) else (
    echo.
    echo [ERROR] Build failed. Please inspect the error output above.
)

if not "%1"=="--no-pause" pause
