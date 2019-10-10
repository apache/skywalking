#!/bin/bash

home="$(cd "$(dirname $0)"; pwd)"

java -jar ${agent_opts} "-Dskywalking.agent.service_name=jettyserver-scenario" ${home}/../libs/jettyserver-scenario.jar &
sleep 1

java -jar ${agent_opts} "-Dskywalking.agent.service_name=jettyclient-scenario"  ${home}/../libs/jettyclient-scenario.jar &
