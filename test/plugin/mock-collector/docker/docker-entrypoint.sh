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

#!/usr/bin/env bash

echo "replace GRPC_BIND_HOST with $GRPC_BIND_HOST"
eval sed -i -e 's/\{GRPC_BIND_HOST\}/$GRPC_BIND_HOST/' /usr/local/skywalking-mock-collector/config/config.properties

echo "replace GRPC_BIND_PORT with $GRPC_BIND_PORT"
eval sed -i -e 's/\{GRPC_BIND_PORT\}/$GRPC_BIND_PORT/' /usr/local/skywalking-mock-collector/config/config.properties

exec "$@"
