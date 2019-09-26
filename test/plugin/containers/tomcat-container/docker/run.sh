#!/usr/bin/env bash

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

    echo -e "\033[31m[ERROR] ${SCENARIO_NAME}-${SCENARIO_VERSION} health check failed!\033[0m"
    exit 1
}

SCENARIO_HOME=/usr/local/skywalking-agent-scenario/ && mkdir -p ${SCENARIO_HOME}

rm /usr/local/tomcat/webapps/* -rf
sed -i "s%securerandom.source=file:/dev/random%securerandom.source=file:/dev/urandom%g" $JAVA_HOME/jre/lib/security/java.security

# build structure
DEPLOY_PACKAGES=($(cd ${SCENARIO_PACKAGES} && ls -p | grep -v /))
echo "[${SCENARIO_SUPPORT_FRAMEWORK}] deploy packages: ${DEPLOY_PACKAGES}"
cp ${SCENARIO_HOME}/packages/*.war /usr/local/tomcat/webapps/

# start mock collector
echo "To start mock collector"
${SCENARIO_HOME}/skywalking-mock-collector/bin/collector-startup.sh \
  1>${SCENARIO_HOME}/logs/collector.log 2>&1 &
healthCheck http://localhost:12800/receiveData

echo "To start tomcat"
/usr/local/tomcat/bin/catalina.sh start 1>${SCENARIO_HOME}/logs/catalina.log 2>&1 &
healthCheck ${SCENARIO_HEALTH_CHECK_URL}

echo "To visit entry service"
curl ${SCENARIO_ENTRY_SERVICE}
sleep 5

echo "To receive actual data"
curl -s http://localhost:12800/receiveData > ${SCENARIO_HOME}/data/actualData.yaml

###
echo "To validate"
java -jar -Dv2=true -DtestDate="`date +%Y-%m-%d-%H-%M`" -DtestCasePath=${SCENARIO_HOME}/data/ /skywalking-validator-tools.jar
status=$?

if [[ $status -eq 0 ]]; then
  echo "Scenario[${SCENARIO_SUPPORT_FRAMEWORK}, ${SCENARIO_VERSION}] passed!"
else
  echo -e "\033[31mScenario[${SCENARIO_SUPPORT_FRAMEWORK}, ${SCENARIO_VERSION}] failed!\033[0m"
fi