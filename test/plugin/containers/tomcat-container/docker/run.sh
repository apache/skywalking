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

function exitOnError() {
    echo -e "\033[31m[ERROR] $1\033[0m">&2
    exit 1
}

function healthCheck() {
    HEALTH_CHECK_URL=$1

    for ((i=1; i<=10; i++));
    do
        STATUS_CODE="$(curl -Is ${HEALTH_CHECK_URL} | head -n 1)"
        if [[ $STATUS_CODE == *"200"* ]]; then
          echo "${HEALTH_CHECK_URL}: ${STATUS_CODE}"
          return 0
        fi
        sleep 2
    done

    exitOnError "${SCENARIO_NAME}-${SCENARIO_VERSION} health check failed!"
}

SCENARIO_HOME=/usr/local/skywalking-agent-scenario/

# Speed up launch tomcat
rm /usr/local/tomcat/webapps/* -rf # remove needn't app
sed -i "s%securerandom.source=file:/dev/random%securerandom.source=file:/dev/urandom%g" $JAVA_HOME/jre/lib/security/java.security

# To deploy testcase
cp ${SCENARIO_HOME}/packages/*.war /usr/local/tomcat/webapps/

# start mock collector
echo "To start mock collector"
${SCENARIO_HOME}/skywalking-mock-collector/bin/collector-startup.sh \
  1>${SCENARIO_HOME}/logs/collector.out 2>${SCENARIO_HOME}/logs/collector.err &
healthCheck http://localhost:12800/receiveData

echo "To start tomcat"
/usr/local/tomcat/bin/catalina.sh start 1>${SCENARIO_HOME}/logs/catalina.out 2>${SCENARIO_HOME}/logs/catalina.err &
healthCheck ${SCENARIO_HEALTH_CHECK_URL}

echo "To visit entry service"
curl ${SCENARIO_ENTRY_SERVICE}
sleep 5

echo "To receive actual data"
curl -s http://localhost:12800/receiveData > ${SCENARIO_HOME}/data/actualData.yaml

echo "To validate"
java -jar \
  -Dv2=true \
  -DtestDate="`date +%Y-%m-%d-%H-%M`" \
  -DtestCasePath=${SCENARIO_HOME}/data/ \
  ${SCENARIO_HOME}/skywalking-validator-tools.jar 1>${SCENARIO_HOME}/logs/validate.log 2>&2
status=$?

if [[ $status -eq 0 ]]; then
  echo "Scenario[${SCENARIO_SUPPORT_FRAMEWORK}, ${SCENARIO_VERSION}] passed!"
else
  exitOnError "Scenario[${SCENARIO_SUPPORT_FRAMEWORK}, ${SCENARIO_VERSION}] failed!"
fi
exit $status