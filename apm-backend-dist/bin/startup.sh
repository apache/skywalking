#!/usr/bin/env sh

PRG="$0"
PRGDIR=`dirname "$PRG"`
COLLECTOR_EXE=collectorService.sh
WEBAPP_EXE=webappService.sh

exec "$PRGDIR"/"$COLLECTOR_EXE" start

exec "$PRGDIR"/"$COLLECTOR_EXE" start
