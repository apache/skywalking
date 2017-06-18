#!/usr/bin/env bash

echo "replace CLUSTER_CURRENT_HOST_NAME with $CLUSTER_CURRENT_HOST_NAME"
eval sed -i -e 's/\{CLUSTER_CURRENT_HOST_NAME\}/$CLUSTER_CURRENT_HOST_NAME/' /usr/local/skywalking-collector/config/collector.config

echo "replace CLUSTER_CURRENT_PORT with $CLUSTER_CURRENT_PORT"
eval sed -i -e 's/\{CLUSTER_CURRENT_PORT\}/$CLUSTER_CURRENT_PORT/' /usr/local/skywalking-collector/config/collector.config

echo "replace CLUSTER_CURRENT_ROLES with $CLUSTER_CURRENT_ROLES"
eval sed -i -e 's/\{CLUSTER_CURRENT_ROLES\}/$CLUSTER_CURRENT_ROLES/' /usr/local/skywalking-collector/config/collector.config

echo "replace CLUSTER_SEED_NODES with $CLUSTER_SEED_NODES"
eval sed -i -e 's/\{CLUSTER_SEED_NODES\}/$CLUSTER_SEED_NODES/' /usr/local/skywalking-collector/config/collector.config

echo "replace ES_CLUSTER_NAME with $ES_CLUSTER_NAME"
eval sed -i -e 's/\{ES_CLUSTER_NAME\}/$ES_CLUSTER_NAME/' /usr/local/skywalking-collector/config/collector.config

echo "replcae ES_CLUSTER_NODES with $ES_CLUSTER_NODES"
eval sed -i -e 's/\{ES_CLUSTER_NODES\}/$ES_CLUSTER_NODES/'  /usr/local/skywalking-collector/config/collector.config

echo "replace HTTP_HOST_NAME with $HTTP_HOST_NAME"
eval sed -i -e 's/\{HTTP_HOST_NAME\}/$HTTP_HOST_NAME/'  /usr/local/skywalking-collector/config/collector.config

echo "replace HTTP_PORT with $HTTP_PORT"
eval sed -i -e 's/\{HTTP_PORT\}/$HTTP_PORT/' /usr/local/skywalking-collector/config/collector.config

exec "$@"
