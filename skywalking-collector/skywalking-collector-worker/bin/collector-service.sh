#!/usr/bin/env bash

PRG="$0"
PRGDIR=`dirname "$PRG"`
[ -z "$COLLECTOR_HOME" ] && COLLECTOR_HOME=`cd "$PRGDIR/.." >/dev/null; pwd`

COLLECTOR_LOGS_DIR="${COLLECTOR_HOME}/logs"
COLLECTOR_RUNTIME_OPTIONS=" -Xms256M -Xmx512M"

if [ ! -d "${COLLECTOR_HOME}/logs" ]; then
    mkdir -p "${COLLECTOR_LOGS_DIR}"
fi

_RUNJAVA=${JAVA_HOME}/bin/java
[ -z "$JAVA_HOME" ] && _RUNJAVA=`java`

echo "Starting collector...."
eval exec "\"$_RUNJAVA\" ${COLLECTOR_RUNTIME_OPTIONS} -jar ${COLLECTOR_HOME}/libs/skywalking-collector.jar \
        2>${COLLECTOR_LOGS_DIR}/collector.log 1> /dev/null &"

if [ $? -eq 0 ]; then
    sleep 1
	echo "Collector started successfully!"
else
	echo "Collector started failure!"
	exit 1
fi
