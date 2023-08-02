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

FROM eclipse-temurin:11-jre-alpine

ENV JAVA_OPTS=" -Xms256M " \
    SW_OAP_ADDRESS="http://127.0.0.1:12800" \
    SW_ZIPKIN_ADDRESS="http://127.0.0.1:9412"

WORKDIR skywalking

ARG DIST
COPY "$DIST" .

RUN tar -xzf "$DIST" --strip 1; \
    rm -rf "$DIST"; \
    rm -rf "config"; \
    rm -rf "bin"; \
    rm -rf "oap-libs"; \
    rm -rf "agent";

COPY docker-entrypoint.sh .
COPY log4j2.xml webapp/

EXPOSE 8080

ENTRYPOINT ["bash", "docker-entrypoint.sh"]
