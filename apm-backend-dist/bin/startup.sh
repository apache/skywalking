#!/usr/bin/env sh

PRG="$0"
PRGDIR=`dirname "$PRG"`
COLLECTOR_EXE=collectorService.sh
WEBAPP_EXE=webappService.sh

"$PRGDIR"/"$COLLECTOR_EXE"

"$PRGDIR"/"$WEBAPP_EXE"
