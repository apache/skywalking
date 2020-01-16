#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


function exitOnError() {
    echo -e "\033[31m[ERROR] $1\033[0m">&2
    exitAndClean 1
}

function exitAndClean() {
    [[ -f ${SCENARIO_HOME}/data/actualData.yaml ]] && rm -rf ${SCENARIO_HOME}/data/actualData.yaml
    [[ -d ${SCENARIO_HOME}/logs ]] && rm -rf ${SCENARIO_HOME}/logs
    exit $1
}

function healthCheck() {
    HEALTH_CHECK_URL=$1
    STATUS_CODE="-1"
    TIMES=${TIMES:-150}
    for ((i=1; i<=${TIMES}; i++));
    do
        STATUS_CODE="$(curl --max-time 3 -Is ${HEALTH_CHECK_URL} | head -n 1)"
        if [[ $STATUS_CODE == *"200"* ]]; then
          echo "${HEALTH_CHECK_URL}: ${STATUS_CODE}"
          return 0
        fi
        sleep 2
    done

    exitOnError "${SCENARIO_NAME}-${SCENARIO_VERSION} url=${HEALTH_CHECK_URL}, status=${STATUS_CODE} health check failed!"
}

if [[ -z "${SCENARIO_START_SCRIPT}" ]]; then
    exitOnError "The name of startup script cannot be empty!"
fi

TOOLS_HOME=/usr/local/skywalking/tools
SCENARIO_HOME=/usr/local/skywalking/scenario

unzip -q ${SCENARIO_HOME}/*.zip -d /var/run/
if [[ ! -f /var/run/${SCENARIO_NAME}/${SCENARIO_START_SCRIPT} ]]; then
    exitOnError "The required startup script not exists!"
fi

echo "To start mock collector"
${TOOLS_HOME}/skywalking-mock-collector/bin/collector-startup.sh 1>/dev/null &
healthCheck http://localhost:12800/receiveData

# start applications
export agent_opts="-javaagent:${SCENARIO_HOME}/agent/skywalking-agent.jar
    -Dskywalking.collector.grpc_channel_check_interval=2
    -Dskywalking.collector.app_and_service_register_check_interval=2
    -Dskywalking.collector.discovery_check_interval=2
    -Dskywalking.collector.backend_service=localhost:19876
    -Dskywalking.agent.service_name=${SCENARIO_NAME}
    -Dskywalking.logging.dir=/usr/local/skywalking/scenario/logs
    -Dskywalking.agent.authentication=test-token
    -Xms256m -Xmx256m ${agent_opts}"
exec /var/run/${SCENARIO_NAME}/${SCENARIO_START_SCRIPT} 1>/dev/null &

healthCheck http://localhost:12800/status
healthCheck ${SCENARIO_HEALTH_CHECK_URL}

echo "To visit entry service"
curl -s --max-time 3 ${SCENARIO_ENTRY_SERVICE}
sleep 5

echo "To receive actual data"
curl -s --max-time 3 http://localhost:12800/receiveData > ${SCENARIO_HOME}/data/actualData.yaml
[[ ! -f ${SCENARIO_HOME}/data/actualData.yaml ]] && exitOnError "${SCENARIO_NAME}-${SCENARIO_VERSION}, 'actualData.yaml' Not Found!"

echo "To validate"
java -jar \
    -Xmx256m -Xms256m \
    -DcaseName="${SCENARIO_NAME}-${SCENARIO_VERSION}" \
    -DtestCasePath=${SCENARIO_HOME}/data/ \
    ${TOOLS_HOME}/skywalking-validator-tools.jar 1>/dev/null
status=$?

if [[ $status -eq 0 ]]; then
  echo "Scenario[${SCENARIO_NAME}-${SCENARIO_VERSION}] passed!" >&2
else
  cat ${SCENARIO_HOME}/data/actualData.yaml >&2
  exitOnError "Scenario[${SCENARIO_NAME}-${SCENARIO_VERSION}] failed!"
fi
exitAndClean $status
