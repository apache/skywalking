#!/usr/bin/env sh
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



PRG="$0"
PRGDIR=$(dirname "$PRG")
[ -z "$OAP_HOME" ] && OAP_HOME=$(cd "$PRGDIR/.." > /dev/null || exit 1; pwd)

OAP_LOG_DIR="${OAP_LOG_DIR:-${OAP_HOME}/logs}"

if [ ! -d "${OAP_LOG_DIR}" ]; then
    mkdir -p "${OAP_LOG_DIR}"
fi

export SW_CONFIG_PATHS=${OAP_HOME}/config

${OAP_HOME}/image/skywalking-oap-native
