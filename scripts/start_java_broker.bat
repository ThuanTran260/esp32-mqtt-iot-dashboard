@echo off
cd /d "%~dp0.."
if not exist "config\broker.properties" (
    copy "config\broker.example.properties" "config\broker.properties" >nul
    echo Created config\broker.properties from the example. Edit it to change port or password.
)
if not exist "java-broker\target\java-broker-1.0-SNAPSHOT.jar" (
    echo Missing java-broker\target\java-broker-1.0-SNAPSHOT.jar. Build it first: mvn clean verify
    exit /b 1
)
java -jar "java-broker\target\java-broker-1.0-SNAPSHOT.jar" --config "config\broker.properties"
exit /b %ERRORLEVEL%
