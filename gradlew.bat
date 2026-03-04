@rem Self-bootstrapping Gradle wrapper for Windows
@rem Downloads Gradle 8.6 automatically on first run
@setlocal
@rem Check if wrapper jar exists
set WRAPPER_JAR=%~dp0gradle\wrapper\gradle-wrapper.jar
if exist "%WRAPPER_JAR%" (
    java -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
    exit /b %ERRORLEVEL%
)

@rem Bootstrap: download and extract Gradle
set GRADLE_VERSION=8.6
set GRADLE_DIST=gradle-%GRADLE_VERSION%-bin
set GRADLE_URL=https://services.gradle.org/distributions/%GRADLE_DIST%.zip
set GRADLE_HOME=%USERPROFILE%\.gradle\wrapper\dists\%GRADLE_DIST%
set GRADLE_BIN=%GRADLE_HOME%\%GRADLE_DIST%\bin\gradle.bat

if not exist "%GRADLE_BIN%" (
    echo Downloading Gradle %GRADLE_VERSION%...
    if not exist "%GRADLE_HOME%" mkdir "%GRADLE_HOME%"
    powershell -Command "Invoke-WebRequest -Uri '%GRADLE_URL%' -OutFile '%GRADLE_HOME%\%GRADLE_DIST%.zip'"
    powershell -Command "Expand-Archive -Path '%GRADLE_HOME%\%GRADLE_DIST%.zip' -DestinationPath '%GRADLE_HOME%'"
    del "%GRADLE_HOME%\%GRADLE_DIST%.zip"
    echo Gradle %GRADLE_VERSION% ready.
)

call "%GRADLE_BIN%" %*
