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

AGENT_VERSION=6.5.0

ls /sw && echo 'Remove SkyWalking agent directory' && rm -rf /sw/agent && ls /sw

echo 'Download SkyWalking 6.x...' \
  && curl -o /tmp/sw.tar.gz https://archive.apache.org/dist/skywalking/${AGENT_VERSION}/apache-skywalking-apm-${AGENT_VERSION}.tar.gz \
  && tar -C / -zxvf /tmp/sw.tar.gz \
  && ls /apache-skywalking-apm-bin \
  && mv /apache-skywalking-apm-bin/agent /sw/agent \
  && ls /sw
