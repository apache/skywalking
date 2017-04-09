#!/usr/bin/env bash

PRG="$0"
PRGDIR=`dirname "$PRG"`
EXECUTABLE=collector-service.sh

exec "$PRGDIR"/"$EXECUTABLE" start
