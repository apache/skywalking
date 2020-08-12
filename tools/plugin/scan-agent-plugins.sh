#!/usr/bin/env bash

#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &&cd ../.. && pwd)"

PLUGIN_POSITION_FILE=$SRC_DIR/"plugin-position.txt"
PLUGIN_FILE=$SRC_DIR/"plugin.txt"

find $WORK_DIR/apm-sniffer -name "skywalking-plugin.def"|grep "src/main/resources" > $PLUGIN_POSITION_FILE

cat $PLUGIN_POSITION_FILE | while read LINE
do
cat $LINE|grep -v "#"|grep -v "^$"|awk -F "=" '{print $1}' >> temp.txt
done

cat temp.txt| sort|uniq|awk NF  > $PLUGIN_FILE
rm -rf $PLUGIN_POSITION_FILE && rm -rf temp.txt





