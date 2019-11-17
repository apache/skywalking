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

#!/usr/bin/awk -f

BEGIN {
    in_cluster_section=0;
    in_cluster_zk_section=0;

    in_storage_section=0;
    in_storage_es_section=0;
    in_storage_h2_section=0;
}

{
    if (in_cluster_section == 0) {
        in_cluster_section=$0 ~ /^cluster:$/
    } else {
        in_cluster_section=$0 ~ /^(#|\s{2})/
    }
    if (in_storage_section == 0) {
        in_storage_section=$0 ~ /^storage:$/
    } else {
        in_storage_section=$0 ~ /^(#|\s{2})/
    }

    if (in_cluster_section == 1) {
        # in the cluster: section now
        # disable standalone module
        if ($0 ~ /^  standalone:$/) {
            print "#" $0
        } else {
            if (in_cluster_zk_section == 0) {
                in_cluster_zk_section=$0 ~ /^#?\s+zookeeper:$/
            } else {
                in_cluster_zk_section=$0 ~ /^(#\s{4}|\s{2})/
            }
            if (in_cluster_zk_section == 1) {
                # in the cluster.zookeeper section now
                # uncomment zk config
                gsub("^#", "", $0)
                print
            } else {
                print
            }
        }
    } else if (in_storage_section == 1) {
        # in the storage: section now
        # disable h2 module
        if (in_storage_es_section == 0) {
            if (ENVIRON["ES_VERSION"] ~ /^6.+/) {
                in_storage_es_section=$0 ~ /^#?\s+elasticsearch:$/
            } else if (ENVIRON["ES_VERSION"] ~ /^7.+/) {
                in_storage_es_section=$0 ~ /^#?\s+elasticsearch7:$/
            }
        } else {
            in_storage_es_section=$0 ~ /^#?\s{4}/
        }
        if (in_storage_h2_section == 0) {
            in_storage_h2_section=$0 ~ /^#?\s+h2:$/
        } else {
            in_storage_h2_section=$0 ~ /^#?\s{4}/
        }
        if (in_storage_es_section == 1) {
            # in the storage.elasticsearch section now
            # uncomment es config
            gsub("^#", "", $0)
            print
        } else if (in_storage_h2_section == 1) {
            # comment out h2 config
            if ($0 !~ /^#/) {
                print "#" $0
            } else {
                print
            }
        } else {
            print
        }
    } else {
        print
    }
}

