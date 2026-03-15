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

# Apache SkyWalking release automation script.
# Usage: bash release.sh

set -e -o pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PROJECT_DIR=$(cd "${SCRIPT_DIR}/../.." && pwd)
PRODUCT_NAME="apache-skywalking-apm"
SVN_DEV_URL="https://dist.apache.org/repos/dist/dev/skywalking"
CLONE_DIR="${SCRIPT_DIR}/skywalking"
MVN=./mvnw

# ========================== Shared functions ==========================

clone_repo() {
    cd "${SCRIPT_DIR}"
    if [ -d "${CLONE_DIR}" ]; then
        rm -rf "${CLONE_DIR}"
    fi
    echo "Cloning the repository..."
    git clone --recurse-submodules -j4 --depth 1 https://github.com/apache/skywalking.git
}

build_release_artifacts() {
    local TAG=v${RELEASE_VERSION}

    cd "${CLONE_DIR}"

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
    mv dist/${PRODUCT_NAME}-bin.tar.gz "${SCRIPT_DIR}"/${PRODUCT_NAME}-${RELEASE_VERSION}-bin.tar.gz

    cd "${SCRIPT_DIR}"

    gpg --armor --detach-sig ${PRODUCT_NAME}-${RELEASE_VERSION}-src.tar.gz
    gpg --armor --detach-sig ${PRODUCT_NAME}-${RELEASE_VERSION}-bin.tar.gz

    shasum -a 512 ${PRODUCT_NAME}-${RELEASE_VERSION}-src.tar.gz > ${PRODUCT_NAME}-${RELEASE_VERSION}-src.tar.gz.sha512
    shasum -a 512 ${PRODUCT_NAME}-${RELEASE_VERSION}-bin.tar.gz > ${PRODUCT_NAME}-${RELEASE_VERSION}-bin.tar.gz.sha512

    echo "Release artifacts built successfully."
}

prepare_next_version() {
    cd "${CLONE_DIR}"

    echo "Setting the next release version ${NEXT_RELEASE_VERSION}-SNAPSHOT in pom.xml..."
    ${MVN} versions:set-property -DgenerateBackupPoms=false -Dproperty=revision -DnewVersion=${NEXT_RELEASE_VERSION}-SNAPSHOT

    echo "Committing the pom.xml changes..."
    git add pom.xml
    git commit -m "Update the next release version to ${NEXT_RELEASE_VERSION}-SNAPSHOT"

    echo "Moving the changelog file..."
    mv docs/en/changes/changes.md docs/en/changes/changes-${RELEASE_VERSION}.md

    echo "Updating the changelog file..."
    cat docs/en/changes/changes.tpl | sed "s/NEXT_RELEASE_VERSION/${NEXT_RELEASE_VERSION}/g" > docs/en/changes/changes.md

    echo "Committing the changelog files..."
    git add docs
    git commit -m "Update the changelog files"

    echo "Updating the menu.yml file..."
    local new_menu_file=$(mktemp)
    local major_version=$(echo ${RELEASE_VERSION} | cut -d. -f1)
    yq '(.catalog[] | select(.name=="Changelog") | .catalog[] | select(.name=="'"${major_version}.x Releases"'") | .catalog) |= [{ "name": "'"${RELEASE_VERSION}"'", "path": "/en/changes/changes-'${RELEASE_VERSION}'" }] + .' docs/menu.yml > ${new_menu_file}
    mv ${new_menu_file} docs/menu.yml
    git add docs
    git commit -m "Update the menu.yml file"

    echo "Pushing the changes to the remote repository..."
    git push --set-upstream origin ${RELEASE_VERSION}-release

    echo "Opening the PR..."
    open https://github.com/apache/skywalking/pull/new/${RELEASE_VERSION}-release
}

upload_to_svn() {
    local SRC_TAR="${SCRIPT_DIR}/${PRODUCT_NAME}-${RELEASE_VERSION}-src.tar.gz"
    local BIN_TAR="${SCRIPT_DIR}/${PRODUCT_NAME}-${RELEASE_VERSION}-bin.tar.gz"
    local SVN_DIR="${SCRIPT_DIR}/svn-staging"
    local SVN_VERSION_DIR="${SVN_DIR}/${RELEASE_VERSION}"

    if [ -d "${SVN_DIR}" ]; then
        rm -rf "${SVN_DIR}"
    fi

    echo "Checking out SVN dev directory..."
    svn co --depth immediates "${SVN_DEV_URL}" "${SVN_DIR}"

    if [ -d "${SVN_VERSION_DIR}" ]; then
        echo "Version folder ${RELEASE_VERSION} already exists in SVN, updating artifacts..."
        svn update "${SVN_VERSION_DIR}"
    else
        mkdir -p "${SVN_VERSION_DIR}"
        cd "${SVN_DIR}"
        svn add "${RELEASE_VERSION}"
    fi

    cp "${SRC_TAR}" "${SRC_TAR}.asc" "${SRC_TAR}.sha512" "${SVN_VERSION_DIR}/"
    cp "${BIN_TAR}" "${BIN_TAR}.asc" "${BIN_TAR}.sha512" "${SVN_VERSION_DIR}/"

    cd "${SVN_DIR}"
    svn add --force "${RELEASE_VERSION}"
    svn commit -m "Upload Apache SkyWalking ${RELEASE_VERSION} release candidate"

    echo "Artifacts uploaded to: ${SVN_DEV_URL}/${RELEASE_VERSION}"

    rm -rf "${SVN_DIR}"
}

generate_vote_email() {
    local TAG=v${RELEASE_VERSION}
    local SRC_TAR="${SCRIPT_DIR}/${PRODUCT_NAME}-${RELEASE_VERSION}-src.tar.gz"
    local BIN_TAR="${SCRIPT_DIR}/${PRODUCT_NAME}-${RELEASE_VERSION}-bin.tar.gz"
    local SRC_SHA512=$(cat "${SRC_TAR}.sha512")
    local BIN_SHA512=$(cat "${BIN_TAR}.sha512")
    local VOTE_DATE=$(date +"%B %d, %Y")

    # Extract submodule commit IDs from the cloned release repo
    local UI_COMMIT=$(git -C "${CLONE_DIR}" submodule status -- skywalking-ui | awk '{print $1}' | tr -d '+-')
    local PROTOCOL_COMMIT=$(git -C "${CLONE_DIR}" submodule status -- apm-protocol/apm-network/src/main/proto | awk '{print $1}' | tr -d '+-')
    local QUERY_COMMIT=$(git -C "${CLONE_DIR}" submodule status -- oap-server/server-query-plugin/query-graphql-plugin/src/main/resources/query-protocol | awk '{print $1}' | tr -d '+-')

    cat <<EOF

========================================================================
Vote Email - Copy and send to dev@skywalking.apache.org
========================================================================

Mail title: [VOTE] Release Apache SkyWalking version ${RELEASE_VERSION}

Mail content:
Hi All,
This is a call for vote to release Apache SkyWalking version ${RELEASE_VERSION}.

Release notes:

 * https://github.com/apache/skywalking/blob/master/docs/en/changes/changes-${RELEASE_VERSION}.md

Release Candidate:

 * ${SVN_DEV_URL}/${RELEASE_VERSION}
 * sha512 checksums
   - ${SRC_SHA512}
   - ${BIN_SHA512}

Release Tag :

 * (Git Tag) ${TAG}

Release CommitID :

 * https://github.com/apache/skywalking/tree/${TAG}
 * Git submodule
   * skywalking-ui: https://github.com/apache/skywalking-booster-ui/tree/${UI_COMMIT}
   * apm-protocol/apm-network/src/main/proto: https://github.com/apache/skywalking-data-collect-protocol/tree/${PROTOCOL_COMMIT}
   * oap-server/server-query-plugin/query-graphql-plugin/src/main/resources/query-protocol https://github.com/apache/skywalking-query-protocol/tree/${QUERY_COMMIT}

Keys to verify the Release Candidate :

 * https://dist.apache.org/repos/dist/release/skywalking/KEYS

Guide to build the release from source :

 * https://github.com/apache/skywalking/blob/${TAG}/docs/en/guides/How-to-build.md

Voting will start now (${VOTE_DATE}) and will remain open for at least 72 hours, Request all PMC members to give their vote.
[ ] +1 Release this package.
[ ] +0 No opinion.
[ ] -1 Do not release this package because....

========================================================================

EOF
}

# ========================== Main flow ==========================

# Step 1: Check GPG signer
echo "=== Step 1: Checking GPG signer ==="

GPG_KEY_ID=$(git config user.signingkey 2>/dev/null || true)
if [ -z "$GPG_KEY_ID" ]; then
    echo "No git signing key configured. Trying default GPG key..."
    GPG_KEY_ID=$(gpg --list-secret-keys --keyid-format LONG 2>/dev/null | grep -A1 '^sec' | tail -1 | awk '{print $1}' || true)
fi

if [ -z "$GPG_KEY_ID" ]; then
    echo "ERROR: No GPG secret key found. Please configure a GPG key first."
    exit 1
fi

GPG_UIDS=$(gpg --list-secret-keys --keyid-format LONG 2>/dev/null | grep 'uid' | sed 's/.*] //')
GPG_EMAIL=$(echo "$GPG_UIDS" | grep -oE '[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}' | head -1)

if [[ "$GPG_EMAIL" != *"@apache.org" ]]; then
    echo "WARNING: GPG key email '${GPG_EMAIL}' is not an @apache.org address."
    echo "Apache releases must be signed with an @apache.org GPG key."
    exit 1
fi

echo "GPG Signer: ${GPG_UIDS}"
echo "GPG Email:  ${GPG_EMAIL}"
read -p "Is this the correct GPG signer? [y/N] " confirm
if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    echo "Aborted."
    exit 1
fi

# Step 2: Check required tools
echo ""
echo "=== Step 2: Checking required tools ==="

MISSING_TOOLS=()
for tool in gpg svn shasum git yq; do
    if ! command -v "$tool" &>/dev/null; then
        MISSING_TOOLS+=("$tool")
    fi
done

if [ ${#MISSING_TOOLS[@]} -gt 0 ]; then
    echo "ERROR: Missing required tools: ${MISSING_TOOLS[*]}"
    exit 1
fi

echo "All required tools are available."

# Step 3: Detect current version
echo ""
echo "=== Step 3: Detecting current version ==="

CURRENT_VERSION=$(grep '<revision>' "${PROJECT_DIR}/pom.xml" | head -1 | sed 's/.*<revision>\(.*\)<\/revision>.*/\1/')

if [ -z "$CURRENT_VERSION" ]; then
    echo "ERROR: Could not detect version from pom.xml."
    exit 1
fi

echo "Current version: ${CURRENT_VERSION}"

# Step 4: Calculate versions
echo ""
echo "=== Step 4: Calculating versions ==="

RELEASE_VERSION="${CURRENT_VERSION%-SNAPSHOT}"

if [ "$RELEASE_VERSION" == "$CURRENT_VERSION" ]; then
    echo "WARNING: Current version '${CURRENT_VERSION}' does not end with -SNAPSHOT."
    read -p "Continue with release version '${RELEASE_VERSION}'? [y/N] " confirm
    if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
        echo "Aborted."
        exit 1
    fi
fi

MAJOR=$(echo "$RELEASE_VERSION" | cut -d. -f1)
MINOR=$(echo "$RELEASE_VERSION" | cut -d. -f2)
NEXT_MINOR=$((MINOR + 1))
NEXT_RELEASE_VERSION="${MAJOR}.${NEXT_MINOR}.0"

echo "Release version:      ${RELEASE_VERSION}"
echo "Next dev version:     ${NEXT_RELEASE_VERSION}-SNAPSHOT"
read -p "Are these versions correct? [y/N] " confirm
if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    read -p "Enter release version: " RELEASE_VERSION
    read -p "Enter next release version (without -SNAPSHOT): " NEXT_RELEASE_VERSION
fi

# Step 5: Clone and build
echo ""
echo "=== Step 5: Cloning repository and building release artifacts ==="
clone_repo
build_release_artifacts

# Verify artifacts
SRC_TAR="${SCRIPT_DIR}/${PRODUCT_NAME}-${RELEASE_VERSION}-src.tar.gz"
BIN_TAR="${SCRIPT_DIR}/${PRODUCT_NAME}-${RELEASE_VERSION}-bin.tar.gz"

for f in "${SRC_TAR}" "${SRC_TAR}.asc" "${SRC_TAR}.sha512" \
         "${BIN_TAR}" "${BIN_TAR}.asc" "${BIN_TAR}.sha512"; do
    if [ ! -f "$f" ]; then
        echo "ERROR: Expected artifact not found: $f"
        exit 1
    fi
done

echo "All artifacts verified:"
ls -lh "${SCRIPT_DIR}"/${PRODUCT_NAME}-${RELEASE_VERSION}-*

# Step 6: Upload to SVN
echo ""
echo "=== Step 6: Uploading to SVN staging ==="
upload_to_svn

# Step 7: Generate vote email
echo ""
echo "=== Step 7: Generating vote email ==="
generate_vote_email

# Step 8: Next version
echo ""
echo "=== Step 8: Starting next version iteration ==="
prepare_next_version

echo ""
echo "========================================================================="
echo "Release process complete!"
echo "  Release version: ${RELEASE_VERSION}"
echo "  Next dev version: ${NEXT_RELEASE_VERSION}-SNAPSHOT"
echo "  SVN staging: ${SVN_DEV_URL}/${RELEASE_VERSION}"
echo ""
echo "Next steps:"
echo "  1. Send the vote email to dev@skywalking.apache.org"
echo "  2. Merge the next-version PR once the vote passes"
echo "========================================================================="
