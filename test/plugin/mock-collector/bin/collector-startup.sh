#!/usr/bin/env bash

PRG="$0"
PRGDIR=`dirname "$PRG"`
[ -z "$COLLECTOR_HOME" ] && COLLECTOR_HOME=`cd "$PRGDIR/.." >/dev/null; pwd`

_RUNJAVA=${JAVA_HOME}/bin/java
[ -z "$JAVA_HOME" ] && _RUNJAVA=`java`

CLASSPATH="$COLLECTOR_HOME/config:$CLASSPATH"
for i in "$COLLECTOR_HOME"/libs/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done

JAVA_OPTS="${JAVA_OPTS} -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 "

$_RUNJAVA ${JAVA_OPTS}  -classpath $CLASSPATH org.apache.skywalking.plugin.test.mockcollector.Main
