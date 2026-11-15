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

SHELL = /bin/bash -eo pipefail

CHART_DIR = chart
VERSION = $(shell cat ${CHART_DIR}/Chart.yaml | grep '^version: ' | awk '{print $$2}')
CHART_NAME = $(shell cat ${CHART_DIR}/Chart.yaml | grep '^name: ' | awk '{print $$2}')

RELEASE_SRC = ${CHART_NAME}-${VERSION}-src

prepare:
	cp -R NOTICE ${CHART_DIR}/NOTICE
	cp -R LICENSE ${CHART_DIR}/LICENSE

package: prepare
	helm dep up ${CHART_DIR}
	helm package ${CHART_DIR}
	rm -rf ${CHART_DIR}/NOTICE
	rm -rf ${CHART_DIR}/LICENSE

clean:
	rm -rf bin/ \
	rm -rf ${CHART_DIR}/NOTICE \
	rm -rf ${CHART_DIR}/LICENSE \
	rm -rf ${CHART_DIR}/Chart.lock \
	rm -rf ${CHART_DIR}/charts \
	rm -rf ${CHART_NAME}-${VERSION}.tgz \
	rm -rf ${CHART_NAME}-${VERSION}.tgz.asc \
	rm -rf ${CHART_NAME}-${VERSION}.tgz.sha512 \
	rm -rf ${RELEASE_SRC}.tgz \
	rm -rf ${RELEASE_SRC}.tgz.asc \
	rm -rf ${RELEASE_SRC}.tgz.sha512

release-src:
	mkdir -p build && tar -zcvf build/$(RELEASE_SRC).tgz \
	--exclude bin \
	--exclude .git \
	--exclude .idea \
	--exclude .gitignore \
	--exclude .DS_Store \
	--exclude .github \
	--exclude build \
	.
	mv build/$(RELEASE_SRC).tgz . && rm -rf build

release: release-src package
	gpg --batch --yes --armor --detach-sig $(RELEASE_SRC).tgz
	shasum -a 512 $(RELEASE_SRC).tgz > $(RELEASE_SRC).tgz.sha512
	gpg --batch --yes --armor --detach-sig $(CHART_NAME)-$(VERSION).tgz
	shasum -a 512 $(CHART_NAME)-$(VERSION).tgz > $(CHART_NAME)-$(VERSION).tgz.sha512

publish: package
	helm push ${CHART_NAME}-${VERSION}.tgz oci://registry-1.docker.io/apache