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

# Runtime-rule lifecycle + cross-node convergence on a Kubernetes (kind) cluster
# deployed in NO-INIT mode — the topology every production SkyWalking cluster runs
# (a one-shot `-Dmode=init` Job creates static schema, the OAP Deployment runs
# `-Dmode=no-init`). This is the deployment that exercises the runtime-rule
# schema-change path on a no-init node: applying a NEW MAL rule must drive the
# backend DDL (create the BanyanDB measure) on the cluster main even though it is a
# no-init OAP — the init Job never knew about a metric created at runtime, so the
# main is the only node that can create it.
#
# Coverage (drive on OAP-1, observe convergence on OAP-2 within a reconciler tick):
#   1. Apply seed-rule on OAP-1 → ACTIVE (NEW: first-time measure creation on no-init)
#   2. OAP-2 converges on the same (status, contentHash)
#   3. STRUCTURAL update on OAP-1 → re-converge on OAP-2 (new metric, new measure)
#   4. Inactivate on OAP-1 → INACTIVE on OAP-2
#   5. Delete on OAP-1 → row gone on OAP-2
#
# The pre-fix bug: on a no-init OAP the apply blocked forever in the storage
# installer's init-node poll loop and never created the measure, so step 1 never
# reached ACTIVE. Reaching ACTIVE here is the end-to-end regression assertion.
#
# Failures route to stderr so the e2e harness's stdout capture stays clean.

set -euo pipefail

log() { echo "[cluster-flow] $*" >&2; }
fail() { log "FAIL: $*"; exit 1; }

NS="${SW_NAMESPACE:-skywalking}"
# Pod-template labels set by the skywalking-helm OAP Deployment (release name = skywalking).
OAP_SELECTOR="${OAP_SELECTOR:-app=skywalking,component=oap,release=skywalking}"
OAP1_PORT="${OAP1_PORT:-17128}"
OAP2_PORT="${OAP2_PORT:-17129}"
OAP1_BASE="http://127.0.0.1:${OAP1_PORT}"
OAP2_BASE="http://127.0.0.1:${OAP2_PORT}"
# Admin REST port inside each OAP container (SW_ADMIN_SERVER=default).
ADMIN_CONTAINER_PORT="${ADMIN_CONTAINER_PORT:-17128}"

SEED_DIR="${SEED_DIR:-$(pwd)/test/e2e-v2/cases/runtime-rule/mal-storage/seed-rules}"
SEED_NEW="${SEED_DIR}/seed-rule.yaml"
SEED_STRUCT="${SEED_DIR}/seed-rule-structural.yaml"
CATALOG="otel-rules"
NAME="cluster_rr"

# Generous on a kind host: two reconciler ticks (default 30 s) + BanyanDB schema
# propagation + RPC jitter.
CONVERGE_TIMEOUT_S="${CONVERGE_TIMEOUT_S:-120}"

[ -f "${SEED_NEW}" ] || fail "seed-rule.yaml missing at ${SEED_NEW}"
[ -f "${SEED_STRUCT}" ] || fail "seed-rule-structural.yaml missing at ${SEED_STRUCT}"

# --- Discover the two OAP pods and port-forward each node's admin REST -------------
# The OAP Deployment runs >= 2 replicas behind one Service; the Service load-balances,
# so addressing individual nodes (to assert cross-node convergence) needs per-pod
# forwards rather than a single Service forward.
log "waiting for >= 2 ready OAP pods in ns/${NS} (selector: ${OAP_SELECTOR})"
deadline=$(( $(date +%s) + 300 ))
PODS=()
while true; do
    # Only Ready pods — a no-init OAP keeps port 12800 closed (and stays NotReady)
    # until the init Job has created the static schema. Read into an array without
    # mapfile/readarray so the script runs under macOS bash 3.2 as well as CI bash 4+.
    PODS=()
    while IFS= read -r _pod; do
        [ -n "${_pod}" ] && PODS+=("${_pod}")
    done < <(kubectl -n "${NS}" get pods -l "${OAP_SELECTOR}" \
        -o jsonpath='{range .items[*]}{range @.status.conditions[?(@.type=="Ready")]}{@.status}{end} {.metadata.name}{"\n"}{end}' \
        2>/dev/null | awk '$1=="True"{print $2}')
    if [ "${#PODS[@]}" -ge 2 ]; then
        break
    fi
    if [ "$(date +%s)" -ge "${deadline}" ]; then
        kubectl -n "${NS}" get pods -l "${OAP_SELECTOR}" >&2 || true
        fail "fewer than 2 ready OAP pods after 300s (got ${#PODS[@]})"
    fi
    sleep 5
done
POD1="${PODS[0]}"
POD2="${PODS[1]}"
log "OAP pods: OAP-1=${POD1} OAP-2=${POD2}"

kubectl -n "${NS}" port-forward "pod/${POD1}" "${OAP1_PORT}:${ADMIN_CONTAINER_PORT}" >/dev/null 2>&1 &
PF1=$!
kubectl -n "${NS}" port-forward "pod/${POD2}" "${OAP2_PORT}:${ADMIN_CONTAINER_PORT}" >/dev/null 2>&1 &
PF2=$!
trap 'kill "${PF1}" "${PF2}" 2>/dev/null || true' EXIT

# --- swctl admin helpers (per-node --admin-url) ------------------------------------
admin() { local base="$1"; shift; swctl --display json --admin-url="${base}" admin "$@"; }

list_row() {
    local base="$1"
    admin "${base}" runtime-rule list 2>/dev/null \
        | jq -c '.rules[]
                 | select(.catalog == "'"${CATALOG}"'" and .name == "'"${NAME}"'")
                 | select(.status != "n/a")' \
        | head -1
}

list_status() { list_row "$1" | jq -r '.status // empty'; }
list_hash() { list_row "$1" | jq -r '.contentHash // empty'; }
list_apply_error() { list_row "$1" | jq -r '.lastApplyError // empty'; }

await_status() {
    local base="$1" expected="$2" deadline=$(( $(date +%s) + CONVERGE_TIMEOUT_S ))
    while true; do
        local got; got="$(list_status "${base}")"
        [ "${got}" = "${expected}" ] && return 0
        if [ "$(date +%s)" -ge "${deadline}" ]; then
            fail "${base} did not reach status='${expected}' within ${CONVERGE_TIMEOUT_S}s (last='${got}', applyError='$(list_apply_error "${base}")')"
        fi
        sleep 2
    done
}

await_hash() {
    local base="$1" expected_hash="$2" deadline=$(( $(date +%s) + CONVERGE_TIMEOUT_S ))
    while true; do
        local got; got="$(list_hash "${base}")"
        [ "${got}" = "${expected_hash}" ] && return 0
        if [ "$(date +%s)" -ge "${deadline}" ]; then
            fail "${base} did not converge to contentHash='${expected_hash:0:8}…' within ${CONVERGE_TIMEOUT_S}s (last='${got:0:8}…')"
        fi
        sleep 2
    done
}

# Poll a node until its row is ACTIVE with a contentHash different from prev, then echo the new hash.
# Needed after a STRUCTURAL apply on an already-ACTIVE row: the status stays ACTIVE across the async
# apply (which returns at FENCING and persists the new content in the background after the schema
# fence), so await_status "ACTIVE" returns on the first iteration with the OLD hash. Gating on the
# contentHash advancing is the only signal the new content is durable on this node.
await_hash_change() {
    local base="$1" prev="$2" deadline=$(( $(date +%s) + CONVERGE_TIMEOUT_S ))
    while true; do
        local got; got="$(list_hash "${base}")"
        [ -n "${got}" ] && [ "${got}" != "${prev}" ] && { echo "${got}"; return 0; }
        if [ "$(date +%s)" -ge "${deadline}" ]; then
            fail "${base} contentHash did not advance past '${prev:0:8}…' within ${CONVERGE_TIMEOUT_S}s (last='${got:0:8}…', applyError='$(list_apply_error "${base}")')"
        fi
        sleep 2
    done
}

await_absent() {
    local base="$1" deadline=$(( $(date +%s) + CONVERGE_TIMEOUT_S ))
    while true; do
        [ -z "$(list_row "${base}")" ] && return 0
        if [ "$(date +%s)" -ge "${deadline}" ]; then
            fail "${base} did not drop row within ${CONVERGE_TIMEOUT_S}s"
        fi
        sleep 2
    done
}

assert_no_apply_error() {
    local base="$1" err; err="$(list_apply_error "${base}")"
    [ -z "${err}" ] || fail "${base} reports lastApplyError='${err}' (no-init schema change failed)"
}

apply_on() {
    local base="$1" body="$2" extra="${3:-}"
    local -a flags=(--catalog "${CATALOG}" --name "${NAME}" -f "${body}")
    [[ "${extra}" == *allowStorageChange=true* ]] && flags+=(--allow-storage-change)
    admin "${base}" runtime-rule add "${flags[@]}" || fail "addOrUpdate against ${base} failed"
}

# --- Wait for both OAPs' admin REST to answer through the forwards -----------------
for pair in "OAP-1 ${OAP1_BASE}" "OAP-2 ${OAP2_BASE}"; do
    set -- ${pair}; label="$1"; base="$2"
    log "waiting for ${label} admin REST (${base})"
    deadline=$(( $(date +%s) + 120 ))
    until admin "${base}" runtime-rule list >/dev/null 2>&1; do
        if [ "$(date +%s)" -ge "${deadline}" ]; then fail "${label} admin not ready after 120s"; fi
        sleep 2
    done
done
log "both OAP admin endpoints ready"

# --- Phase 1: apply NEW on OAP-1 — first-time measure creation on a no-init node ----
log "=== Phase 1: apply (NEW) on OAP-1 — exercises no-init schema creation ==="
apply_on "${OAP1_BASE}" "${SEED_NEW}" >/dev/null
await_status "${OAP1_BASE}" "ACTIVE"
assert_no_apply_error "${OAP1_BASE}"
hash_initial="$(list_hash "${OAP1_BASE}")"
log "OAP-1 → ACTIVE @ ${hash_initial:0:8}… (measure created on a no-init OAP)"
await_status "${OAP2_BASE}" "ACTIVE"
await_hash "${OAP2_BASE}" "${hash_initial}"
log "OAP-2 converged to ${hash_initial:0:8}…"

# --- Phase 2: STRUCTURAL update on OAP-1 — second measure created on no-init --------
log "=== Phase 2: STRUCTURAL on OAP-1 ==="
apply_on "${OAP1_BASE}" "${SEED_STRUCT}" "allowStorageChange=true" >/dev/null
# Structural apply is async: wait for OAP-1's own contentHash to ADVANCE (the row is ACTIVE before
# and after, so await_status "ACTIVE" alone would read the stale pre-apply hash) before capturing it.
hash_struct="$(await_hash_change "${OAP1_BASE}" "${hash_initial}")"
assert_no_apply_error "${OAP1_BASE}"
[ "${hash_struct}" != "${hash_initial}" ] || fail "OAP-1 contentHash unchanged after STRUCTURAL apply"
log "OAP-1 → ACTIVE @ ${hash_struct:0:8}… (was ${hash_initial:0:8}…)"
await_hash "${OAP2_BASE}" "${hash_struct}"
log "OAP-2 converged to ${hash_struct:0:8}…"

# --- Phase 3: inactivate on OAP-1, observe INACTIVE on OAP-2 -----------------------
log "=== Phase 3: /inactivate on OAP-1 ==="
admin "${OAP1_BASE}" runtime-rule inactivate --catalog "${CATALOG}" --name "${NAME}" >/dev/null \
    || fail "inactivate against OAP-1 failed"
await_status "${OAP1_BASE}" "INACTIVE"
log "OAP-1 → INACTIVE"
await_status "${OAP2_BASE}" "INACTIVE"
log "OAP-2 converged to INACTIVE"

# --- Phase 4: delete on OAP-1, observe row gone on OAP-2 ---------------------------
log "=== Phase 4: /delete on OAP-1 ==="
admin "${OAP1_BASE}" runtime-rule delete --catalog "${CATALOG}" --name "${NAME}" >/dev/null \
    || fail "delete against OAP-1 failed"
await_absent "${OAP1_BASE}"
log "OAP-1 → row gone"
await_absent "${OAP2_BASE}"
log "OAP-2 converged: row gone"

# --- Phase 5: forward-path coverage — drive a write on OAP-2 -----------------------
# Phases 1-4 drove OAP-1; whether that exercised the cross-node Forward depends on which
# node the hash-router picked as main. Driving a write on OAP-2 as well guarantees the
# Forward path is exercised regardless: whichever of OAP-1 / OAP-2 is NOT the main forwards
# the write to the main. This is the path that regressed on Kubernetes — every replica
# shared selfNodeId=0.0.0.0_11800 (the 0.0.0.0 gRPC bind host), so the main's self-loop
# guard rejected a legitimate forward as HTTP 400 forward_self_loop. With a unique per-pod
# id the forward completes; a failure here (esp. forward_self_loop) re-opens that bug.
NAME_B="cluster_rr_fwd"
log "=== Phase 5: apply on OAP-2 (guarantees cross-node Forward coverage) ==="
admin "${OAP2_BASE}" runtime-rule add --catalog "${CATALOG}" --name "${NAME_B}" -f "${SEED_NEW}" >/dev/null \
    || fail "addOrUpdate on OAP-2 failed — cross-node Forward broken (e.g. forward_self_loop)?"
b_deadline=$(( $(date +%s) + CONVERGE_TIMEOUT_S ))
while true; do
    b_status="$(admin "${OAP2_BASE}" runtime-rule list 2>/dev/null \
        | jq -r '.rules[] | select(.catalog=="'"${CATALOG}"'" and .name=="'"${NAME_B}"'") | .status' | head -1)"
    [ "${b_status}" = "ACTIVE" ] && break
    [ "$(date +%s)" -ge "${b_deadline}" ] && fail "OAP-2 write did not reach ACTIVE within ${CONVERGE_TIMEOUT_S}s (last='${b_status}')"
    sleep 2
done
log "OAP-2 write → ACTIVE (cross-node Forward path OK)"
# Cleanup also forwards from OAP-2: inactivate (soft-pause) is required before delete,
# so this exercises the Forward path for the inactivate + delete operations too.
admin "${OAP2_BASE}" runtime-rule inactivate --catalog "${CATALOG}" --name "${NAME_B}" >/dev/null \
    || fail "inactivate of ${NAME_B} on OAP-2 failed"
admin "${OAP2_BASE}" runtime-rule delete --catalog "${CATALOG}" --name "${NAME_B}" >/dev/null \
    || fail "cleanup delete of ${NAME_B} on OAP-2 failed"
log "Phase 5 cleanup done (inactivate + delete forwarded OK)"

log "=== ALL CLUSTER (kind) PHASES PASSED ==="
