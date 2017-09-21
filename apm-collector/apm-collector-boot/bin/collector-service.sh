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
[ -z "$JAVA_HOME" ] && _RUNJAVA=java

CLASSPATH="$COLLECTOR_HOME/config:$CLASSPATH"
for i in "$COLLECTOR_HOME"/libs/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done
COLLECTOR_OPTIONS=" -Dcollector.logDir=$COLLECTOR_LOGS_DIR"
echo "Starting collector...."

eval exec "\"$_RUNJAVA\" ${JAVA_OPTS} ${COLLECTOR_OPTIONS} -classpath $CLASSPATH org.skywalking.apm.collector.boot.CollectorBootStartUp \
        2>${COLLECTOR_LOGS_DIR}/collector.log 1> /dev/null &"

retval=$?
pid=$!
FAIL_MSG="Collector started failure!"
SUCCESS_MSG="Collector started successfully!"
[ ${retval} -eq 0 ] || (echo ${FAIL_MSG}; exit ${retval})
sleep 1
if ! ps -p ${pid} > /dev/null ; then
    echo ${FAIL_MSG}
    exit 1
fi
echo ${SUCCESS_MSG}
