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

# Drives the DSL debug API for OAL against the shipped ServiceRelation
# dispatcher. The simple/jdk-style provider/consumer agents emit trace
# segments → ServiceRelation source events → the debug session captures
# their pipeline samples (one record = one ServiceRelation walked end-to-end).
#
# Asserts the post-reshape wire contract:
#   - .rule envelope at top level (name + dsl)
#   - .nodes[].records[].samples[] shape (no .stage on samples)
#   - each sample has sourceText, continueOn, payload, sourceLine
#   - filter clauses appear as samples whose sourceText starts with .filter
#   - aggregation samples carry the verbatim function (e.g. cpm())
#   - source samples carry rich ServiceRelation fields in payload.fields
#   - per-metric gate isolation: only the SERVER filter clause leaks through

set -euo pipefail

log() { echo "[oal-flow] $*" >&2; }
fail() { log "FAIL: $*"; exit 1; }

OAP_HOST="${OAP_HOST:-127.0.0.1}"
OAP_REST_PORT="${OAP_REST_PORT:-17128}"
OAP_BASE="http://${OAP_HOST}:${OAP_REST_PORT}"

SETTLE_SECONDS="${SETTLE_SECONDS:-300}"

# OAL gate is per-metric. We target service_relation_server_cpm — a shipped
# rule from core.oal that the simple/jdk-style provider/consumer trace flow
# fires reliably. The session captures only this metric's pipeline; sibling
# rules (server_call_sla, client_cpm, etc.) on the same dispatcher stay silent.
CATALOG="oal"
NAME="core.oal"
METRIC="service_relation_server_cpm"
CLIENT_ID="e2e-dsldbg-oal-1"

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

# --- Phase 1: install session ---------------------------------------------------------
log "=== Phase 1: install session on (${CATALOG}, ${NAME}, ${METRIC}) ==="
install_body="$(curl -fsS -XPOST \
    "${OAP_BASE}/dsl-debugging/session?catalog=${CATALOG}&name=${NAME}&ruleName=${METRIC}&clientId=${CLIENT_ID}")"
log "  install → ${install_body}"
SESSION_ID="$(echo "${install_body}" | jq -r '.sessionId // empty')"
[ -n "${SESSION_ID}" ] || fail "install did not return sessionId — body: ${install_body}"
log "✓ session installed: ${SESSION_ID}"

# --- Phase 2: wait for capture --------------------------------------------------------
log "=== Phase 2: wait for trace flow + OAL pipeline (budget ${SETTLE_SECONDS}s) ==="
deadline=$(( $(date +%s) + SETTLE_SECONDS ))
records_count=0
collect_body=""
while (( $(date +%s) < deadline )); do
    collect_body="$(curl -fsS "${OAP_BASE}/dsl-debugging/session/${SESSION_ID}")"
    records_count="$(echo "${collect_body}" | jq '[.nodes[].records[]] | length')"
    if [ "${records_count}" -gt 0 ]; then
        # Wait for at least one execution record with both source + filter samples.
        complete="$(echo "${collect_body}" | jq '[.nodes[].records[] | select((.samples | length) >= 2)] | length')"
        if [ "${complete}" -gt 0 ]; then
            break
        fi
    fi
    sleep 5
done
[ "${records_count}" -gt 0 ] \
    || fail "no records captured within ${SETTLE_SECONDS}s — payload: ${collect_body}"
log "  captured ${records_count} record(s)"
echo "${collect_body}" > /tmp/dsldbg-oal-response.json

# --- Phase 3: assert envelope shape ---------------------------------------------------
log "=== Phase 3: assert envelope shape ==="
log "  collect payload (truncated 800c): ${collect_body:0:800}"

# Per-record dsl: every record carries the verbatim rule source as of capture.
# Per-record (not envelope-level) so hot-update mid-session stays unambiguous.
empty_dsl="$(echo "${collect_body}" | jq '[.nodes[].records[] | select((.dsl // "") == "")] | length')"
[ "${empty_dsl}" = "0" ] \
    || fail "${empty_dsl} record(s) carry empty .dsl — should be verbatim core.oal source"
echo "${collect_body}" | jq -e --arg n "service_relation_server_cpm" \
    '[.nodes[].records[] | select(.dsl | contains($n))] | length > 0' >/dev/null \
    || fail "no record's .dsl contains the bound rule name"

# slice-level capturedAt
echo "${collect_body}" | jq -e '.capturedAt > 0' >/dev/null \
    || fail "envelope-level capturedAt missing"

# Each record has a startedAtMs and a non-empty samples[]
missing_started="$(echo "${collect_body}" | jq '[.nodes[].records[] | select((.startedAtMs // 0) <= 0)] | length')"
[ "${missing_started}" = "0" ] \
    || fail "${missing_started} record(s) missing startedAtMs"
empty_samples="$(echo "${collect_body}" | jq '[.nodes[].records[] | select((.samples | length) == 0)] | length')"
[ "${empty_samples}" = "0" ] \
    || fail "${empty_samples} record(s) carry zero samples — terminal probe published an empty execution"

# --- Phase 4: assert sample shape -----------------------------------------------------
log "=== Phase 4: assert sample shape ==="

# Every sample has sourceText, continueOn, payload, sourceLine.
total_samples="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[]] | length')"
[ "${total_samples}" -gt 0 ] || fail "no samples captured"
log "  total samples across all records: ${total_samples}"

# No legacy 'stage' field anywhere on samples
legacy_stage="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[] | select(has("stage"))] | length')"
[ "${legacy_stage}" = "0" ] \
    || fail "${legacy_stage} sample(s) still carry the legacy .stage field"

# No legacy per-record 'content' field
legacy_content="$(echo "${collect_body}" | jq '[.nodes[].records[] | select(has("content"))] | length')"
[ "${legacy_content}" = "0" ] \
    || fail "${legacy_content} record(s) still carry legacy .content (renamed to .dsl)"

# Empty sourceText is forbidden
empty_st="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[] | select((.sourceText // "") == "")] | length')"
[ "${empty_st}" = "0" ] \
    || fail "${empty_st} sample(s) carry empty sourceText"

# Filter samples — verbatim ANTLR slice, leading dot included.
expected_filter='.filter(detectPoint == DetectPoint.SERVER)'
filter_match="$(echo "${collect_body}" | jq -r --arg s "${expected_filter}" \
    '[.nodes[].records[].samples[] | select(.sourceText == $s)] | length')"
[ "${filter_match}" -gt 0 ] \
    || fail "no sample carries verbatim OAL filter slice '${expected_filter}'"
log "  ✓ filter sample carries verbatim ANTLR slice (leading dot included)"

# Per-metric gate isolation: CLIENT filter MUST NOT leak.
client_leak="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[] | select(.sourceText == ".filter(detectPoint == DetectPoint.CLIENT)")] | length')"
[ "${client_leak}" = "0" ] \
    || fail "${client_leak} CLIENT-filter sample(s) leaked into the SERVER-cpm session"

# Aggregation sample — verbatim cpm() must appear.
cpm_match="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[] | select(.sourceText == "cpm()")] | length')"
[ "${cpm_match}" -gt 0 ] \
    || fail "no aggregation sample carries verbatim 'cpm()'"

# Source sample — payload exposes rich ServiceRelation fields.
source_text_first="$(echo "${collect_body}" | jq -r '.nodes[].records[0].samples[0].sourceText')"
log "  first sample sourceText: ${source_text_first}"
src_payload="$(echo "${collect_body}" | jq '.nodes[].records[0].samples[0].payload')"
log "  first sample payload: ${src_payload}"

# Type field
type_match="$(echo "${collect_body}" | jq -r '.nodes[].records[0].samples[0].payload.type')"
[ "${type_match}" = "ServiceRelation" ] \
    || fail "first sample payload.type is '${type_match}', expected ServiceRelation"

# Rich fields surface from ServiceRelation.toDebugJson()
for field in sourceServiceName destServiceName sourceLayer destLayer detectPoint; do
    has_field="$(echo "${collect_body}" | jq --arg f "${field}" '[.nodes[].records[].samples[] | select(.payload.fields[$f] != null)] | length')"
    [ "${has_field}" -gt 0 ] \
        || fail "no source sample exposes ServiceRelation.${field} via toDebugJson"
done
log "  ✓ ServiceRelation toDebugJson exposes sourceServiceName, destServiceName, layers, detectPoint"

# continueOn is a boolean on every sample.
non_bool="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[] | select((.continueOn | type) != "boolean")] | length')"
[ "${non_bool}" = "0" ] \
    || fail "${non_bool} sample(s) have non-boolean continueOn"

# At least one filter sample with continueOn=true (a SERVER source survived).
kept_filter="$(echo "${collect_body}" | jq --arg s "${expected_filter}" \
    '[.nodes[].records[].samples[] | select(.sourceText == $s and .continueOn == true)] | length')"
[ "${kept_filter}" -gt 0 ] \
    || fail "no SERVER filter sample with continueOn=true — the rule never fired through"

# sourceLine populated on every OAL sample (every OAL rule maps to a single line).
missing_line="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[] | select((.sourceLine // 0) <= 0)] | length')"
[ "${missing_line}" = "0" ] \
    || fail "${missing_line} sample(s) missing sourceLine — should point to core.oal line"

log "✓ OAL shape valid (${records_count} records, ${total_samples} samples, source/filter/cpm covered)"

# --- Phase 5: stop session ------------------------------------------------------------
log "=== Phase 5: stop session ==="
curl -fsS -XPOST "${OAP_BASE}/dsl-debugging/session/${SESSION_ID}/stop" \
    | jq -e '.localStopped == true' >/dev/null \
    || fail "localStopped != true"

log "=== ALL OAL FLOW PHASES PASSED ==="
