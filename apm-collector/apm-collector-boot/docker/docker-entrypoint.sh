#!/bin/sh

echo "replace {ZK_ADDRESSES} to ${ZK_ADDRESSES}"
eval sed -i -e 's/\{ZK_ADDRESSES\}/${ZK_ADDRESSES}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {ES_CLUSTER_NAME} to ${ES_CLUSTER_NAME}"
eval sed -i -e 's/\{ES_CLUSTER_NAME\}/${ES_CLUSTER_NAME}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {ES_ADDRESSES} to ${ES_ADDRESSES}"
eval sed -i -e 's/\{ES_ADDRESSES\}/${ES_ADDRESSES}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {BIND_HOST} to ${BIND_HOST}"
eval sed -i -e 's/\{BIND_HOST\}/${BIND_HOST}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {GRPC_BIND_PORT} to ${GRPC_BIND_PORT}"
eval sed -i -e 's/\{GRPC_BIND_PORT\}/${GRPC_BIND_PORT}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {AGENT_SERVER_BIND_PORT} to ${AGENT_SERVER_BIND_PORT}"
eval sed -i -e 's/\{AGENT_SERVER_BIND_PORT\}/${AGENT_SERVER_BIND_PORT}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {AGENT_STREAM_JETTY_BIND_PORT} to ${AGENT_STREAM_JETTY_BIND_PORT}"
eval sed -i -e 's/\{AGENT_STREAM_JETTY_BIND_PORT\}/${AGENT_STREAM_JETTY_BIND_PORT}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {UI_BIND_HOST} to ${UI_BIND_HOST}"
eval sed -i -e 's/\{UI_BIND_HOST\}/${UI_BIND_HOST}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {UI_BIND_PORT} to ${UI_BIND_PORT}"
eval sed -i -e 's/\{UI_BIND_PORT\}/${UI_BIND_PORT}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {CLUSTER_BIND_HOST} to ${CLUSTER_BIND_HOST}"
eval sed -i -e 's/\{CLUSTER_BIND_HOST\}/${CLUSTER_BIND_HOST}/' /usr/local/skywalking-collector/config/application.yml

echo "replace {CLUSTER_BIND_PORT} to ${CLUSTER_BIND_PORT}"
eval sed -i -e 's/\{CLUSTER_BIND_PORT\}/${CLUSTER_BIND_PORT}/' /usr/local/skywalking-collector/config/application.yml


exec "$@"
