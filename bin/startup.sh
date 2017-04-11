#!/usr/bin/env bash

PRG="$0"
PRGDIR=`dirname "$PRG"`
EXECUTABLE=web-service.sh

exec "$PRGDIR"/"$EXECUTABLE" start
