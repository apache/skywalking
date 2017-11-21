#!/bin/sh

echo "replace {ZK_ADDRESSES} to ${ZK_ADDRESSES}"
eval sed -i -e 's/\{ZK_ADDRESSES\}/${ZK_ADDRESSES}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {ES_CLUSTER_NAME} to ${ES_CLUSTER_NAME}"
eval sed -i -e 's/\{ES_CLUSTER_NAME\}/${ES_CLUSTER_NAME}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {ES_ADDRESSES} to ${ES_ADDRESSES}"
eval sed -i -e 's/\{ES_ADDRESSES\}/${ES_ADDRESSES}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {BIND_HOST} to ${BIND_HOST}"
eval sed -i -e 's/\{BIND_HOST\}/${BIND_HOST}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {NAMING_BIND_HOST} to ${NAMING_BIND_HOST}"
eval sed -i -e 's/\{NAMING_BIND_HOST\}/${NAMING_BIND_HOST}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {NAMING_BIND_PORT} to ${NAMING_BIND_PORT}"
eval sed -i -e 's/\{NAMING_BIND_PORT\}/${NAMING_BIND_PORT}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {REMOTE_BIND_PORT} to ${REMOTE_BIND_PORT}"
eval sed -i -e 's/\{REMOTE_BIND_PORT\}/${REMOTE_BIND_PORT}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {AGENT_GRPC_BIND_PORT} to ${AGENT_GRPC_BIND_PORT}"
eval sed -i -e 's/\{AGENT_GRPC_BIND_PORT\}/${AGENT_GRPC_BIND_PORT}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {AGENT_JETTY_BIND_HOST} to ${AGENT_JETTY_BIND_HOST}"
eval sed -i -e 's/\{AGENT_JETTY_BIND_HOST\}/${AGENT_JETTY_BIND_HOST}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {AGENT_JETTY_BIND_PORT} to ${AGENT_JETTY_BIND_PORT}"
eval sed -i -e 's/\{AGENT_JETTY_BIND_PORT\}/${AGENT_JETTY_BIND_PORT}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {UI_JETTY_BIND_PORT} to ${UI_JETTY_BIND_PORT}"
eval sed -i -e 's/\{UI_JETTY_BIND_PORT\}/${UI_JETTY_BIND_PORT}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {UI_JETTY_BIND_HOST} to ${UI_JETTY_BIND_HOST}"
eval sed -i -e 's/\{UI_JETTY_BIND_HOST\}/${UI_JETTY_BIND_HOST}/' /usr/local/skywalking-collector/config/application.yml


exec "$@"
