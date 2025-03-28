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

if ! command -v yq &> /dev/null; then
  echo "yq is not installed. Please install yq first."
  exit 1
fi

if [ -d "skywalking" ]; then
  rm -rf skywalking
fi

echo "Cloning the repository..."
git clone --recurse-submodules -j4 --depth 1 https://github.com/apache/skywalking.git && cd skywalking

echo "Checking out the release branch ${RELEASE_VERSION}-release..."
git checkout -b ${RELEASE_VERSION}-release

echo "Setting the next release version ${NEXT_RELEASE_VERSION} in pom.xml..."
./mvnw versions:set-property -DgenerateBackupPoms=false -Dproperty=revision -DnewVersion=${NEXT_RELEASE_VERSION}-SNAPSHOT

echo "Committing the pom.xml changes..."
git add pom.xml
git commit -m "Update the next release version to ${NEXT_RELEASE_VERSION}-SNAPSHOT"

echo "Moving the changelog file..."
mv docs/en/changes/changes.md docs/en/changes-$RELEASE_VERSION.md

echo "Updating the changelog file..."
cat docs/en/changes/changes.tpl | sed "s/NEXT_RELEASE_VERSION/${NEXT_RELEASE_VERSION}/g" > docs/en/changes/changes.md

echo "Committing the changelog files..."
git add docs
git commit -m "Update the changelog files"

echo "Updating the menu.yml file..."
new_menu_file=$(mktemp)
major_version=$(echo ${RELEASE_VERSION} | cut -d. -f1)
yq '(.catalog[] | select(.name=="Changelog") | .catalog[] | select(.name=="'"${major_version}.x Releases"'") | .catalog) |= [{ "name": "'"${RELEASE_VERSION}"'", "path": "/en/changes/changes-'${RELEASE_VERSION}'" }] + .' docs/menu.yml > ${new_menu_file}
mv ${new_menu_file} docs/menu.yml
git add docs
git commit -m "Update the menu.yml file"

echo "Pushing the changes to the remote repository..."
git push --set-upstream origin ${RELEASE_VERSION}-release

echo "Opening the PR..."
open https://github.com/apache/skywalking/pull/new/${RELEASE_VERSION}-release
