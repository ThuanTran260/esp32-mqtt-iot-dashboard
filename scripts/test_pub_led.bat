@echo off
rem ==============================================================================
rem Integrated IoT System - CLI LED Actuator Publisher
rem Component: scripts/test_pub_led.bat
rem Note: No wildcard characters permitted in this script
rem ==============================================================================
setlocal EnableExtensions

title MQTT LED Actuator Publisher (Topic: sensor/led)
chcp 65001 >nul

cd /d "%~dp0"

rem Step 1: Locate mosquitto_pub.exe binary across system paths
set "MOSQUITTO_PUB="

rem 1.1 Check system PATH
where mosquitto_pub.exe >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    set "MOSQUITTO_PUB=mosquitto_pub"
    goto :BIN_LOCATED
)

rem 1.2 Check 64-bit Program Files
if exist "%ProgramFiles%\mosquitto\mosquitto_pub.exe" (
    set "MOSQUITTO_PUB=%ProgramFiles%\mosquitto\mosquitto_pub.exe"
    goto :BIN_LOCATED
)

rem 1.3 Check 32-bit Program Files (x86)
if defined ProgramFiles(x86) (
    if exist "%ProgramFiles(x86)%\mosquitto\mosquitto_pub.exe" (
        set "MOSQUITTO_PUB=%ProgramFiles(x86)%\mosquitto\mosquitto_pub.exe"
        goto :BIN_LOCATED
    )
)

rem 1.4 Check Local AppData
if defined LOCALAPPDATA (
    if exist "%LOCALAPPDATA%\mosquitto\mosquitto_pub.exe" (
        set "MOSQUITTO_PUB=%LOCALAPPDATA%\mosquitto\mosquitto_pub.exe"
        goto :BIN_LOCATED
    )
)

rem 1.5 Check SystemDrive root (C:\mosquitto)
if exist "%SystemDrive%\mosquitto\mosquitto_pub.exe" (
    set "MOSQUITTO_PUB=%SystemDrive%\mosquitto\mosquitto_pub.exe"
    goto :BIN_LOCATED
)

rem 1.6 Check D:\mosquitto (SGU lab guide default)
if exist "D:\mosquitto\mosquitto_pub.exe" (
    set "MOSQUITTO_PUB=D:\mosquitto\mosquitto_pub.exe"
    goto :BIN_LOCATED
)

rem 1.7 Check E:\mosquitto (Workspace drive)
if exist "E:\mosquitto\mosquitto_pub.exe" (
    set "MOSQUITTO_PUB=E:\mosquitto\mosquitto_pub.exe"
    goto :BIN_LOCATED
)

rem 1.8 Check relative broker directory
if exist "%~dp0..\broker\mosquitto_pub.exe" (
    set "MOSQUITTO_PUB=%~dp0..\broker\mosquitto_pub.exe"
    goto :BIN_LOCATED
)

rem 1.9 Check local directory
if exist "%~dp0mosquitto_pub.exe" (
    set "MOSQUITTO_PUB=%~dp0mosquitto_pub.exe"
    goto :BIN_LOCATED
)

rem Step 2: Handle binary missing scenario
echo ==============================================================================
echo [ERROR] Mosquitto publisher executable (mosquitto_pub.exe) was not found!
echo ==============================================================================
echo Searched locations:
echo   1. System PATH
echo   2. %ProgramFiles%\mosquitto\mosquitto_pub.exe
echo   3. %ProgramFiles(x86)%\mosquitto\mosquitto_pub.exe
echo   4. %LOCALAPPDATA%\mosquitto\mosquitto_pub.exe
echo   5. %SystemDrive%\mosquitto\mosquitto_pub.exe
echo   6. D:\mosquitto\mosquitto_pub.exe
echo   7. E:\mosquitto\mosquitto_pub.exe
echo   8. %~dp0..\broker\mosquitto_pub.exe
echo   9. %~dp0mosquitto_pub.exe
echo.
echo TO RESOLVE THIS ISSUE:
echo   Run in PowerShell / CMD:
echo     winget install EclipseFoundation.Mosquitto
echo   Or download official installer from: https://mosquitto.org/download/
echo ==============================================================================
exit /b 1

:BIN_LOCATED
rem Step 3: Configure target parameters and payload
set "HOST=localhost"
set "PORT=1883"
set "TOPIC=sensor/led"
set "USER=user1"
set "PASS=123456"
set "CMD_PAYLOAD=ON"

rem Support custom command parameter (%1 or %~1), default to ON/OFF
if not "%~1"=="" (
    set "CMD_PAYLOAD=%~1"
)

rem Normalize common case variations
if /i "%CMD_PAYLOAD%"=="on" set "CMD_PAYLOAD=ON"
if /i "%CMD_PAYLOAD%"=="off" set "CMD_PAYLOAD=OFF"

echo [INFO] Publishing LED actuator command to MQTT broker...
echo Broker:   %HOST%:%PORT%
echo Topic:    %TOPIC%
echo User:     %USER%
echo Command:  %CMD_PAYLOAD%
echo.

rem Step 4: Execute publish command
"%MOSQUITTO_PUB%" -h %HOST% -p 1883 -t "sensor/led" -u user1 -P 123456 -m "%CMD_PAYLOAD%"
set "EXIT_CODE=%ERRORLEVEL%"

rem Step 5: Post-execution status handling
if %EXIT_CODE% NEQ 0 (
    echo.
    echo ==============================================================================
    echo [ERROR] Publishing failed with exit code: %EXIT_CODE%
    echo ==============================================================================
    echo Potential causes:
    echo   1. Mosquitto broker is not running. Launch it with: broker\start_broker.bat
    echo   2. Authentication failure - check user1 / 123456 credentials in password.txt
    echo   3. Port 1883 is inaccessible or blocked by firewall.
    echo ==============================================================================
    exit /b %EXIT_CODE%
)

echo [INFO] LED command published successfully.
endlocal
exit /b 0
