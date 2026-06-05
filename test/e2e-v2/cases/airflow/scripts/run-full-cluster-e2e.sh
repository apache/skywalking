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

# Full local run: compose up -> setup (tools + workload) -> verify all cases.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../../.." && pwd)"
cd "${REPO_ROOT}"

export OTEL_COLLECTOR_VERSION="${OTEL_COLLECTOR_VERSION:-0.102.1}"
export SW_AGENT_JDK_VERSION="${SW_AGENT_JDK_VERSION:-8}"

# shellcheck source=cluster-compose-env.sh
source "${SCRIPT_DIR}/cluster-compose-env.sh"

echo "=== Airflow cluster full e2e (project ${COMPOSE_PROJECT_NAME}) ==="
dc down --remove-orphans 2>/dev/null || true
dc up -d

/usr/bin/bash test/e2e-v2/cases/airflow/scripts/run-cluster-setup.sh
/usr/bin/bash test/e2e-v2/cases/airflow/scripts/verify-cluster-e2e.sh

echo "=== Full cluster e2e PASSED ==="
