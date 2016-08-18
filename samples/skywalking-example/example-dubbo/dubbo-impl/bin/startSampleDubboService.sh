#!/bin/sh

# Get standard environment variables
SAMPLE_DUBBO_BIN_PATH=$(cd `dirname $0`; pwd)
SAMPLE_DUBBO_CFG_DIR="${SAMPLE_DUBBO_BIN_PATH}/../config"
SAMPLE_DUBBO_LIB_DIR="${SAMPLE_DUBBO_BIN_PATH}/../lib"
SAMPLE_DUBBO_LOG_DIR="${SAMPLE_DUBBO_BIN_PATH}/../log"

#echo $SW_SERVER_BIN_DIR
#set java home
if [ "$JAVA_HOME" != "" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA=java
fi

CLASSPATH="$SAMPLE_DUBBO_CFG_DIR:$CLASSPATH"

for i in "${SAMPLE_DUBBO_LIB_DIR}"/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done

echo "CLASSPATH=$CLASSPATH"

$JAVA -javaagent:${SAMPLE_DUBBO_BIN_PATH}/../agent/skywalking-agent-2.0-2016.jar -classpath $CLASSPATH com.ai.cloud.skywalking.sample.util.DubboStart
