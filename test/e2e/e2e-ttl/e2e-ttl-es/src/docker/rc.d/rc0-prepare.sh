#!/usr/bin/env bash
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

apt-get update && apt-get install -y gawk

original_wd=$(pwd)

# substitute application.yml to be capable of es mode
cd ${SW_HOME}/config \
    && gawk -f /es_storage.awk application.yml > es_storage_app.yml \
    && mv es_storage_app.yml application.yml \
    && sed '/<Loggers>/a<logger name="org.apache.skywalking.oap.server.storage" level="DEBUG"/>' log4j2.xml > log4j2debuggable.xml \
    && mv log4j2debuggable.xml log4j2.xml

cd ${original_wd}