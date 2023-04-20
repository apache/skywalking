#!/bin/bash
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

    curl -X POST "${E2E_ES_HOST}/${INDEX_NAME_PREFIX}0/_doc/?pretty" -H 'Content-Type: application/json' -d'{"message": "GET /search HTTP/1.1 200 '${i}'","userid":"test"}'



    if [ $i -eq 10 ]
    then
      break
    fi
    i=`expr $i + 1`
done

while true
do
      curl -X GET "${E2E_ES_HOST}/${INDEX_NAME_PREFIX}0/_search?size=20&pretty" -H 'Content-Type: application/json' -d'
      {
        "query": {
          "term": {
            "userid": "test"
          }
        }
      }
      '

      curl -X POST "${E2E_ES_HOST}/${INDEX_NAME_PREFIX}0/_search?pretty" -H 'Content-Type: application/json' -d'
      {
        "suggest": {
          "my-suggest-1" : {
            "text" : "test",
            "term" : {
              "field" : "userid"
            }
          }
        }
      }
      '

      curl -X POST "${E2E_ES_HOST}/${INDEX_NAME_PREFIX}0/_search?scroll=1m&pretty" -H 'Content-Type: application/json' -d'
      {
        "size": 100,
        "query": {
          "match": {
            "userid": "test"
          }
        }
      }
      '

      sleep 2
done