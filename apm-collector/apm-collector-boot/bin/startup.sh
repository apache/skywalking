#!/usr/bin/env sh

PRG="$0"
PRGDIR=`dirname "$PRG"`
EXECUTABLE=collectorService.sh

exec "$PRGDIR"/"$EXECUTABLE" start
