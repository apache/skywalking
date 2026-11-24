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

# DSL Debug API — TAL, native input. The emitter reports one typical segment a second: an Entry span, a Local span,
# and Exit spans to a database (mysql:3306) and a cache (redis:6379), in the order an agent finishes them.
#   0. status lists the tal catalog; the listing has the rules and their files
#   1. a recordCap above the debug API's cap (100) is refused
#   2. four sessions capture together: native-segment in statement (recordCap 3) and block granularity,
#      native-virtual-database in statement and native-virtual-cache in block granularity
#   3. native-segment: the record lists the segment's four spans; span 0 starts with its first {} steps; the
#      build {} steps and the Segment source sit on the one span whose run queued them; lines are the file's
#   4. the virtual rules: the Exit span of their layer sends its sources in the build phase, every other span's
#      run aborts and is published with the abort line, as LAL's are
#   5. block granularity keeps the blocks and a step per output, no statements
#   6. stop

set -euo pipefail

log() { echo "[tal-native-flow] $*" >&2; }
fail() { log "FAIL: $*"; exit 1; }

OAP_HOST="${OAP_HOST:-127.0.0.1}"
OAP_REST_PORT="${OAP_REST_PORT:-17128}"
OAP_BASE="http://${OAP_HOST}:${OAP_REST_PORT}"

admin() { swctl --display json --admin-url="${OAP_BASE}" admin "$@"; }

SETTLE_SECONDS="${SETTLE_SECONDS:-300}"
STATEMENT_CAP=3

log "waiting for OAP admin port"
deadline=$(( $(date +%s) + 120 ))
until admin dsl-debug status >/dev/null 2>&1; do
    if [ "$(date +%s)" -ge "${deadline}" ]; then fail "OAP admin not ready after 120s"; fi
    sleep 2
done

log "=== Phase 0: status and listing ==="
admin dsl-debug status | jq -e '.injectionEnabled == true and (.catalogs | index("tal") != null)' >/dev/null \
    || fail "status does not list the tal catalog: $(admin dsl-debug status)"
files="$(curl -fsS "${OAP_BASE}/runtime/tal/files")"
for pair in native-segment:native-segment native-virtual-service:native-virtual-database \
            native-virtual-service:native-virtual-cache; do
    echo "${files}" | jq -e --arg f "${pair%%:*}" --arg r "${pair##*:}" \
        '.files[] | select(.name == $f and .list == "nativeTalFiles" and .inputType == "sw-native")
         | .rules[] | select(.name == $r and .debuggable == true and .order >= 0)' >/dev/null \
        || fail "listing has no debuggable ${pair##*:} in ${pair%%:*}: ${files}"
done
content="$(curl -fsS "${OAP_BASE}/runtime/tal/files/native-segment")"
echo "${content}" | grep -q -- "- name: native-segment" || fail "native-segment's content does not hold its rule"
# The rule's `dsl: |` key line; its DSL starts on the next line, so every step's file line is after it.
DSL_KEY_LINE="$(echo "${content}" | awk 'index($0, "- name: native-segment") {f=1} f && /dsl:/ {print NR; exit}')"
[ -n "${DSL_KEY_LINE}" ] || fail "no dsl key for native-segment"

log "=== Phase 1: a recordCap above the debug API's cap is refused ==="
over="$(curl -sS -o /tmp/dsldbg-tal-over.json -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -d '{"recordCap": 101}' \
    "${OAP_BASE}/dsl-debugging/session?catalog=tal&name=native-segment&ruleName=native-segment&clientId=e2e-dsldbg-tal-over")"
[ "${over}" = "400" ] && jq -e '.code == "invalid_limits"' /tmp/dsldbg-tal-over.json >/dev/null \
    || fail "a recordCap of 101 was not refused: HTTP ${over} $(cat /tmp/dsldbg-tal-over.json)"

log "=== Phase 2: four sessions capture together ==="
start_session() {
    # name rule granularity clientId [recordCap, the debug API's cap by default]
    local body
    body="$(admin dsl-debug session start --catalog tal --name "$1" --rule-name "$2" --granularity "$3" \
        --client-id "$4" --record-cap "${5:-100}")"
    echo "${body}" | jq -r '.sessionId // empty'
}
SEGMENT_STATEMENT="$(start_session native-segment native-segment statement e2e-tal-seg-stmt "${STATEMENT_CAP}")"
SEGMENT_BLOCK="$(start_session native-segment native-segment block e2e-tal-seg-block)"
DATABASE="$(start_session native-virtual-service native-virtual-database statement e2e-tal-db)"
CACHE="$(start_session native-virtual-service native-virtual-cache block e2e-tal-cache)"
for id in "${SEGMENT_STATEMENT}" "${SEGMENT_BLOCK}" "${DATABASE}" "${CACHE}"; do
    [ -n "${id}" ] || fail "a session did not install"
done
log "✓ sessions installed"

# A segment record of the emitter's four spans whose run on the span of the given layer sent the given source.
wanted() {
    echo "[.nodes[].records[] | select(.context.kind == \"segment\" and .context.segment.spanCount == 4)
           | select([.spans[] | select(.span.spanLayer == \"$1\") | .run.outputs[]?
                     | select(.kind == \"source\" and .scope == \"$2\")] | length > 0)]"
}
SEGMENT_WANTED="$(wanted Database Segment)"
deadline=$(( $(date +%s) + SETTLE_SECONDS ))
while (( $(date +%s) < deadline )); do
    seg_stmt="$(admin dsl-debug session get "${SEGMENT_STATEMENT}")"
    seg_block="$(admin dsl-debug session get "${SEGMENT_BLOCK}")"
    db="$(admin dsl-debug session get "${DATABASE}")"
    cache="$(admin dsl-debug session get "${CACHE}")"
    if [ "$(echo "${seg_stmt}" | jq "${SEGMENT_WANTED} | length")" -gt 0 ] \
        && [ "$(echo "${seg_block}" | jq "${SEGMENT_WANTED} | length")" -gt 0 ] \
        && [ "$(echo "${db}" | jq "$(wanted Database DatabaseAccess) | length")" -gt 0 ] \
        && [ "$(echo "${cache}" | jq "$(wanted Cache CacheAccess) | length")" -gt 0 ]; then
        break
    fi
    sleep 5
done
echo "${seg_stmt}" > /tmp/dsldbg-tal-native-response.json
echo "${seg_block}" > /tmp/dsldbg-tal-native-block-response.json
echo "${db}" > /tmp/dsldbg-tal-native-database-response.json
echo "${cache}" > /tmp/dsldbg-tal-native-cache-response.json

log "=== Phase 3: native-segment, statement granularity ==="
record="$(echo "${seg_stmt}" | jq "${SEGMENT_WANTED} | .[0]")"
[ "${record}" != "null" ] || fail "no native-segment record of the emitter's segment: ${seg_stmt:0:2000}"
log "  record (truncated 1500c): ${record:0:1500}"
echo "${seg_stmt}" | jq -e --argjson cap "${STATEMENT_CAP}" '[.nodes[] | select((.records | length) > $cap)] | length == 0' \
    >/dev/null || fail "a node kept more records than the session's recordCap ${STATEMENT_CAP}"
# The shipped native-segment.yaml opens the rule's DSL on line 30, after its `dsl: |`; every step's file line counts
# from there.
echo "${record}" | jq -e '.rule.file == "native-segment.yaml" and .rule.inputType == "sw-native"
    and .rule.dslFirstLine == "30"
    and ([.spans[].run.steps[]? | select(.sourceLine != null and .sourceLine < 30)] | length == 0)
    and (.context.segment.service == "e2e-tal-native-svc")
    and (.context.derived.segmentLatency | type) == "number"
    and (.context.derived.segmentSampled | type) == "boolean"' >/dev/null \
    || fail "the record's rule or context is not the emitter's segment"
echo "${record}" | jq -e '[.spans[].span | "\(.spanType)/\(.spanLayer)/\(.peer)"]
    == ["Exit/Database/mysql:3306", "Exit/Cache/redis:6379", "Local/Unknown/", "Entry/Http/"]' >/dev/null \
    || fail "the record does not list the segment's four spans in wire order: $(echo "${record}" | jq -c '[.spans[].span]')"
echo "${record}" | jq -e '[.spans[] | select(.span.spanId == 0) | .run.steps[] | select(.stage == "first")]
                          | length > 0' >/dev/null || fail "span 0 has no first {} steps"
echo "${record}" | jq -e '[.spans[] | select([.run.steps[] | select(.stage == "build")] | length > 0)
                          | .span.spanId] == [2]' >/dev/null \
    || fail "the build {} steps are not on the first span in wire order alone"
echo "${record}" | jq -e '[.spans[0].run.outputs[] | select(.kind == "source" and .scope == "Segment")] | length == 1' \
    >/dev/null || fail "the Segment source is not on the span that queued the build"
echo "${record}" | jq -e '[.spans[].run.steps[] | select(.kind == "if" and (.result | type) != "boolean")]
                          | length == 0' >/dev/null || fail "an if step carries no boolean result"
echo "${record}" | jq -e --argjson key "${DSL_KEY_LINE}" \
    '[.spans[].run.steps[] | select(has("dslLine") or (has("sourceLine") and .sourceLine <= $key))] | length == 0' \
    >/dev/null || fail "a step's line is not a line of the rule file, after its dsl key at line ${DSL_KEY_LINE}"
echo "${record}" | jq -e '[.spans[] | select(.run.state.segmentDefs | has("buildQueued"))] | length == 4' \
    >/dev/null || fail "not every span's run carries the segmentDef values"
log "✓ native-segment statement record valid"

log "=== Phase 4: the virtual rules ==="
# The span of the rule's layer sends its sources in its build phase; the other three runs abort.
check_virtual() {
    # body layer scope rule
    local rec
    rec="$(echo "$1" | jq "$(wanted "$2" "$3") | .[0]")"
    [ "${rec}" != "null" ] || fail "$4: no record of the emitter's segment: ${1:0:2000}"
    echo "${rec}" | jq -e --arg layer "$2" --arg scope "$3" '
        ([.spans[] | select(.span.spanLayer == $layer) | .run | select(.continueOn)
          | [.outputs[].scope] | (index("ServiceMeta") != null and index($scope) != null)] == [true])
        and ([.spans[] | select(.span.spanLayer != $layer) | .run
              | select(.continueOn == false and (.reason | startswith("abort {} at line")))] | length == 3)' \
        >/dev/null || fail "$4: the ${2} span's sources or the other spans' aborts are wrong: ${rec:0:2000}"
    echo "${rec}"
}
db_record="$(check_virtual "${db}" Database DatabaseAccess native-virtual-database)"
echo "${db_record}" | jq -e '[.spans[] | select(.span.spanLayer == "Database") | .run.steps[]
                             | select(.stage == "build")] | length > 0' >/dev/null \
    || fail "native-virtual-database: the database span has no build steps"
cache_record="$(check_virtual "${cache}" Cache CacheAccess native-virtual-cache)"
log "✓ virtual database and cache records valid"

log "=== Phase 5: block granularity ==="
block_record="$(echo "${seg_block}" | jq "${SEGMENT_WANTED} | .[0]")"
for rec in "${block_record}" "${cache_record}"; do
    echo "${rec}" | jq -e '[.spans[].run.steps[] | select(.kind == "if" or .kind == "def" or .kind == "assign"
                            or .kind == "segmentDef")] | length == 0' >/dev/null \
        || fail "a block record carries statement steps: ${rec:0:1500}"
    # What the run pushed forward has its step in block granularity too, naming its output.
    echo "${rec}" | jq -e '([.spans[].run.steps[] | select(.kind == "block")] | length > 0)
        and ([.spans[].run as $run | $run.steps[] | select(.kind == "source") | $run.outputs[.output]
              | select(.kind == "source")] | length > 0)' >/dev/null \
        || fail "a block record lacks its blocks or its output steps: ${rec:0:1500}"
done
log "✓ block records valid"

log "=== Phase 6: stop the sessions ==="
for id in "${SEGMENT_STATEMENT}" "${SEGMENT_BLOCK}" "${DATABASE}" "${CACHE}"; do
    admin dsl-debug session stop "${id}" | jq -e '.localStopped == true' >/dev/null \
        || fail "localStopped != true for ${id}"
done

log "=== ALL TAL NATIVE FLOW PHASES PASSED ==="
