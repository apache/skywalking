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

# DSL Debug API — TAL, OTLP input. Drives the shipped otlp-general rule:
#   0. status lists the tal catalog; the listing has the rule
#   1. a statement session and a block session (recordCap 10) capture span
#      records together, one span each
#   2. the frontend's CLIENT run sent the caller half; the backend's SERVER
#      run sent the callee half
#   3. the trace-pairing capture on the trace's owner matched the backend's peer to its server, the alias pushed
#   4. the block session keeps the blocks and the same outputs, no statements,
#      and no more records than its recordCap
#   5. the statement session read five records a page, while it captures, covers
#      the capture without overlap
#   6. stop both

set -euo pipefail

log() { echo "[tal-otlp-flow] $*" >&2; }
fail() { log "FAIL: $*"; exit 1; }

OAP_HOST="${OAP_HOST:-127.0.0.1}"
OAP_REST_PORT="${OAP_REST_PORT:-17128}"
OAP_BASE="http://${OAP_HOST}:${OAP_REST_PORT}"

admin() { swctl --display json --admin-url="${OAP_BASE}" admin "$@"; }

SETTLE_SECONDS="${SETTLE_SECONDS:-300}"
FILE="otlp-general"
RULE="otlp-general"
PEER="e2e-tal-backend:8080"
CLIENT_ID="e2e-dsldbg-tal-otlp-1"
BLOCK_CLIENT_ID="e2e-dsldbg-tal-otlp-block"
BLOCK_CAP=10

log "waiting for OAP admin port"
deadline=$(( $(date +%s) + 120 ))
until admin dsl-debug status >/dev/null 2>&1; do
    if [ "$(date +%s)" -ge "${deadline}" ]; then fail "OAP admin not ready after 120s"; fi
    sleep 2
done

log "=== Phase 0: status and listing ==="
admin dsl-debug status | jq -e '.catalogs | index("tal") != null' >/dev/null \
    || fail "status does not list the tal catalog: $(admin dsl-debug status)"
curl -fsS "${OAP_BASE}/runtime/tal/files" | jq -e --arg f "${FILE}" --arg r "${RULE}" \
    '.files[] | select(.name == $f and .list == "talFiles" and .inputType == "otlp")
     | .rules[] | select(.name == $r and .layer == "OTLP_GENERAL" and .debuggable == true)' >/dev/null \
    || fail "listing has no debuggable ${RULE} in ${FILE}"

log "=== Phase 1: capture ==="
install_body="$(admin dsl-debug session start --catalog tal --name "${FILE}" --rule-name "${RULE}" \
    --client-id "${CLIENT_ID}" --granularity statement)"
SESSION_ID="$(echo "${install_body}" | jq -r '.sessionId // empty')"
[ -n "${SESSION_ID}" ] || fail "install did not return sessionId — body: ${install_body}"
block_body="$(admin dsl-debug session start --catalog tal --name "${FILE}" --rule-name "${RULE}" \
    --client-id "${BLOCK_CLIENT_ID}" --granularity block --record-cap "${BLOCK_CAP}")"
BLOCK_SESSION_ID="$(echo "${block_body}" | jq -r '.sessionId // empty')"
[ -n "${BLOCK_SESSION_ID}" ] || fail "block install did not return sessionId — body: ${block_body}"
curl -fsS "${OAP_BASE}/trace-pairing-debugging/status" | jq -e '.capture.state == "off"' >/dev/null \
    || fail "the trace-pairing capture is on before anyone started it"
curl -fsS -X POST "${OAP_BASE}/trace-pairing-debugging/start" | jq -e '.nodes[0].capture.state == "capturing"' \
    >/dev/null || fail "the trace-pairing capture did not start"
log "✓ sessions installed: statement ${SESSION_ID}, block ${BLOCK_SESSION_ID}; the pairing capture started"

CALLER='[.nodes[].records[] | select(.context.kind == "span") | .spans[0]
         | select(.run.outputs[]? | .kind == "pairing" and .role == "caller" and .outcome == "sent")]'
CALLEE='[.nodes[].records[] | select(.context.kind == "span") | .spans[0]
         | select(.run.outputs[]? | .kind == "pairing" and .role == "callee" and .outcome == "sent")]'
FOUND="[.matchedPeers[] | select(.peer == \"${PEER}\" and .server == \"e2e-tal-backend\"
        and (.outcomes.registered // 0) > 0)]"
# The block session installs after the statement one, so both are polled until each holds what is asserted.
BLOCK_OUTPUTS='[.nodes[].records[] | select(.context.kind == "span") | .spans[0].run.outputs[]
                | select(.kind == "metrics" or .kind == "pairing") | .kind] | unique | length'
deadline=$(( $(date +%s) + SETTLE_SECONDS ))
body=""
block=""
pairing=""
while (( $(date +%s) < deadline )); do
    body="$(admin dsl-debug session get "${SESSION_ID}")"
    block="$(admin dsl-debug session get "${BLOCK_SESSION_ID}")"
    # Each read is a ping that keeps the capture running.
    pairing="$(curl -fsS "${OAP_BASE}/trace-pairing-debugging/pairings")"
    if [ "$(echo "${body}" | jq "${CALLER} | length")" -gt 0 ] \
        && [ "$(echo "${body}" | jq "${CALLEE} | length")" -gt 0 ] \
        && [ "$(echo "${pairing}" | jq "${FOUND} | length")" -gt 0 ] \
        && [ "$(echo "${block}" | jq "${BLOCK_OUTPUTS}")" -eq 2 ]; then
        break
    fi
    sleep 5
done
echo "${body}" > /tmp/dsldbg-tal-otlp-response.json
log "  payload (truncated 1500c): ${body:0:1500}"

log "=== Phase 2: span records ==="
echo "${body}" | jq -e '[.nodes[].records[] | select(.context.kind == "span") | select((.spans | length) != 1)]
                        | length == 0' >/dev/null \
    || fail "an OTLP record does not hold exactly one span"
echo "${body}" | jq -e '[.nodes[].records[] | select(.context.kind == "span")
                        | select(.context.resource["service.layer"] != "OTLP_GENERAL")] | length == 0' >/dev/null \
    || fail "an OTLP record's context lacks the resource the span arrived with"
caller="$(echo "${body}" | jq "${CALLER} | .[0]")"
[ "${caller}" != "null" ] || fail "no run sent a caller half"
echo "${caller}" | jq -e --arg p "${PEER}" '.meta.kind == "CLIENT"
    and ([.run.outputs[] | select(.kind == "pairing" and .peer == $p)] | length == 1)
    and ([.run.steps[] | select(.kind == "prepareNetworkAlias" and .value == $p)] | length > 0)' >/dev/null \
    || fail "the CLIENT run's caller half does not name ${PEER}: ${caller:0:1500}"
callee="$(echo "${body}" | jq "${CALLEE} | .[0]")"
[ "${callee}" != "null" ] || fail "no run sent a callee half"
echo "${callee}" | jq -e '.meta.kind == "SERVER" and .meta.service == "e2e-tal-backend"
    and ([.run.outputs[] | select(.kind == "metrics")] | length > 0)
    and (.run.state.meta.layer == "OTLP_GENERAL")
    and ([.run.steps[] | select(.kind == "if" and (.result | type) == "boolean")] | length > 0)' >/dev/null \
    || fail "the SERVER run lacks its metrics, end state or conditions: ${callee:0:1500}"

log "✓ OTLP TAL statement records valid"

log "=== Phase 3: the pairing on the trace's owner ==="
echo "${pairing}" > /tmp/dsldbg-tal-otlp-pairing.json
echo "${pairing}" | jq -e --arg p "${PEER}" '.state == "capturing"
    and .received.caller > 0 and .received.callee > 0 and .matched > 0
    and ([.matchedPeers[] | select(.peer == $p and .server == "e2e-tal-backend"
          and (.callers | index("e2e-tal-frontend")) != null
          and .address == "e2e-tal-frontend->" + $p and .outcomes.registered > 0)] | length == 1)' >/dev/null \
    || fail "the capture did not match ${PEER} to e2e-tal-backend, dialed by e2e-tal-frontend: ${pairing:0:1500}"
# The read is of one round, which every node that answered holds.
echo "${pairing}" | jq -e --arg round "$(echo "${pairing}" | jq -r .roundId)" '(.roundId | length) > 0
    and .startTime > 0 and ([.nodes[] | select(.status == "ok")] | length) > 0
    and all(.nodes[]; .status != "ok" or .roundId == $round)' \
    >/dev/null || fail "the read names no round, or a node that answered holds another: ${pairing:0:1500}"
log "✓ the peer is matched to its server, in one round"

log "=== Phase 4: the block session ==="
echo "${block}" > /tmp/dsldbg-tal-otlp-block-response.json
echo "${block}" | jq -e --argjson cap "${BLOCK_CAP}" '[.nodes[] | select((.records | length) > $cap)] | length == 0' \
    >/dev/null || fail "a node kept more block records than the session's recordCap ${BLOCK_CAP}"
echo "${block}" | jq -e '[.nodes[].records[] | select(.context.kind == "span")] | length > 0' >/dev/null \
    || fail "the block session captured no span record: ${block:0:1500}"
echo "${block}" | jq -e '[.nodes[].records[].spans[].run.steps[] | select(.kind == "if" or .kind == "def")]
                         | length == 0' >/dev/null \
    || fail "a block record carries statement steps: ${block:0:1500}"
echo "${block}" | jq -e '[.nodes[].records[] | select(.context.kind == "span") | .spans[0].run.outputs[]
                         | select(.kind == "metrics" or .kind == "pairing")] | length > 0' >/dev/null \
    || fail "the block records lack the outputs a statement record has: ${block:0:1500}"
# What a run pushed forward has its step in block granularity too: the metrics and the pairing half.
for kind in metrics pairing; do
    echo "${block}" | jq -e --arg k "${kind}" '[.nodes[].records[].spans[].run as $run | $run.steps[]
                             | select(.kind == $k) | $run.outputs[.output] | select(.kind == $k)] | length > 0' \
        >/dev/null || fail "no block step names a ${kind} output: ${block:0:1500}"
done
log "✓ OTLP TAL block records valid"

log "=== Phase 5: read the statement session page by page ==="
# While the session still captures: each page holds what was captured at or after its point and before the next one.
# Capture times are compared as the strings they are, `<millis>.<six-digit nanos>`, whose fixed width orders them.
whole="$(curl -fsS "${OAP_BASE}/dsl-debugging/session/${SESSION_ID}")"
# A page holds pageSize records, more only when those after the fifth share its capture time.
PAGE_SIZE_HOLDS='(([.nodes[].records[].captureTime] | sort) as $t | ($t | length) <= 5 or $t[4] == $t[-1])'
KEY='"\(.captureTime)@\(.context.kind)@\(.spans[0].span.spanId // "")"'
since=""
next=""
seen=""
for _ in $(seq 1 100); do
    query="pageSize=5"
    [ -n "${since}" ] && query="${query}&capturedSince=${since}"
    page="$(curl -fsS "${OAP_BASE}/dsl-debugging/session/${SESSION_ID}?${query}")"
    next="$(echo "${page}" | jq -r '.nextCapturedSince')"
    echo "${page}" | jq -e --arg since "${since}" --arg next "${next}" \
        '($since == "" or $next >= $since)
         and ([.nodes[].records[]] | all(($since == "" or .captureTime >= $since) and .captureTime < $next))
         and '"${PAGE_SIZE_HOLDS}" >/dev/null \
        || fail "the page from '${since}' holds a record outside it: ${page:0:1500}"
    keys="$(echo "${page}" | jq -r "[.nodes[].records[] | ${KEY}] | join(\" \")")"
    [ -z "${keys}" ] && break
    seen="${seen} ${keys}"
    since="${next}"
done
[ "$(echo ${seen} | tr ' ' '\n' | sort | uniq -d | wc -l | tr -d ' ')" = "0" ] || fail "two pages hold one record"
for key in $(echo "${whole}" | jq -r --arg next "${next}" ".nodes[].records[] | select(.captureTime < \$next) | ${KEY}"); do
    case " ${seen} " in
        *" ${key} "*) ;;
        *) fail "the record ${key} is on no page" ;;
    esac
done
log "✓ pages cover the capture without overlap"

log "=== Phase 6: stop the sessions ==="
for id in "${SESSION_ID}" "${BLOCK_SESSION_ID}"; do
    admin dsl-debug session stop "${id}" | jq -e '.localStopped == true' >/dev/null \
        || fail "localStopped != true for ${id}"
done
curl -fsS -X POST "${OAP_BASE}/trace-pairing-debugging/stop" | jq -e '.nodes[0].capture.state == "off"' \
    >/dev/null || fail "the trace-pairing capture did not stop"
# A stop releases what the capture found, as a DSL session's records go when it stops: the client keeps what it read.
curl -fsS "${OAP_BASE}/trace-pairing-debugging/pairings" \
    | jq -e '.state == "off" and .matched == 0 and (.matchedPeers | length) == 0' \
    >/dev/null || fail "a stopped trace-pairing capture still holds what it found"

log "=== ALL TAL OTLP FLOW PHASES PASSED ==="
