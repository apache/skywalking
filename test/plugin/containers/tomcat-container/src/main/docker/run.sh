#!/usr/bin/env bash

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

set -ex
[[ -n $DEBUG_MODE ]] && export

function exitOnError() {
    echo -e "\033[31m[ERROR] $1\033[0m">&2
    exitAndClean 1
}

function exitAndClean() {
    [[ -n $DEBUG_MODE ]] && exit $1

    [[ -f ${SCENARIO_HOME}/data/actualData.yaml ]] && rm -rf ${SCENARIO_HOME}/data/actualData.yaml
    [[ -d ${LOGS_HOME} ]] && rm -rf ${LOGS_HOME}
    exit $1
}

function healthCheck() {
    HEALTH_CHECK_URL=$1

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

    exitOnError "${SCENARIO_NAME}-${SCENARIO_VERSION} health check failed!"
}

TOOLS_HOME=/usr/local/skywalking/tools
SCENARIO_HOME=/usr/local/skywalking/scenario
export LOGS_HOME=${SCENARIO_HOME}/logs # share to catalina.sh

[[ ! -d ${LOGS_HOME} ]] && mkdir $LOGS_HOME

# Speed up launch tomcat
rm /usr/local/tomcat/webapps/* -rf # remove needn't app
sed -i "s%securerandom.source=file:/dev/random%securerandom.source=file:/dev/urandom%g" $JAVA_HOME/jre/lib/security/java.security

# To deploy testcase
cp ${SCENARIO_HOME}/*.war /usr/local/tomcat/webapps/

# start mock collector
echo "To start mock collector"
${TOOLS_HOME}/skywalking-mock-collector/bin/collector-startup.sh 1>${LOGS_HOME}/collector.out &
healthCheck http://localhost:12800/receiveData

echo "To start tomcat"
/usr/local/tomcat/bin/catalina.sh start 1>${LOGS_HOME}/catalina.out &

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
    ${TOOLS_HOME}/skywalking-validator.jar 1>${LOGS_HOME}/validator.out
status=$?

if [[ $status -eq 0 ]]; then
    echo "Scenario[${SCENARIO_NAME}-${SCENARIO_VERSION}] passed!" >&2
else
    cat ${SCENARIO_HOME}/data/actualData.yaml >&2
    exitOnError "Scenario[${SCENARIO_NAME}-${SCENARIO_VERSION}] failed!"
fi
exitAndClean $status
