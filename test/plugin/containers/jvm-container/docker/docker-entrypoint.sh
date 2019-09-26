#!/bin/sh
/usr/local/skywalking-agent-scenario/skywalking-mock-collector/collector-startup.sh &
sleep 30
# start applications
exec "$@" &
sleep 60
curl ${SCENARIO_ENTRY_SERVICE}
sleep 40
curl http://localhost:12800/receiveData > ${SCENARIO_DATA}/${SCENARIO_NAME}_${SCENARIO_VERSION}/actualData.yaml
#
echo "Scenario[${SCENARIO_NAME}, ${SCENARIO_VERSION}] build successfully!"
