#!/usr/bin/env sh

PRG="$0"
PRGDIR=`dirname "$PRG"`
[ -z "$COLLECTOR_HOME" ] && COLLECTOR_HOME=`cd "$PRGDIR/.." >/dev/null; pwd`

COLLECT_LOG_DIR="${COLLECTOR_HOME}/logs"
JAVA_OPTS=" -Xms256M -Xmx512M"

if [ ! -d "${COLLECTOR_HOME}/logs" ]; then
    mkdir -p "${COLLECT_LOG_DIR}"
fi

_RUNJAVA=${JAVA_HOME}/bin/java
[ -z "$JAVA_HOME" ] && _RUNJAVA=java

CLASSPATH="$COLLECTOR_HOME/config:$CLASSPATH"
for i in "$COLLECTOR_HOME"/collector-libs/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done

COLLECTOR_OPTIONS=" -Dcollector.logDir=${COLLECT_LOG_DIR}"

eval exec "\"$_RUNJAVA\" ${JAVA_OPTS} ${COLLECTOR_OPTIONS} -classpath $CLASSPATH org.apache.skywalking.apm.collector.boot.CollectorBootStartUp \
        2>${COLLECT_LOG_DIR}/collector.log 1> /dev/null &"

if [ $? -eq 0 ]; then
    sleep 1
	echo "Skywalking Collector started successfully!"
else
	echo "Skywalking Collector started failure!"
	exit 1
fi
