@echo off
setlocal

echo ==========================================
echo      StarryForge Plugin Build Script
echo ==========================================
echo.

:: Ensure we are in the script's directory (Project Root)
cd /d "%~dp0"

:: 1. Check for Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not in PATH!
    echo Please install JDK 21 and make sure 'java' command works.
    pause
    exit /b
)

:: 2. Check for Maven in PATH or Specific Location
set "MAVEN_HOME_CUSTOM=C:\apache-maven-3.9.11-bin\apache-maven-3.9.11"

if exist "%MAVEN_HOME_CUSTOM%\bin\mvn.cmd" (
    echo [INFO] Found Maven at custom path: %MAVEN_HOME_CUSTOM%
    call "%MAVEN_HOME_CUSTOM%\bin\mvn.cmd" clean package
    goto :check_build
)

call mvn -v >nul 2>&1
if %errorlevel% equ 0 (
    echo [INFO] Found Maven in PATH.
    call mvn clean package
    goto :check_build
)

:: 3. If not in PATH, ask user
echo [WARN] Maven 'mvn' command not found in PATH or custom location.
echo.
echo Please unzip your 'apache-maven-3.9.11-bin.zip' first.
echo Then, find the folder 'apache-maven-3.9.11' (it should contain a 'bin' folder).
echo.
set /p MAVEN_DIR="> Drag and drop the unzipped 'apache-maven-3.9.11' folder here: "

:: Remove quotes if present
set MAVEN_DIR=%MAVEN_DIR:"=%

if exist "%MAVEN_DIR%\bin\mvn.cmd" (
    echo [INFO] Found Maven at: %MAVEN_DIR%
    call "%MAVEN_DIR%\bin\mvn.cmd" clean package
) else (
    echo.
    echo [ERROR] Could not find '\bin\mvn.cmd' in that folder.
    echo Please make sure you selected the correct directory.
    goto :end
)

:check_build
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Maven build failed.
    goto :end
)

echo.
echo Build process finished.

if exist "target\StarryForge-1.0-SNAPSHOT.jar" (
    echo [SUCCESS] Jar found at target\StarryForge-1.0-SNAPSHOT.jar
    
    :: Check if plugins folder exists
    if exist "..\plugins\" (
        echo Copying to plugins folder...
        copy /Y "target\StarryForge-1.0-SNAPSHOT.jar" "..\plugins\StarryForge-1.0-SNAPSHOT.jar"
        if %errorlevel% equ 0 (
            echo [SUCCESS] Deployed to server plugins folder.
        ) else (
            echo [ERROR] Failed to copy jar file.
        )
    ) else (
        echo [WARN] Plugins folder not found at ..\plugins. Skipping deployment.
    )
) else (
    echo [ERROR] Target jar file not found. Build might have failed silently.
)

:end
:: pause
