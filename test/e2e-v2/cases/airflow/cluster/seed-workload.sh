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

# Sustained native OTel traffic for cluster e2e (mirrors deploy/airflow-local/seed-demo-full.ps1).
# Phases: asset -> deferrable -> load bursts -> hold with periodic top-up + DAG reserialize.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=compose-env.sh
source "${SCRIPT_DIR}/compose-env.sh"

SCHEDULER="${AIRFLOW_SCHEDULER_SERVICE:-airflow-scheduler}"

ASSET_ROUNDS="${ASSET_ROUNDS:-6}"
DEFERRABLE_TRIGGERS="${DEFERRABLE_TRIGGERS:-8}"
LOAD_BURSTS="${LOAD_BURSTS:-5}"
HOLD_SECONDS="${HOLD_SECONDS:-${RUN_SECONDS:-180}}"
TOPUP_INTERVAL="${TOPUP_INTERVAL:-30}"

ASSET_DAGS=(e2e_asset_producer e2e_asset_consumer)
DEFERRABLE_DAG="e2e_deferrable"
LOAD_DAG="cluster_load"
WARMUP_DAGS=(cluster_smoke example_bash_operator example_python_operator)

trigger_dag() {
  local dag="$1"
  if ! dc exec -T "${SCHEDULER}" airflow dags trigger "${dag}" >/dev/null 2>&1; then
    echo "  warn: trigger ${dag} failed (missing or paused?)" >&2
  fi
}

reserialize_dags() {
  dc exec -T "${SCHEDULER}" airflow dags reserialize >/dev/null 2>&1 || true
}

echo "=== Airflow cluster e2e seed (project ${COMPOSE_PROJECT_NAME}) ==="
echo "  asset_rounds=${ASSET_ROUNDS} deferrable=${DEFERRABLE_TRIGGERS} load_bursts=${LOAD_BURSTS} hold=${HOLD_SECONDS}s"

echo "[1/4] Sync DAG metadata (dagbag / parse metrics)..."
reserialize_dags
sleep 8

echo "[2/4] Asset DAGs (${ASSET_ROUNDS} rounds) -> asset_triggered_dagruns..."
for round in $(seq 1 "${ASSET_ROUNDS}"); do
  for dag in "${ASSET_DAGS[@]}"; do
    trigger_dag "${dag}"
  done
  if [[ "${round}" -lt "${ASSET_ROUNDS}" ]]; then
    sleep 8
  fi
done

echo "[3/4] Deferrable sensors (${DEFERRABLE_TRIGGERS} triggers) -> triggerer triggers_* / heartbeat..."
for _ in $(seq 1 "${DEFERRABLE_TRIGGERS}"); do
  trigger_dag "${DEFERRABLE_DAG}"
  sleep 4
done

echo "[4/4] Load bursts (${LOAD_BURSTS} x ${LOAD_DAG}) -> executor queued/running, pool scheduled..."
for dag in "${WARMUP_DAGS[@]}"; do
  trigger_dag "${dag}"
done
for burst in $(seq 1 "${LOAD_BURSTS}"); do
  echo "  burst ${burst}/${LOAD_BURSTS}"
  trigger_dag "${LOAD_DAG}"
  sleep 3
done

echo "Hold ${HOLD_SECONDS}s with top-up (sustain queue + deferrable + asset + dagbag refresh)..."
elapsed=0
while [[ "${elapsed}" -lt "${HOLD_SECONDS}" ]]; do
  step="${TOPUP_INTERVAL}"
  if [[ $((HOLD_SECONDS - elapsed)) -lt "${step}" ]]; then
    step=$((HOLD_SECONDS - elapsed))
  fi
  sleep "${step}"
  elapsed=$((elapsed + step))
  if [[ "${elapsed}" -lt "${HOLD_SECONDS}" ]]; then
    trigger_dag "${LOAD_DAG}"
    trigger_dag "${DEFERRABLE_DAG}"
    trigger_dag "e2e_asset_producer"
    reserialize_dags
    echo "  ... ${elapsed}s / ${HOLD_SECONDS}s (topped up load + deferrable + asset + reserialize)"
  fi
done

# Allow at least two OTel export cycles before verify (interval configured in docker-compose).
OTEL_FLUSH_SECONDS="${OTEL_FLUSH_SECONDS:-35}"
echo "OTel flush wait ${OTEL_FLUSH_SECONDS}s..."
sleep "${OTEL_FLUSH_SECONDS}"

echo "Workload seed complete."
