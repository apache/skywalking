#!/usr/bin/env bash
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

apt-get update && apt-get -y install git

echo 'git clone skyalking-nginx-lua lib from https://github.com/apache/skywalking-nginx-lua.git'

git clone https://github.com/apache/skywalking-nginx-lua.git /usr/share/skywalking-nginx-lua \
  && cd /usr/share/skywalking-nginx-lua \
  && git checkout $SKYWALKING_NINGX_LUA_GIT_COMMIT_ID \
  && ls ./

/usr/bin/openresty -c /var/nginx/conf.d/nginx.conf
sync
