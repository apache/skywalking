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
# For example: RELEASE_VERSION=5.0.0-alpha


RELEASE_VERSION=${RELEASE_VERSION}
TAG_NAME=v${RELEASE_VERSION}
PRODUCT_NAME="apache-skywalking-apm"

echo "Release version "${RELEASE_VERSION}
echo "Source tag "${TAG_NAME}

if [ "$RELEASE_VERSION" == "" ]; then
  echo "RELEASE_VERSION environment variable not found, Please setting the RELEASE_VERSION."
  echo "For example: export RELEASE_VERSION=5.0.0-alpha"
  exit 1
fi

echo "Creating source package"

PRODUCT_NAME=${PRODUCT_NAME}-${RELEASE_VERSION}

rm -rf ${PRODUCT_NAME}
mkdir ${PRODUCT_NAME}

git clone https://github.com/apache/skywalking.git ./${PRODUCT_NAME}
cd ${PRODUCT_NAME}

TAG_EXIST=`git tag -l ${TAG_NAME} | wc -l`

if [ ${TAG_EXIST} -ne 1 ]; then
    echo "Could not find the tag named" ${TAG_NAME}
    exit 1
fi

git checkout ${TAG_NAME}

git submodule init
git submodule update

cd ..

tar czf ${PRODUCT_NAME}-src.tgz \
    --exclude ${PRODUCT_NAME}/.git/ --exclude ${PRODUCT_NAME}/.DS_Store/ \
    --exclude ${PRODUCT_NAME}/.github/ --exclude ${PRODUCT_NAME}/.gitignore/ \
    --exclude ${PRODUCT_NAME}/.gitmodules/ \
    --exclude ${PRODUCT_NAME}/skywalking-ui/.git/ --exclude ${PRODUCT_NAME}/skywalking-ui/.DS_Store/ \
    --exclude ${PRODUCT_NAME}/skywalking-ui/.github/ --exclude ${PRODUCT_NAME}/skywalking-ui/.gitignore/ \
    --exclude ${PRODUCT_NAME}/skywalking-ui/.travis.yml/ \
    --exclude ${PRODUCT_NAME}/apm-protocol/apm-network/src/main/proto/.git/ \
    ${PRODUCT_NAME}

gpg --armor --detach-sig ${PRODUCT_NAME}-src.tgz

shasum -a 512 ${PRODUCT_NAME}-src.tgz > ${PRODUCT_NAME}-src.tgz.sha512
