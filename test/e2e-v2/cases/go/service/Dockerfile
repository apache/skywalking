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

ARG GO_VERSION=1.19
ARG SW_AGENT_GO_REPO_URL=https://github.com/apache/skywalking-go.git
ARG SW_AGENT_GO_COMMIT
FROM golang:${GO_VERSION}-alpine AS builder

ARG SW_AGENT_GO_REPO_URL
ARG SW_AGENT_GO_COMMIT

RUN apk add --no-cache git make bash

WORKDIR /opt
RUN git clone ${SW_AGENT_GO_REPO_URL} skywalking-go
WORKDIR /opt/skywalking-go
RUN git checkout ${SW_AGENT_GO_COMMIT} && make build

WORKDIR /go-service
COPY .. /go-service

ENV CGO_ENABLED=0
ENV GO111MODULE=on

RUN echo '' >> go.mod && echo 'replace github.com/apache/skywalking-go => /opt/skywalking-go' >> go.mod

RUN \
  TARGET_GOOS=$(go env GOOS) && \
  TARGET_GOARCH=$(go env GOARCH) && \
  COMMIT_SHORT_HASH=$(echo "${SW_AGENT_GO_COMMIT}" | cut -c1-7) && \
  AGENT_FILENAME="skywalking-go-agent-${COMMIT_SHORT_HASH}-${TARGET_GOOS}-${TARGET_GOARCH}" && \
  AGENT_TOOL_PATH="/opt/skywalking-go/bin/${AGENT_FILENAME}" && \
  echo "INFO: Using ARG SW_AGENT_GO_COMMIT=${SW_AGENT_GO_COMMIT}, COMMIT_SHORT_HASH=${COMMIT_SHORT_HASH}" && \
  echo "INFO: Target GOOS from 'go env': ${TARGET_GOOS}" && \
  echo "INFO: Target GOARCH from 'go env': ${TARGET_GOARCH}" && \
  echo "INFO: Attempting to use agent tool path: ${AGENT_TOOL_PATH}" && \
  if [ ! -f "${AGENT_TOOL_PATH}" ]; then \
    echo "ERROR: Agent executable not found at ${AGENT_TOOL_PATH}" >&2; \
    echo "ERROR: Please verify the SW_AGENT_GO_COMMIT, and check the output of 'make build' in skywalking-go." >&2; \
    echo "Listing contents of /opt/skywalking-go/bin/:" >&2; \
    ls -l /opt/skywalking-go/bin/ >&2; \
    exit 1; \
    fi && \
  go mod tidy && go build -toolexec="${AGENT_TOOL_PATH}" -a -o service

FROM alpine:3.11

COPY --from=builder /go-service/service /service

ENTRYPOINT ["/service"]