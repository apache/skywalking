# Licensed to the Apache Software Foundation (ASF) under one
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

#!/bin/bash

set -e

echo "[Entrypoint] Apache SkyWalking Docker Image"

if [[ "$SW_TELEMETRY" = "so11y" ]]; then
    export SW_RECEIVER_SO11Y=default
    echo "Set SW_RECEIVER_SO11Y to ${SW_RECEIVER_SO11Y}"
fi

EXT_LIB_DIR=/skywalking/ext-libs
EXT_CONFIG_DIR=/skywalking/ext-config

# Override configuration files
cp -vfR ${EXT_CONFIG_DIR}/ config/

CLASSPATH="config:$CLASSPATH"
for i in oap-libs/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done
for i in ${EXT_LIB_DIR}/*.jar
do
    CLASSPATH="$i:$CLASSPATH"
done

set -ex
exec java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap \
     ${JAVA_OPTS} -classpath ${CLASSPATH} org.apache.skywalking.oap.server.starter.OAPServerStartUp "$@"
