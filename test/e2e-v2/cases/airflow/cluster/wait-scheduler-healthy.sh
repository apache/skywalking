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

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=compose-env.sh
source "${SCRIPT_DIR}/compose-env.sh"

SCHEDULER="${AIRFLOW_SCHEDULER_SERVICE:-airflow-scheduler}"
MAX_ATTEMPTS="${SCHEDULER_HEALTH_ATTEMPTS:-90}"
SLEEP_SECONDS="${SCHEDULER_HEALTH_INTERVAL_SECONDS:-10}"

echo "Waiting for ${SCHEDULER} (compose project ${COMPOSE_PROJECT_NAME})..."

for _ in $(seq 1 "${MAX_ATTEMPTS}"); do
  if dc exec -T "${SCHEDULER}" \
    airflow jobs check --job-type SchedulerJob --hostname "${SCHEDULER}"; then
    echo "Airflow scheduler healthy"
    exit 0
  fi
  sleep "${SLEEP_SECONDS}"
done

echo "Airflow scheduler did not become healthy in time"
exit 1
