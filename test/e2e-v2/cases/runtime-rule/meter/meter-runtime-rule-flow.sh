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
# Runtime-rule hot-update over the `meter-analyzer-config` catalog.
#
# A readiness phase followed by five capability phases, each guarding something this
# catalog did not have before meter rules started loading through meter-analyzer's
# Rules/Rule pipeline:
#
#   0. Admin API reachable (readiness only, asserts no capability).
#   1. BUNDLED VISIBILITY — the shipped batch-meter.yaml appears in
#      `runtime-rule list`. Only true once meter rules go through
#      RuleSetMerger and are recorded in StaticRuleRegistry. Previously the
#      meter loader read YAML directly, so the catalog was invisible here.
#   2. HOT ADD — a pure-runtime meter rule (no on-disk twin) becomes a live,
#      queryable metric with no OAP restart. Proves the applied MetricConvert
#      reached MeterProcessService's MalConverterRegistry and that
#      MeterProcessor picks it up on the next batch.
#   3. EDIT — re-applying the same (catalog, name) with an extra metric
#      registers the new metric AND keeps the already-registered one flowing.
#      Proves converter REPLACEMENT, not just first insertion.
#   4. DSL DEBUG — a debug session binds a BUNDLED meter rule and captures
#      records carrying the verbatim DSL, real samples, and a terminal
#      meterEmit. Proves the MAL catalog predicates accept the meter catalog
#      and that MeterProcessService publishes its holders at boot.
#   5. INACTIVATE — the runtime rule is soft-paused: the row goes INACTIVE and
#      its metrics stop producing new buckets, while the bundled rule keeps
#      producing. Proves converter REMOVAL.
#
# FRESHNESS: every "is it flowing / has it stopped" assertion compares the SET
# OF TIME-BUCKET IDS carrying a non-null value against a baseline captured at
# the transition. A metric that merely retains old buckets from before the
# transition therefore does NOT satisfy "still flowing", and a stopped metric
# cannot be masked by history. Bucket ids are compared instead of absolute
# --start/--end timestamps so the assertions are immune to host-vs-container
# clock and timezone skew.
#
set -euo pipefail

OAP_HOST="${OAP_HOST:-127.0.0.1}"
OAP_REST_PORT="${OAP_REST_PORT:-17128}"
OAP_GQL_PORT="${OAP_GQL_PORT:-12800}"
SENDER_HOST="${SENDER_HOST:-127.0.0.1}"
SENDER_PORT="${SENDER_PORT:-9093}"
SEED_DIR="${SEED_DIR:-./seed-rules}"

CATALOG="meter-analyzer-config"
BUNDLED_NAME="batch-meter"
RUNTIME_NAME="e2e_rr_meter"

# Metric emitted by the bundled rule (metricPrefix `batch` + rule `test`).
BUNDLED_METRIC="batch_test"
# Metric emitted by the runtime rule (metricPrefix `e2e_rr_meter` + rule `batch`).
RUNTIME_METRIC="e2e_rr_meter_batch"
# Second metric the v2 EDIT of that same rule adds.
RUNTIME_METRIC_V2="e2e_rr_meter_batch_scaled"

# The mock sender always reports under this identity.
SVC="test-service"
INST="test-instance"

REST_BASE="http://${OAP_HOST}:${OAP_REST_PORT}"
GQL_BASE="http://${OAP_HOST}:${OAP_GQL_PORT}/graphql"

# First-data budget: needs a minute-bucket boundary plus a persistence flush on
# a cold pipeline.
METRIC_BUDGET_S="${METRIC_BUDGET_S:-240}"
# Budget for a NEW bucket once the pipeline is already warm.
FRESH_BUDGET_S="${FRESH_BUDGET_S:-150}"
# Grace after /inactivate for in-flight samples and the open minute bucket to
# land, before the "has it stopped" baseline is taken.
STOP_GRACE_S="${STOP_GRACE_S:-90}"
# How long to keep pushing after that baseline while proving nothing new lands.
STOP_SETTLE_S="${STOP_SETTLE_S:-120}"
# Minimum number of SUCCESSFUL observations before "the metric stopped" may be
# concluded. Guards the negative assertion against a vacuous pass when the query
# path itself is broken for the whole settle window.
MIN_STOPPED_OBSERVATIONS="${MIN_STOPPED_OBSERVATIONS:-3}"
# Budget for an /addOrUpdate to reach ACTIVE. The apply is async — the REST call returns
# after the durable commit, while the schema fence rolls out in the background (its own
# default budget is 180s), so this must not be a single read.
APPLY_LAND_S="${APPLY_LAND_S:-120}"
# Budget for the debug session to capture its first record.
DEBUG_BUDGET_S="${DEBUG_BUDGET_S:-150}"
DBG_CLIENT_ID="${DBG_CLIENT_ID:-e2e-rr-meter-dbg-1}"

log()  { echo "[meter-runtime-rule-flow] $*" >&2; }
fail() { echo "[meter-runtime-rule-flow] FAIL: $*" >&2; exit 1; }

# Same convention as the otel-catalog flow: every runtime-rule REST call goes
# through swctl's admin command tree. swctl passes --catalog through verbatim,
# so `meter-analyzer-config` needs no CLI change.
admin() { swctl --display json --admin-url="${REST_BASE}" admin "$@"; }

# Push one batch of native MeterData (raw meter `batch_test`).
push_meter() {
  curl -s -XPOST "http://${SENDER_HOST}:${SENDER_PORT}/sendBatchMetrics" >/dev/null \
    || fail "mock sender did not accept /sendBatchMetrics"
}

# Newline-separated, sorted set of time-bucket ids that currently carry a
# non-null value for a metric.
#
# Return code is load-bearing, and MUST stay that way: a transport error, a
# non-JSON body, or a malformed envelope returns non-zero, which is NOT the same
# as "queried fine, metric has no data yet" (empty stdout, return 0). The
# negative assertion below relies on that distinction — if a broken query were
# silently reported as an empty bucket set, "no new buckets" would be
# indistinguishable from "never managed to look", and the stopped-check would
# pass vacuously.
metric_buckets() {
  local metric="$1"
  local out
  out="$(swctl --display json --base-url="${GQL_BASE}" metrics exec \
          --expression="${metric}" \
          --service-name="${SVC}" --instance-name="${INST}" 2>/dev/null)" \
    || return 1
  # A well-formed response is a JSON object carrying `results`. Anything else
  # (empty body, HTML error page, truncated JSON) is a query failure, not an
  # empty result set. `results: null` is accepted — that is the legitimate
  # "metric not present yet" shape.
  printf '%s' "${out}" | jq -e 'type == "object" and has("results")' >/dev/null 2>&1 \
    || return 1
  printf '%s' "${out}" \
    | jq -r '[.results[]?.values[]? | select(.value != null) | .id] | sort | .[]' \
    || return 1
}

# Ids present now that were absent from the supplied baseline. Propagates a
# query failure as a non-zero return so callers can tell it apart from "no new
# buckets".
new_buckets_since() {
  local metric="$1"
  local baseline="$2"
  local current
  current="$(metric_buckets "${metric}")" || return 1
  comm -13 <(printf '%s\n' "${baseline}" | sort -u) \
           <(printf '%s\n' "${current}" | sort -u)
}

# Block until the metric produces ANY non-null bucket. Used only for the very
# first data point of a cold pipeline.
wait_metric() {
  local metric="$1"
  local deadline=$(( $(date +%s) + METRIC_BUDGET_S ))
  local buckets
  local failures=0
  while (( $(date +%s) < deadline )); do
    push_meter
    sleep 10
    if ! buckets="$(metric_buckets "${metric}")"; then
      failures=$(( failures + 1 ))
      continue
    fi
    if [[ -n "${buckets}" ]]; then
      log "  ${metric} has data"
      return 0
    fi
  done
  fail "${metric} never produced a value within ${METRIC_BUDGET_S}s (${failures} query failure(s))"
}

# Block until the metric produces a bucket NEWER than the supplied baseline.
# This is the assertion that actually proves the converter is live right now.
wait_fresh_metric() {
  local metric="$1"
  local baseline="$2"
  local deadline=$(( $(date +%s) + FRESH_BUDGET_S ))
  local fresh
  local failures=0
  while (( $(date +%s) < deadline )); do
    push_meter
    sleep 10
    if ! fresh="$(new_buckets_since "${metric}" "${baseline}")"; then
      failures=$(( failures + 1 ))
      continue
    fi
    if [[ -n "${fresh}" ]]; then
      log "  ${metric} produced fresh bucket(s): $(echo "${fresh}" | tr '\n' ' ')"
      return 0
    fi
  done
  fail "${metric} produced no NEW bucket within ${FRESH_BUDGET_S}s (${failures} query failure(s)) — the converter is not live"
}

# Assert the metric produces NO bucket newer than the baseline for the whole
# settle window, while samples keep being pushed.
#
# This is a NEGATIVE assertion, so "we never saw anything" must NOT count as
# success. Every loop iteration that fails to query is recorded, and the check
# only concludes "stopped" if at least MIN_STOPPED_OBSERVATIONS iterations
# actually observed the metric successfully.
assert_metric_stopped() {
  local metric="$1"
  local baseline="$2"
  local deadline=$(( $(date +%s) + STOP_SETTLE_S ))
  local observations=0
  local failures=0
  local fresh
  while (( $(date +%s) < deadline )); do
    push_meter
    sleep 10
    if ! fresh="$(new_buckets_since "${metric}" "${baseline}")"; then
      failures=$(( failures + 1 ))
      log "  WARN: query for ${metric} failed (${failures} so far) — not counted as an observation"
      continue
    fi
    observations=$(( observations + 1 ))
    if [[ -n "${fresh}" ]]; then
      fail "${metric} produced a NEW bucket after inactivate: $(echo "${fresh}" | tr '\n' ' ')"
    fi
  done
  (( observations >= MIN_STOPPED_OBSERVATIONS )) \
    || fail "cannot conclude ${metric} stopped: only ${observations} successful observation(s) in ${STOP_SETTLE_S}s (${failures} query failure(s)); need >= ${MIN_STOPPED_OBSERVATIONS}"
  log "  ${metric} produced no new buckets across ${observations} successful observation(s) — converter removed"
}

# Status string the /list row carries for a (catalog, name), or empty.
rule_status() {
  admin runtime-rule list 2>/dev/null \
    | jq -r --arg c "${CATALOG}" --arg n "$1" \
        '.rules[] | select(.catalog == $c and .name == $n) | .status' | head -1
}

# ---- phase 0: admin API reachable -----------------------------------------
log "phase 0: waiting for the runtime-rule admin API"
deadline=$(( $(date +%s) + 180 ))
until admin runtime-rule list >/dev/null 2>&1; do
  (( $(date +%s) < deadline )) || fail "runtime-rule admin API never became reachable"
  sleep 3
done
log "  admin API up"

# ---- phase 1: bundled meter rule is visible -------------------------------
log "phase 1: bundled ${CATALOG}/${BUNDLED_NAME} must be visible to runtime-rule"
admin runtime-rule list \
  | jq -e --arg c "${CATALOG}" --arg n "${BUNDLED_NAME}" \
      '.rules[] | select(.catalog==$c and .name==$n)' >/dev/null \
  || fail "bundled ${CATALOG}/${BUNDLED_NAME} is NOT visible in runtime-rule list — meter rules are not reaching StaticRuleRegistry"
log "  bundled rule visible"

log "  and the bundled metric ${BUNDLED_METRIC} produces data"
wait_metric "${BUNDLED_METRIC}"

# ---- phase 2: hot-add a pure-runtime meter rule ---------------------------
log "phase 2: hot-add ${CATALOG}/${RUNTIME_NAME} (no on-disk twin)"
admin runtime-rule add --catalog "${CATALOG}" --name "${RUNTIME_NAME}" \
  -f "${SEED_DIR}/meter-v1.yaml" \
  || fail "addOrUpdate of ${RUNTIME_NAME} returned non-2xx"

# The apply is asynchronous — addOrUpdate returns once the row is persisted, while the
# schema fence and peer roll-out complete in the background. Poll rather than reading the
# status once, exactly as cases/dsl-debugging/mal/dsl-debug-flow.sh does.
wait_rule_active() {
  local name="$1"
  local deadline=$(( $(date +%s) + APPLY_LAND_S ))
  local status=""
  while (( $(date +%s) < deadline )); do
    status="$(rule_status "${name}" || true)"
    [[ "${status}" == "ACTIVE" ]] && { log "  ${CATALOG}/${name} is ACTIVE"; return 0; }
    sleep 3
  done
  fail "${CATALOG}/${name} did not reach ACTIVE within ${APPLY_LAND_S}s (last saw '${status}')"
}

wait_rule_active "${RUNTIME_NAME}"

log "  applied; ${RUNTIME_METRIC} must become queryable with no restart"
wait_metric "${RUNTIME_METRIC}"

# ---- phase 3: EDIT the runtime rule in place ------------------------------
# Baselines are captured BEFORE the edit, so the post-edit assertions cannot be
# satisfied by buckets the v1 converter already wrote.
log "phase 3: edit ${CATALOG}/${RUNTIME_NAME} in place (v1 -> v2, adds a metric)"
pre_edit_v1="$(metric_buckets "${RUNTIME_METRIC}")" \
  || fail "could not read a baseline for ${RUNTIME_METRIC} before the edit"
# v2's metric does not exist yet, so an empty set is the expected answer here.
# Tolerate a query failure specifically because "unknown metric" may surface as
# an error rather than an empty envelope; an empty baseline only ever makes the
# follow-up fresh-bucket assertion easier to satisfy, never the stopped-check.
pre_edit_v2="$(metric_buckets "${RUNTIME_METRIC_V2}" || true)"

admin runtime-rule add --catalog "${CATALOG}" --name "${RUNTIME_NAME}" \
  -f "${SEED_DIR}/meter-v2.yaml" \
  || fail "addOrUpdate (edit) of ${RUNTIME_NAME} returned non-2xx"

# Declaring an extra metric makes this a STRUCTURAL apply, so the fence/roll-out window is
# at its widest here. Settle on ACTIVE first, so a stalled apply is reported as such rather
# than surfacing later as a confusing "converter is not live".
wait_rule_active "${RUNTIME_NAME}"

log "  the newly declared ${RUNTIME_METRIC_V2} must produce a fresh bucket"
wait_fresh_metric "${RUNTIME_METRIC_V2}" "${pre_edit_v2}"

log "  and the metric v1 already registered must still produce fresh buckets"
wait_fresh_metric "${RUNTIME_METRIC}" "${pre_edit_v1}"

# ---- phase 4: DSL debug session against a BUNDLED meter rule --------------
# Deliberately targets the BUNDLED rule, not the runtime one: binding a runtime
# rule only exercises MalRuleEngine's generic publish path, which already worked
# for otel. Binding a BUNDLED meter rule exercises the wiring that is new here —
# MeterProcessService.start() calling MalStaticBindingHook.publish, plus
# MALHolderRegistry/MALDebugRecorderFactory accepting METER_ANALYZER_CONFIG.
#
# Assertions are deliberately shape-agnostic. The sibling otel case
# (cases/dsl-debugging/mal) asserts a much richer envelope, but those checks are
# bound to its seed rule's shape (multi-metric expression, decoy family, a
# `plus` op). The mock sender emits a single unlabelled raw meter, so the
# meaningful assertion here is "a meter rule can be bound at all, and its
# captures carry a real DSL body, real samples, and a terminal meterEmit".
log "phase 4: DSL debug session on bundled ${CATALOG}/${BUNDLED_NAME}"

admin dsl-debug status | jq -e '.injectionEnabled == true' >/dev/null \
  || fail "dsl-debug injectionEnabled is not true"

install_body="$(admin dsl-debug session start \
  --catalog "${CATALOG}" --name "${BUNDLED_NAME}" \
  --rule-name "${BUNDLED_METRIC}" --client-id "${DBG_CLIENT_ID}")" \
  || fail "dsl-debug session start rejected the ${CATALOG} catalog"
session_id="$(echo "${install_body}" | jq -r '.sessionId // empty')"
[[ -n "${session_id}" ]] \
  || fail "session start returned no sessionId — body: ${install_body}"
log "  session installed: ${session_id}"

deadline=$(( $(date +%s) + DEBUG_BUDGET_S ))
collect_body=""
records=0
while (( $(date +%s) < deadline )); do
  push_meter
  sleep 5
  collect_body="$(admin dsl-debug session get "${session_id}")"
  records="$(echo "${collect_body}" | jq '[.nodes[].records[]] | length')"
  (( records > 0 )) && break
done
(( records > 0 )) \
  || fail "no debug records captured for the bundled meter rule within ${DEBUG_BUDGET_S}s — payload: ${collect_body}"
log "  captured ${records} record(s)"

# Every record must carry the verbatim rule body as of capture.
empty_dsl="$(echo "${collect_body}" | jq '[.nodes[].records[] | select((.dsl // "") == "")] | length')"
[[ "${empty_dsl}" == "0" ]] || fail "${empty_dsl} record(s) carry empty .dsl"

empty_samples="$(echo "${collect_body}" | jq '[.nodes[].records[] | select((.samples | length) == 0)] | length')"
[[ "${empty_samples}" == "0" ]] || fail "${empty_samples} record(s) carry zero samples"

# At least one capture saw real traffic rather than an empty SampleFamily.
real_traffic="$(echo "${collect_body}" | jq '[.nodes[].records[].samples[] | select((.payload.samples // 0) > 0)] | length')"
(( real_traffic > 0 )) \
  || fail "no sample reports payload.samples > 0 — meter data never reached the rule"

# And at least one execution closes on a meterEmit carrying this metric.
emit="$(echo "${collect_body}" | jq --arg n "${BUNDLED_METRIC}" \
  '[.nodes[].records[] | .samples[-1] | select(.payload.metric == $n)] | length')"
(( emit > 0 )) \
  || fail "no execution closes with a meterEmit sample carrying payload.metric=${BUNDLED_METRIC}"
log "  envelope valid: dsl + samples + terminal meterEmit for ${BUNDLED_METRIC}"

admin dsl-debug session stop "${session_id}" | jq -e '.localStopped == true' >/dev/null \
  || fail "dsl-debug session stop did not report localStopped"
log "  session stopped"

# ---- phase 5: inactivate the runtime rule ---------------------------------
log "phase 5: inactivate ${CATALOG}/${RUNTIME_NAME}"
admin runtime-rule inactivate --catalog "${CATALOG}" --name "${RUNTIME_NAME}" \
  || fail "inactivate of ${RUNTIME_NAME} returned non-2xx"

# Also async: poll rather than reading once.
inactivate_deadline=$(( $(date +%s) + APPLY_LAND_S ))
status=""
while (( $(date +%s) < inactivate_deadline )); do
  status="$(rule_status "${RUNTIME_NAME}" || true)"
  [[ "${status}" == "INACTIVE" ]] && break
  sleep 3
done
[[ "${status}" == "INACTIVE" ]] \
  || fail "${CATALOG}/${RUNTIME_NAME} status is '${status}' after ${APPLY_LAND_S}s, expected INACTIVE"
log "  row is INACTIVE"

# Let in-flight samples and the currently-open minute bucket land before taking
# the "nothing new after this point" baseline.
log "  waiting ${STOP_GRACE_S}s for in-flight buckets to settle"
grace_deadline=$(( $(date +%s) + STOP_GRACE_S ))
while (( $(date +%s) < grace_deadline )); do
  push_meter
  sleep 10
done

# Both metrics are known to exist by now, so a failure here is a real problem —
# and an empty baseline would weaken the stopped-check that follows.
post_inactivate_runtime="$(metric_buckets "${RUNTIME_METRIC}")" \
  || fail "could not read a post-inactivate baseline for ${RUNTIME_METRIC}"
post_inactivate_bundled="$(metric_buckets "${BUNDLED_METRIC}")" \
  || fail "could not read a post-inactivate baseline for ${BUNDLED_METRIC}"

log "  ${RUNTIME_METRIC} must produce NO new buckets from here on"
assert_metric_stopped "${RUNTIME_METRIC}" "${post_inactivate_runtime}"

# Same window, opposite expectation: the bundled rule is untouched by the
# runtime rule's teardown and must still be writing fresh buckets.
log "  while bundled ${BUNDLED_METRIC} must still produce fresh buckets"
wait_fresh_metric "${BUNDLED_METRIC}" "${post_inactivate_bundled}"

log "ALL PHASES PASSED"
