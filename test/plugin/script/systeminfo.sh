#!/bin/bash
#
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

hostname=$(hostname)
ip_address=$(hostname --ip-address)
echo -e "hostname: ${hostname}(${ip_address}) \n"

cpu_usage_rate=$(grep 'cpu ' /proc/stat | awk '{usage=($2+$4)*100/($2+$4+$5)} END {print usage "%"}')
load_average=$(uptime |grep -o --color=never "load average:.*")

echo -e "CPU usage rate: ${cpu_usage_rate}, ${load_average} \n"

runnings=$(docker ps -q |wc -l)
all=$(docker ps -aq |wc -l)
volumes=$(docker volume ls |wc -l)
danglings=$(docker images -qf dangling=true |wc -l)

echo -e "docker stats: runnings=${runnings:=0}, all=${all:=0}, volumes=${volumes:=0}, dangling images:${danglings}\n"

echo -e "Memory usage:"
free -m

echo "Disk usage:"
df -h