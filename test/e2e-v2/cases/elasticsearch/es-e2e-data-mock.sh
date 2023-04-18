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

i=0
while [ $i -lt 10 ]
do
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

    curl -X POST "${E2E_ES_HOST}/${INDEX_NAME_PREFIX}${i}/_doc/?pretty" -H 'Content-Type: application/json' -d'
    {
      "@timestamp": "2099-11-15T13:12:00",
      "message": "GET /search HTTP/1.1 200 1070000",
      "user": {
        "id": "kimchy"
      }
    }
    '

    if [ $i -eq 10 ]
    then
      break
    fi
    i=`expr $i + 1`
done