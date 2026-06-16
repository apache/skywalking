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

# All runtime-rule REST calls go through swctl's `admin` command tree instead of
# raw curl. `--display json` keeps the response body shape identical to the old
# curl output, so the jq assertions below are unchanged.
admin() { swctl --display json --admin-url="${OAP_BASE}" admin "$@"; }

list_row() {
    local catalog="$1" name="$2"
    admin runtime-rule list 2>/dev/null \
        | jq -c '.rules[]
                 | select(.catalog == "'"${catalog}"'" and .name == "'"${name}"'")
                 | select(.status != "n/a")' \
        | head -1
}
list_field() {
    local catalog="$1" name="$2" field="$3"
    list_row "${catalog}" "${name}" | jq -r '."'"${field}"'" // empty'
}

# Budget for an async apply to land in /list. A NEW / STRUCTURAL addOrUpdate is async: it returns
# immediately at FENCING and the rule row is persisted only AFTER a background schema fence, so a
# single read right after the 2xx can miss the row (or read a stale contentHash). The waiters below
# poll within this budget. FILTER_ONLY edits persist synchronously, so they return on the first poll.
APPLY_LAND_S="${APPLY_LAND_S:-200}"

# Poll until the (catalog,name) row reaches expected_status, up to APPLY_LAND_S.
await_status() {
    local catalog="$1" name="$2" expected="$3"
    log "  await /list ${catalog}/${name} status=${expected} (budget ${APPLY_LAND_S}s)"
    local deadline=$(( $(date +%s) + APPLY_LAND_S )) got=""
    while true; do
        got="$(list_field "${catalog}" "${name}" status)"
        [ "${got}" = "${expected}" ] && return 0
        if [ "$(date +%s)" -ge "${deadline}" ]; then
            fail "${catalog}/${name} did not reach status='${expected}' within ${APPLY_LAND_S}s (last='${got}')"
        fi
        sleep 2
    done
}

# Poll until the (catalog,name) row is ACTIVE with a contentHash different from prev, then echo the
# new hash. The wait-condition for a swap whose status stays ACTIVE before and after the apply, so
# the contentHash advancing is the only signal the new content actually landed (subsumes the
# async-persist window and a possible STRUCTURAL classification of the swap).
await_hash_changed() {
    local catalog="$1" name="$2" prev="$3"
    log "  await /list ${catalog}/${name} contentHash≠${prev:0:8}… (budget ${APPLY_LAND_S}s)"
    local deadline=$(( $(date +%s) + APPLY_LAND_S )) status="" hash=""
    while true; do
        status="$(list_field "${catalog}" "${name}" status)"
        hash="$(list_field "${catalog}" "${name}" contentHash)"
        if [ "${status}" = "ACTIVE" ] && [ -n "${hash}" ] && [ "${hash}" != "${prev}" ]; then
            echo "${hash}"
            return 0
        fi
        if [ "$(date +%s)" -ge "${deadline}" ]; then
            fail "${catalog}/${name} contentHash did not advance past '${prev:0:8}…' within ${APPLY_LAND_S}s (last status='${status}' hash='${hash:0:8}…')"
        fi
        sleep 2
    done
}

apply_rule() {
    local catalog="$1" name="$2" body="$3"
    admin runtime-rule add --catalog "${catalog}" --name "${name}" -f "${body}" >/dev/null \
        || fail "addOrUpdate ${catalog}/${name} from ${body} failed"
}

# Retries 503 cluster_not_ready for up to 60s — the reconciler's peer-refresh
# window briefly returns 503 right after a structural reshape (e.g. LAL
# delete that retires its dispatcher). Mirrors the MAL flow's pattern. Pass the
# runtime-rule subcommand and its flags.
retry_admin() {
    local deadline=$(( $(date +%s) + 60 ))
    local out
    while (( $(date +%s) < deadline )); do
        out="$(admin "$@" 2>&1)" && return 0
        if echo "${out}" | grep -q "HTTP 503"; then
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
    retry_admin runtime-rule inactivate --catalog "${catalog}" --name "${name}" >/dev/null \
        || fail "inactivate ${catalog}/${name} failed"
}

delete_rule() {
    local catalog="$1" name="$2"
    retry_admin runtime-rule delete --catalog "${catalog}" --name "${name}" >/dev/null \
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
until admin runtime-rule list >/dev/null 2>&1; do
    if [ "$(date +%s)" -ge "${deadline}" ]; then fail "OAP not ready after 90s"; fi
    sleep 2
done
log "OAP ready"

# --- Phase 0: apply log-mal aggregation -----------------------------------------------
log "=== Phase 0: apply log-mal aggregation rule ==="
apply_rule "${MAL_CATALOG}" "${MAL_NAME}" "${SEED_MAL}"
await_status "${MAL_CATALOG}" "${MAL_NAME}" "ACTIVE"
log "log-mal → ACTIVE"

# --- Phase 1: apply LAL v1 ------------------------------------------------------------
log "=== Phase 1: apply LAL v1 (extractor stamps step=v1) ==="
apply_rule "${LAL_CATALOG}" "${LAL_NAME}" "${SEED_V1}"
await_status "${LAL_CATALOG}" "${LAL_NAME}" "ACTIVE"
hash_v1="$(list_field "${LAL_CATALOG}" "${LAL_NAME}" contentHash)"
[ -n "${hash_v1}" ] || fail "v1 contentHash empty"
log "v1 → ACTIVE @ ${hash_v1:0:8}…"
await_metric_for_step "v1"

# --- Phase 2: swap to LAL v2 (same key, step flips to v2) -----------------------------
log "=== Phase 2: swap to LAL v2 (extractor stamps step=v2) ==="
apply_rule "${LAL_CATALOG}" "${LAL_NAME}" "${SEED_V2}"
hash_v2="$(await_hash_changed "${LAL_CATALOG}" "${LAL_NAME}" "${hash_v1}")"
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
