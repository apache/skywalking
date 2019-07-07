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

#!/usr/bin/env bash

if test "${MODE}" = "cluster"; then
    original_wd=$(pwd)

    zk_version=3.5.5
    export ZK_HOME=/zk
    wget http://mirror.bit.edu.cn/apache/zookeeper/zookeeper-${zk_version}/apache-zookeeper-${zk_version}-bin.tar.gz -P /tmp/ \
        && tar -zxf /tmp/apache-zookeeper-${zk_version}-bin.tar.gz -C /tmp \
        && mv /tmp/apache-zookeeper-${zk_version}-bin/* ${ZK_HOME} \
        && cp ${ZK_HOME}/conf/zoo_sample.cfg ${ZK_HOME}/conf/zoo.cfg \
        && echo 'export JVMFLAGS="-Xmx256m"' > ${ZK_HOME}/conf/java.env \

    es_version=6.3.2
    export ES_HOME=/es
    wget http://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-oss-${es_version}.tar.gz -P /tmp/ \
        && tar -zxf /tmp/elasticsearch-oss-${es_version}.tar.gz -C /tmp \
        && mv /tmp/elasticsearch-${es_version}/* ${ES_HOME}

    # substitute application.yml to be capable of cluster mode
    cd ${SW_HOME}/config \
        && awk -f /clusterize.awk application.yml > clusterized_app.yml \
        && mv clusterized_app.yml application.yml

    cd ${SW_HOME}/webapp \
        && awk '/^\s+listOfServers/ {gsub("127.0.0.1:12800", "127.0.0.1:12800,127.0.0.1:12801", $0)} {print}' webapp.yml > clusterized_webapp.yml \
        && mv clusterized_webapp.yml webapp.yml

    cd ${original_wd}
fi
