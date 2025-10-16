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

FROM ghcr.io/apache/skywalking-go/skywalking-go:${SW_AGENT_GO_COMMIT}-go1.19 AS base

ENV CGO_ENABLED=0
ENV GO111MODULE=on

WORKDIR /go-service
COPY .. /go-service

RUN go mod tidy && go build -toolexec="skywalking-go-agent" -a -o service

FROM alpine:3.10

COPY --from=base /go-service/service /service

ENTRYPOINT ["/service"]