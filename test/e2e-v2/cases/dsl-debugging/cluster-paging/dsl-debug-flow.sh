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

# DSL Debug API — paging across OAP nodes. Spans reach oap1 only:
#   0. both admin ports answer
#   1. a session started through oap2 installs on both nodes
#   2. a read through oap2 holds oap1's records, which came over the streamed collect
#   3. a read paged five records at a time, landing on oap2 and oap1 in turn, covers the capture: no record on two
#      pages, every record of a whole read on a page
#   4. stop

set -euo pipefail

log() { echo "[cluster-paging-flow] $*" >&2; }
fail() { log "FAIL: $*"; exit 1; }

NODE1="http://${OAP_HOST:-127.0.0.1}:${OAP1_REST_PORT:-17128}"
NODE2="http://${OAP_HOST:-127.0.0.1}:${OAP2_REST_PORT:-27128}"
SETTLE_SECONDS="${SETTLE_SECONDS:-300}"

# A record is known by its node, its capture time and its span: two nodes can capture at the same time. A response
# names its own node's slice by no peer, and the other's by its admin address. Capture times are compared as the
# strings they are, `<millis>.<six-digit nanos>`, whose fixed width orders them.
RECORD_KEYS='.nodes[] | (if .peer == null then $self else (.peer | split(":")[0]) end) as $node
             | .records[] | "\($node)@\(.captureTime)@\(.context.kind)@\(.spans[0].span.spanId // "")"'
PAGE_SIZE_HOLDS='(([.nodes[].records[].captureTime] | sort) as $t | ($t | length) <= 5 or $t[4] == $t[-1])'
self_of() { if [ "$1" = "${NODE2}" ]; then echo oap2; else echo oap1; fi; }

log "=== Phase 0: both admin ports ==="
for node in "${NODE1}" "${NODE2}"; do
    deadline=$(( $(date +%s) + 120 ))
    until curl -fsS "${node}/dsl-debugging/status" >/dev/null 2>&1; do
        if [ "$(date +%s)" -ge "${deadline}" ]; then fail "${node} admin not ready after 120s"; fi
        sleep 2
    done
done

log "=== Phase 1: a session through oap2 ==="
# The nodes learn each other from ZooKeeper, and the admin channels reconcile every 5s, so the install is retried
# until oap2 reaches oap1 too.
deadline=$(( $(date +%s) + 120 ))
SESSION_ID=""
while (( $(date +%s) < deadline )); do
    body="$(curl -fsS -X POST \
        "${NODE2}/dsl-debugging/session?catalog=tal&name=otlp-general&ruleName=otlp-general&clientId=e2e-dsldbg-cluster")"
    if echo "${body}" | jq -e '[.peers[]? | select(.ack == "INSTALLED")] | length >= 1' >/dev/null; then
        SESSION_ID="$(echo "${body}" | jq -r '.sessionId')"
        break
    fi
    sleep 5
done
[ -n "${SESSION_ID}" ] || fail "the session did not install on oap1 through oap2: ${body:0:1500}"
log "✓ session ${SESSION_ID} installed on both nodes"

log "=== Phase 2: oap2 reads oap1's records ==="
FROM_PEER='[.nodes[] | select(.peer != null and (.status == "ok" or .status == "captured")) | .records | length] | add // 0'
deadline=$(( $(date +%s) + SETTLE_SECONDS ))
whole=""
while (( $(date +%s) < deadline )); do
    whole="$(curl -fsS "${NODE2}/dsl-debugging/session/${SESSION_ID}")"
    if [ "$(echo "${whole}" | jq "${FROM_PEER}")" -ge 20 ]; then
        break
    fi
    sleep 5
done
echo "${whole}" > /tmp/dsldbg-cluster-paging-response.json
[ "$(echo "${whole}" | jq "${FROM_PEER}")" -ge 20 ] \
    || fail "oap2 read too few records from oap1: $(echo "${whole}" | jq -c '[.nodes[] | {peer, status, records: (.records | length)}]')"
echo "${whole}" | jq -e '[.nodes[] | select(.peer != null) | .records[]
                         | select((.captureTime | type) != "string" or (.spans | length) != 1)] | length == 0' \
    >/dev/null || fail "a record from oap1 lacks its capture time or its span"
log "✓ $(echo "${whole}" | jq "${FROM_PEER}") records from oap1 over the stream"

# Node ids need not tell two nodes apart, as every node binding 0.0.0.0 reports the same one; instance ids do.
echo "${whole}" | jq -e '[.nodes[] | select(.status == "ok" or .status == "captured") | .instanceId]
        | length == 2 and all(type == "string" and . != "") and (unique | length) == 2' >/dev/null \
    || fail "the two nodes' slices lack distinct instance ids: ${whole:0:1500}"
log "✓ each node's slice carries its own instance id"

log "=== Phase 3: paging across the nodes ==="
since=""
next=""
seen=""
pages=0
via="${NODE2}"
for _ in $(seq 1 300); do
    query="pageSize=5"
    [ -n "${since}" ] && query="${query}&capturedSince=${since}"
    page="$(curl -fsS "${via}/dsl-debugging/session/${SESSION_ID}?${query}")"
    next="$(echo "${page}" | jq -r '.nextCapturedSince')"
    # A page holds pageSize records, more only when those after the fifth share its capture time, as records two
    # nodes, or one node's clock tick, captured together are not split.
    echo "${page}" | jq -e --arg since "${since}" --arg next "${next}" \
        '($since == "" or $next >= $since)
         and ([.nodes[].records[]] | all(($since == "" or .captureTime >= $since) and .captureTime < $next))
         and '"${PAGE_SIZE_HOLDS}" >/dev/null \
        || fail "the page from '${since}', read through ${via}, holds a record outside it: ${page:0:1500}"
    keys="$(echo "${page}" | jq -r --arg self "$(self_of "${via}")" "[${RECORD_KEYS}] | join(\" \")")"
    [ -z "${keys}" ] && break
    seen="${seen} ${keys}"
    since="${next}"
    pages=$((pages + 1))
    # The next page lands on the other node, as a load balancer may route it.
    if [ "${via}" = "${NODE2}" ]; then via="${NODE1}"; else via="${NODE2}"; fi
done
[ "${pages}" -ge 2 ] || fail "the capture was read in ${pages} pages, not across the nodes"
[ "$(echo ${seen} | tr ' ' '\n' | sort | uniq -d | wc -l | tr -d ' ')" = "0" ] || fail "two pages hold one record"
# The whole read went through oap2.
for key in $(echo "${whole}" | jq -r --arg self oap2 --arg next "${next}" \
        "[${RECORD_KEYS}] as \$keys | [.nodes[] | .records[] | .captureTime < \$next] as \$before
         | range(\$keys | length) | select(\$before[.]) | \$keys[.]"); do
    case " ${seen} " in
        *" ${key} "*) ;;
        *) fail "the record ${key} is on no page" ;;
    esac
done
log "✓ $(echo ${seen} | wc -w | tr -d ' ') records over ${pages} pages through both nodes, none twice, none missing"

log "=== Phase 4: stop ==="
curl -fsS -X POST "${NODE2}/dsl-debugging/session/${SESSION_ID}/stop" | jq -e '.localStopped == true' >/dev/null \
    || fail "localStopped != true"

log "=== ALL CLUSTER PAGING FLOW PHASES PASSED ==="
