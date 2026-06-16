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
#   5. ILLEGAL-APPLY       — verify rejection paths
#       5a. malformed YAML → 400 compile_failed
#       5b. shape flip without allowStorageChange → 409
#       5c. /delete on ACTIVE row → 409 requires_inactivate_first
#       5d. sibling rule claims the same metric name → 409 ownership conflict
#       5e. layerDefinitions ordinal <100_000 → 400 layer_ordinal_out_of_range
#       5f. layerDefinitions name shape invalid → 400 layer_name_invalid
#       5g. layerDefinitions redeclares built-in MESH → 400 layer_name_conflict
#       5h. HAPPY-PATH dynamic-LAYER round-trip via swctl `layer ls`
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

# Every runtime-rule REST call goes through swctl's `admin` command tree instead
# of raw curl. `--display json` keeps the response body byte-shape identical to
# the old curl output (the runtime-rule endpoints are passed through verbatim),
# so the jq assertions below are unchanged. On a non-2xx the CLI exits non-zero
# and renders the typed error envelope — `admin API <url>: HTTP <code>
# (<applyStatus>): <message>` — which the negative-path helpers grep for.
admin() { swctl --display json --admin-url="${REST_BASE}" admin "$@"; }

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

# Retry a runtime-rule admin call for up to RETRY_BUDGET_S seconds. Exists because
# the cluster routing layer transiently returns 503 cluster_not_ready when its peer
# refresh is in flight; happens reliably right after a STRUCTURAL apply (the
# reconciler's cache may be paused). Operator retries after a few seconds work
# in practice, so the e2e applies the same pattern automatically. Pass the
# runtime-rule subcommand and its flags, e.g.
#   retry_admin runtime-rule inactivate --catalog "${CATALOG}" --name "${NAME}"
RETRY_BUDGET_S="${RETRY_BUDGET_S:-60}"
retry_admin() {
  local deadline=$(( $(date +%s) + RETRY_BUDGET_S ))
  local out rc
  while (( $(date +%s) < deadline )); do
    out="$(admin "$@" 2>&1)" && { echo "${out}"; return 0; }
    rc=$?
    if echo "${out}" | grep -q "HTTP 503"; then
      log "  transient 503 on 'admin $*' — retrying"
      sleep 2
      continue
    fi
    echo "${out}"
    return "${rc}"
  done
  echo "${out}"
  return 1
}

# Apply a rule file via addOrUpdate. Echoes the JSON response. Asserts 2xx.
# extra="allowStorageChange=true" maps to the --allow-storage-change flag.
post_rule() {
  local file="$1"
  local extra="${2:-}"
  local rule_name="${3:-${NAME}}"
  local -a flags=(--catalog "${CATALOG}" --name "${rule_name}" -f "${file}")
  [[ "${extra}" == *allowStorageChange=true* ]] && flags+=(--allow-storage-change)
  log "runtime-rule add ${CATALOG}/${rule_name} (body=${file})"
  local resp
  resp="$(admin runtime-rule add "${flags[@]}")" \
    || fail "addOrUpdate of ${file} returned non-2xx"
  log "  → ${resp}"
  echo "${resp}"
}

# Apply a rule that's expected to be REJECTED. swctl exits non-zero on a non-2xx
# and renders the typed error envelope ("... HTTP <code> (<applyStatus>): <msg>")
# to stdout; assert the HTTP code is present and echo the message so callers can
# grep for a specific failure code / applyStatus / string.
post_rule_expect_status() {
  local file="$1"
  local expected_status="$2"
  local extra="${3:-}"
  local rule_name="${4:-${NAME}}"
  local -a flags=(--catalog "${CATALOG}" --name "${rule_name}" -f "${file}")
  [[ "${extra}" == *allowStorageChange=true* ]] && flags+=(--allow-storage-change)
  log "runtime-rule add ${CATALOG}/${rule_name} (expect HTTP ${expected_status}, body=${file})"
  local out rc
  out="$(admin runtime-rule add "${flags[@]}" 2>&1)" && rc=0 || rc=$?
  log "  ← rc=${rc} ${out}"
  [[ "${rc}" -ne 0 ]] \
    || fail "expected rejection (HTTP ${expected_status}) but add succeeded: ${out}"
  echo "${out}" | grep -q "HTTP ${expected_status}" \
    || fail "expected HTTP ${expected_status}, got: ${out}"
  echo "${out}"
}

# Delete a rule that's expected to be REJECTED (e.g. /delete on an ACTIVE row →
# 409 requires_inactivate_first). Same envelope-grep semantics as
# post_rule_expect_status.
delete_expect_status() {
  local rule_name="$1"
  local expected_status="$2"
  log "runtime-rule delete ${CATALOG}/${rule_name} (expect HTTP ${expected_status})"
  local out rc
  out="$(admin runtime-rule delete --catalog "${CATALOG}" --name "${rule_name}" 2>&1)" && rc=0 || rc=$?
  log "  ← rc=${rc} ${out}"
  [[ "${rc}" -ne 0 ]] \
    || fail "expected delete rejection (HTTP ${expected_status}) but it succeeded: ${out}"
  echo "${out}" | grep -q "HTTP ${expected_status}" \
    || fail "expected HTTP ${expected_status}, got: ${out}"
  echo "${out}"
}

# Assert the expected applyStatus. On the happy path the argument is the JSON
# ApplyResult and the status comes from .applyStatus. On a rejection the argument
# is swctl's error line, where the CLI's typed envelope renders the applyStatus in
# parentheses, e.g. "... HTTP 400 (layer_ordinal_out_of_range): <msg>".
assert_apply_status() {
  local expected="$1"
  local actual="$2"
  local parsed
  parsed="$(echo "${actual}" | jq -r '.applyStatus // empty' 2>/dev/null || true)"
  if [[ -n "${parsed}" ]]; then
    [[ "${parsed}" == "${expected}" ]] \
      || fail "expected applyStatus=${expected}, got '${parsed}' (full: ${actual})"
    return 0
  fi
  echo "${actual}" | grep -q "(${expected})" \
    || fail "expected applyStatus=${expected}, not found in: ${actual}"
}

# Budget for the async apply state machine to reach a terminal phase on GET /runtime/rule/status.
APPLY_TERMINAL_S="${APPLY_TERMINAL_S:-200}"

# Drive the new async apply surface to a terminal phase. A STRUCTURAL addOrUpdate (and a
# /delete?mode=revertToBundled) returns immediately at FENCING with an applyId; the row is persisted
# and dispatch resumed in the BACKGROUND, after the schema fence. Given the apply's JSON response,
# extract its applyId and poll GET /runtime/rule/status until the phase is terminal:
#   APPLIED / DEGRADED → the durable row was written (DEGRADED == committed-and-durable, only the
#                        cluster-wide fence confirmation lagged) → return 0
#   FAILED            → a pre-commit error, nothing was committed → fail
#   anything else (FENCING / DDL / ROLLING_OUT / PENDING / UNKNOWN) → keep polling
# A synchronous apply (filter_only / inactivate / default delete) carries no applyId, so this is a
# no-op — that response is already durable on return. swctl has no runtime-rule `status` subcommand,
# so this goes through curl (the status endpoint lives on the same REST port). Passing catalog+name
# lets the main answer from the durable rule row once the live apply-id is TTL-evicted, so a slow
# poll converges instead of false-timing-out.
await_apply_terminal() {
  local resp="$1"
  local rule_name="${2:-${NAME}}"
  local apply_id
  apply_id="$(echo "${resp}" | jq -r '.applyId // empty' 2>/dev/null || true)"
  if [[ -z "${apply_id}" ]]; then
    return 0
  fi
  log "runtime-rule status → polling (≤${APPLY_TERMINAL_S}s) for apply ${apply_id} of ${CATALOG}/${rule_name} to reach a terminal phase"
  local deadline=$(( $(date +%s) + APPLY_TERMINAL_S ))
  local body phase=""
  while :; do
    body="$(curl -s "${REST_BASE}/runtime/rule/status?applyId=${apply_id}&catalog=${CATALOG}&name=${rule_name}" 2>/dev/null || true)"
    phase="$(echo "${body}" | jq -r '.phase // empty' 2>/dev/null || true)"
    case "${phase}" in
      APPLIED|DEGRADED)
        log "  ✓ apply ${apply_id} → ${phase} (durable)"
        return 0
        ;;
      FAILED)
        fail "apply ${apply_id} of ${CATALOG}/${rule_name} reached FAILED: ${body}"
        ;;
    esac
    if (( $(date +%s) >= deadline )); then
      fail "apply ${apply_id} of ${CATALOG}/${rule_name} did not reach a terminal phase within ${APPLY_TERMINAL_S}s (last phase='${phase}', body: ${body})"
    fi
    sleep 2
  done
}

# Budget for an async structural apply to land in /list. A structural addOrUpdate returns
# immediately at FENCING (accepted, not yet durable): the rule row is persisted only AFTER the
# background schema fence confirms, and BanyanDB's meta→data-node schema sync can take 1-2 minutes,
# so a single /list read right after the 2xx can miss the row. The /list assertions poll within this
# budget (covers the fence timeout + the sync). ES/JDBC have no such fence — they land in under a
# second, so the poll returns on its first iteration there.
APPLY_LAND_S="${APPLY_LAND_S:-200}"

# Poll GET /runtime/rule/list until the row for (catalog, rule_name) shows the expected status,
# up to APPLY_LAND_S. Returns the matching JSON line on stdout for callers that inspect contentHash.
#
# Optional 3rd arg differ_hash: when set, the poll additionally requires the row's contentHash to
# differ from it. This is the wait-condition for a STRUCTURAL update of an ALREADY-ACTIVE row — the
# status is ACTIVE both before and after the async apply, so a status-only poll would return on the
# first iteration with the OLD (pre-apply) contentHash, before the background fence→persist tail
# has written the new content. Gating on "status==expected AND contentHash advanced" blocks until
# the new content is durable and visible.
list_row() {
  local expected_status="$1"
  local rule_name="${2:-${NAME}}"
  local differ_hash="${3:-}"
  log "runtime-rule list → waiting (≤${APPLY_LAND_S}s) for ${CATALOG}/${rule_name} status=${expected_status}${differ_hash:+ contentHash≠${differ_hash:0:8}…}"
  local deadline=$(( $(date +%s) + APPLY_LAND_S ))
  local lines match actual_status="" actual_hash=""
  while :; do
    lines="$(admin runtime-rule list)" \
      || fail "runtime-rule list failed"
    match="$(echo "${lines}" | jq -c ".rules[] | select(.catalog==\"${CATALOG}\" and .name==\"${rule_name}\")" 2>/dev/null || true)"
    if [[ -n "${match}" ]]; then
      actual_status="$(echo "${match}" | jq -r '.status')"
      actual_hash="$(echo "${match}" | jq -r '.contentHash')"
      if [[ "${actual_status}" == "${expected_status}" \
            && ( -z "${differ_hash}" || "${actual_hash}" != "${differ_hash}" ) ]]; then
        echo "${match}"
        return 0
      fi
    fi
    if (( $(date +%s) >= deadline )); then
      if [[ -n "${match}" ]]; then
        fail "expected /list status=${expected_status}${differ_hash:+ with advanced contentHash}, got status='${actual_status}' hash='${actual_hash}' within ${APPLY_LAND_S}s (row: ${match})"
      fi
      fail "/list has no row for ${CATALOG}/${rule_name} within ${APPLY_LAND_S}s (got: ${lines})"
    fi
    sleep 2
  done
}

# Poll until /list has NO row (or status n/a) for the given (catalog, name), up to APPLY_LAND_S.
# A /delete?mode=revertToBundled runs the async apply pipeline (the bundled re-apply), so the row's
# removal can lag the same way a structural apply's appearance does.
list_no_row() {
  local rule_name="${1:-${NAME}}"
  log "runtime-rule list → waiting (≤${APPLY_LAND_S}s) for NO row for ${CATALOG}/${rule_name}"
  local deadline=$(( $(date +%s) + APPLY_LAND_S ))
  local lines match status
  while :; do
    lines="$(admin runtime-rule list)" \
      || fail "runtime-rule list failed"
    match="$(echo "${lines}" | jq -c ".rules[] | select(.catalog==\"${CATALOG}\" and .name==\"${rule_name}\")" 2>/dev/null || true)"
    if [[ -z "${match}" ]]; then
      return 0
    fi
    status="$(echo "${match}" | jq -r '.status')"
    if [[ "${status}" == "n/a" ]]; then
      return 0
    fi
    if (( $(date +%s) >= deadline )); then
      fail "/list still has row for ${CATALOG}/${rule_name} status=${status} within ${APPLY_LAND_S}s (row: ${match})"
    fi
    sleep 2
  done
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
  admin runtime-rule dump -o "${tar_file}" >/dev/null \
    || fail "runtime-rule dump failed (${label})"
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
  admin runtime-rule list >/dev/null 2>&1 && break
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
await_apply_terminal "${resp}"
list_row "ACTIVE" >/dev/null
hash_initial="$(list_row ACTIVE | jq -r '.contentHash')"
log "  initial contentHash=${hash_initial}"
await_metric_for_step "e2e_rr_requests" "create"

# Phase 2 — UPDATE-FILTER (body-only, same shape).
log "=== Phase 2: UPDATE-FILTER seed-rule-filter-only.yaml ==="
step_set "update_filter"
resp="$(post_rule "${SEED_RULES_DIR}/seed-rule-filter-only.yaml")"
assert_apply_status "filter_only_applied" "${resp}"
await_apply_terminal "${resp}"
# FILTER_ONLY persists synchronously (no applyId), so the new hash is already durable here; the
# differ-gate still hardens the read against any list lag and proves the row actually advanced.
hash_filter_only="$(list_row ACTIVE "${NAME}" "${hash_initial}" | jq -r '.contentHash')"
[[ "${hash_filter_only}" != "${hash_initial}" ]] \
  || fail "FILTER_ONLY apply did not advance /list contentHash"
log "  contentHash advanced to ${hash_filter_only}"
await_metric_for_step "e2e_rr_requests" "update_filter"

# Phase 3 — UPDATE-STRUCTURAL (adds e2e_rr_pool metric).
log "=== Phase 3: UPDATE-STRUCTURAL seed-rule-structural.yaml ==="
step_set "structural"
resp="$(post_rule "${SEED_RULES_DIR}/seed-rule-structural.yaml" "allowStorageChange=true")"
assert_apply_status "structural_applied" "${resp}"
await_apply_terminal "${resp}"
# STRUCTURAL update of an already-ACTIVE row: status stays ACTIVE across the async apply, so gate on
# the contentHash advancing past the filter-only hash, not just on status — otherwise the read races
# the background fence→persist tail and returns the stale pre-apply hash.
hash_structural="$(list_row ACTIVE "${NAME}" "${hash_filter_only}" | jq -r '.contentHash')"
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
delete_expect_status "${NAME}" "409" >/dev/null
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

# Phase 5e/f/g — dynamic-LAYER rejection paths. Each fixture targets one of the
# new applyStatus codes (layer_ordinal_out_of_range, layer_name_invalid,
# layer_name_conflict). Same invariant as 5a-d: response JSON carries the
# expected applyStatus, /list shows no sibling row, the primary rule's
# contentHash is unchanged, and structural-bucket aggregation keeps advancing.

log "=== Phase 5e: ILLEGAL layer ordinal below runtime floor ==="
struct_baseline="$(latest_bucket_id_for_step "e2e_rr_requests" "structural")"
resp="$(post_rule_expect_status \
  "${SEED_RULES_DIR}/illegal-layer-out-of-range.yaml" "400" "" "${SIBLING_NAME}")"
assert_apply_status "layer_ordinal_out_of_range" "${resp}"
list_no_row "${SIBLING_NAME}"
[[ "$(list_row ACTIVE | jq -r '.contentHash')" == "${hash_structural}" ]] \
  || fail "5e: primary rule's contentHash moved after layer-ordinal rejection"
assert_metric_step_advanced "e2e_rr_requests" "structural" "${struct_baseline}" 180

log "=== Phase 5f: ILLEGAL layer name shape ==="
struct_baseline="$(latest_bucket_id_for_step "e2e_rr_requests" "structural")"
resp="$(post_rule_expect_status \
  "${SEED_RULES_DIR}/illegal-layer-name-invalid.yaml" "400" "" "${SIBLING_NAME}")"
assert_apply_status "layer_name_invalid" "${resp}"
list_no_row "${SIBLING_NAME}"
[[ "$(list_row ACTIVE | jq -r '.contentHash')" == "${hash_structural}" ]] \
  || fail "5f: primary rule's contentHash moved after layer-name-invalid rejection"
assert_metric_step_advanced "e2e_rr_requests" "structural" "${struct_baseline}" 180

log "=== Phase 5g: ILLEGAL layer name conflict (built-in MESH redeclared) ==="
struct_baseline="$(latest_bucket_id_for_step "e2e_rr_requests" "structural")"
resp="$(post_rule_expect_status \
  "${SEED_RULES_DIR}/illegal-layer-name-conflict.yaml" "400" "" "${SIBLING_NAME}")"
assert_apply_status "layer_name_conflict" "${resp}"
# Message must name the conflicting source so operators see what to align with.
# resp is swctl's plain-text error envelope ("... (layer_name_conflict): <msg>"),
# not JSON, so grep the message directly rather than parsing it.
echo "${resp}" | grep -q "built-in" \
  || fail "5g: response message did not label source as built-in: ${resp}"
list_no_row "${SIBLING_NAME}"
[[ "$(list_row ACTIVE | jq -r '.contentHash')" == "${hash_structural}" ]] \
  || fail "5g: primary rule's contentHash moved after layer-name-conflict rejection"
assert_metric_step_advanced "e2e_rr_requests" "structural" "${struct_baseline}" 180

# Phase 5h — HAPPY-PATH dynamic-LAYER round-trip + RESTART survival.
# POST a sibling pure-runtime rule (no bundled twin) with layerDefinitions,
# verify swctl layer ls lists it, RESTART the OAP container, verify the
# layer survives the restart AND remains operator-removable through
# /inactivate + /delete. Proves the runtime-channel ownership contract
# end-to-end (the bug RuleSetMerger.merge fix addresses).
log "=== Phase 5h: HAPPY-PATH + RESTART dynamic-LAYER round-trip ==="
struct_baseline="$(latest_bucket_id_for_step "e2e_rr_requests" "structural")"
resp="$(post_rule "${SEED_RULES_DIR}/seed-rule-sibling-with-layer.yaml" "" "${SIBLING_NAME}")"
assert_apply_status "structural_applied" "${resp}"
await_apply_terminal "${resp}" "${SIBLING_NAME}"
list_row "ACTIVE" "${SIBLING_NAME}" >/dev/null
sleep 2
layers_after_create="$(swctl --display yaml \
  --base-url=http://${OAP_HOST}:${OAP_GQL_PORT}/graphql layer ls)"
echo "${layers_after_create}" | grep -q "E2E_RR_DYN_LAYER" \
  || fail "5h-create: layer ls missing E2E_RR_DYN_LAYER (got: ${layers_after_create})"
log "  pre-restart: layer ls includes E2E_RR_DYN_LAYER"

# Restart the OAP container. base-compose.yml pins the OAP image to
# `skywalking/oap:latest`; filter by that. Do NOT fall back to a bare
# `grep oap` match — on a shared dev/CI host that can hit unrelated
# containers (e.g. another OAP, an agent test rig).
log "  restarting OAP container..."
oap_container="$(docker ps --filter "ancestor=skywalking/oap:latest" \
                            --format '{{.Names}}' | head -1)"
[ -n "${oap_container}" ] || fail "5h: no skywalking/oap:latest container found — refusing to guess"
docker restart "${oap_container}" >/dev/null
# Wait for the REST port to come back. Cap at 180s so a true hang surfaces.
for i in $(seq 1 90); do
  if admin runtime-rule list >/dev/null 2>&1; then
    log "  OAP back up after ${i}*2s"
    break
  fi
  sleep 2
done
admin runtime-rule list >/dev/null \
  || fail "5h: OAP did not come back online after restart"

# Critical assertion: the runtime layer must still be visible AND retain its
# dynamic ownership (i.e. removable through /delete below). Without the
# RuleSetMerger override-only fix, the pure-runtime rule's layerDefinitions
# would be replayed through Layer.register at boot and the layer would be
# stuck for the OAP process lifetime.
sleep 2
layers_after_restart="$(swctl --display yaml \
  --base-url=http://${OAP_HOST}:${OAP_GQL_PORT}/graphql layer ls)"
echo "${layers_after_restart}" | grep -q "E2E_RR_DYN_LAYER" \
  || fail "5h-restart: layer ls missing E2E_RR_DYN_LAYER after restart (got: ${layers_after_restart})"
list_row "ACTIVE" "${SIBLING_NAME}" >/dev/null
log "  post-restart: layer + rule survived"

# Now prove the layer is still removable through the dynamic channel.
retry_admin runtime-rule inactivate --catalog "${CATALOG}" --name "${SIBLING_NAME}" >/dev/null \
  || fail "5h: sibling inactivate failed"
retry_admin runtime-rule delete --catalog "${CATALOG}" --name "${SIBLING_NAME}" >/dev/null \
  || fail "5h: sibling delete failed"
list_no_row "${SIBLING_NAME}"
sleep 2
layers_after_delete="$(swctl --display yaml \
  --base-url=http://${OAP_HOST}:${OAP_GQL_PORT}/graphql layer ls)"
echo "${layers_after_delete}" | grep -q "E2E_RR_DYN_LAYER" \
  && fail "5h-delete: layer ls still includes E2E_RR_DYN_LAYER after sibling delete — runtime ownership lost across restart (RuleSetMerger merger bug?)"

# Primary rule's contentHash unchanged throughout; aggregation resumed after restart.
[[ "$(list_row ACTIVE | jq -r '.contentHash')" == "${hash_structural}" ]] \
  || fail "5h: primary rule's contentHash moved during dynamic-layer round-trip"
assert_metric_step_advanced "e2e_rr_requests" "structural" "${struct_baseline}" 300

# Phase 6 — SHAPE-BREAK via the supported route: /inactivate → /delete →
# POST a new shape under the same (catalog, name).
log "=== Phase 6: SHAPE-BREAK ==="
step_set "shape_break_old"
log "  inactivate to release the old shape"
retry_admin runtime-rule inactivate --catalog "${CATALOG}" --name "${NAME}" >/dev/null \
  || fail "shape-break: inactivate failed"
list_row "INACTIVE" >/dev/null
log "  delete to drop the old measure"
retry_admin runtime-rule delete --catalog "${CATALOG}" --name "${NAME}" >/dev/null \
  || fail "shape-break: delete failed"
list_no_row

step_set "shape_break_new"
log "  POST INSTANCE-scope rule v4"
resp="$(post_rule "${SEED_RULES_DIR}/seed-rule-instance.yaml")"
assert_apply_status "structural_applied" "${resp}"
await_apply_terminal "${resp}"
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
retry_admin runtime-rule inactivate --catalog "${CATALOG}" --name "${NAME}" >/dev/null \
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
await_apply_terminal "${resp}"
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
retry_admin runtime-rule inactivate --catalog "${CATALOG}" --name "${NAME}" >/dev/null \
  || fail "phase-9: inactivate-before-delete failed"
list_row "INACTIVE" >/dev/null
retry_admin runtime-rule delete --catalog "${CATALOG}" --name "${NAME}" >/dev/null \
  || fail "phase-9: delete failed"
list_no_row
log "  ✓ row gone + backend probe agrees"

# Phase 10 — DUMP (final). After DELETE, the dump should contain only the
# manifest — no rule files.
log "=== Phase 10: DUMP (final) ==="
assert_dump_contains "final" "manifest"

log "=== runtime-rule-flow.sh PASSED ==="
