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

# Runtime-rule lifecycle flow.
#
# Drives the full runtime-rule API surface against an OAP under test on
# port 17128 and asserts each transition end-to-end:
#
#   1. CREATE              — POST rule v1 (1 metric, SERVICE-scope)
#   2. UPDATE-FILTER       — POST rule v2 (body change ×10, same shape)
#   3. UPDATE-STRUCTURAL   — POST rule v3 (adds 2nd metric)
#   4. DUMP (mid-flight)   — GET /dump returns tar.gz with the live ruleset
#   5. ILLEGAL-APPLY × 4   — verify rejection paths
#       5a. malformed YAML → 400 compile_failed
#       5b. shape flip without allowStorageChange → 409
#       5c. /delete on ACTIVE row → 409 requires_inactivate_first
#       5d. sibling rule claims the same metric name → 409 ownership conflict
#   6. SHAPE-BREAK         — /inactivate → /delete → POST rule v4 (INSTANCE-scope)
#   7. INACTIVATE          — POST /inactivate (soft-pause)
#   8. ACTIVATE            — re-POST /addOrUpdate (lossless reactivate)
#   9. DELETE              — /inactivate → /delete (destructive)
#  10. DUMP (final)        — GET /dump returns tar.gz with manifest only
#
# Per-phase data attribution: the emitter publishes a `step` label whose
# value the flow rewrites between phases via `docker exec`. After each
# phase that writes data the verify queries select rows by the current
# step value, so the e2e proves "the row carrying step=<phase> exists in
# storage" or "step=<illegal_*> rows never appeared because the rule was
# rejected".
#
set -euo pipefail

OAP_HOST="${OAP_HOST:-oap}"
OAP_REST_PORT="${OAP_REST_PORT:-17128}"
OAP_GQL_PORT="${OAP_GQL_PORT:-12800}"
SEED_RULES_DIR="${SEED_RULES_DIR:-/seed-rules}"
SETTLE_SECONDS="${SETTLE_SECONDS:-360}"  # first phase needs minute-bucket boundary + OTLP export interval + flush latency + register-and-aggregate path; subsequent phases land within ~120s but the upper bound stays here for resilience under CI load
CATALOG="otel-rules"
NAME="e2e_rr"
SIBLING_NAME="e2e_rr_sibling"

REST_BASE="http://${OAP_HOST}:${OAP_REST_PORT}"
GQL_BASE="http://${OAP_HOST}:${OAP_GQL_PORT}"

# ---- helpers --------------------------------------------------------------

log()   { echo "[runtime-rule-flow] $*" >&2; }
fail()  { echo "[runtime-rule-flow] FAIL: $*" >&2; exit 1; }

# Resolve the otlp-emitter container by name fragment so we don't need to know
# the compose project name. Cached on first lookup.
EMITTER_CONTAINER=""
emitter_container() {
  if [[ -n "${EMITTER_CONTAINER}" ]]; then
    echo "${EMITTER_CONTAINER}"
    return
  fi
  EMITTER_CONTAINER="$(docker ps --filter "name=otlp-emitter" --format "{{.Names}}" | head -1)"
  [[ -n "${EMITTER_CONTAINER}" ]] || fail "no running container matching name=otlp-emitter"
  echo "${EMITTER_CONTAINER}"
}

# Flip the emitter's `step` label. The emitter re-reads /tmp/step on every
# tick, so subsequent samples carry the new value within a producer interval
# (~2 s) and reach storage after one OTLP export + one MAL aggregation hop.
step_set() {
  local value="$1"
  local container
  container="$(emitter_container)"
  docker exec "${container}" sh -c "echo '${value}' > /tmp/step" \
    || fail "failed to set step=${value} on ${container}"
  log "  step=${value}"
}

# Retry a 2xx-or-fail curl for up to RETRY_BUDGET_S seconds. Exists because the
# cluster routing layer transiently returns 503 cluster_not_ready when its peer
# refresh is in flight; happens reliably right after a STRUCTURAL apply (the
# reconciler's cache may be paused). Operator retries after a few seconds work
# in practice, so the e2e applies the same pattern automatically.
RETRY_BUDGET_S="${RETRY_BUDGET_S:-60}"
retry_curl_post() {
  local url="$1"
  local body_arg="${2:-}"   # e.g. --data-binary @file ; empty for empty-body POST
  local deadline=$(( $(date +%s) + RETRY_BUDGET_S ))
  local out
  while (( $(date +%s) < deadline )); do
    if [[ -n "${body_arg}" ]]; then
      # shellcheck disable=SC2086
      out="$(curl -fsS -XPOST ${body_arg} -H "Content-Type: text/plain" "${url}" 2>&1)" && {
        echo "${out}"; return 0;
      }
    else
      out="$(curl -fsS -XPOST "${url}" 2>&1)" && { echo "${out}"; return 0; }
    fi
    if [[ "${out}" == *503* ]]; then
      log "  transient 503 on ${url} — retrying"
      sleep 2
      continue
    fi
    echo "${out}"
    return 1
  done
  echo "${out}"
  return 1
}

# POST a rule file to /addOrUpdate. Echoes the JSON response. Asserts 200.
post_rule() {
  local file="$1"
  local extra_qs="${2:-}"
  local rule_name="${3:-${NAME}}"
  local url="${REST_BASE}/runtime/rule/addOrUpdate?catalog=${CATALOG}&name=${rule_name}${extra_qs:+&${extra_qs}}"
  log "POST ${url} (body=${file})"
  local resp
  resp="$(curl -fsS -XPOST --data-binary "@${file}" -H "Content-Type: text/plain" "${url}")" \
    || fail "addOrUpdate of ${file} returned non-2xx"
  log "  → ${resp}"
  echo "${resp}"
}

# POST a rule that's expected to be REJECTED. Captures the HTTP status and the
# response body via curl's separate -w / -o, asserts the status matches, and
# echoes the body so callers can grep for a specific failure code/string.
post_rule_expect_status() {
  local file="$1"
  local expected_status="$2"
  local extra_qs="${3:-}"
  local rule_name="${4:-${NAME}}"
  local url="${REST_BASE}/runtime/rule/addOrUpdate?catalog=${CATALOG}&name=${rule_name}${extra_qs:+&${extra_qs}}"
  log "POST ${url} (expect HTTP ${expected_status}, body=${file})"
  local body_file http_status
  body_file="$(mktemp)"
  http_status="$(curl -sS -o "${body_file}" -w '%{http_code}' \
    -XPOST --data-binary "@${file}" -H "Content-Type: text/plain" "${url}")"
  local body
  body="$(cat "${body_file}")"
  rm -f "${body_file}"
  log "  ← HTTP ${http_status} body=${body}"
  [[ "${http_status}" == "${expected_status}" ]] \
    || fail "expected HTTP ${expected_status}, got ${http_status} (body: ${body})"
  echo "${body}"
}

# POST a non-/addOrUpdate endpoint that's expected to be REJECTED. Same
# semantics as post_rule_expect_status but takes an explicit URL.
post_url_expect_status() {
  local url="$1"
  local expected_status="$2"
  log "POST ${url} (expect HTTP ${expected_status})"
  local body_file http_status
  body_file="$(mktemp)"
  http_status="$(curl -sS -o "${body_file}" -w '%{http_code}' -XPOST "${url}")"
  local body
  body="$(cat "${body_file}")"
  rm -f "${body_file}"
  log "  ← HTTP ${http_status} body=${body}"
  [[ "${http_status}" == "${expected_status}" ]] \
    || fail "expected HTTP ${expected_status}, got ${http_status} (body: ${body})"
  echo "${body}"
}

# Assert the JSON response carries the expected applyStatus.
assert_apply_status() {
  local expected="$1"
  local actual_json="$2"
  local actual
  actual="$(echo "${actual_json}" | jq -r '.applyStatus // empty')"
  [[ "${actual}" == "${expected}" ]] \
    || fail "expected applyStatus=${expected}, got '${actual}' (full: ${actual_json})"
}

# GET /runtime/rule/list and ensure the row matches the expected status. Returns
# the matching JSON line on stdout for callers that want to inspect contentHash.
list_row() {
  local expected_status="$1"
  local rule_name="${2:-${NAME}}"
  log "GET /runtime/rule/list → looking for ${CATALOG}/${rule_name} status=${expected_status}"
  local lines
  lines="$(curl -fsS "${REST_BASE}/runtime/rule/list")" \
    || fail "GET /runtime/rule/list failed"
  local match
  match="$(echo "${lines}" | jq -c ".rules[] | select(.catalog==\"${CATALOG}\" and .name==\"${rule_name}\")" 2>/dev/null || true)"
  [[ -n "${match}" ]] \
    || fail "/list has no row for ${CATALOG}/${rule_name} (got: ${lines})"
  local actual_status
  actual_status="$(echo "${match}" | jq -r '.status')"
  [[ "${actual_status}" == "${expected_status}" ]] \
    || fail "expected /list status=${expected_status}, got '${actual_status}' (row: ${match})"
  echo "${match}"
}

# Assert that /list does NOT have a row for the given (catalog, name).
list_no_row() {
  local rule_name="${1:-${NAME}}"
  log "GET /runtime/rule/list → expect NO row for ${CATALOG}/${rule_name}"
  local lines match
  lines="$(curl -fsS "${REST_BASE}/runtime/rule/list")" \
    || fail "GET /runtime/rule/list failed"
  match="$(echo "${lines}" | jq -c ".rules[] | select(.catalog==\"${CATALOG}\" and .name==\"${rule_name}\")" 2>/dev/null || true)"
  if [[ -n "${match}" ]]; then
    local status
    status="$(echo "${match}" | jq -r '.status')"
    [[ "${status}" == "n/a" ]] \
      || fail "/list still has row for ${CATALOG}/${rule_name} status=${status} (row: ${match})"
  fi
}

# Per-phase entity scope. SHAPE-BREAK reshapes the metric from SERVICE to
# SERVICE_INSTANCE, after which swctl needs both --service-name AND
# --instance-name to resolve the entity. Phases set this before calling
# the helpers; default is SERVICE.
ENTITY_INSTANCE="${ENTITY_INSTANCE:-}"

# Sample query: swctl returns YAML; non-empty .values means at least one minute
# bucket has data for the given metric scoped to the current ENTITY_INSTANCE
# (empty = SERVICE-scope, set = SERVICE_INSTANCE-scope).
swctl_metric_has_value() {
  local metric="$1"
  local out
  if [[ -n "${ENTITY_INSTANCE}" ]]; then
    out="$(swctl --display yaml --base-url="${GQL_BASE}/graphql" \
      metrics exec --expression="${metric}" \
      --service-name="e2e-rr-svc" --instance-name="${ENTITY_INSTANCE}" 2>&1)" || {
      log "  swctl exec ${metric} (instance=${ENTITY_INSTANCE}) failed: ${out}"
      return 1
    }
  else
    out="$(swctl --display yaml --base-url="${GQL_BASE}/graphql" \
      metrics exec --expression="${metric}" --service-name="e2e-rr-svc" 2>&1)" || {
      log "  swctl exec ${metric} failed: ${out}"
      return 1
    }
  fi
  log "  swctl ${metric} → ${out}"
  echo "${out}" | grep -qE '^\s*value:\s*"?-?[0-9]+(\.[0-9]+)?"?\s*$' && return 0
  return 1
}

# Like swctl_metric_has_value but the MQE expression filters on a `step`
# label so the result is restricted to a specific lifecycle phase.
swctl_metric_has_value_for_step() {
  local metric="$1"
  local step="$2"
  local expr="${metric}{step='${step}'}"
  swctl_metric_has_value "${expr}"
}

await_metric() {
  local metric="$1"
  log "awaiting metric ${metric} (up to ${SETTLE_SECONDS}s)"
  local deadline=$(( $(date +%s) + SETTLE_SECONDS ))
  while (( $(date +%s) < deadline )); do
    if swctl_metric_has_value "${metric}"; then
      log "  ✓ ${metric} has values"
      return 0
    fi
    sleep 5
  done
  fail "metric ${metric} never produced a value within ${SETTLE_SECONDS}s"
}

await_metric_for_step() {
  local metric="$1"
  local step="$2"
  log "awaiting metric ${metric}{step='${step}'} (up to ${SETTLE_SECONDS}s)"
  local deadline=$(( $(date +%s) + SETTLE_SECONDS ))
  while (( $(date +%s) < deadline )); do
    if swctl_metric_has_value_for_step "${metric}" "${step}"; then
      log "  ✓ ${metric}{step='${step}'} has values"
      return 0
    fi
    sleep 5
  done
  fail "metric ${metric}{step='${step}'} never produced a value within ${SETTLE_SECONDS}s"
}

# Storage-direct version of await_metric_for_step that bypasses the MQE
# query path and asks BanyanDB for the measure's raw rows. Needed for
# phases that change the metric's scope (e.g. SERVICE → SERVICE_INSTANCE
# in the SHAPE-BREAK phase): MQE resolves entity by service / instance
# binding which lags behind the BanyanDB-side schema change, so the right
# truth signal for "the new rule is producing data" is the storage layer.
await_step_in_banyandb() {
  local metric="$1"        # e.g. e2e_rr_requests
  local step="$2"          # e.g. shape_break_new
  local since_ms="${3:-$(( $(date +%s) - 120 ))000}"
  local measure="${metric}_minute"
  local group="sw_metricsMinute"
  log "awaiting BanyanDB row in ${group}/${measure} carrying step='${step}' (up to ${SETTLE_SECONDS}s, since ts=${since_ms}ms)"
  local deadline=$(( $(date +%s) + SETTLE_SECONDS ))
  local now_iso since_iso
  since_iso="$(python3 -c "import datetime; print(datetime.datetime.fromtimestamp(${since_ms}/1000, tz=datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'))")"
  while (( $(date +%s) < deadline )); do
    now_iso="$(python3 -c "import datetime; print(datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'))")"
    local body
    body="$(curl -s -X POST "${BYDB_BASE}/api/v1/measure/data" -H 'Content-Type: application/json' \
      -d "{\"groups\":[\"${group}\"],\"name\":\"${measure}\",\"timeRange\":{\"begin\":\"${since_iso}\",\"end\":\"${now_iso}\"},\"tagProjection\":{\"tagFamilies\":[{\"name\":\"storage-only\",\"tags\":[\"entity_id\"]}]},\"fieldProjection\":{\"names\":[\"datatable_value\"]}}" 2>/dev/null)"
    # Each post-shape-break row's datatable_value is a packed string like
    #   {step=create},286|{step=shape_break_new},168|{step=structural},112
    # Match the literal `step=<value>,<num>` substring to confirm the rule
    # actually emitted that step's bucket. Bypasses MQE entity binding.
    if echo "${body}" | grep -q "step=${step},[0-9]"; then
      log "  ✓ BanyanDB row found with step=${step}"
      return 0
    fi
    sleep 5
  done
  fail "no BanyanDB row in ${group}/${measure} carrying step=${step} within ${SETTLE_SECONDS}s (last body: ${body:-<empty>})"
}

# Negative-direction await. Polls for SETTLE_SECONDS hoping the metric STAYS
# empty; succeeds if no value materialises in that window. Used to assert
# the INACTIVATE soft-pause window genuinely drops samples (the rule's MAL
# converter is unregistered, so emitter samples produce no rows).
expect_no_metric_for_step() {
  local metric="$1"
  local step="$2"
  local window="${3:-${SETTLE_SECONDS}}"
  log "expecting NO metric ${metric}{step='${step}'} within ${window}s"
  local deadline=$(( $(date +%s) + window ))
  while (( $(date +%s) < deadline )); do
    if swctl_metric_has_value_for_step "${metric}" "${step}"; then
      fail "metric ${metric}{step='${step}'} unexpectedly produced a value (proves the phase wasn't rejected / paused)"
    fi
    sleep 5
  done
  log "  ✓ ${metric}{step='${step}'} stayed empty for ${window}s"
}

# Capture the latest non-null bucket id for {metric, step} so a follow-up
# call to assert_metric_step_advanced can prove a NEW bucket landed after
# some intervening event. Used after ILLEGAL-APPLY rejections to prove the
# existing rule's MAL converter kept aggregating (a rejection that
# accidentally tore down the converter would freeze the bucket id even
# though contentHash stays unchanged).
latest_bucket_id_for_step() {
  local metric="$1"
  local step="$2"
  local out
  out="$(swctl --display yaml --base-url="${GQL_BASE}/graphql" \
    metrics exec --expression="${metric}{step='${step}'}" --service-name="e2e-rr-svc" 2>/dev/null)" || {
    echo ""
    return
  }
  # Parse YAML: rows look like
  #   - id: "1777335720000"
  #     value: "19"
  # Match the id of the most-recent row whose value is NOT null. awk pairs
  # adjacent id+value lines and prints the id only when value is numeric.
  echo "${out}" | awk '
    /^[[:space:]]*-[[:space:]]*id:/ { id = $0 }
    /^[[:space:]]*value:/ {
      if ($0 ~ /value:[[:space:]]*"?-?[0-9]/) {
        gsub(/[^0-9]/, "", id)
        print id
      }
    }
  ' | tail -1
}

# Assert the {metric, step} latest bucket id is strictly greater than the
# baseline captured earlier — proves a NEW bucket landed.
assert_metric_step_advanced() {
  local metric="$1"
  local step="$2"
  local baseline="$3"
  local window="${4:-${SETTLE_SECONDS}}"
  log "expecting metric ${metric}{step='${step}'} to advance past id=${baseline} within ${window}s"
  local deadline=$(( $(date +%s) + window ))
  local latest
  while (( $(date +%s) < deadline )); do
    latest="$(latest_bucket_id_for_step "${metric}" "${step}")"
    if [[ -n "${latest}" && -n "${baseline}" && "${latest}" -gt "${baseline}" ]]; then
      log "  ✓ ${metric}{step='${step}'} advanced ${baseline} → ${latest}"
      return 0
    fi
    sleep 5
  done
  fail "metric ${metric}{step='${step}'} did not advance past ${baseline} within ${window}s (latest=${latest:-<none>})"
}

# Fetch /runtime/rule/dump, save the tar.gz, and assert it contains expected
# entries. Pass expected basenames (without leading paths) — any one missing
# is a failure. To assert "manifest only", pass just `manifest.yaml`.
assert_dump_contains() {
  local label="$1"
  shift
  local tar_file
  tar_file="$(mktemp)"
  curl -fsS "${REST_BASE}/runtime/rule/dump" -o "${tar_file}" \
    || fail "GET /runtime/rule/dump failed (${label})"
  local entries
  entries="$(tar -tzf "${tar_file}" 2>&1)" \
    || { rm -f "${tar_file}"; fail "${label}: dump body is not a valid tar.gz: ${entries}"; }
  log "${label} dump entries: ${entries//$'\n'/, }"
  rm -f "${tar_file}"
  for required in "$@"; do
    echo "${entries}" | grep -q "${required}" \
      || fail "${label}: dump missing required entry ${required} (got: ${entries})"
  done
  log "  ✓ ${label} dump contains: $*"
}

# ---- flow -----------------------------------------------------------------

log "waiting for OAP runtime-rule port ${OAP_REST_PORT}"
for _ in $(seq 1 60); do
  curl -fsS "${REST_BASE}/runtime/rule/list" >/dev/null 2>&1 && break
  sleep 2
done

# Resolve the emitter container so subsequent step_set calls don't pay the
# `docker ps` cost twice.
emitter_container >/dev/null

# Phase 1 — CREATE.
log "=== Phase 1: CREATE seed-rule.yaml ==="
step_set "create"
resp="$(post_rule "${SEED_RULES_DIR}/seed-rule.yaml")"
assert_apply_status "structural_applied" "${resp}"
list_row "ACTIVE" >/dev/null
hash_initial="$(list_row ACTIVE | jq -r '.contentHash')"
log "  initial contentHash=${hash_initial}"
await_metric_for_step "e2e_rr_requests" "create"

# Phase 2 — UPDATE-FILTER (body-only, same shape).
log "=== Phase 2: UPDATE-FILTER seed-rule-filter-only.yaml ==="
step_set "update_filter"
resp="$(post_rule "${SEED_RULES_DIR}/seed-rule-filter-only.yaml")"
assert_apply_status "filter_only_applied" "${resp}"
hash_filter_only="$(list_row ACTIVE | jq -r '.contentHash')"
[[ "${hash_filter_only}" != "${hash_initial}" ]] \
  || fail "FILTER_ONLY apply did not advance /list contentHash"
log "  contentHash advanced to ${hash_filter_only}"
await_metric_for_step "e2e_rr_requests" "update_filter"

# Phase 3 — UPDATE-STRUCTURAL (adds e2e_rr_pool metric).
log "=== Phase 3: UPDATE-STRUCTURAL seed-rule-structural.yaml ==="
step_set "structural"
resp="$(post_rule "${SEED_RULES_DIR}/seed-rule-structural.yaml" "allowStorageChange=true")"
assert_apply_status "structural_applied" "${resp}"
hash_structural="$(list_row ACTIVE | jq -r '.contentHash')"
[[ "${hash_structural}" != "${hash_filter_only}" ]] \
  || fail "STRUCTURAL apply did not advance /list contentHash"
log "  contentHash advanced to ${hash_structural}"
await_metric_for_step "e2e_rr_requests" "structural"
await_metric_for_step "e2e_rr_pool" "structural"

# Phase 4 — DUMP (mid-flight).
log "=== Phase 4: DUMP (mid-flight) ==="
assert_dump_contains "mid-flight" "manifest" "${NAME}"

# Phase 5 — ILLEGAL-APPLY × 4. Each rejection must:
#   (a) return the documented HTTP status code,
#   (b) leave /list contentHash unchanged (the bad rule never replaced the
#       active one),
#   (c) leave the existing rule's MAL converter alive — a rejection that
#       accidentally tore down the converter would freeze the bucket id for
#       step=structural even though contentHash stays unchanged.
# We do NOT change `step` here: the structural rule from phase 3 keeps
# aggregating regardless of which rule the operator just tried to push, so
# any `step=illegal_*` rows that appear would be the existing rule's output,
# not evidence the rejection failed.

log "=== Phase 5a: ILLEGAL malformed YAML ==="
struct_baseline="$(latest_bucket_id_for_step "e2e_rr_requests" "structural")"
post_rule_expect_status "${SEED_RULES_DIR}/illegal-malformed.yaml" "400" >/dev/null
[[ "$(list_row ACTIVE | jq -r '.contentHash')" == "${hash_structural}" ]] \
  || fail "5a: contentHash moved after malformed YAML rejection"
assert_metric_step_advanced "e2e_rr_requests" "structural" "${struct_baseline}" 180

log "=== Phase 5b: ILLEGAL shape flip without allowStorageChange ==="
struct_baseline="$(latest_bucket_id_for_step "e2e_rr_requests" "structural")"
post_rule_expect_status "${SEED_RULES_DIR}/illegal-shape-flip.yaml" "409" >/dev/null
[[ "$(list_row ACTIVE | jq -r '.contentHash')" == "${hash_structural}" ]] \
  || fail "5b: contentHash moved after shape-flip rejection"
assert_metric_step_advanced "e2e_rr_requests" "structural" "${struct_baseline}" 180

log "=== Phase 5c: ILLEGAL /delete on ACTIVE row ==="
struct_baseline="$(latest_bucket_id_for_step "e2e_rr_requests" "structural")"
post_url_expect_status "${REST_BASE}/runtime/rule/delete?catalog=${CATALOG}&name=${NAME}" "409" >/dev/null
[[ "$(list_row ACTIVE | jq -r '.contentHash')" == "${hash_structural}" ]] \
  || fail "5c: row state changed after /delete-on-ACTIVE rejection"
assert_metric_step_advanced "e2e_rr_requests" "structural" "${struct_baseline}" 180

log "=== Phase 5d: ILLEGAL duplicate metric ownership (sibling rule) ==="
struct_baseline="$(latest_bucket_id_for_step "e2e_rr_requests" "structural")"
post_rule_expect_status "${SEED_RULES_DIR}/illegal-duplicate-metric.yaml" "409" "" "${SIBLING_NAME}" >/dev/null
list_no_row "${SIBLING_NAME}"
[[ "$(list_row ACTIVE | jq -r '.contentHash')" == "${hash_structural}" ]] \
  || fail "5d: primary rule's contentHash moved after sibling-conflict rejection"
assert_metric_step_advanced "e2e_rr_requests" "structural" "${struct_baseline}" 180

# Phase 6 — SHAPE-BREAK via the supported route: /inactivate → /delete →
# POST a new shape under the same (catalog, name).
log "=== Phase 6: SHAPE-BREAK ==="
step_set "shape_break_old"
log "  /inactivate to release the old shape"
inactivate_url="${REST_BASE}/runtime/rule/inactivate?catalog=${CATALOG}&name=${NAME}"
retry_curl_post "${inactivate_url}" >/dev/null \
  || fail "shape-break: inactivate failed"
list_row "INACTIVE" >/dev/null
log "  /delete to drop the old measure"
delete_url="${REST_BASE}/runtime/rule/delete?catalog=${CATALOG}&name=${NAME}"
retry_curl_post "${delete_url}" >/dev/null \
  || fail "shape-break: delete failed"
list_no_row

step_set "shape_break_new"
log "  POST INSTANCE-scope rule v4"
resp="$(post_rule "${SEED_RULES_DIR}/seed-rule-instance.yaml")"
assert_apply_status "structural_applied" "${resp}"
hash_shape_break="$(list_row ACTIVE | jq -r '.contentHash')"
log "  contentHash after shape break = ${hash_shape_break}"
# Rule v4 is INSTANCE-scope; swctl now needs --instance-name to resolve
# the entity. Set ENTITY_INSTANCE for the remainder of the flow (phases
# 6, 8 read it; phase 7's expect-empty doesn't need it but harmless).
ENTITY_INSTANCE="e2e-rr-i1"
await_metric_for_step "e2e_rr_requests" "shape_break_new"

# Phase 7 — INACTIVATE (soft-pause: backend schema + data preserved).
# Order matters: /inactivate FIRST, then flip step. Otherwise the brief
# window where the rule is still active aggregates a few `step=inactivate`
# samples and the soft-pause assertion below fails for the wrong reason.
log "=== Phase 7: INACTIVATE (soft-pause) ==="
retry_curl_post "${inactivate_url}" >/dev/null \
  || fail "phase-7: inactivate failed"
list_row "INACTIVE" >/dev/null
step_set "inactivate"
expect_no_metric_for_step "e2e_rr_requests" "inactivate" 30

# Phase 8 — ACTIVATE (re-POST same content; lossless).
log "=== Phase 8: ACTIVATE ==="
step_set "activate"
resp="$(post_rule "${SEED_RULES_DIR}/seed-rule-instance.yaml")"
status="$(echo "${resp}" | jq -r '.applyStatus // empty')"
[[ "${status}" == "structural_applied" || "${status}" == "no_change" ]] \
  || fail "ACTIVATE: unexpected applyStatus=${status} (full: ${resp})"
list_row "ACTIVE" >/dev/null
await_metric_for_step "e2e_rr_requests" "activate"
# NOTE: we do NOT re-assert "no step=inactivate rows" here. Phase 7's in-window
# check already proved the soft-pause window dropped samples. After re-activate,
# OTel's PeriodicExportingMetricReader keeps exporting every counter's
# cumulative value on each tick, including the {step=inactivate} counter that
# still holds its last value from phase 7. Once the MAL converter is back, those
# cumulative re-exports flow into storage — that's emitter-side OTel semantics,
# not a runtime-rule contract violation.

# Phase 9 — DELETE (destructive).
log "=== Phase 9: DELETE ==="
step_set "delete_attempt"
retry_curl_post "${inactivate_url}" >/dev/null \
  || fail "phase-9: inactivate-before-delete failed"
list_row "INACTIVE" >/dev/null
retry_curl_post "${delete_url}" >/dev/null \
  || fail "phase-9: delete failed"
list_no_row
log "  ✓ row gone + backend probe agrees"

# Phase 10 — DUMP (final). After DELETE, the dump should contain only the
# manifest — no rule files.
log "=== Phase 10: DUMP (final) ==="
assert_dump_contains "final" "manifest"

log "=== runtime-rule-flow.sh PASSED ==="
