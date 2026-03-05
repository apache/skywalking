#!/usr/bin/env bash

#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# This script relies on few environment variables to determine source code package
# behavior, those variables are:
#   RELEASE_VERSION -- The version of this source package.
#   NEXT_RELEASE_VERSION -- The version of the next release.
# For example: RELEASE_VERSION=10.0.0, NEXT_RELEASE_VERSION=10.0.1

set -e -o pipefail

if [ "$RELEASE_VERSION" == "" ] || [ "$NEXT_RELEASE_VERSION" == "" ]; then
  echo "RELEASE_VERSION or NEXT_RELEASE_VERSION environment variable not found."
  echo "Please set the RELEASE_VERSION and NEXT_RELEASE_VERSION."
  echo "For example: export RELEASE_VERSION=10.0.0"
  echo "           : export NEXT_RELEASE_VERSION=10.0.1"
  exit 1
fi

PRODUCT_NAME="apache-skywalking-apm"
TAG=v${RELEASE_VERSION}
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
MVN=${MVN:-./mvnw}

if [ -d "skywalking" ]; then
  rm -rf skywalking
fi

echo "Cloning the repository..."
git clone --recurse-submodules -j4 --depth 1 https://github.com/apache/skywalking.git && cd skywalking

echo "Checking out the release branch ${RELEASE_VERSION}-release..."
git checkout -b ${RELEASE_VERSION}-release

log_file=$(mktemp)
echo "Setting the release version ${RELEASE_VERSION} in pom.xml, log file: ${log_file}"
${MVN} versions:set-property -DgenerateBackupPoms=false -Dproperty=revision -DnewVersion=${RELEASE_VERSION} > ${log_file} 2>&1

echo "Committing the pom.xml changes..."
git add pom.xml
git commit -m "Prepare for release ${RELEASE_VERSION}"

echo "Creating the release tag ${TAG}..."
git tag ${TAG}

echo "Pushing the release tag ${TAG} to the remote repository..."
git push --set-upstream origin ${TAG}

log_file=$(mktemp)
echo "Generating a static version.properties, log file: ${log_file}"
${MVN} -q -pl oap-server/server-starter -am initialize \
       -DgenerateGitPropertiesFilename="$(pwd)/oap-server/server-starter/src/main/resources/version.properties" > ${log_file} 2>&1

echo "Creating the release source artifacts..."
tar czf "${SCRIPT_DIR}"/${PRODUCT_NAME}-${RELEASE_VERSION}-src.tar.gz \
    --exclude .git \
    --exclude .DS_Store \
    --exclude .github \
    --exclude .gitignore \
    --exclude .gitmodules \
    --exclude .mvn/wrapper/maven-wrapper.jar \
    .

log_file=$(mktemp)
echo "Building the release binary artifacts, log file: ${log_file}"
${MVN} install package -DskipTests > ${log_file} 2>&1
mv "${SCRIPT_DIR}"/skywalking/dist/${PRODUCT_NAME}-bin.tar.gz ./${PRODUCT_NAME}-${RELEASE_VERSION}-bin.tar.gz

cd "${SCRIPT_DIR}"
gpg --armor --detach-sig ${PRODUCT_NAME}-${RELEASE_VERSION}-src.tar.gz
gpg --armor --detach-sig ${PRODUCT_NAME}-${RELEASE_VERSION}-bin.tar.gz

shasum -a 512 ${PRODUCT_NAME}-${RELEASE_VERSION}-src.tar.gz > ${PRODUCT_NAME}-${RELEASE_VERSION}-src.tar.gz.sha512
shasum -a 512 ${PRODUCT_NAME}-${RELEASE_VERSION}-bin.tar.gz > ${PRODUCT_NAME}-${RELEASE_VERSION}-bin.tar.gz.sha512
