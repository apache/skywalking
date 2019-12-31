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

# in order to make it easier to restart the OAP (by executing the restart script) from outside (container),
# we'll expose a tcp port and whenever we receive a message on that port, we'll restart the OAP server,
# socat will help on this to execute the script when receiving a message on that port

apt-get update && apt-get -y install socat

# socat will execute the command in a new shell, thus won't catch the original functions' declarations
# so we'll put the restart command in a script file

echo '
    ps -ef | grep -v grep | grep oap.logDir | awk '"'"'{print $2}'"'"' | xargs --no-run-if-empty kill -9
    rm -rf /tmp/oap/trace_buffer1
    rm -rf /tmp/oap/mesh_buffer1
    echo "restarting OAP server..." \
        && SW_RECEIVER_BUFFER_PATH=/tmp/oap/trace_buffer1 \
        && SW_SERVICE_MESH_BUFFER_PATH=/tmp/oap/mesh_buffer1 \
        && cd /sw \
        && bash bin/oapService.sh > /dev/null 2>&1 &
' > /usr/bin/restart_oap

sync

chmod +x /usr/bin/restart_oap

sync
