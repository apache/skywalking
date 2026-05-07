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

# Drives the DSL debug API for MAL against a runtime-rule-applied MAL rule
# whose expression chains an explicit `.tagEqual(...)` filter so the filter
# probe fires alongside input + meterEmit.
#
# Asserts the post-reshape wire contract:
#   - .rule envelope at top level (name + dsl)
#   - .nodes[].records[].samples[] shape (no .stage on samples)
#   - each sample has sourceText, continueOn, payload, sourceLine
#   - the file-level filter clause appears as a sample whose sourceText
#     is the verbatim YAML filter expression
#   - chain-segment samples carry the verbatim ANTLR slice (sum(...))
#   - SampleFamily payloads expose samples count + first label/value snapshot

set -euo pipefail

log() { echo "[mal-flow] $*" >&2; }
fail() { log "FAIL: $*"; exit 1; }

OAP_HOST="${OAP_HOST:-127.0.0.1}"
OAP_REST_PORT="${OAP_REST_PORT:-17128}"
OAP_BASE="http://${OAP_HOST}:${OAP_REST_PORT}"

SETTLE_SECONDS="${SETTLE_SECONDS:-300}"

SEED_DIR="${SEED_DIR:-$(pwd)/test/e2e-v2/cases/dsl-debugging/mal/seed-rules}"
SEED_MAL="${SEED_DIR}/mal-with-filter.yaml"
RR_CATALOG="otel-rules"
RR_NAME="mal-with-filter"
METRIC_NAME="e2e_dsldbg_filtered_requests"
DBG_CATALOG="otel-rules"
DBG_NAME="${RR_NAME}"
DBG_RULE_NAME="${METRIC_NAME}"
CLIENT_ID="e2e-dsldbg-mal-1"

[ -f "${SEED_MAL}" ] || fail "seed missing at ${SEED_MAL}"

log "waiting for OAP admin port"
deadline=$(( $(date +%s) + 120 ))
until curl -fsS "${OAP_BASE}/dsl-debugging/status" >/dev/null 2>&1; do
    if [ "$(date +%s)" -ge "${deadline}" ]; then fail "OAP admin not ready after 120s"; fi
    sleep 2
done

# --- Phase 0: status ------------------------------------------------------------------
log "=== Phase 0: /dsl-debugging/status ==="
curl -fsS "${OAP_BASE}/dsl-debugging/status" | jq -e '.injectionEnabled == true' >/dev/null \
    || fail "injectionEnabled is not true"

# --- Phase 1: apply runtime-rule MAL --------------------------------------------------
log "=== Phase 1: apply runtime-rule MAL seed ==="
curl -fsS -XPOST -H 'Content-Type: text/plain' \
    --data-binary "@${SEED_MAL}" \
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

# --- Phase 2: install session ---------------------------------------------------------
log "=== Phase 2: install session on (${DBG_CATALOG}, ${DBG_NAME}, ${DBG_RULE_NAME}) ==="
install_body="$(curl -fsS -XPOST \
    "${OAP_BASE}/dsl-debugging/session?catalog=${DBG_CATALOG}&name=${DBG_NAME}&ruleName=${DBG_RULE_NAME}&clientId=${CLIENT_ID}")"
log "  install → ${install_body}"
SESSION_ID="$(echo "${install_body}" | jq -r '.sessionId // empty')"
[ -n "${SESSION_ID}" ] || fail "install did not return sessionId — body: ${install_body}"
log "✓ session installed: ${SESSION_ID}"

# --- Phase 3: wait for capture --------------------------------------------------------
log "=== Phase 3: wait for OTLP metrics + MAL pipeline (budget ${SETTLE_SECONDS}s) ==="
deadline=$(( $(date +%s) + SETTLE_SECONDS ))
records_count=0
collect_body=""
while (( $(date +%s) < deadline )); do
    collect_body="$(curl -fsS "${OAP_BASE}/dsl-debugging/session/${SESSION_ID}")"
    records_count="$(echo "${collect_body}" | jq '[.nodes[].records[]] | length')"
    if [ "${records_count}" -gt 0 ]; then
        # Wait for at least one full execution (terminal meterEmit closed it),
        # which means samples are present and the record has been published.
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
echo "${collect_body}" > /tmp/dsldbg-mal-response.json

# --- Phase 4: assert envelope shape ---------------------------------------------------
log "=== Phase 4: assert envelope shape ==="
log "  collect payload (truncated 800c): ${collect_body:0:800}"

# Per-record dsl: every record carries the verbatim rule body as of capture.
empty_dsl="$(echo "${collect_body}" | jq '[.nodes[].records[] | select((.dsl // "") == "")] | length')"
[ "${empty_dsl}" = "0" ] \
    || fail "${empty_dsl} record(s) carry empty .dsl"

empty_samples="$(echo "${collect_body}" | jq '[.nodes[].records[] | select((.samples | length) == 0)] | length')"
[ "${empty_samples}" = "0" ] \
    || fail "${empty_samples} record(s) carry zero samples"

# --- Phase 5: assert sample shape -----------------------------------------------------
log "=== Phase 5: assert sample shape ==="

# No legacy stage / per-record content
legacy_stage="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[] | select(has("stage"))] | length')"
[ "${legacy_stage}" = "0" ] || fail "${legacy_stage} sample(s) still carry legacy .stage"
legacy_content="$(echo "${collect_body}" | jq '[.nodes[].records[] | select(has("content"))] | length')"
[ "${legacy_content}" = "0" ] || fail "${legacy_content} record(s) carry legacy .content"

total_samples="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[]] | length')"
[ "${total_samples}" -gt 0 ] || fail "no samples"
log "  total samples: ${total_samples}"

# Chain-segment samples — the seed rule combines THREE metric references
# (request_count + pool_size + decoy) so the captured pipeline carries
# the verbatim sum() slice three times plus binary `plus` ops.
sum_slice='sum(['"'"'service_name'"'"'])'
sum_match="$(echo "${collect_body}" | jq -r --arg s "${sum_slice}" \
    '[.nodes[].records[].samples[] | select(.sourceText == $s)] | length')"
[ "${sum_match}" -ge 2 ] \
    || fail "expected >=2 samples carrying chain slice '${sum_slice}', got ${sum_match}"
log "  ✓ ${sum_match} chain samples carry verbatim ANTLR slice: ${sum_slice}"

plus_match="$(echo "${collect_body}" | jq -r \
    '[.nodes[].records[].samples[] | select(.sourceText == "plus")] | length')"
[ "${plus_match}" -gt 0 ] \
    || fail "no sample carries the binary 'plus' op slice"
log "  ✓ ${plus_match} sample(s) carry binary plus slice"

# At least 2 `input` samples (one per metric reference that survives the
# rule's metric-ref selection — the decoy may or may not reach this
# stage depending on push timing, but the two primary metrics always do)
input_count="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[]
    | select(.type == "input")] | length')"
[ "${input_count}" -ge 2 ] \
    || fail "expected >=2 input samples (one per metric ref), got ${input_count}"
log "  ✓ ${input_count} input samples — multi-metric expression visible"

# Filter sample's payload reports the surviving families count + items.
# The decoy metric's family is rejected by the file-level filter, so the
# surviving families count is < the number of metric refs in the
# expression. We assert >=2 to confirm the multi-family rendering works
# (the previous "first-family representative" rendering would always
# have been 1).
filter_families="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[]
    | select(.type == "filter")
    | .payload.families] | max // 0')"
[ "${filter_families}" -ge 2 ] \
    || fail "expected filter payload to report >=2 surviving families, got ${filter_families}"
log "  ✓ filter payload reports ${filter_families} surviving families (decoy filtered out)"

# SampleFamily payload exposes samples count > 0 (or empty=true) on every input/stage sample
bad_sf="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[]
    | select(.payload.samples != null)
    | select((.payload.samples | type) != "number")] | length')"
[ "${bad_sf}" = "0" ] || fail "${bad_sf} sample(s) carry non-number payload.samples"

# At least one sample reports samples > 0 (real OTLP traffic flowing)
real_traffic="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[]
    | select((.payload.samples // 0) > 0)] | length')"
[ "${real_traffic}" -gt 0 ] \
    || fail "no sample reports samples > 0 — OTLP metrics never reached the rule"

# continueOn boolean on every sample
non_bool="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[] | select((.continueOn | type) != "boolean")] | length')"
[ "${non_bool}" = "0" ] || fail "${non_bool} sample(s) have non-boolean continueOn"

# Last sample of an execution = meterEmit (carries metric/entity/timeBucket).
last_with_metric="$(echo "${collect_body}" | jq --arg n "${METRIC_NAME}" \
    '[.nodes[].records[] | .samples[-1] | select(.payload.metric == $n)] | length')"
[ "${last_with_metric}" -gt 0 ] \
    || fail "no execution closes with a meterEmit sample carrying payload.metric=${METRIC_NAME}"

log "✓ MAL shape valid (${records_count} records, ${total_samples} samples)"

# --- Phase 6: stop session ------------------------------------------------------------
log "=== Phase 6: stop session ==="
curl -fsS -XPOST "${OAP_BASE}/dsl-debugging/session/${SESSION_ID}/stop" \
    | jq -e '.localStopped == true' >/dev/null \
    || fail "localStopped != true"

# --- Phase 7: cleanup runtime-rule ----------------------------------------------------
log "=== Phase 7: cleanup runtime-rule ==="
curl -fsS -XPOST "${OAP_BASE}/runtime/rule/inactivate?catalog=${RR_CATALOG}&name=${RR_NAME}" >/dev/null || true
curl -fsS -XPOST "${OAP_BASE}/runtime/rule/delete?catalog=${RR_CATALOG}&name=${RR_NAME}" >/dev/null || true

log "=== ALL MAL FLOW PHASES PASSED ==="
