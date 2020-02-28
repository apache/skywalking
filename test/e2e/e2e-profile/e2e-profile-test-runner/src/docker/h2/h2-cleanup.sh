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

h2_pid=`ps -ef | grep java | grep 'org.h2.tools.Server' | awk '{print $2}'`
if (${task_id}); then
  echo "Closing h2 storage(pid:$h2_pid)..."

  for PID in $h2_pid ; do
      kill $PID > /dev/null 2>&1
  done
fi

# clean up h2 storage files
if [ -d /tmp/h2 ]; then
  rm -rf /tmp/h2
fi