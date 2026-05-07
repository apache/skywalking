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

# Drives the DSL debug API in LAL block-granularity mode against the SAME
# multi-statement runtime-rule the lal-statement case uses. The block-mode
# session collapses the per-statement probes into a single block synopsis,
# so comparing the two captures shows the granularity invariant directly:
# block produces input + (at most one) function + output; statement
# produces input + N function (one per statement) + output.
#
# Asserts:
#   - .rule envelope at top level
#   - .nodes[].records[].samples[] shape (no .stage on samples)
#   - block-mode invariant: NO sample has a non-empty .sourceText pointing
#     at an extractor statement (.sourceLine pointing into the body) — that's
#     the per-statement appendLine probe and it's silenced in block mode
#   - terminal sample is type=output

set -euo pipefail

log() { echo "[lal-block-flow] $*" >&2; }
fail() { log "FAIL: $*"; exit 1; }

OAP_HOST="${OAP_HOST:-127.0.0.1}"
OAP_REST_PORT="${OAP_REST_PORT:-17128}"
OAP_BASE="http://${OAP_HOST}:${OAP_REST_PORT}"

SETTLE_SECONDS="${SETTLE_SECONDS:-180}"

# Reuse the same multi-statement seed the statement case uses, so comparing
# /tmp/dsldbg-lal-block-response.json with /tmp/dsldbg-lal-statement-response.json
# shows the granularity diff with all other inputs identical.
SEED_DIR="${SEED_DIR:-$(pwd)/test/e2e-v2/cases/dsl-debugging/lal-statement/seed-rules}"
SEED_LAL="${SEED_DIR}/lal-multistatement.yaml"
RR_CATALOG="lal"
RR_NAME="e2e-dsldbg-statement"
DBG_CATALOG="lal"
DBG_NAME="${RR_NAME}"
DBG_RULE_NAME="${RR_NAME}"
CLIENT_ID="e2e-dsldbg-lal-block-1"

[ -f "${SEED_LAL}" ] || fail "seed missing at ${SEED_LAL}"

log "waiting for OAP admin port"
deadline=$(( $(date +%s) + 90 ))
until curl -fsS "${OAP_BASE}/dsl-debugging/status" >/dev/null 2>&1; do
    if [ "$(date +%s)" -ge "${deadline}" ]; then fail "OAP admin not ready after 90s"; fi
    sleep 2
done

# --- Phase 0: status check ------------------------------------------------------------
log "=== Phase 0: /dsl-debugging/status ==="
status_body="$(curl -fsS "${OAP_BASE}/dsl-debugging/status")"
echo "${status_body}" | jq -e '.injectionEnabled == true' >/dev/null \
    || fail "injectionEnabled is not true"

# --- Phase 1: apply runtime-rule LAL --------------------------------------------------
log "=== Phase 1: apply runtime-rule LAL seed (shared with lal-statement case) ==="
curl -fsS -XPOST -H 'Content-Type: text/plain' \
    --data-binary "@${SEED_LAL}" \
    "${OAP_BASE}/runtime/rule/addOrUpdate?catalog=${RR_CATALOG}&name=${RR_NAME}" >/dev/null \
    || fail "addOrUpdate ${RR_CATALOG}/${RR_NAME} failed"
deadline=$(( $(date +%s) + 60 ))
status=""
while (( $(date +%s) < deadline )); do
    status="$(curl -fsS "${OAP_BASE}/runtime/rule/list" 2>/dev/null \
        | jq -r --arg c "${RR_CATALOG}" --arg n "${RR_NAME}" \
            '.rules[] | select(.catalog == $c and .name == $n) | .status' | head -1)"
    [ "${status}" = "ACTIVE" ] && break
    sleep 2
done
[ "${status}" = "ACTIVE" ] || fail "runtime-rule did not reach ACTIVE within 60s (saw '${status}')"
log "✓ seed applied: ${RR_CATALOG}/${RR_NAME} ACTIVE"

# --- Phase 2: install with granularity=block (default) --------------------------------
log "=== Phase 2: install session with granularity=block ==="
install_body="$(curl -fsS -XPOST \
    "${OAP_BASE}/dsl-debugging/session?catalog=${DBG_CATALOG}&name=${DBG_NAME}&ruleName=${DBG_RULE_NAME}&clientId=${CLIENT_ID}&granularity=block")"
log "  install → ${install_body}"
SESSION_ID="$(echo "${install_body}" | jq -r '.sessionId // empty')"
[ -n "${SESSION_ID}" ] || fail "install did not return sessionId"
echo "${install_body}" | jq -e '.granularity == "block"' >/dev/null \
    || fail "expected granularity=block, got: $(echo "${install_body}" | jq -r .granularity)"
log "✓ session installed: ${SESSION_ID}, granularity=block"

# --- Phase 3: wait for capture --------------------------------------------------------
log "=== Phase 3: wait for log emitter to drive captures (budget ${SETTLE_SECONDS}s) ==="
deadline=$(( $(date +%s) + SETTLE_SECONDS ))
records_count=0
collect_body=""
while (( $(date +%s) < deadline )); do
    collect_body="$(curl -fsS "${OAP_BASE}/dsl-debugging/session/${SESSION_ID}")"
    records_count="$(echo "${collect_body}" | jq '[.nodes[].records[]] | length')"
    if [ "${records_count}" -gt 0 ]; then
        # Block mode: a fully walked record has at minimum input + output samples.
        ready="$(echo "${collect_body}" | jq '[.nodes[].records[] | select((.samples | length) >= 2)] | length')"
        if [ "${ready}" -gt 0 ]; then
            break
        fi
    fi
    sleep 5
done
[ "${records_count}" -gt 0 ] \
    || fail "no records captured within ${SETTLE_SECONDS}s — payload: ${collect_body}"
log "  captured ${records_count} record(s)"
echo "${collect_body}" > /tmp/dsldbg-lal-block-response.json

# --- Phase 4: assert envelope shape ---------------------------------------------------
log "=== Phase 4: assert envelope shape ==="
log "  collect payload (truncated 800c): ${collect_body:0:800}"

empty_dsl="$(echo "${collect_body}" | jq '[.nodes[].records[] | select((.dsl // "") == "")] | length')"
[ "${empty_dsl}" = "0" ] || fail "${empty_dsl} record(s) carry empty .dsl"

# --- Phase 5: assert block-mode sample shape ------------------------------------------
log "=== Phase 5: assert block-mode sample shape ==="

# No legacy stage / per-record content
legacy_stage="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[] | select(has("stage"))] | length')"
[ "${legacy_stage}" = "0" ] || fail "${legacy_stage} sample(s) carry legacy .stage"
legacy_content="$(echo "${collect_body}" | jq '[.nodes[].records[] | select(has("content"))] | length')"
[ "${legacy_content}" = "0" ] || fail "${legacy_content} record(s) carry legacy .content"

# Block-mode invariant: NO per-statement samples leak through. The
# per-statement probe (appendLine) emits TYPE_FUNCTION samples whose
# sourceText is the verbatim DSL statement (e.g. "tag stage: 'extractor'")
# AND whose sourceLine points into the rule body. Block mode silences
# those — only block-level synopses (sourceText empty, sourceLine 0)
# are allowed.
per_statement_leaked="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[]
    | select(.type == "function" and .sourceText != "" and (.sourceLine // 0) > 0)] | length')"
[ "${per_statement_leaked}" = "0" ] \
    || fail "${per_statement_leaked} per-statement sample(s) leaked through block mode"

# Every record ends with output (sink/metricSink terminal probe).
last_terminal="$(echo "${collect_body}" | jq '[.nodes[].records[] | .samples[-1]
    | select(.type == "output")] | length')"
[ "${last_terminal}" = "${records_count}" ] \
    || fail "expected every record to end with output — got ${last_terminal}/${records_count}"

# continueOn boolean on every sample
non_bool="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[] | select((.continueOn | type) != "boolean")] | length')"
[ "${non_bool}" = "0" ] || fail "${non_bool} sample(s) have non-boolean continueOn"

log "✓ block-mode shape valid (${records_count} records; per-statement probes silenced)"

# --- Phase 6: stop session ------------------------------------------------------------
log "=== Phase 6: stop session ==="
stop_body="$(curl -fsS -XPOST "${OAP_BASE}/dsl-debugging/session/${SESSION_ID}/stop")"
echo "${stop_body}" | jq -e '.localStopped == true' >/dev/null \
    || fail "localStopped != true"

# --- Phase 7: cleanup runtime-rule ----------------------------------------------------
log "=== Phase 7: cleanup runtime-rule ==="
curl -fsS -XPOST "${OAP_BASE}/runtime/rule/inactivate?catalog=${RR_CATALOG}&name=${RR_NAME}" >/dev/null || true
curl -fsS -XPOST "${OAP_BASE}/runtime/rule/delete?catalog=${RR_CATALOG}&name=${RR_NAME}" >/dev/null || true

log "=== ALL LAL BLOCK FLOW PHASES PASSED ==="
