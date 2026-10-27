@echo off
setlocal enabledelayedexpansion

echo ================================================================
echo   KMAutoNav - Geely Galaxy M7 Simulator Launcher
echo ================================================================

:: Configure Environment Variables
if "%JAVA_HOME%"=="" (
    set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
)
if "%ANDROID_HOME%"=="" (
    set "ANDROID_HOME=C:\Users\samba\AppData\Local\Android\Sdk"
)

set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\emulator;%ANDROID_HOME%\platform-tools;%PATH%"

echo [1/4] Checking Android Virtual Device (AVD)...
set "AVD_NAME=KMAutoNav_Simulator"

echo [2/4] Starting Android Simulator (!AVD_NAME!)...
start "" "%ANDROID_HOME%\emulator\emulator.exe" -avd !AVD_NAME! -gpu host -no-snapshot-load

echo [3/4] Waiting for emulator to boot...
adb connect 127.0.0.1:5555 >nul 2>&1
adb wait-for-device
echo Waiting for system boot completion...
:wait_boot
for /f "tokens=*" %%i in ('adb shell getprop sys.boot_completed 2^>nul') do set BOOT=%%i
if not "%BOOT%"=="1" (
    timeout /t 3 /nobreak >nul
    goto wait_boot
)
echo Emulator is ready!

echo [4/4] Building, installing, and launching KMAutoNav...
call gradlew.bat installDebug

echo Granting automotive permissions...
adb shell pm grant com.khmerauto.nav.debug android.permission.ACCESS_FINE_LOCATION >nul 2>&1
adb shell pm grant com.khmerauto.nav.debug android.permission.ACCESS_COARSE_LOCATION >nul 2>&1
adb shell pm grant com.khmerauto.nav.debug android.permission.RECORD_AUDIO >nul 2>&1

echo Launching KMAutoNav Cockpit on Simulator...
adb shell am start -n com.khmerauto.nav.debug/com.khmerauto.nav.MainActivity

echo ================================================================
echo   KMAutoNav is now running on the Android Simulator!
echo ================================================================
if not "%1"=="--no-pause" pause
