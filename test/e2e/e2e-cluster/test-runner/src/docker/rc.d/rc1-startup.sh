# Licensed to the SkyAPM under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#!/usr/bin/env bash

echo 'starting OAP server...' && start_oap 'init'

echo 'starting Web app...' && start_webapp '0.0.0.0' 8081

if test "${MODE}" = "cluster"; then
    # start another OAP server in a different port
    SW_CORE_GRPC_PORT=11801 \
        && SW_CORE_REST_PORT=12801 \
        && echo 'starting OAP server...' \
        && start_oap 'no-init'

    # start another WebApp server in a different port
    echo 'starting Web app...' \
        && start_webapp '0.0.0.0' 8082
fi

echo 'starting instrumented services...' && start_instrumented_services

check_tcp 127.0.0.1 \
          9090 \
          60 \
          10 \
          "waiting for the instrumented service 0 to be ready"

if [[ $? -ne 0 ]]; then
    echo "instrumented service 0 failed to start in 30 * 10 seconds: "
    cat ${SERVICE_LOG}/*
    exit 1
fi

check_tcp 127.0.0.1 \
          9091 \
          60 \
          10 \
          "waiting for the instrumented service 1 to be ready"

if [[ $? -ne 0 ]]; then
    echo "instrumented service 1 failed to start in 24 * 10 seconds: "
    cat ${SERVICE_LOG}/*
    exit 1
fi

echo "SkyWalking e2e container is ready for tests"

tail -f ${OAP_LOG_DIR}/* \
        ${WEBAPP_LOG_DIR}/* \
        ${SERVICE_LOG}/* \
        ${ES_HOME}/logs/elasticsearch.log \
        ${ES_HOME}/logs/stdout.log
