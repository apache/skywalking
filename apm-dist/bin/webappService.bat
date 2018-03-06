@echo off

setlocal
set WEBAPP_PROCESS_TITLE=Skywalking-Webapp
set WEBAPP_HOME=%~dp0%..
set JARPATH=%WEBAPP_HOME%\webapp

if defined JAVA_HOME (
 set _EXECJAVA="%JAVA_HOME:"=%"\bin\java
)

if not defined JAVA_HOME (
 echo "JAVA_HOME not set."
 set _EXECJAVA=java
)

start "%WEBAPP_PROCESS_TITLE%" %_EXECJAVA%  -jar %JARPATH%/skywalking-webapp.jar --server.port=8080 --collector.ribbon.listOfServers=127.0.0.1:10800
endlocal
