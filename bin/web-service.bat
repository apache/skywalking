@echo off

setlocal
set WEB_PROCESS_TITLE=Skywalking-Web
set WEB_BASE_PATH=%~dp0%..
set WEB_RUNTIME_OPTIONS="-Xms256M -Xmx512M"

set CLASSPATH=%WEB_BASE_PATH%\config;
SET CLASSPATH=%WEB_BASE_PATH%\libs\*;%CLASSPATH%

if ""%JAVA_HOME%"" == """" (
  set _EXECJAVA=java
) else (
  set _EXECJAVA="%JAVA_HOME%"/bin/java
)

start /MIN "%WEB_PROCESS_TITLE%" %_EXECJAVA% "%WEB_RUNTIME_OPTIONS%" -cp "%CLASSPATH%" org.skywalking.apm.ui.ApplicationStartUp &
echo Skywalking Web started successfully!

endlocal
