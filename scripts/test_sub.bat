@echo off
rem ==============================================================================
rem Integrated IoT System - MQTT CLI Subscriber
rem Component: scripts/test_sub.bat
rem Project: SGU Network Programming Coursework
rem Subscribes to topic sensor/# (or custom topic) on localhost:1883 with user1/123456
rem ==============================================================================
setlocal EnableExtensions

rem Set console window title and UTF-8 encoding
title MQTT Subscriber - sensor/# (localhost:1883)
chcp 65001 >nul

rem Step 1: Switch working directory to script location
cd /d "%~dp0"

rem Step 2: Display help if requested
if "%~1"=="/?" goto :SHOW_HELP
if "%~1"=="-h" if "%~2"=="" goto :SHOW_HELP
if "%~1"=="--help" goto :SHOW_HELP
if "%~1"=="help" goto :SHOW_HELP

rem Step 3: Locate mosquitto_sub.exe across system and local paths
set "MOSQUITTO_SUB_CMD="

rem 3.1 Check system PATH
where mosquitto_sub.exe >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    set "MOSQUITTO_SUB_CMD=mosquitto_sub"
    goto :BIN_LOCATED
)

rem 3.2 Check 64-bit Program Files
if exist "%ProgramFiles%\mosquitto\mosquitto_sub.exe" (
    set "MOSQUITTO_SUB_CMD=%ProgramFiles%\mosquitto\mosquitto_sub.exe"
    goto :BIN_LOCATED
)

rem 3.3 Check 32-bit Program Files (x86)
if defined ProgramFiles(x86) (
    if exist "%ProgramFiles(x86)%\mosquitto\mosquitto_sub.exe" (
        set "MOSQUITTO_SUB_CMD=%ProgramFiles(x86)%\mosquitto\mosquitto_sub.exe"
        goto :BIN_LOCATED
    )
)

rem 3.4 Check Local AppData
if exist "%LOCALAPPDATA%\mosquitto\mosquitto_sub.exe" (
    set "MOSQUITTO_SUB_CMD=%LOCALAPPDATA%\mosquitto\mosquitto_sub.exe"
    goto :BIN_LOCATED
)

rem 3.5 Check User Profile
if exist "%USERPROFILE%\mosquitto\mosquitto_sub.exe" (
    set "MOSQUITTO_SUB_CMD=%USERPROFILE%\mosquitto\mosquitto_sub.exe"
    goto :BIN_LOCATED
)

rem 3.6 Check SystemDrive root (C:\mosquitto)
if exist "%SystemDrive%\mosquitto\mosquitto_sub.exe" (
    set "MOSQUITTO_SUB_CMD=%SystemDrive%\mosquitto\mosquitto_sub.exe"
    goto :BIN_LOCATED
)

rem 3.7 Check D:\mosquitto (SGU lab guide default)
if exist "D:\mosquitto\mosquitto_sub.exe" (
    set "MOSQUITTO_SUB_CMD=D:\mosquitto\mosquitto_sub.exe"
    goto :BIN_LOCATED
)

rem 3.8 Check E:\mosquitto (Workspace drive)
if exist "E:\mosquitto\mosquitto_sub.exe" (
    set "MOSQUITTO_SUB_CMD=E:\mosquitto\mosquitto_sub.exe"
    goto :BIN_LOCATED
)

rem 3.9 Check broker folder relative to scripts directory
if exist "%~dp0..\broker\mosquitto_sub.exe" (
    set "MOSQUITTO_SUB_CMD=%~dp0..\broker\mosquitto_sub.exe"
    goto :BIN_LOCATED
)

rem 3.10 Check local script directory
if exist "%~dp0mosquitto_sub.exe" (
    set "MOSQUITTO_SUB_CMD=%~dp0mosquitto_sub.exe"
    goto :BIN_LOCATED
)

rem Step 4: Handle executable missing
echo ==============================================================================
echo [ERROR] Mosquitto subscriber CLI (mosquitto_sub.exe) was not found!
echo ==============================================================================
echo The subscriber script searched the following locations:
echo   1. System PATH
echo   2. %ProgramFiles%\mosquitto\mosquitto_sub.exe
echo   3. %ProgramFiles(x86)%\mosquitto\mosquitto_sub.exe
echo   4. %LOCALAPPDATA%\mosquitto\mosquitto_sub.exe
echo   5. %USERPROFILE%\mosquitto\mosquitto_sub.exe
echo   6. %SystemDrive%\mosquitto\mosquitto_sub.exe
echo   7. D:\mosquitto\mosquitto_sub.exe
echo   8. E:\mosquitto\mosquitto_sub.exe
echo   9. %~dp0..\broker\mosquitto_sub.exe
echo  10. %~dp0mosquitto_sub.exe
echo.
echo TO RESOLVE THIS ISSUE:
echo   Option A (Recommended via Windows Package Manager):
echo     Run in PowerShell / CMD:
echo       winget install EclipseFoundation.Mosquitto
echo.
echo   Option B (Manual Download):
echo     Download and install the official Windows binary from:
echo       https://mosquitto.org/download/
echo.
echo   Option C (Add to PATH):
echo     If Mosquitto is installed in another directory, add that directory
echo     to your Windows PATH environment variable.
echo ==============================================================================
goto :ERROR_EXIT

:BIN_LOCATED
echo ==============================================================================
echo Eclipse Mosquitto MQTT Subscriber
echo ==============================================================================
echo Binary:        %MOSQUITTO_SUB_CMD%
echo Host:          localhost
echo Port:          1883
echo Username:      user1
echo Password:      123456
echo Verbose:       Enabled (-v)
echo.

rem Step 5: Execute subscriber with default or custom parameters
if "%~1"=="" goto :SUB_DEFAULT

set "PARAM1=%~1"
if "%PARAM1:~0,1%"=="-" goto :SUB_FLAGS

rem Custom topic specified as first argument
set "CUSTOM_TOPIC=%~1"
shift
echo Topic:         %CUSTOM_TOPIC%
echo Extra Flags:   %1 %2 %3 %4 %5 %6 %7 %8 %9
echo Press Ctrl+C in this window to stop subscribing.
echo ==============================================================================
echo.
"%MOSQUITTO_SUB_CMD%" -h localhost -p 1883 -u user1 -P 123456 -t "%CUSTOM_TOPIC%" -v %1 %2 %3 %4 %5 %6 %7 %8 %9
goto :POST_EXEC

:SUB_FLAGS
rem Flags passed directly, keeping default topic sensor/#
echo Topic:         sensor/#
echo Extra Flags:   %*
echo Press Ctrl+C in this window to stop subscribing.
echo ==============================================================================
echo.
"%MOSQUITTO_SUB_CMD%" -h localhost -p 1883 -u user1 -P 123456 -t "sensor/#" -v %*
goto :POST_EXEC

:SUB_DEFAULT
rem Default execution: subscribe to sensor/#
echo Topic:         sensor/# (All telemetry, LED, and status messages)
echo Press Ctrl+C in this window to stop subscribing.
echo ==============================================================================
echo.
"%MOSQUITTO_SUB_CMD%" -h localhost -p 1883 -u user1 -P 123456 -t "sensor/#" -v
goto :POST_EXEC

:POST_EXEC
set "SUB_EXIT_CODE=%ERRORLEVEL%"

rem Handle Ctrl+C exit cleanly
if %SUB_EXIT_CODE% EQU -1073741510 goto :CLEAN_EXIT
if %SUB_EXIT_CODE% EQU 3221225786 goto :CLEAN_EXIT
if %SUB_EXIT_CODE% EQU 0 goto :CLEAN_EXIT

echo.
echo ==============================================================================
echo [ERROR] mosquitto_sub exited with code: %SUB_EXIT_CODE%
echo ==============================================================================
echo Common causes for subscriber failure:
echo   1. Mosquitto broker is not running.
echo      Start the broker with:
echo        broker\start_broker.bat
echo   2. Connection refused on localhost:1883.
echo      Check if port 1883 is active:
echo        netstat -ano ^| findstr :1883
echo   3. Authentication failed.
echo      Ensure user1 / 123456 is configured in broker\password.txt.
echo ==============================================================================
goto :ERROR_EXIT

:CLEAN_EXIT
echo.
echo [INFO] Subscriber stopped cleanly.
endlocal
exit /b 0

:ERROR_EXIT
echo.
echo Execution halted due to errors.
if not defined CI (
    pause
)
endlocal
exit /b 1

:SHOW_HELP
echo ==============================================================================
echo Mosquitto MQTT Subscriber Script (scripts\test_sub.bat)
echo ==============================================================================
echo Usage:
echo   test_sub.bat                        Subscribes to default topic 'sensor/#'
echo   test_sub.bat [topic]                Subscribes to specified custom topic
echo   test_sub.bat [topic] [extra_flags]  Subscribes to topic with extra mosquitto_sub flags
echo   test_sub.bat [extra_flags]          Subscribes to 'sensor/#' with extra flags
echo.
echo Examples:
echo   test_sub.bat
echo   test_sub.bat sensor/data
echo   test_sub.bat sensor/led
echo   test_sub.bat sensor/status
echo   test_sub.bat sensor/# -C 5
echo ==============================================================================
endlocal
exit /b 0
