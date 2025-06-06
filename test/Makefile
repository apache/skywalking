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
SW_ROOT := $(shell dirname $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST)))))
CONTEXT := $(SW_ROOT)/test/e2e-v2/java-test-service
DOCKER_BUILD_TOP:=${SW_ROOT}/dist/docker_build
HUB ?= ghcr.io/apache/skywalking
TAG ?= latest
JRE ?= 8u322-b06-jre-focal
BASE_IMAGE ?= eclipse-temurin:$(JRE)

E2E_SERVICE_PROVIDER_NAME = e2e-service-provider
E2E_SERVICE_CONSUMER_NAME = e2e-service-consumer
TRACE_MOCKER_NAME = trace-mocker

.PHONY: init
init:
	cd $(SW_ROOT) && git submodule update --init --recursive

.PHONY: build.e2e-service
build.e2e-service:
	cd $(SW_ROOT) && ./mvnw --batch-mode -f $(CONTEXT)/pom.xml clean package

DOCKER_TARGETS:=docker.e2e-service-provider docker.e2e-service-consumer docker.trace-mocker

.PHONY: docker docker.e2e-service
docker: init build.e2e-service docker.e2e-service

%.e2e-service-provider: NAME = $(E2E_SERVICE_PROVIDER_NAME)
%.e2e-service-consumer: NAME = $(E2E_SERVICE_CONSUMER_NAME)
%.trace-mocker: NAME = $(TRACE_MOCKER_NAME)

docker.%: PLATFORMS =
docker.%: LOAD_OR_PUSH = --load
push.%: PLATFORMS = --platform linux/amd64,linux/arm64
push.%: LOAD_OR_PUSH = --push

push.docker.e2e-service-% docker.e2e-service-%: $(CONTEXT)/Dockerfile
	$(DOCKER_RULE)

push.docker.trace-mocker docker.trace-mocker: $(CONTEXT)/Dockerfile
	$(DOCKER_RULE)

docker.e2e-service: $(DOCKER_TARGETS)
docker.push-e2e-service: $(DOCKER_TARGETS:%=push.%)

# $^ the name of the dependencies for the target
# Rule Steps #
##############
# 1. Make a directory $(DOCKER_BUILD_TOP)/$(NAME)
# 2. This rule uses cp to copy all dependency filenames into into $(DOCKER_BUILD_TOP/$(NAME)
# 3. This rule finally runs docker build passing $(BUILD_ARGS) to docker if they are specified as a dependency variable

define DOCKER_RULE
	mkdir -p $(DOCKER_BUILD_TOP)/$(NAME)
	cp -r $^ $(SW_ROOT)/test/e2e-v2/java-test-service/$(NAME)/target/$(NAME)-2.0.0.jar $(DOCKER_BUILD_TOP)/$(NAME)
	docker buildx create --use --driver docker-container --name skywalking_test_service > /dev/null 2>&1 || true
	docker buildx build $(PLATFORMS) $(LOAD_OR_PUSH) \
		--no-cache \
		--build-arg SERVICE_JAR="./$(NAME)-2.0.0.jar" \
		--build-arg BASE_IMAGE=$(BASE_IMAGE) \
		-t $(HUB)/$(NAME):$(TAG) \
		-t $(HUB)/$(NAME):latest \
		$(DOCKER_BUILD_TOP)/$(NAME)
	docker buildx rm skywalking_test_service || true
endef