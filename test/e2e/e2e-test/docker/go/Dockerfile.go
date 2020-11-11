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

FROM golang:1.12 AS builder

ARG COMMIT_HASH=38c3b84741dd6c0609965e9df0fcc633915d3ea5
ARG GO2SKY_CODE=${COMMIT_HASH}.tar.gz
ARG GO2SKY_CODE_URL=https://github.com/SkyAPM/go2sky/archive/${GO2SKY_CODE}

ENV CGO_ENABLED=0
ENV GO111MODULE=on

WORKDIR /go2sky

ADD ${GO2SKY_CODE_URL} .
RUN tar -xf ${GO2SKY_CODE} --strip 1
RUN rm ${GO2SKY_CODE}

WORKDIR /go2sky/test/e2e/example-server
RUN go build -o main

FROM alpine:3.10

COPY --from=builder /go2sky/test/e2e/example-server/main .
ENTRYPOINT ["/main"]