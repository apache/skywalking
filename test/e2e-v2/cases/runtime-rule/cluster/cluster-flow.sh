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

# Drives a runtime-rule apply on OAP-1 and asserts OAP-2 converges on the same
# (catalog, name, contentHash) within the reconciler tick window. Run from the
# repo root.
#
# Coverage:
#   1. Apply seed-rule on OAP-1 → ACTIVE
#   2. Wait for OAP-2 to see the rule via /list (one tick = ~30 s default)
#   3. STRUCTURAL update on OAP-1 → re-converge on OAP-2 (different content hash)
#   4. Inactivate on OAP-1 → INACTIVE on OAP-2
#   5. Delete on OAP-1 → row gone on OAP-2
#
# Failures route to stderr so the e2e harness's stdout capture stays clean.

set -euo pipefail

log() { echo "[cluster-flow] $*" >&2; }
fail() { log "FAIL: $*"; exit 1; }

OAP1_PORT="${OAP1_PORT:-17128}"
OAP2_PORT="${OAP2_PORT:-17129}"
OAP1_BASE="http://127.0.0.1:${OAP1_PORT}"
OAP2_BASE="http://127.0.0.1:${OAP2_PORT}"
SEED_DIR="${SEED_DIR:-$(pwd)/test/e2e-v2/cases/runtime-rule/mal-storage/seed-rules}"
SEED_NEW="${SEED_DIR}/seed-rule.yaml"
SEED_STRUCT="${SEED_DIR}/seed-rule-structural.yaml"
CATALOG="otel-rules"
NAME="cluster_rr"

# Two ticks worth — default reconciler interval is 30 s; allow a generous 90 s for
# convergence on a busy CI host.
CONVERGE_TIMEOUT_S="${CONVERGE_TIMEOUT_S:-90}"

[ -f "${SEED_NEW}" ] || fail "seed-rule.yaml missing at ${SEED_NEW}"

list_row() {
    local base="$1"
    curl -fsS "${base}/runtime/rule/list" 2>/dev/null \
        | jq -c '.rules[]
                 | select(.catalog == "'"${CATALOG}"'" and .name == "'"${NAME}"'")
                 | select(.status != "n/a")' \
        | head -1
}

list_status() {
    local base="$1"
    list_row "${base}" | jq -r '.status // empty'
}

list_hash() {
    local base="$1"
    list_row "${base}" | jq -r '.contentHash // empty'
}

await_status() {
    local base="$1" expected="$2" deadline=$(( $(date +%s) + CONVERGE_TIMEOUT_S ))
    while true; do
        local got
        got="$(list_status "${base}")"
        if [ "${got}" = "${expected}" ]; then
            return 0
        fi
        if [ "$(date +%s)" -ge "${deadline}" ]; then
            fail "${base} did not reach status='${expected}' within ${CONVERGE_TIMEOUT_S}s (last='${got}')"
        fi
        sleep 2
    done
}

await_hash() {
    local base="$1" expected_hash="$2" deadline=$(( $(date +%s) + CONVERGE_TIMEOUT_S ))
    while true; do
        local got
        got="$(list_hash "${base}")"
        if [ "${got}" = "${expected_hash}" ]; then
            return 0
        fi
        if [ "$(date +%s)" -ge "${deadline}" ]; then
            fail "${base} did not converge to contentHash='${expected_hash:0:8}…' within ${CONVERGE_TIMEOUT_S}s (last='${got:0:8}…')"
        fi
        sleep 2
    done
}

await_absent() {
    local base="$1" deadline=$(( $(date +%s) + CONVERGE_TIMEOUT_S ))
    while true; do
        if [ -z "$(list_row "${base}")" ]; then
            return 0
        fi
        if [ "$(date +%s)" -ge "${deadline}" ]; then
            fail "${base} did not drop row within ${CONVERGE_TIMEOUT_S}s"
        fi
        sleep 2
    done
}

apply_on() {
    local base="$1" body="$2" extra="${3:-}"
    local query="catalog=${CATALOG}&name=${NAME}"
    if [ -n "${extra}" ]; then
        query="${query}&${extra}"
    fi
    local resp; resp="$(curl -fsS -XPOST -H 'Content-Type: text/plain' \
        --data-binary "@${body}" "${base}/runtime/rule/addOrUpdate?${query}")" \
        || fail "addOrUpdate against ${base} failed"
    echo "${resp}"
}

# --- Wait for both OAPs to come up -------------------------------------------------
log "waiting for OAP-1 (${OAP1_BASE})"
deadline=$(( $(date +%s) + 120 ))
until curl -fsS "${OAP1_BASE}/runtime/rule/list" >/dev/null 2>&1; do
    if [ "$(date +%s)" -ge "${deadline}" ]; then fail "OAP-1 not ready after 120s"; fi
    sleep 2
done
log "waiting for OAP-2 (${OAP2_BASE})"
deadline=$(( $(date +%s) + 120 ))
until curl -fsS "${OAP2_BASE}/runtime/rule/list" >/dev/null 2>&1; do
    if [ "$(date +%s)" -ge "${deadline}" ]; then fail "OAP-2 not ready after 120s"; fi
    sleep 2
done
log "both OAPs ready"

# --- Phase 1: apply on OAP-1, observe convergence on OAP-2 -------------------------
log "=== Phase 1: apply (NEW) on OAP-1 ==="
apply_on "${OAP1_BASE}" "${SEED_NEW}" >/dev/null
await_status "${OAP1_BASE}" "ACTIVE"
hash_initial="$(list_hash "${OAP1_BASE}")"
log "OAP-1 → ACTIVE @ ${hash_initial:0:8}…"
await_status "${OAP2_BASE}" "ACTIVE"
await_hash "${OAP2_BASE}" "${hash_initial}"
log "OAP-2 converged to ${hash_initial:0:8}…"

# --- Phase 2: STRUCTURAL update on OAP-1, observe new hash on OAP-2 ----------------
log "=== Phase 2: STRUCTURAL on OAP-1 ==="
apply_on "${OAP1_BASE}" "${SEED_STRUCT}" "allowStorageChange=true" >/dev/null
hash_struct="$(list_hash "${OAP1_BASE}")"
[ "${hash_struct}" != "${hash_initial}" ] || fail "OAP-1 contentHash unchanged after STRUCTURAL apply"
log "OAP-1 → ACTIVE @ ${hash_struct:0:8}… (was ${hash_initial:0:8}…)"
await_hash "${OAP2_BASE}" "${hash_struct}"
log "OAP-2 converged to ${hash_struct:0:8}…"

# --- Phase 3: inactivate on OAP-1, observe INACTIVE on OAP-2 -----------------------
log "=== Phase 3: /inactivate on OAP-1 ==="
curl -fsS -XPOST "${OAP1_BASE}/runtime/rule/inactivate?catalog=${CATALOG}&name=${NAME}" >/dev/null \
    || fail "inactivate against OAP-1 failed"
await_status "${OAP1_BASE}" "INACTIVE"
log "OAP-1 → INACTIVE"
await_status "${OAP2_BASE}" "INACTIVE"
log "OAP-2 converged to INACTIVE"

# --- Phase 4: delete on OAP-1, observe row gone on OAP-2 ---------------------------
log "=== Phase 4: /delete on OAP-1 ==="
curl -fsS -XPOST "${OAP1_BASE}/runtime/rule/delete?catalog=${CATALOG}&name=${NAME}" >/dev/null \
    || fail "delete against OAP-1 failed"
await_absent "${OAP1_BASE}"
log "OAP-1 → row gone"
await_absent "${OAP2_BASE}"
log "OAP-2 converged: row gone"

log "=== ALL CLUSTER PHASES PASSED ==="
