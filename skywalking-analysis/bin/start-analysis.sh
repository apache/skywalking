#!/bin/bash

# check environment virables
SW_ANALYSIS_HOME=/tmp/skywalking-analysis

# check Java home
if [ -z "$JAVA_HOME" -a -z "$JRE_HOME" ]; then
  if $darwin; then
    # Bugzilla 54390
    if [ -x '/usr/libexec/java_home' ] ; then
      export JAVA_HOME=`/usr/libexec/java_home`
    # Bugzilla 37284 (reviewed).
    elif [ -d "/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home" ]; then
      export JAVA_HOME="/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home"
    fi
  else
    JAVA_PATH=`which java 2>/dev/null`
    if [ "x$JAVA_PATH" != "x" ]; then
      JAVA_PATH=`dirname $JAVA_PATH 2>/dev/null`
      JRE_HOME=`dirname $JAVA_PATH 2>/dev/null`
    fi
    if [ "x$JRE_HOME" = "x" ]; then
      # XXX: Should we try other locations?
      if [ -x /usr/bin/java ]; then
        JRE_HOME=/usr
      fi
    fi
  fi
  if [ -z "$JAVA_HOME" -a -z "$JRE_HOME" ]; then
    echo "Neither the JAVA_HOME nor the JRE_HOME environment variable is defined"
    echo "At least one of these environment variable is needed to run this program"
    exit 1
  fi
fi
if [ -z "$JAVA_HOME" -a "$1" = "debug" ]; then
  echo "JAVA_HOME should point to a JDK in order to run in debug mode."
  exit 1
fi
if [ -z "$JRE_HOME" ]; then
  JRE_HOME="$JAVA_HOME"
fi

#check hbase home
HBASE_HOME=${HOME}/hbase-1.1.2

#check hadoop home
HADOOP_HOME=${HOME}/hadoop-2.6.0

## check provious execute time
PID_DIR="${SW_ANALYSIS_HOME}/tmp"

if [ ! -d "$PID_DIR" ]; then
    mkdir -p $PID_DIR
fi

PID_FILES="${PID_DIR}/analysis.pid"

if [ ! -f "$FILE_PROVIOUS_EXECUTE_TIME" ]; then
  touch "$PID_FILES"
fi

START_TIME=`cat ${PID_FILES}`
if [ "$START_TIME" = "" ]; then
    START_TIME=`date --date='3 month ago' "+%Y-%m-%d/%H:%M:%S"`
fi
#echo $START_TIME

##Get the current datetime
END_TIME=`date "+%Y-%m-%d/%H:%M:%S"`
#echo $END_TIME

## execute command
HADOOP_CLASSPATH=`${HBASE_HOME}/bin/hbase classpath` ${HADOOP_HOME}/bin/hadoop jar skywalking-analysis-1.0-SNAPSHOT.jar ${START_TIME} ${END_TIME}

echo $END_TIME > ${PID_FILES}
