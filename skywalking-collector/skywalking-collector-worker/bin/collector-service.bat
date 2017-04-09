@echo off

setlocal
set COLLECOTR_PROCESS_TITLE=Skywalking-Collector
set COLLECTOR_BASE_PATH=%~dp0%..
set COLLECTOR_RUNTIME_OPTIONS="-Xms256M -Xmx512M"

if ""%JAVA_HOME%"" == """" (
  set _EXECJAVA=java
) else (
  set _EXECJAVA="%JAVA_HOME%"/bin/java
)

start /MIN "%COLLECOTR_PROCESS_TITLE%" %_EXECJAVA% "%COLLECTOR_RUNTIME_OPTIONS%" -jar "%COLLECTOR_BASE_PATH%"/libs/skywalking-collector.jar &
echo Collector started successfully!

endlocal
