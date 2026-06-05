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
# shellcheck source=cluster-compose-env.sh
source "${SCRIPT_DIR}/cluster-compose-env.sh"

SCHEDULER="${AIRFLOW_SCHEDULER_SERVICE:-airflow-scheduler}"
ROUNDS="${SEED_ROUNDS:-3}"
INTERVAL="${SEED_INTERVAL_SECONDS:-20}"
RUN_SECONDS="${RUN_SECONDS:-240}"

# Native OTel coverage: deferrable (triggerer triggers_*) and dataset (asset_*).
NATIVE_OTEL_DAGS=(
  e2e_deferrable
  e2e_dataset_producer
  e2e_dataset_consumer
)

LOAD_DAGS=(
  cluster_smoke
  cluster_load
  example_bash_operato
  example_python_operato
  example_branch_operato
  example_short_circuit_operato
)

echo "=== Airflow real-cluster e2e: seed workload (project ${COMPOSE_PROJECT_NAME}, ${ROUNDS} rounds, then ${RUN_SECONDS}s) ==="

# Scheduler health does not guarantee DagModel is populated; trigger fails silently otherwise.
echo "Syncing DAG metadata to database..."
dc exec -T "${SCHEDULER}" airflow dags reserialize >/dev/null 2>&1 || true
sleep 10

trigger_dags() {
  local dag
  for dag in "$@"; do
    dc exec -T "${SCHEDULER}" airflow dags trigger "${dag}" >/dev/null 2>&1 || true
  done
}

echo "Trigger native-OTel DAGs (deferrable + dataset)..."
trigger_dags "${NATIVE_OTEL_DAGS[@]}"

for round in $(seq 1 "${ROUNDS}"); do
  echo "Trigger load round ${round}/${ROUNDS}"
  trigger_dags "${LOAD_DAGS[@]}"
  trigger_dags e2e_deferrable e2e_dataset_producer e2e_dataset_consumer
  if [[ "${round}" -lt "${ROUNDS}" ]]; then
    sleep "${INTERVAL}"
  fi
done

echo "Running ${RUN_SECONDS}s for deferrable triggers, dataset events, and MAL aggregation..."
sleep "${RUN_SECONDS}"
echo "Workload seed complete."
