#!/usr/bin/env bash

PRG="$0"
PRGDIR=`dirname "$PRG"`
[ -z "$WEB_HOME" ] && WEB_HOME=`cd "$PRGDIR/.." >/dev/null; pwd`

WEB_LOGS_DIR="${WEB_HOME}/logs"
JAVA_OPTS=" -Xms256M -Xmx512M"

if [ ! -d "${WEB_HOME}/logs" ]; then
    mkdir -p "${WEB_LOGS_DIR}"
fi

_RUNJAVA=${JAVA_HOME}/bin/java
[ -z "$JAVA_HOME" ] && _RUNJAVA=`java`

CLASSPATH="$WEB_HOME/config:$CLASSPATH"
for i in "$WEB_HOME"/libs/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done

echo "Starting web service...."
$_RUNJAVA ${JAVA_OPTS} -classpath $CLASSPATH org.skywalking.apm.ui.ApplicationStartUp
