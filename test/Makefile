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
E2E_MOCK_LLM_SERVER_NAME = e2e-mock-llm-server
E2E_SPRING_AI_SERVICE_NAME = e2e-spring-ai-service
TRACE_MOCKER_NAME = trace-mocker

.PHONY: init
init:
	cd $(SW_ROOT) && git submodule update --init --recursive

.PHONY: build.e2e-service
build.e2e-service:
	cd $(SW_ROOT) && ./mvnw --batch-mode -f $(CONTEXT)/pom.xml clean package

DOCKER_TARGETS:=docker.e2e-service-provider docker.e2e-service-consumer docker.e2e-mock-llm-server docker.e2e-spring-ai-service docker.trace-mocker

# e2e-spring-ai-service is a Spring Boot 4 / Spring AI module, so the e2e reactor only builds it
# under its jdk-17 profile (<jdk>[17,)</jdk> in java-test-service/pom.xml). On an older JDK the
# module is skipped, and DOCKER_RULE then either fails on the missing jar or -- worse -- packages
# one left over from an earlier JDK 17 build, since `clean` never reaches a module outside the
# reactor. Refuse up front rather than let that reach a case: compose bind-mounts the jar by path,
# so a missing one makes Docker create a DIRECTORY in its place and the container dies with
# "Invalid or corrupt jarfile", which names neither the module nor the JDK.
# Scoped to the goals that need the module, so the other images stay buildable by name.
JAVA_MAJOR := $(shell $(if $(JAVA_HOME),$(JAVA_HOME)/bin/java,java) -version 2>&1 \
                | head -1 | cut -d'"' -f2 | sed -E 's/^1\.//' | cut -d'.' -f1)
SPRING_AI_GOALS := docker docker.e2e-service docker.push-e2e-service \
                   docker.e2e-spring-ai-service push.docker.e2e-spring-ai-service
ifneq ($(filter $(SPRING_AI_GOALS),$(MAKECMDGOALS)),)
ifneq ($(shell test "$(JAVA_MAJOR)" -ge 17 2>/dev/null && echo yes),yes)
$(error e2e-spring-ai-service requires JDK 17+ for Spring AI / Spring Boot 4; found $(if $(JAVA_MAJOR),Java $(JAVA_MAJOR),no java on PATH). Set JAVA_HOME to a 17+ JDK, or build the other images by name, e.g. make docker.e2e-service-provider)
endif
endif

.PHONY: docker docker.e2e-service
docker: init build.e2e-service docker.e2e-service

%.e2e-service-provider: NAME = $(E2E_SERVICE_PROVIDER_NAME)
%.e2e-service-consumer: NAME = $(E2E_SERVICE_CONSUMER_NAME)
%.e2e-mock-llm-server: NAME = $(E2E_MOCK_LLM_SERVER_NAME)
%.e2e-spring-ai-service: NAME = $(E2E_SPRING_AI_SERVICE_NAME)
# Spring AI requires Java 17; the shared BASE_IMAGE above is a Java 8 JRE.
%.e2e-spring-ai-service: BASE_IMAGE = eclipse-temurin:17-jre
%.trace-mocker: NAME = $(TRACE_MOCKER_NAME)

docker.%: PLATFORMS =
docker.%: LOAD_OR_PUSH = --load
push.%: PLATFORMS = --platform linux/amd64,linux/arm64
push.%: LOAD_OR_PUSH = --push

push.docker.e2e-service-% docker.e2e-service-%: $(CONTEXT)/Dockerfile
	$(DOCKER_RULE)

push.docker.e2e-mock-llm-server docker.e2e-mock-llm-server: $(CONTEXT)/Dockerfile
	$(DOCKER_RULE)

push.docker.e2e-spring-ai-service docker.e2e-spring-ai-service: $(CONTEXT)/Dockerfile
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