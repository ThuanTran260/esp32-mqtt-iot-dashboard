@echo off
cd /d "%~dp0.."
if not exist "config\client.properties" (
    echo Missing config\client.properties. Copy config\client.example.properties to config\client.properties and set the broker IPv4 address.
    exit /b 1
)
if not exist "java-desktop\target\java-desktop-1.0-SNAPSHOT.jar" (
    echo Missing java-desktop\target\java-desktop-1.0-SNAPSHOT.jar. Build it first: mvn clean verify
    exit /b 1
)
java -jar "java-desktop\target\java-desktop-1.0-SNAPSHOT.jar" subscribe --config "config\client.properties" --topic "sensor/#" --seconds 3600 %*
exit /b %ERRORLEVEL%
