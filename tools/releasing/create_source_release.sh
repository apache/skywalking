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

##
## Variables with defaults (if not overwritten by environment)
##
RELEASE_VERSION=${RELEASE_VERSION}
PRODUCT_NAME="apache-skywalking-apm-incubating"

if [ "$RELEASE_VERSION" == "" ]; then
  echo "RELEASE_VERSION variable is null"
  exit 1
fi

echo "Creating source package"

PRODUCT_NAME=${PRODUCT_NAME}-${RELEASE_VERSION}

rm -rf ${PRODUCT_NAME}
mkdir ${PRODUCT_NAME}

rsync -a ../../ \
  --exclude ".git" --exclude ".gitignore" --exclude ".gitattributes" --exclude ".travis.yml" \
  --exclude "deploysettings.xml" --exclude "CHANGELOG" --exclude ".github" --exclude "target" \
  --exclude ".idea" --exclude "*.iml" --exclude ".DS_Store" --exclude "build-target" \
  --exclude "/docs/" --exclude "/dist/" --exclude "/tools/" --exclude "/skywalking-agent/" \
  --exclude "/skywalking-ui/dist/" --exclude "/skywalking-ui/node/" --exclude "/skywalking-ui/node_modules/" \
  ${PRODUCT_NAME}

tar czf ${PRODUCT_NAME}-src.tgz ${PRODUCT_NAME}

gpg --armor --detach-sig $PRODUCT_NAME-src.tgz

md5 -r $PRODUCT_NAME-src.tgz > $PRODUCT_NAME-src.tgz.md5
shasum -a 512 $PRODUCT_NAME-src.tgz > $PRODUCT_NAME-src.tgz.sha512
