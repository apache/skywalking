#!/bin/bash
set -e

echo "replace SERVER_PORT with $SERVER_PORT"
eval sed -i -e 's/\{SERVER_PORT\}/$SERVER_PORT/' /home/elasticsearch/skywalking-storage/config/config.properties

echo "replace REGISTRY_CENTER_URL with $REGISTRY_CENTER_URL"
eval sed -i -e 's/\{REGISTRY_CENTER_URL\}/$REGISTRY_CENTER_URL/' /home/elasticsearch/skywalking-storage/config/config.properties

gosu elasticsearch "$@"