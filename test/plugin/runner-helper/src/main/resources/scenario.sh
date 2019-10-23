#!/usr/bin/env bash

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
PRGDIR=`dirname "$PRG"`
[ -z "$SCENARIO_HOME" ] && SCENARIO_HOME=`cd "$PRGDIR" >/dev/null; pwd`

state_house=$1
testcase_name=${scenario_name}-${scenario_version}

status=1
<#noparse>touch ${state_house}/${testcase_name}.RUNNING</#noparse>

${running_script}

<#noparse>
if [[ ${status} -eq 0 ]]; then
  mv ${state_house}/${testcase_name}.RUNNING ${state_house}/${testcase_name}.FINISH
else
  mv ${state_house}/${testcase_name}.RUNNING ${state_house}/${testcase_name}.FAILURE
fi
</#noparse>
