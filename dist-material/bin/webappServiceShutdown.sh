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
[ -z "$SW_HOME" ] && SW_HOME=$(cd "$PRGDIR/.." > /dev/null || exit 1; pwd)

UI_PID_FILE="${SW_HOME}/bin/ui.pid"

if [ -f $UI_PID_FILE ]; then
  kill -9 $(cat "$UI_PID_FILE")
  rm $UI_PID_FILE
  echo 'SkyWalking UI stopped successfully!'
else
  echo 'SkyWalking UI not exist(could not find file $UI_PID_FILE)!'
fi