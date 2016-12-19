#!/bin/sh

ROUTING_HOME=`dirname "$0"`/..
ROUTING_CFG_DIR="${ROUTING_HOME}/config"
ROUTING_LOG_DIR="${ROUTING_HOME}/logs"

if [ "$JAVA_HOME" != "" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA=java
fi

if [ ! -d "${ROUTING_LOG_DIR}" ]; then
    mkdir -p ${ROUTING_LOG_DIR}
fi

CLASSPATH="$ROUTING_CFG_DIR:$CLASSPATH"

for i in "${ROUTING_HOME}"/libs/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done

echo "CLASSPATH=$CLASSPATH"


$JAVA ${JAVA_OPTS} -classpath $CLASSPATH com.a.eye.skywalking.routing.Main