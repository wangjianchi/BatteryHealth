@rem Gradle startup script for Windows
@if "%DEBUG%"=="" @echo off
@rem Set local scope for variables
setlocal
set APP_HOME=%~dp0
set APP_HOME=%APP_HOME:~0,-1%

java -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*

:end
endlocal
