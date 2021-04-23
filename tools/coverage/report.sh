#!/usr/bin/env bash

# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

set -ex

JACOCO_HOME=${JACOCO_HOME:-test/jacoco}

ls -alh "${JACOCO_HOME}"

for exec_data in "${JACOCO_HOME}"/*.exec; do
  exec_data=${exec_data/*\//}
  exec_data=${exec_data/.exec/}

  sudo rm -rf "${JACOCO_HOME}"/classes/"${exec_data}"/org/apache/skywalking/oap/server/core/query/entity || true
  sudo rm -rf "${JACOCO_HOME}"/classes/"${exec_data}"/org/apache/skywalking/testcase || true
  sudo rm -rf "${JACOCO_HOME}"/classes/"${exec_data}"/org/apache/skywalking/e2e || true
  sudo rm -rf "${JACOCO_HOME}"/classes/"${exec_data}"/test/apache/skywalking/e2e || true

  java -jar "${JACOCO_HOME}"/jacococli.jar report \
    --classfiles "${JACOCO_HOME}"/classes/"$exec_data" \
    --xml=/tmp/report-"$exec_data".xml \
    --html=/tmp/report-html-"$exec_data" \
    "${JACOCO_HOME}"/"$exec_data".exec
done

bash <(curl -s https://codecov.io/bash) -X fix -f /tmp/report-*.xml || true
