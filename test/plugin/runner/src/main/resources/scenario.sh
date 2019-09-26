#!/usr/bin/env bash

PRG="$0"
PRGDIR=`dirname "$PRG"`
[ -z "$SCENARIO_HOME" ] && SCENARIO_HOME=`cd "$PRGDIR" >/dev/null; pwd`

state_house=$1
testcase_name=${scenario_name}-${scenario_version}

<#noparse>touch ${state_house}/${testcase_name}.RUNNING</#noparse>
start_stamp=`date +%s`

${running_script}

cid=`docker ps -aqf "name=skywalking-agent-test-${scenario_name}-${scenario_version}"`
<#noparse>

elapsed=$(( `date +%s` - $start_stamp ))
printf "${testcase_name} Elapsed: %2dHour %2dMin %2dSec\n" $(( ${elapsed}/3600 )) $(( ${elapsed}%3600/60 )) $(( ${elapsed}%60 ))

docker container rm -f $cid
docker volume prune -f
docker network prune -f

mv ${state_house}/${testcase_name}.RUNNING ${state_house}/${testcase_name}.FINISH
</#noparse>