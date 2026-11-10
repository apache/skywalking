#!/usr/bin/env bash

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

set -ex

LIB_HOME=$1
MYSQL_URL="https://repo.maven.apache.org/maven2/mysql/mysql-connector-java/8.0.13/mysql-connector-java-8.0.13.jar"
MYSQL_DRIVER="mysql-connector-java-8.0.13.jar"

# ensure the curl command been installed
if ! command -v curl &> /dev/null; then
    apt update -y && apt install -y curl
fi

# --fail so an HTTP error, such as a 429 from Maven Central, is not saved as the jar, and --max-time bounds each
# attempt. The attempts are retried here, not with curl's --retry, which sleeps for as long as a 429's Retry-After
# asks. After curl is installed, the download takes at most about 320 seconds, and if every attempt fails the script
# exits non-zero, so the OAP does not start.
for attempt in 1 2 3 4 5; do
    if [ "${attempt}" -gt 1 ]; then
        sleep 5
    fi
    if curl --fail --location --connect-timeout 10 --max-time 60 -o "${LIB_HOME}/${MYSQL_DRIVER}" "${MYSQL_URL}"; then
        exit 0
    fi
done
echo "Fail to download ${MYSQL_DRIVER}."
exit 1
