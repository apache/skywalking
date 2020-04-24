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

FROM tomcat:8.5.42-jdk8-openjdk
MAINTAINER zhangxin@apache.org
WORKDIR /usr/local/skywalking/tools

ENV CATALINA_OPTS ""

COPY run.sh /
COPY catalina.sh /usr/local/tomcat/bin/

COPY skywalking-validator.jar /usr/local/skywalking/tools/skywalking-validator-tools.jar
COPY skywalking-mock-collector.tar.gz /usr/local/skywalking/tools
RUN tar -xvf skywalking-mock-collector.tar.gz -C /usr/local/skywalking/tools

RUN chmod +x /usr/local/tomcat/bin/catalina.sh && chmod +x /run.sh

WORKDIR /usr/local/skywalking/scenario
CMD ["/run.sh"]
