#!/usr/bin/env bash

echo "collector services : $COLLECTOR_SERVERS"
IFS=', ' read -r -a array <<< "$COLLECTOR_SERVERS"

echo "clear config.properties"
echo "" > /usr/local/skywalking-web/config/collector_config.properties

for index in "${!array[@]}"
do
    echo "collector.servers[$index]=${array[index]}">> /usr/local/skywalking-web/config/collector_config.properties
done

echo "config.properties as following"
cat /usr/local/skywalking-web/config/collector_config.properties

exec "$@"
