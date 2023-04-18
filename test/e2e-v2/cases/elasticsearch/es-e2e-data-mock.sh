#!/bin/sh
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -e

E2E_ES_HOST="es:9200"

INDEX_NAME_PREFIX="e2e-index-"

for (( i = 0; i < 10; i++ )); do
    curl -X PUT "${E2E_ES_HOST}/${INDEX_NAME_PREFIX}${i}?pretty" -H 'Content-Type: application/json' -d'
    {
      "settings": {
        "index": {
          "number_of_shards": 3,
          "number_of_replicas": 2
        }
      }
    }
    '
done

for (( i = 0; i < 10; i++ )); do
    curl -X POST "${E2E_ES_HOST}/_bulk?pretty" -H 'Content-Type: application/json' -d'
    { "index" : { "_index" : "'"${INDEX_NAME_PREFIX}${i}"'", "_id" : "1" } }
    { "field1" : "value1" }
    { "delete" : { "_index" : "'"${INDEX_NAME_PREFIX}${i}"'", "_id" : "2" } }
    { "create" : { "_index" : "'"${INDEX_NAME_PREFIX}${i}"'", "_id" : "3" } }
    { "field1" : "value3" }
    { "update" : {"_id" : "1", "_index" : "'"${INDEX_NAME_PREFIX}${i}"'"} }
    { "doc" : {"field2" : "value2"} }
    '
done

