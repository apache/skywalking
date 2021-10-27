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

SW_ROOT := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
CONTEXT ?= ${SW_ROOT}/dist
SKIP_TEST ?= false
DIST ?= apache-skywalking-apm-bin.tar.gz
CLI_VERSION ?= 0.9.0 # CLI version inside OAP image should always use an Apache released artifact.

init:
	cd $(SW_ROOT) && git submodule update --init --recursive

.PHONY: build.all build.backend build.ui build.docker

build.all:
	cd $(SW_ROOT) && ./mvnw --batch-mode clean package -Dmaven.test.skip=$(SKIP_TEST)

build.backend:
	cd $(SW_ROOT) && ./mvnw --batch-mode clean package -Dmaven.test.skip=$(SKIP_TEST) -Pbackend,dist

build.ui:
	cd $(SW_ROOT) && ./mvnw --batch-mode clean package -Dmaven.test.skip=$(SKIP_TEST) -Pui,dist

DOCKER_BUILD_TOP:=${CONTEXT}/docker_build

HUB ?= skywalking
OAP_NAME ?= oap
UI_NAME ?= ui
TAG ?= latest

.SECONDEXPANSION: #allow $@ to be used in dependency list

.PHONY: docker docker.all docker.oap

docker: init build.all docker.all

DOCKER_TARGETS:=docker.oap docker.ui

docker.all: $(DOCKER_TARGETS)

ifneq ($(SW_OAP_BASE_IMAGE),)
  BUILD_ARGS := $(BUILD_ARGS) --build-arg BASE_IMAGE=$(SW_OAP_BASE_IMAGE)
endif

BUILD_ARGS := $(BUILD_ARGS) --build-arg DIST=$(DIST) --build-arg SKYWALKING_CLI_VERSION=$(CLI_VERSION)

docker.oap: $(CONTEXT)/$(DIST)
docker.oap: $(SW_ROOT)/docker/oap/Dockerfile.oap
docker.oap: $(SW_ROOT)/docker/oap/docker-entrypoint.sh
docker.oap: $(SW_ROOT)/docker/oap/log4j2.xml
docker.oap: NAME = $(OAP_NAME)
	$(call DOCKER_RULE, $(DOCKER_BUILD_TOP)/$@, $^)

docker.ui: $(CONTEXT)/$(DIST)
docker.ui: $(SW_ROOT)/docker/ui/Dockerfile.ui
docker.ui: $(SW_ROOT)/docker/ui/docker-entrypoint.sh
docker.ui: $(SW_ROOT)/docker/ui/logback.xml
docker.oap: NAME = $(UI_NAME)
	$(call DOCKER_RULE, $(DOCKER_BUILD_TOP)/$@, $^)

# $@ is the name of the target
# $^ the name of the dependencies for the target
# Rule Steps #
##############
# 1. Make a directory $(DOCKER_BUILD_TOP)/%@
# 2. This rule uses cp to copy all dependency filenames into into $(DOCKER_BUILD_TOP)/$@
# 3. This rule then changes directories to $(DOCKER_BUID_TOP)/$@
# 4. This rule runs $(BUILD_PRE) prior to any docker build and only if specified as a dependency variable
# 5. This rule finally runs docker build passing $(BUILD_ARGS) to docker if they are specified as a dependency variable

# DOCKER_RULE=time (mkdir -p $(DOCKER_BUILD_TOP)/$@ && cp -r $^ $(DOCKER_BUILD_TOP)/$@ && cd $(DOCKER_BUILD_TOP)/$@ && $(BUILD_PRE) docker build --no-cache $(BUILD_ARGS) -t $(HUB)/$(NAME):$(TAG) -f Dockerfile$(suffix $@) .)
define DOCKER_RULE
    mkdir -p $(1)
    cp -r $(2) $(1)
    cd $(1) && \
    $(BUILD_PRE) docker buildx build --platform linux/arm64,linux/amd64 --no-cache $(BUILD_ARGS) -t $(HUB)/$(NAME):$(TAG) -f Dockerfile$(suffix $@) .
endef


