#!/usr/bin/env bash

SW_PREFIX="${SW_SERVER_BIN}/.."
SW_LOG_DIR="${SW_SERVER_BIN}/../log"
SW_CFG_DIR="${SW_SERVER_BIN}/../config"

#设置Java home
if [ "$JAVA_HOME" != "" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA=java
fi

CLASSPATH="$SW_CFG_DIR:$CLASSPATH"

for i in "${SW_SERVER_BIN}"/lib/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done

if $cygwin
then
    CLASSPATH=`cygpath -wp "$CLASSPATH"`
fi

echo "CLASSPATH=$CLASSPATH"