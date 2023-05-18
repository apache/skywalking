#!/bin/bash
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

len=$(/usr/local/bin/redis-cli -h redis_1 slowlog len)

if [[ $len -gt 0 ]]; then
   
    result=$(/usr/local/bin/redis-cli -h redis_1 slowlog get $len)

    single_line_log="$(echo "$result" | tr '\n' ' ')"
    processed_result=$(echo "$single_line_log" | sed  's/\([0-9]\{1,3\}\.\)\{3\}[0-9]\{1,3\}:[0-9]\{1,5\}/&\n/g')
    echo "$processed_result" >> /scripts/slowlog.log
fi
/usr/local/bin/redis-cli -h redis_1 slowlog reset
