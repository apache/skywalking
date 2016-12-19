#!/usr/bin/env bash
STORAGE_SERVER_BIN="$0"

# Get standard environment variables
STORAGE_SERVER_BIN_DIR=`dirname "$STORAGE_SERVER_BIN"`
STORAGE_PREFIX="${STORAGE_SERVER_BIN_DIR}/.."
STORAGE_LOG_DIR="${STORAGE_SERVER_BIN_DIR}/../logs"
STORAGE_CFG_DIR="${STORAGE_SERVER_BIN_DIR}/../config"

#echo $STORAGE_SERVER_BIN_DIR
#set java home
if [ "$JAVA_HOME" != "" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA=java
fi

if [ ! -d "${STORAGE_LOG_DIR}" ]; then
    mkdir -p "${STORAGE_LOG_DIR}"
fi

CLASSPATH="$STORAGE_CFG_DIR:$CLASSPATH"

for i in "${STORAGE_SERVER_BIN_DIR}"/../libs/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done

echo "CLASSPATH=$CLASSPATH"


$JAVA ${JAVA_OPTS} -DSTORAGE_HOME=${STORAGE_SERVER_BIN_DIR}/.. -DDATA_INDEX_HOME=${STORAGE_SERVER_BIN_DIR}/../data/index -classpath $CLASSPATH com.a.eye.skywalking.storage.Main >> ${STORAGE_SERVER_BIN_DIR}/../logs/storage-server.log & 2>&1&
