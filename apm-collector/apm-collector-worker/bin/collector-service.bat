@echo off

setlocal
set COLLECOTR_PROCESS_TITLE=Skywalking-Collector
set COLLECTOR_BASE_PATH=%~dp0%..
set COLLECTOR_RUNTIME_OPTIONS="-Xms256M -Xmx512M"

set CLASSPATH=%COLLECTOR_BASE_PATH%\config;
SET CLASSPATH=%COLLECTOR_BASE_PATH%\libs\*;%CLASSPATH%

if ""%JAVA_HOME%"" == """" (
  set _EXECJAVA=java
) else (
  set _EXECJAVA="%JAVA_HOME%"/bin/java
)

start /MIN "%COLLECOTR_PROCESS_TITLE%" %_EXECJAVA% "%COLLECTOR_RUNTIME_OPTIONS%" -cp "%CLASSPATH%" org.skywalking.apm.collector.worker.CollectorBootStartUp &
echo Collector started successfully!

endlocal
