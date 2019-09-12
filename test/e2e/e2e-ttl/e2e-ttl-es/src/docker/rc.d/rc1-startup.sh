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

echo 'starting OAP server...' \
    && SW_STORAGE_ES_BULK_ACTIONS=1 \
    SW_CORE_DATA_KEEPER_EXECUTE_PERIOD=1 \
    SW_STORAGE_ES_MONTH_METRIC_DATA_TTL=4 \
    SW_STORAGE_ES_OTHER_METRIC_DATA_TTL=5 \
    SW_STORAGE_ES_FLUSH_INTERVAL=1 \
    SW_RECEIVER_BUFFER_PATH=/tmp/oap/trace_buffer1 \
    SW_SERVICE_MESH_BUFFER_PATH=/tmp/oap/mesh_buffer1 \
    start_oap 'init'

echo 'starting Web app...' \
    && start_webapp '0.0.0.0' 8081

echo "SkyWalking e2e container is ready for tests"

tail -f ${OAP_LOG_DIR}/* \
        ${WEBAPP_LOG_DIR}/* \
        ${ES_HOME}/logs/elasticsearch.log \
        ${ES_HOME}/logs/stdout.log
