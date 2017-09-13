@echo off

setlocal
set COLLECOTR_PROCESS_TITLE=Skywalking-Collector
set COLLECTOR_BASE_PATH=%~dp0%..
set COLLECTOR_RUNTIME_OPTIONS="-Xms256M -Xmx512M -Dcollector.logDir=%COLLECTOR_BASE_PATH%\logs"

set CLASSPATH=%COLLECTOR_BASE_PATH%\config;
SET CLASSPATH=%COLLECTOR_BASE_PATH%\libs\*;%CLASSPATH%

if defined JAVA_HOME (
 set _EXECJAVA="%JAVA_HOME:"=%"\bin\java
)

if not defined JAVA_HOME (
  echo "JAVA_HOME not set."
  set _EXECJAVA=java
)

start /MIN "%COLLECOTR_PROCESS_TITLE%" %_EXECJAVA% "%COLLECTOR_RUNTIME_OPTIONS%" -cp "%CLASSPATH%" org.skywalking.apm.collector.boot.CollectorBootStartUp &
echo Collector started successfully!

endlocal
