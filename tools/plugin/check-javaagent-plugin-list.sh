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
GENERNATE_PLUGINS_LIST=${SRC_DIR}/genernate-javaagent-plugin-list.txt
MD_PLUGINS_LIST=${SRC_DIR}/md-javaagent-plugin-list.txt

function genernateJavaagentPluginList() {
    position_file="javaagent-position.txt"
    find ${WORK_DIR}/apm-sniffer -name "skywalking-plugin.def"|grep "src/main/resources" > ${position_file}
    cat ${position_file} | while read line
    do
        cat ${line}|grep -v "#"|awk -F "=" '{print $1}' >> temp.txt
    done
    cat temp.txt|sort|uniq|awk NF|grep -E '^[a-z].*'  > ${GENERNATE_PLUGINS_LIST}
    rm -rf temp.txt
}

function getMdJavaagentPluginList() {
    md_javaagent_plugins_file=${WORK_DIR}/docs/en/setup/service-agent/java-agent/Plugin-list.md
    cat  ${md_javaagent_plugins_file}|grep -v "#" |awk -F " " '{ print $2}'|grep -E '^[a-z].*'|sort|uniq|awk NF >${MD_PLUGINS_LIST}
}

genernateJavaagentPluginList
getMdJavaagentPluginList
diff -w -bB -U0 ${MD_PLUGINS_LIST} ${GENERNATE_PLUGINS_LIST}


