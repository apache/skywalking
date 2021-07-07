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

export SW_ROOT := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))

export SW_OUT:=${SW_ROOT}/dist

SKIP_TEST?=false

init:
	cd $(SW_ROOT) && git submodule update --init --recursive

.PHONY: build.all build.agent build.backend build.ui build.docker

build.all:
	cd $(SW_ROOT) && ./mvnw --batch-mode clean package -Dmaven.test.skip=$(SKIP_TEST)

build.agent:
	cd $(SW_ROOT) && ./mvnw --batch-mode clean package -Dmaven.test.skip=$(SKIP_TEST) -Pagent,dist

build.backend:
	cd $(SW_ROOT) && ./mvnw --batch-mode clean package -Dmaven.test.skip=$(SKIP_TEST) -Pbackend,dist

build.ui:
	cd $(SW_ROOT) && ./mvnw --batch-mode clean package -Dmaven.test.skip=$(SKIP_TEST) -Pui,dist

DOCKER_BUILD_TOP:=${SW_OUT}/docker_build

HUB?=skywalking

TAG?=latest

ES_VERSION?=es6

.SECONDEXPANSION: #allow $@ to be used in dependency list

.PHONY: docker docker.all docker.oap

docker: init build.all docker.all

DOCKER_TARGETS:=docker.oap docker.ui docker.agent

docker.all: $(DOCKER_TARGETS)

ifeq ($(ES_VERSION),es7)
  DIST_NAME := apache-skywalking-apm-bin-es7
else
  DIST_NAME := apache-skywalking-apm-bin
endif

ifneq ($(SW_OAP_BASE_IMAGE),)
  BUILD_ARGS := $(BUILD_ARGS) --build-arg BASE_IMAGE=$(SW_OAP_BASE_IMAGE)
endif

BUILD_ARGS := $(BUILD_ARGS) --build-arg DIST_NAME=$(DIST_NAME)

docker.oap: $(SW_OUT)/$(DIST_NAME).tar.gz
docker.oap: $(SW_ROOT)/docker/oap/Dockerfile.oap
docker.oap: $(SW_ROOT)/docker/oap/docker-entrypoint.sh
docker.oap: $(SW_ROOT)/docker/oap/log4j2.xml
		$(DOCKER_RULE)

docker.ui: $(SW_OUT)/apache-skywalking-apm-bin.tar.gz
docker.ui: $(SW_ROOT)/docker/ui/Dockerfile.ui
docker.ui: $(SW_ROOT)/docker/ui/docker-entrypoint.sh
docker.ui: $(SW_ROOT)/docker/ui/logback.xml
		$(DOCKER_RULE)

docker.agent: $(SW_OUT)/apache-skywalking-apm-bin.tar.gz
docker.agent: $(SW_ROOT)/docker/agent/Dockerfile.agent
		$(DOCKER_RULE)


# $@ is the name of the target
# $^ the name of the dependencies for the target
# Rule Steps #
##############
# 1. Make a directory $(DOCKER_BUILD_TOP)/%@
# 2. This rule uses cp to copy all dependency filenames into into $(DOCKER_BUILD_TOP/$@
# 3. This rule then changes directories to $(DOCKER_BUID_TOP)/$@
# 4. This rule runs $(BUILD_PRE) prior to any docker build and only if specified as a dependency variable
# 5. This rule finally runs docker build passing $(BUILD_ARGS) to docker if they are specified as a dependency variable

DOCKER_RULE=time (mkdir -p $(DOCKER_BUILD_TOP)/$@ && cp -r $^ $(DOCKER_BUILD_TOP)/$@ && cd $(DOCKER_BUILD_TOP)/$@ && $(BUILD_PRE) docker build --no-cache $(BUILD_ARGS) -t $(HUB)/$(subst docker.,,$@):$(TAG) -f Dockerfile$(suffix $@) .)

# for each docker.XXX target create a push.docker.XXX target that pushes
# the local docker image to another hub
# a possible optimization is to use tag.$(TGT) as a dependency to do the tag for us
$(foreach TGT,$(DOCKER_TARGETS),$(eval push.$(TGT): | $(TGT) ; \
	time (docker push $(HUB)/$(subst docker.,,$(TGT)):$(TAG))))

# create a DOCKER_PUSH_TARGETS that's each of DOCKER_TARGETS with a push. prefix
DOCKER_PUSH_TARGETS:=
$(foreach TGT,$(DOCKER_TARGETS),$(eval DOCKER_PUSH_TARGETS+=push.$(TGT)))

# Will build and push docker images.
docker.push: $(DOCKER_PUSH_TARGETS)


