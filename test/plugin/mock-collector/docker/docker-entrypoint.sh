#!/usr/bin/env bash

echo "replace GRPC_BIND_HOST with $GRPC_BIND_HOST"
eval sed -i -e 's/\{GRPC_BIND_HOST\}/$GRPC_BIND_HOST/' /usr/local/skywalking-mock-collector/config/config.properties

echo "replace GRPC_BIND_PORT with $GRPC_BIND_PORT"
eval sed -i -e 's/\{GRPC_BIND_PORT\}/$GRPC_BIND_PORT/' /usr/local/skywalking-mock-collector/config/config.properties

exec "$@"
