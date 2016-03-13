#!/bin/bash

# check environment variables
SW_ANALYSIS_HOME=${HOME}/skywalking-analysis

#check hbase home
HBASE_HOME=${HOME}/hbase-1.1.2

#check hadoop home
HADOOP_HOME=${HOME}/hadoop-2.6.0

#check skywalking runtime config directory is exisit, if not, will create it.
SW_RT_CONF_DIR="${SW_ANALYSIS_HOME}/runtime-conf"
if [ ! -d "$SW_RT_CONF_DIR" ]; then
    mkdir -p $SW_RT_CONF_DIR
fi

# get the previous process id
PID_FILES="${SW_RT_CONF_DIR}/analysis.pid"

if [ ! -f "$FILE_PREVIOUS_EXECUTE_TIME" ]; then
  touch "$PID_FILES"
fi

SW_ANALYSIS_PID=`cat ${PID_FILES}`
# check if the skywalking analysis process is running
if [ "$SW_ANALYSIS_PID" != "" ]; then
    PS_OUT=`ps -ef | grep $SW_ANALYSIS_PID | grep -v 'grep' | grep -v $0`
    result=$(echo $PS_OUT | grep $SW_ALALYSIS_PID)
    if [ "$result" != "" ]; then
        echo "The skywalking analysis process is running. Will delay the analysis."
        exit -1;
    fi
fi

#skywalking analysis mode:1)accumulate(default) 2)rewrite
SW_ANALYSIS_MODE=ACCUMULATE
#skywalking rewrite execute dates. Each number represents the day of the month.
REWRITE_EXECUTIVE_DAY_ARR=(5,10)

#Get the previous execute time of rewrite mode
PRE_TIME_OF_REWRITE_FILE="${SW_RT_CONF_DIR}/rewrite_pre_time.conf"
if [ ! -f "$PRE_TIME_OF_REWRITE_FILE" ]; then
    echo "skywalking rewrite time file is not exists, create it"
    touch $PRE_TIME_OF_REWRITE_FILE
fi

PRE_TIME_OF_REWRITE_TIME=`cat $PRE_TIME_OF_REWRITE_FILE`
#check if the day is in the date of rewrite mode
if [ "$PRE_TIME_OF_REWRITE_TIME" != "" ]; then
    TODAY=$(date "+%d")
    if [ "$PRE_TIME_OF_REWRITE_TIME" != $TODAY ]; then
        THE_DAY_OF_MONTH=$(date "+%d")
        for THE_DAY in ${REWRITE_EXECUTIVE_DAY_ARR[@]}
        do
            if [ ${THE_DAY} -eq ${THE_DAY_OF_MONTH} ]; then
                SW_ANALYSIS_MODE=REWRITE
                START_TIME=$(date --date='1 month ago' '+%Y-%m-01/00:00:00')
                echo "skywalking analysis will execute rewrite mode. Start time:${START_TIME}"
                break
            fi
        done
    else
        echo "${TODAY} has been execute rewrite analysis process.Will not execute rewrite mode!!"
    fi
fi

if [ "${SW_ANALYSIS_MODE}" != "REWRITE" ]; then
    #check the file of previous executive accumulate mode time
    PRE_TIME_OF_ACCUMULATE_FILE="${SW_RT_CONF_DIR}/accumulate_pre_time.conf"
    if [ ! -f "${PRE_TIME_OF_ACCUMULATE_FILE}" ]; then
        echo "skywalking accumulate time file is not exists, create it."
        touch $PRE_TIME_OF_ACCUMULATE_FILE
    fi

    START_TIME=`cat ${PRE_TIME_OF_ACCUMULATE_FILE}`
    if [ "$START_TIME" = "" ]; then
        START_TIME=`date --date='3 month ago' "+%Y-%m-%d/%H:%M:%S"`
    fi
    SW_ANALYSIS_MODE=ACCUMULATE
    echo "skywalking analysis process will execute accumulate mode. start time: ${START_TIME}."
fi

#Get the current datetime
END_TIME=`date --date='10 minute ago' "+%Y-%m-%d/%H:%M:%S"`
#echo $END_TIME

## execute command
echo "Begin to analysis the buried point data between ${START_TIME} to ${END_TIME}."
HADOOP_CLASSPATH=`${HBASE_HOME}/bin/hbase classpath` ${HADOOP_HOME}/bin/hadoop jar skywalking-analysis-1.0-SNAPSHOT.jar -Dskywalking.analysis.mode=${SW_ANALYSIS_MODE} ${START_TIME} ${END_TIME}


if [ "${SW_ANALYSIS_MODE}" = "REWRITE" ]; then
    echo $(date "+%d") > ${PRE_TIME_OF_REWRITE_FILE}
else
    echo $END_TIME > ${PRE_TIME_OF_ACCUMULATE_FILE}
fi
