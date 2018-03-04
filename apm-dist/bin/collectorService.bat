@echo off

setlocal
set COLLECTOR_PROCESS_TITLE=Skywalking-Collector
set COLLECTOR_HOME=%~dp0%..
set COLLECTOR_OPTS="-Xms256M -Xmx512M -Dcollector.logDir=%COLLECTOR_HOME%\logs"

set CLASSPATH=%COLLECTOR_HOME%\config;.;
set CLASSPATH=%COLLECTOR_HOME%\collector-libs\*;%CLASSPATH%

if defined JAVA_HOME (
 set _EXECJAVA="%JAVA_HOME:"=%"\bin\java
)

if not defined JAVA_HOME (
 echo "JAVA_HOME not set."
 set _EXECJAVA=java
)

start "%COLLECTOR_PROCESS_TITLE%" %_EXECJAVA% "%COLLECTOR_OPTS%" -cp "%CLASSPATH%" org.apache.skywalking.apm.collector.boot.CollectorBootStartUp
endlocal
