#!/bin/sh

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false
os400=false
darwin=false
case "`uname`" in
CYGWIN*) cygwin=true;;
OS400*) os400=true;;
Darwin*) darwin=true;;
esac

# resolve links - $0 may be a softlink
SW_SERVER_BIN="$0"

while [ -h "$SW_SERVER_BIN" ]; do
  ls=`ls -ld "$SW_SERVER_BIN"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    SW_SERVER_BIN="$link"
  else
    SW_SERVER_BIN=`dirname "$SW_SERVER_BIN"`/"$link"
  fi
done

# Get standard environment variables
SW_SERVER_BIN_DIR=`dirname "$SW_SERVER_BIN"`
SW_PREFIX="${SW_SERVER_BIN_DIR}/.."
SW_LOG_DIR="${SW_SERVER_BIN_DIR}/../log"
SW_CFG_DIR="${SW_SERVER_BIN_DIR}/../config"

#echo $SW_SERVER_BIN_DIR
#set java home
if [ "$JAVA_HOME" != "" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA=java
fi

CLASSPATH="$SW_CFG_DIR:$CLASSPATH"

for i in "${SW_SERVER_BIN_DIR}"/../lib/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done

echo "CLASSPATH=$CLASSPATH"

JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false"

$JAVA ${JAVA_OPTS} -classpath $CLASSPATH com.a.eye.skywalking.reciever.CollectionServer >> ${SW_SERVER_BIN_DIR}/.
./log/sw-server.log 2>&1 &
