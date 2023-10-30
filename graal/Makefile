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

SHELL := /bin/bash -o pipefail

SW_GRAAL_ROOT := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
CONTEXT = ${SW_GRAAL_ROOT}/dist
DIST ?= apache-skywalking-apm-native-bin.tar.gz
CLI_VERSION ?= 0.12.0 # CLI version inside OAP image should always use an Apache released artifact.

HUB ?= skywalking

TAG ?= latest

DOCKER_BUILD_TOP:=${CONTEXT}/docker_build

NAME := oap

LOAD_OR_PUSH = --load

BUILD_ARGS := $(BUILD_ARGS) --build-arg DIST=$(DIST) --build-arg SKYWALKING_CLI_VERSION=$(CLI_VERSION)

docker.% push.docker.%: $(CONTEXT)/$(DIST) $(SW_GRAAL_ROOT)/docker/%/*
	$(DOCKER_RULE)

define DOCKER_RULE
	mkdir -p $(DOCKER_BUILD_TOP)/$(NAME)
	cp -r $^ $(DOCKER_BUILD_TOP)/$(NAME)
	docker buildx create --use --driver docker-container --name skywalking_main > /dev/null 2>&1 || true
	docker buildx build $(PLATFORMS) $(LOAD_OR_PUSH) \
		--no-cache $(BUILD_ARGS) \
		-t $(HUB)/$(NAME):$(TAG) \
		-t $(HUB)/$(NAME):latest \
		$(DOCKER_BUILD_TOP)/$(NAME)
	docker buildx rm skywalking_main || true
endef
