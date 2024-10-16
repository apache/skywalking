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

# This script is used to test the Kong plugin with the data mock service.

# Add global plugins
curl -i -X POST http://kong-1:8001/plugins \
  --data name=prometheus \
  --data config.per_consumer=true \
  --data config.status_code_metrics=true \
  --data config.ai_metrics=true \
  --data config.latency_metrics=true \
  --data config.bandwidth_metrics=true \
  --data config.upstream_health_metrics=true
curl -i -X POST http://kong-1:8001/plugins \
  --data name=rate-limiting \
  --data config.minute=5 \
  --data config.policy=local

# Add a mock service and route
curl -i -s -X POST http://kong-1:8001/services \
  --data name=sw_service \
  --data url='http://httpbin.konghq.com'
curl -i -X POST http://kong-1:8001/services/sw_service/routes \
  --data 'paths[]=/mock' \
  --data name=sw_route

while true
do
  curl -s -i http://kong-1:8000/mock/anything > /dev/null
  sleep 5
done