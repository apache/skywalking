#!/bin/bash

echo "replace SERVER_PORT with $SERVER_PORT"
eval sed -i -e 's/\{SERVER_PORT\}/$SERVER_PORT/' /usr/local/skywalking-routing/config/config.properties

echo "replace REGISTRY_CENTER_URL with $REGISTRY_CENTER_URL"
eval sed -i -e 's/\{REGISTRY_CENTER_URL\}/$REGISTRY_CENTER_URL/' /usr/local/skywalking-routing/config/config.properties

echo "replace ALARM_REDIS_SERVER whit $ALARM_REDIS_SERVER"
eval sed -i -e 's/\{ALARM_REDIS_SERVER\}/$ALARM_REDIS_SERVER/' /usr/local/skywalking-routing/config/config.properties

exec "$@"