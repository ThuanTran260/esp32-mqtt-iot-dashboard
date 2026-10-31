@echo off
rem ==============================================================================
rem Integrated IoT System - Mosquitto Broker Launcher
rem Component: broker/start_broker.bat
rem Project: SGU Network Programming Coursework
rem ==============================================================================
setlocal EnableExtensions

rem Set console window title and UTF-8 encoding
title Mosquitto MQTT Broker (TCP: 1883, WS: 9001)
chcp 65001 >nul

rem Step 1: Switch working directory to the directory containing this script
cd /d "%~dp0"
echo [INFO] Working directory set to: %CD%

rem Step 2: Pre-execution configuration and password file validation
if not exist "mosquitto.conf" (
    echo ==============================================================================
    echo [ERROR] Configuration file 'mosquitto.conf' was not found!
    echo Current Directory: %CD%
    echo Expected Location: %~dp0mosquitto.conf
    echo ==============================================================================
    goto :ERROR_EXIT
)

if not exist "password.txt" (
    echo ==============================================================================
    echo [ERROR] Password file 'password.txt' was not found!
    echo Mosquitto requires password.txt for user authentication [allow_anonymous false].
    echo Current Directory: %CD%
    echo Expected Location: %~dp0password.txt
    echo You can generate it using:
    echo   mosquitto_passwd -b -c password.txt user1 123456
    echo ==============================================================================
    goto :ERROR_EXIT
)

rem Step 3: Locate mosquitto.exe binary across system paths
set "MOSQUITTO_CMD="

rem 3.1 Check system PATH
where mosquitto.exe >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    set "MOSQUITTO_CMD=mosquitto"
    goto :BIN_LOCATED
)

rem 3.2 Check 64-bit Program Files
if exist "%ProgramFiles%\mosquitto\mosquitto.exe" (
    set "MOSQUITTO_CMD=%ProgramFiles%\mosquitto\mosquitto.exe"
    goto :BIN_LOCATED
)

rem 3.3 Check 32-bit Program Files (x86)
if defined ProgramFiles(x86) (
    if exist "%ProgramFiles(x86)%\mosquitto\mosquitto.exe" (
        set "MOSQUITTO_CMD=%ProgramFiles(x86)%\mosquitto\mosquitto.exe"
        goto :BIN_LOCATED
    )
)

rem 3.4 Check SystemDrive root (C:\mosquitto)
if exist "%SystemDrive%\mosquitto\mosquitto.exe" (
    set "MOSQUITTO_CMD=%SystemDrive%\mosquitto\mosquitto.exe"
    goto :BIN_LOCATED
)

rem 3.5 Check D:\mosquitto (SGU lab guide default)
if exist "D:\mosquitto\mosquitto.exe" (
    set "MOSQUITTO_CMD=D:\mosquitto\mosquitto.exe"
    goto :BIN_LOCATED
)

rem 3.6 Check E:\mosquitto (Workspace drive)
if exist "E:\mosquitto\mosquitto.exe" (
    set "MOSQUITTO_CMD=E:\mosquitto\mosquitto.exe"
    goto :BIN_LOCATED
)

rem 3.7 Check local directory
if exist "mosquitto.exe" (
    set "MOSQUITTO_CMD=.\mosquitto.exe"
    goto :BIN_LOCATED
)

rem Step 4: Handle binary missing scenario
echo ==============================================================================
echo [ERROR] Eclipse Mosquitto executable (mosquitto.exe) was not found!
echo ==============================================================================
echo The broker launcher searched the following locations:
echo   1. System PATH
echo   2. %ProgramFiles%\mosquitto\mosquitto.exe
echo   3. %ProgramFiles(x86)%\mosquitto\mosquitto.exe
echo   4. %SystemDrive%\mosquitto\mosquitto.exe
echo   5. D:\mosquitto\mosquitto.exe
echo   6. E:\mosquitto\mosquitto.exe
echo   7. %CD%\mosquitto.exe
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
echo   Option C (Coursework Archive):
echo     Extract mosquitto.rar into C:\mosquitto or D:\mosquitto,
echo     or add your installation directory to the Windows system PATH.
echo ==============================================================================
goto :ERROR_EXIT

:BIN_LOCATED
echo ==============================================================================
echo Starting Eclipse Mosquitto MQTT Broker...
echo ==============================================================================
echo Executable:     %MOSQUITTO_CMD%
echo Configuration:  %CD%\mosquitto.conf
echo Password File:  %CD%\password.txt
echo TCP Listener:   Port 1883 (ESP32, CLI tools)
echo WS Listener:    Port 9001 (Web Dashboard)
echo Logging Mode:   Verbose (-v)
echo Credentials:    user1 / 123456 (allow_anonymous false)
echo.
echo Press Ctrl+C in this window to stop the broker.
echo ==============================================================================
echo.

rem Step 5: Execute Mosquitto with configuration and verbose logging
"%MOSQUITTO_CMD%" -c mosquitto.conf -v %*
set "EXIT_CODE=%ERRORLEVEL%"

rem Step 6: Post-execution status handling
if %EXIT_CODE% NEQ 0 (
    echo.
    echo ==============================================================================
    echo [ERROR] Mosquitto stopped with error exit code: %EXIT_CODE%
    echo ==============================================================================
    echo Common causes for startup failure:
    echo   1. Port 1883 or Port 9001 is already in use by another application.
    echo      Check active listeners:
    echo        netstat -ano ^| findstr :1883
    echo        netstat -ano ^| findstr :9001
    echo   2. Mosquitto is already running as a background Windows Service.
    echo      Stop the service using:
    echo        net stop mosquitto
    echo   3. Syntax error in mosquitto.conf or corrupted password.txt.
    echo ==============================================================================
    goto :ERROR_EXIT
)

echo [INFO] Mosquitto broker shut down cleanly.
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
