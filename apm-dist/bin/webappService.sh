#!/usr/bin/env sh

PRG="$0"
PRGDIR=`dirname "$PRG"`
[ -z "$WEBAPP_HOME" ] && WEBAPP_HOME=`cd "$PRGDIR/.." >/dev/null; pwd`

WEBAPP_LOG_DIR="${WEBAPP_HOME}/logs"
JAVA_OPTS=" -Xms256M -Xmx512M"
JAR_PATH="${WEBAPP_HOME}/webapp"

if [ ! -d "${WEBAPP_HOME}/logs" ]; then
    mkdir -p "${WEBAPP_LOG_DIR}"
fi

_RUNJAVA=${JAVA_HOME}/bin/java
[ -z "$JAVA_HOME" ] && _RUNJAVA=java

eval exec "\"$_RUNJAVA\" ${JAVA_OPTS} -jar ${JAR_PATH}/skywalking-webapp.jar \
         --server.port=8080 --collector.ribbon.listOfServers=127.0.0.1:10800 \
        2>${WEBAPP_LOG_DIR}/webapp.log 1> /dev/null &"

if [ $? -eq 0 ]; then
    sleep 1
	echo "Skywalking Web Application started successfully!"
else
	echo "Skywalking Web Application started failure!"
	exit 1
fi
