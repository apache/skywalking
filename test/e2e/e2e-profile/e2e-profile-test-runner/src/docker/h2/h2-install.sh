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

# Download and start H2 server
H2_RELEASE_DATE=2017-06-10
curl -L http://www.h2database.com/h2-$H2_RELEASE_DATE.zip -o /tmp/h2-$H2_RELEASE_DATE.zip
[[ $? -ne 0 ]] && echo "Fail to download h2: ${H2_RELEASE_DATE}." && exit 1

# unzip h2 and run it
cd /tmp
unzip h2-$H2_RELEASE_DATE.zip \
  && rm -f h2-$H2_RELEASE_DATE.zip \
  && mkdir -p /tmp/h2/data

java -cp /tmp/h2/bin/h2*.jar org.h2.tools.Server -tcp -tcpAllowOthers -tcpPort 1521 -baseDir /tmp/h2/data
