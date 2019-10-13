#!/bin/bash

home="$(cd "$(dirname $0)"; pwd)"

java -jar ${agent_opts} ${home}/../libs/solrj-7.x-scenario.jar &
