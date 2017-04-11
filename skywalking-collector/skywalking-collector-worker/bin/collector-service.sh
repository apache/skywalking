#!/usr/bin/env bash

PRG="$0"
PRGDIR=`dirname "$PRG"`
[ -z "$COLLECTOR_HOME" ] && COLLECTOR_HOME=`cd "$PRGDIR/.." >/dev/null; pwd`

COLLECTOR_LOGS_DIR="${COLLECTOR_HOME}/logs"
JAVA_OPTS=" -Xms256M -Xmx512M"

if [ ! -d "${COLLECTOR_HOME}/logs" ]; then
    mkdir -p "${COLLECTOR_LOGS_DIR}"
fi

_RUNJAVA=${JAVA_HOME}/bin/java
[ -z "$JAVA_HOME" ] && _RUNJAVA=`java`

CLASSPATH="$COLLECTOR_HOME/config:$CLASSPATH"
for i in "$COLLECTOR_HOME"/libs/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done

echo "Starting collector...."
eval exec "\"$_RUNJAVA\" ${JAVA_OPTS} -classpath $CLASSPATH com.a.eye.skywalking.collector.worker.CollectorBootStartUp \
        2>${COLLECTOR_LOGS_DIR}/collector.log 1> /dev/null &"

if [ $? -eq 0 ]; then
    sleep 1
	echo "Collector started successfully!"
else
	echo "Collector started failure!"
	exit 1
fi
