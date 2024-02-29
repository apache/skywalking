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

ARG BASE_IMAGE='eclipse-temurin:11-jre'

FROM $BASE_IMAGE

ENV SKYWALKING_HOME=/skywalking

ENV PATH=$SKYWALKING_HOME/bin:$PATH

WORKDIR $SKYWALKING_HOME

ENV JAVA_OPTS=" -Xms2G "

ARG DIST

COPY "$DIST" .

RUN set -ex; \
    tar -xzf "$DIST" --strip 1; \
    rm -rf "$DIST"; \
    rm -rf "config/log4j2.xml"; \
    rm -rf "bin"; \
    rm -rf "webapp"; \
    rm -rf "agent"; \
    mkdir "bin";

COPY log4j2.xml config/
COPY docker-entrypoint.sh .

RUN mkdir ext-config; \
    mkdir ext-libs;

EXPOSE 12800 11800 1234

ENTRYPOINT ["bash", "docker-entrypoint.sh"]
