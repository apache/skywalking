@echo off

setlocal
call "%~dp0"\collectorService.bat start
call "%~dp0"\webappService.bat start
endlocal
