@echo off

setlocal
set WEB_PROCESS_TITLE=Skywalking-Web
set WEB_BASE_PATH=%~dp0%..
set WEB_RUNTIME_OPTIONS="-Xms256M -Xmx512M -Dwebui.logDir=%WEB_BASE_PATH%\logs"

set CLASSPATH=%WEB_BASE_PATH%\config;
SET CLASSPATH=%WEB_BASE_PATH%\libs\*;%CLASSPATH%

if defined JAVA_HOME (
 set _EXECJAVA="%JAVA_HOME:"=%"\bin\java
)

if not defined JAVA_HOME (
 echo "JAVA_HOME not set."
 set _EXECJAVA=java
)

start /MIN "%WEB_PROCESS_TITLE%" %_EXECJAVA% "%WEB_RUNTIME_OPTIONS%" -cp "%CLASSPATH%" org.skywalking.apm.ui.ApplicationStartUp &
echo Skywalking Web started successfully!

endlocal
