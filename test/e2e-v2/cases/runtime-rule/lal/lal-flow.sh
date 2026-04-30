#!/usr/bin/env bash
#
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

# Drives runtime-rule LAL hot-update with end-to-end MAL extraction proof:
#
#   0. Apply log-mal aggregation rule (catalog=log-mal-rules)
#   1. Apply LAL v1            — extractor stamps step=v1
#      Verify swctl returns a value for meter_e2e_lal_log_count{step='v1'}
#   2. Swap to LAL v2          — same name, extractor stamps step=v2
#      Verify swctl returns a value for ...{step='v2'} (proves the running
#      extraction switched bodies, not just the persisted rule row)
#   3. Inactivate              — soft-pause; new step values stop landing
#   4. Delete                  — destructive; /list row gone, MAL rule
#      removed too
#
# Run from the repo root.

set -euo pipefail

log() { echo "[lal-flow] $*" >&2; }
fail() { log "FAIL: $*"; exit 1; }

OAP_HOST="${OAP_HOST:-127.0.0.1}"
OAP_REST_PORT="${OAP_REST_PORT:-17128}"
OAP_GQL_PORT="${OAP_GQL_PORT:-12800}"
OAP_BASE="http://${OAP_HOST}:${OAP_REST_PORT}"
GQL_BASE="http://${OAP_HOST}:${OAP_GQL_PORT}"
SEED_DIR="${SEED_DIR:-$(pwd)/test/e2e-v2/cases/runtime-rule/lal/seed-rules}"
SEED_V1="${SEED_DIR}/lal-v1.yaml"
SEED_V2="${SEED_DIR}/lal-v2.yaml"
SEED_MAL="${SEED_DIR}/log-mal.yaml"
LAL_CATALOG="lal"
LAL_NAME="e2e-rr-lal-live"
MAL_CATALOG="log-mal-rules"
MAL_NAME="e2e_lal"
METRIC="meter_e2e_lal_log_count"
SERVICE_NAME="e2e-rr-lal-svc"
# First-phase budget covers minute-bucket boundary + OTLP export interval +
# extraction-then-aggregation latency. Subsequent phases land sooner but the
# upper bound stays here for resilience under CI load.
SETTLE_SECONDS="${SETTLE_SECONDS:-360}"

[ -f "${SEED_V1}" ]  || fail "seed v1 missing at ${SEED_V1}"
[ -f "${SEED_V2}" ]  || fail "seed v2 missing at ${SEED_V2}"
[ -f "${SEED_MAL}" ] || fail "seed mal missing at ${SEED_MAL}"

list_row() {
    local catalog="$1" name="$2"
    curl -fsS "${OAP_BASE}/runtime/rule/list" 2>/dev/null \
        | jq -c '.rules[]
                 | select(.catalog == "'"${catalog}"'" and .name == "'"${name}"'")
                 | select(.status != "n/a")' \
        | head -1
}
list_field() {
    local catalog="$1" name="$2" field="$3"
    list_row "${catalog}" "${name}" | jq -r '."'"${field}"'" // empty'
}

apply_rule() {
    local catalog="$1" name="$2" body="$3"
    curl -fsS -XPOST -H 'Content-Type: text/plain' \
        --data-binary "@${body}" \
        "${OAP_BASE}/runtime/rule/addOrUpdate?catalog=${catalog}&name=${name}" >/dev/null \
        || fail "addOrUpdate ${catalog}/${name} from ${body} failed"
}

# Retries 503 cluster_not_ready for up to 60s — the reconciler's peer-refresh
# window briefly returns 503 right after a structural reshape (e.g. LAL
# delete that retires its dispatcher). Mirrors the MAL flow's pattern.
retry_post() {
    local url="$1"
    local deadline=$(( $(date +%s) + 60 ))
    local out
    while (( $(date +%s) < deadline )); do
        out="$(curl -fsS -XPOST "${url}" 2>&1)" && return 0
        if [[ "${out}" == *503* ]]; then
            sleep 2
            continue
        fi
        echo "${out}" >&2
        return 1
    done
    echo "${out}" >&2
    return 1
}

inactivate_rule() {
    local catalog="$1" name="$2"
    retry_post "${OAP_BASE}/runtime/rule/inactivate?catalog=${catalog}&name=${name}" >/dev/null \
        || fail "inactivate ${catalog}/${name} failed"
}

delete_rule() {
    local catalog="$1" name="$2"
    retry_post "${OAP_BASE}/runtime/rule/delete?catalog=${catalog}&name=${name}" >/dev/null \
        || fail "delete ${catalog}/${name} failed"
}

# Returns 0 if swctl finds at least one minute-bucket sample for the given
# metric+step combo. Reads through GraphQL via swctl's `metrics exec`.
swctl_metric_for_step() {
    local step="$1"
    local expr="${METRIC}{step='${step}'}"
    local out
    out="$(swctl --display yaml --base-url="${GQL_BASE}/graphql" \
        metrics exec --expression="${expr}" \
        --service-name="${SERVICE_NAME}" 2>&1)" || {
        log "  swctl exec ${expr} failed: ${out}"
        return 1
    }
    log "  swctl ${expr} → ${out}"
    echo "${out}" | grep -qE '^\s*value:\s*"?-?[0-9]+(\.[0-9]+)?"?\s*$'
}

await_metric_for_step() {
    local step="$1"
    log "  await ${METRIC}{step='${step}'} (budget ${SETTLE_SECONDS}s)"
    local deadline=$(( $(date +%s) + SETTLE_SECONDS ))
    while (( $(date +%s) < deadline )); do
        if swctl_metric_for_step "${step}"; then
            log "  ✓ ${METRIC}{step='${step}'} has values"
            return 0
        fi
        sleep 5
    done
    fail "${METRIC}{step='${step}'} did not produce a value within ${SETTLE_SECONDS}s"
}

log "waiting for OAP runtime-rule port"
deadline=$(( $(date +%s) + 90 ))
until curl -fsS "${OAP_BASE}/runtime/rule/list" >/dev/null 2>&1; do
    if [ "$(date +%s)" -ge "${deadline}" ]; then fail "OAP not ready after 90s"; fi
    sleep 2
done
log "OAP ready"

# --- Phase 0: apply log-mal aggregation -----------------------------------------------
log "=== Phase 0: apply log-mal aggregation rule ==="
apply_rule "${MAL_CATALOG}" "${MAL_NAME}" "${SEED_MAL}"
mal_status="$(list_field "${MAL_CATALOG}" "${MAL_NAME}" status)"
[ "${mal_status}" = "ACTIVE" ] || fail "MAL rule expected ACTIVE, got '${mal_status}'"
log "log-mal → ACTIVE"

# --- Phase 1: apply LAL v1 ------------------------------------------------------------
log "=== Phase 1: apply LAL v1 (extractor stamps step=v1) ==="
apply_rule "${LAL_CATALOG}" "${LAL_NAME}" "${SEED_V1}"
status="$(list_field "${LAL_CATALOG}" "${LAL_NAME}" status)"
[ "${status}" = "ACTIVE" ] || fail "v1 expected ACTIVE, got '${status}'"
hash_v1="$(list_field "${LAL_CATALOG}" "${LAL_NAME}" contentHash)"
[ -n "${hash_v1}" ] || fail "v1 contentHash empty"
log "v1 → ACTIVE @ ${hash_v1:0:8}…"
await_metric_for_step "v1"

# --- Phase 2: swap to LAL v2 (same key, step flips to v2) -----------------------------
log "=== Phase 2: swap to LAL v2 (extractor stamps step=v2) ==="
apply_rule "${LAL_CATALOG}" "${LAL_NAME}" "${SEED_V2}"
status="$(list_field "${LAL_CATALOG}" "${LAL_NAME}" status)"
[ "${status}" = "ACTIVE" ] || fail "v2 expected ACTIVE, got '${status}'"
hash_v2="$(list_field "${LAL_CATALOG}" "${LAL_NAME}" contentHash)"
[ "${hash_v2}" != "${hash_v1}" ] || fail "v2 contentHash unchanged from v1 (${hash_v2:0:8}…)"
log "v2 → ACTIVE @ ${hash_v2:0:8}… (was ${hash_v1:0:8}…) — swap applied"
await_metric_for_step "v2"

# --- Phase 3: inactivate LAL ----------------------------------------------------------
log "=== Phase 3: inactivate LAL ==="
inactivate_rule "${LAL_CATALOG}" "${LAL_NAME}"
status="$(list_field "${LAL_CATALOG}" "${LAL_NAME}" status)"
[ "${status}" = "INACTIVE" ] || fail "expected INACTIVE, got '${status}'"
log "inactivate → INACTIVE OK"

# --- Phase 4: delete LAL + MAL --------------------------------------------------------
log "=== Phase 4: delete LAL + log-mal rules ==="
delete_rule "${LAL_CATALOG}" "${LAL_NAME}"
deadline=$(( $(date +%s) + 30 ))
while [ -n "$(list_row "${LAL_CATALOG}" "${LAL_NAME}")" ]; do
    if [ "$(date +%s)" -ge "${deadline}" ]; then
        fail "LAL row still present 30s after delete"
    fi
    sleep 2
done
log "LAL row gone OK"

inactivate_rule "${MAL_CATALOG}" "${MAL_NAME}"
delete_rule "${MAL_CATALOG}" "${MAL_NAME}"
deadline=$(( $(date +%s) + 30 ))
while [ -n "$(list_row "${MAL_CATALOG}" "${MAL_NAME}")" ]; do
    if [ "$(date +%s)" -ge "${deadline}" ]; then
        fail "MAL row still present 30s after delete"
    fi
    sleep 2
done
log "MAL row gone OK"

log "=== ALL LAL FLOW PHASES PASSED ==="
