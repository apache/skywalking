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
#
# Inspect API e2e — existing (OAP-aware) + new (foreign-metric) paths against two
# independent OAPs that SHARE one storage backend (no cluster).
#
#   OAP-A loads otel-rules/inspect-e2e.yaml and turns the OTLP emitter's
#         e2e_rr_pool_size into the Service metric meter_inspect_e2e_pool.
#   OAP-B loads NO such rule, so meter_inspect_e2e_pool is "foreign" to it —
#         absent from its local registry but present in the shared storage.
#
# Asserts:
#   1. OAP-A /inspect/metrics lists the metric, /inspect/entities (AWARE/old path)
#      returns inspect-e2e-svc with an mqeEntity.
#   2. OAP-B /inspect/metrics does NOT list it.
#   3. OAP-B /inspect/entities WITHOUT valueColumn/valueType → "unknown locally".
#   4. OAP-B /inspect/entities WITH valueColumn/valueType (FOREIGN/new path) returns
#      the SAME entity, scope=null, no mqeEntity.
#   5. OAP-B POST /inspect/values WITH foreignMetrics (FOREIGN VALUE/new path) returns
#      the metric's value series (42).
set -euo pipefail

A_REST="${A_REST:-http://127.0.0.1:17128}"
B_REST="${B_REST:-http://127.0.0.1:17129}"
METRIC="meter_inspect_e2e_pool"
SVC="inspect-e2e-svc"
SETTLE="${SETTLE:-360}"

log()  { echo "[inspect-flow] $*" >&2; }
fail() { echo "[inspect-flow] FAIL: $*" >&2; exit 1; }

a_inspect() { swctl --display json --admin-url="${A_REST}" admin inspect "$@"; }
b_inspect() { swctl --display json --admin-url="${B_REST}" admin inspect "$@"; }

DAY="$(date -u +%Y-%m-%d)"

log "wait for both OAP inspect ports"
for _ in $(seq 1 90); do
  a_inspect metrics --regex 'service_.*' >/dev/null 2>&1 && \
  b_inspect metrics --regex 'service_.*' >/dev/null 2>&1 && break
  sleep 2
done

# --- 1. OAP-A registers + persists the custom metric (AWARE / existing path) ---
log "await ${METRIC} registered + producing entities on OAP-A (≤${SETTLE}s)"
deadline=$(( $(date +%s) + SETTLE ))
a_rows=""
while :; do
  if a_inspect metrics --regex "${METRIC}" | jq -e '.metrics[]? | select(.name=="'"${METRIC}"'")' >/dev/null 2>&1; then
    a_rows="$(a_inspect entities --metric "${METRIC}" --start "${DAY}" --end "${DAY}" --step DAY 2>/dev/null || true)"
    if echo "${a_rows}" | jq -e '.rows[]? | select(.decoded.serviceName=="'"${SVC}"'")' >/dev/null 2>&1; then
      break
    fi
  fi
  (( $(date +%s) < deadline )) || fail "OAP-A never produced ${METRIC} entity ${SVC} within ${SETTLE}s"
  sleep 5
done
echo "${a_rows}" | jq -e '.rows[] | select(.decoded.serviceName=="'"${SVC}"'") | .mqeEntity.serviceName=="'"${SVC}"'"' >/dev/null \
  || fail "OAP-A aware row missing mqeEntity for ${SVC}: ${a_rows}"
log "  ✓ OAP-A aware /inspect/entities returns ${SVC} with mqeEntity (old path)"

# Value column as OAP-A reports it (applies the per-engine override, e.g. value -> value_ on jdbc).
VC="$(a_inspect metrics --regex "${METRIC}" | jq -r '.metrics[] | select(.name=="'"${METRIC}"'") | .valueColumnName')"
[ -n "${VC}" ] && [ "${VC}" != "null" ] || fail "could not read valueColumnName from OAP-A"
log "  OAP-A reports valueColumn=${VC}"

# --- 2. OAP-B does not know the metric ---
if b_inspect metrics --regex "${METRIC}" | jq -e '.metrics[]? | select(.name=="'"${METRIC}"'")' >/dev/null 2>&1; then
  fail "OAP-B unexpectedly knows ${METRIC} (should be foreign)"
fi
log "  ✓ OAP-B /inspect/metrics excludes ${METRIC}"

# --- 3. OAP-B aware path (no metadata) is rejected ---
if out="$(b_inspect entities --metric "${METRIC}" --start "${DAY}" --end "${DAY}" --step DAY 2>&1)"; then
  fail "OAP-B aware path unexpectedly succeeded for foreign metric: ${out}"
fi
echo "${out}" | grep -qi "unknown locally" \
  || fail "OAP-B aware path expected 'unknown locally', got: ${out}"
log "  ✓ OAP-B aware /inspect/entities (no metadata) → unknown locally"

# --- 4. OAP-B foreign path (valueColumn + valueType) reads the shared-storage rows ---
b_rows="$(b_inspect entities --metric "${METRIC}" --value-column "${VC}" --value-type LONG \
  --start "${DAY}" --end "${DAY}" --step DAY)" \
  || fail "OAP-B foreign path errored"
echo "${b_rows}" | jq -e '.rows[]? | select(.decoded.serviceName=="'"${SVC}"'")' >/dev/null \
  || fail "OAP-B foreign path returned no ${SVC} row: ${b_rows}"
# Foreign response degrades: scope null (the OAP emits null; swctl's typed model renders it as
# an empty string — accept either), no mqeEntity.
[ -z "$(echo "${b_rows}" | jq -r '.scope // ""')" ] \
  || fail "OAP-B foreign response should have empty/null scope: ${b_rows}"
echo "${b_rows}" | jq -e '.rows[] | select(.decoded.serviceName=="'"${SVC}"'") | .mqeEntity == null' >/dev/null \
  || fail "OAP-B foreign row should carry no mqeEntity: ${b_rows}"
log "  ✓ OAP-B FOREIGN /inspect/entities returns ${SVC}, scope=null, no mqeEntity (new path)"

# --- 5. OAP-B foreign VALUE read (admin inspect values) returns the metric value ---
# Uses the cli `admin inspect values` command (skywalking-cli #232, pinned via SW_CTL_COMMIT) →
# POST /inspect/values. MINUTE step: the per-minute value of meter_inspect_e2e_pool (=
# e2e_rr_pool_size summed over one service) is a constant 42; the DAY downsampling is not persisted
# this early in the run. Relative -30m/0m window so no host date-arithmetic is needed.
b_values="$(b_inspect values --expression "${METRIC}" --service-name "${SVC}" \
  --foreign-metric "${METRIC},${VC},LONG" --start "-30m" --end "0m" --step MINUTE)" \
  || fail "OAP-B admin inspect values errored"
echo "${b_values}" | jq -e '[.results[]?.values[]? | select(.value=="42")] | length > 0' >/dev/null \
  || fail "OAP-B admin inspect values returned no '42' value series: ${b_values}"
log "  ✓ OAP-B FOREIGN admin inspect values returns the metric value 42 (new path)"

log "=== inspect-foreign-flow.sh PASSED ==="
