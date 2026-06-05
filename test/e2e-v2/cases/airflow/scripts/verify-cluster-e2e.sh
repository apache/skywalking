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

# Real Celery cluster integration verify (mirrors airflow-cluster-cases.yaml).
# Topology: 1 service + 3 OTLP-exporting instances + 11 service metrics + 13 instance metrics = 26 checks.
# Full SWIP-7 (30 checks) is covered by the mock suite (airflow-cases.yaml).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=cluster-compose-env.sh
source "${SCRIPT_DIR}/cluster-compose-env.sh"

export PATH="/tmp/skywalking-infra-e2e/bin:/usr/bin:/bin:${PATH}"

SERVICE="airflow::airflow-e2e-cluster"
RETRIES="${VERIFY_RETRIES:-18}"
INTERVAL="${VERIFY_INTERVAL_SECONDS:-10}"
REPORT="${VERIFY_REPORT:-test/e2e-v2/cases/airflow/cluster-e2e-report.txt}"

OAP_PORT="$(dc port oap 12800 | cut -d: -f2)"
BASE_URL="http://localhost:${OAP_PORT}/graphql"
SWCTL="${SWCTL:-swctl}"

pass=0
fail=0

log() {
  echo "$@" | tee -a "${REPORT}"
}

check_pass() {
  pass=$((pass + 1))
  log "  PASS: $1"
}

check_fail() {
  fail=$((fail + 1))
  log "  FAIL: $1"
  if [[ -n "${2:-}" ]]; then
    log "        detail: $2"
  fi
}

# At least one time-series point with a non-null numeric value (0 is OK).
metric_has_value() {
  local expression="$1"
  shift
  local out
  out="$("${SWCTL}" --display yaml --base-url="${BASE_URL}" metrics exec \
    --expression="${expression}" --service-name="${SERVICE}" "$@" 2>&1)" || return 1
  echo "${out}" | grep -q 'type: TIME_SERIES_VALUES' || return 1
  echo "${out}" | grep -qE '^[[:space:]]*- id:' || return 1
  echo "${out}" | grep -qE '^[[:space:]]*value: ("[0-9]+(\.[0-9]+)?"|[0-9]+(\.[0-9]+)?)$'
}

verify_metric() {
  local label="$1"
  local expression="$2"
  shift 2
  local attempt
  for attempt in $(seq 1 "${RETRIES}"); do
    if metric_has_value "${expression}" "$@"; then
      check_pass "${label}"
      return 0
    fi
    if [[ "${attempt}" -lt "${RETRIES}" ]]; then
      sleep "${INTERVAL}"
    fi
  done
  check_fail "${label}" "no non-null value after ${RETRIES} attempts"
  return 1
}

mkdir -p "$(dirname "${REPORT}")"
: > "${REPORT}"
log "=== Airflow cluster e2e verify (integration smoke) ==="
log "time: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
log "compose project: ${COMPOSE_PROJECT_NAME}"
log "OAP GraphQL: ${BASE_URL}"
log ""

# --- Topology ---
for attempt in $(seq 1 "${RETRIES}"); do
  if out=$("${SWCTL}" --display yaml --base-url="${BASE_URL}" service ly AIRFLOW 2>&1) &&
    echo "${out}" | grep -q "name: airflow::airflow-e2e-cluster" &&
    echo "${out}" | grep -q "AIRFLOW"; then
    check_pass "service ly AIRFLOW -> airflow::airflow-e2e-cluster"
    break
  fi
  if [[ "${attempt}" -eq "${RETRIES}" ]]; then
    check_fail "service ly AIRFLOW -> airflow::airflow-e2e-cluster"
  else
    sleep "${INTERVAL}"
  fi
done

for attempt in $(seq 1 "${RETRIES}"); do
  if out=$("${SWCTL}" --display yaml --base-url="${BASE_URL}" instance ls --service-name="${SERVICE}" 2>&1) &&
    echo "${out}" | grep -q "name: airflow-scheduler" &&
    echo "${out}" | grep -q "name: airflow-worker-1" &&
    echo "${out}" | grep -q "name: airflow-triggerer"; then
    check_pass "instances: scheduler, worker-1, triggerer"
    break
  fi
  if [[ "${attempt}" -eq "${RETRIES}" ]]; then
    check_fail "instances: scheduler, worker-1, triggerer"
  else
    sleep "${INTERVAL}"
  fi
done

log ""
log "--- Service metrics (11) ---"

SERVICE_METRICS=(
  meter_airflow_scheduler_tasks_executable
  meter_airflow_executor_queued_tasks
  meter_airflow_executor_running_tasks
  meter_airflow_executor_open_slots
  meter_airflow_pool_queued_slots
  meter_airflow_pool_deferred_slots
  meter_airflow_pool_scheduled_slots
  meter_airflow_scheduler_heartbeat
  meter_airflow_scheduler_orphaned_tasks_cleared
  meter_airflow_scheduler_orphaned_tasks_adopted
  meter_airflow_dag_file_queue_size
)

for metric in "${SERVICE_METRICS[@]}"; do
  verify_metric "${metric} (service)" "${metric}" || true
done

log ""
log "--- Instance metrics (13) ---"

# expression|instance-name|label suffix (13 instance-level metrics in cluster smoke)
INSTANCE_METRICS=(
  "meter_airflow_instance_pool_open_slots|airflow-worker-1|pool_open_slots worker-1"
  "meter_airflow_instance_pool_deferred_slots|airflow-worker-1|pool_deferred_slots worker-1"
  "meter_airflow_instance_pool_running_slots|airflow-worker-1|pool_running_slots worker-1"
  "meter_airflow_instance_pool_scheduled_slots|airflow-scheduler|pool_scheduled_slots scheduler"
  "meter_airflow_instance_triggerer_heartbeat|airflow-triggerer|triggerer_heartbeat"
  "meter_airflow_instance_triggers_succeeded|airflow-triggerer|triggers_succeeded"
  "meter_airflow_instance_scheduler_tasks_executable|airflow-scheduler|scheduler_tasks_executable"
  "meter_airflow_instance_scheduler_orphaned_tasks_cleared|airflow-scheduler|scheduler_orphaned_tasks_cleared"
  "meter_airflow_instance_scheduler_orphaned_tasks_adopted|airflow-scheduler|scheduler_orphaned_tasks_adopted"
  "meter_airflow_instance_executor_queued_tasks|airflow-scheduler|executor_queued_tasks scheduler"
  "meter_airflow_instance_executor_running_tasks|airflow-scheduler|executor_running_tasks scheduler"
  "meter_airflow_instance_asset_orphaned|airflow-scheduler|asset_orphaned scheduler"
  "meter_airflow_instance_asset_triggered_dagruns|airflow-scheduler|asset_triggered_dagruns scheduler"
)

for entry in "${INSTANCE_METRICS[@]}"; do
  IFS='|' read -r expression instance label <<< "${entry}"
  verify_metric "${expression} (${label})" "${expression}" --instance-name="${instance}" || true
done

log ""
log "=== Summary ==="
log "PASS: ${pass}  FAIL: ${fail}  TOTAL: $((pass + fail))"
log "Report: ${REPORT}"

if [[ "${fail}" -gt 0 ]]; then
  exit 1
fi
