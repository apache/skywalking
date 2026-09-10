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

# Each verify case is one mode of this script. The reads go through swctl's ai-agent commands, so the CLI
# pinned in script/env is proven against this OAP too; curl covers what a CLI cannot choose, the HTTP
# version, the encoding and the wire status. The framework compares the YAML each mode prints with the
# expected file.
#
#   verify.sh list          OAP            one row per conversation
#   verify.sh list-limit    OAP            limit 1 keeps the newest
#   verify.sh list-instance OAP            the sender filter, hit and miss
#   verify.sh list-window   OAP            a window holding only the first conversation's activity
#   verify.sh list-filters  OAP            one conversation by id, and a title fragment in any case
#   verify.sh views         OAP ASZ        every conversation's asz.view equals the Sessionizer's, through
#                                          swctl as JSON and YAML, and over the route on HTTP/2 and gzipped
#   verify.sh raw-files     OAP            the raw files are the files the document names, and export them
#   verify.sh reject        OAP            a file with a wrong digest is never stored, seen through the export by id
#   verify.sh multi-round   OAP            the session landed in three stages folds to one verified document
#   verify.sh lost-file     OAP            a landed file deleted after a round bound to it: named once, the rest folds
#   verify.sh gap           OAP            a round pushed past a gap: the fold resumes at it and names what is absent once
#   verify.sh size          OAP            a file over maxFileBytes is never stored; one under it is
#   verify.sh changes       OAP            the workspace-changes conversation: the plugin's changes files landed beside
#                                          the transcripts, every change record joins its step, the export names the files
#   verify.sh metrics       OAP            the runtime's token metric the Sessionizer derived, summed over every session
#                                          and sender per minute, and over the run equal to what the scenarios declare
set -euo pipefail

MODE=$1
OAP=${2%/}
ASZ=${3:-}
SERVICE="e2e-ai-agent"
INSTANCE="e2e-sender"
FIRST="00000001-0000-4000-8000-000000000001"
# three-rounds.yaml names no session, so its id is derived from its steps and is the same on every build.
THREE_ROUNDS="bd16edc4-0b6b-4020-8405-3ce58724f2bc"
# lost-file.yaml likewise; its helper's transcript, seq 2, is deleted before the push.
LOST="c9d9b18b-be85-4906-850d-40a1cd240171"
# workspace-changes.yaml likewise: every producer of a change record in one session.
WC="3189c1f0-9ec4-4bd2-88dc-8eda88ac6db3"

# swctl against this OAP, JSON out.
sw() {
  swctl --display json --base-url="$OAP/graphql" "$@"
}

# The view route straight over HTTP: $1 conversation, $2 Accept, then extra curl flags.
route() {
  local c=$1 accept=$2; shift 2
  curl -sf -H "Accept: $accept" "$@" "$OAP/ai-agent/conversations/$c/v1/view?service=$SERVICE"
}

# The document through swctl: $1 conversation, then extra flags such as --yaml.
view() {
  local c=$1; shift
  sw ai-agent view --service-name "$SERVICE" --conversation "$c" "$@"
}

# "yyyy-MM-dd HHmm" (MINUTE) or "yyyy-MM-dd HHmmss" (SECOND) in UTC, from epoch seconds; GNU date, then BSD date.
fmt() {
  local secs=$1 pattern=$2
  date -u -d "@$secs" +"$pattern" 2>/dev/null || date -u -r "$secs" +"$pattern"
}

# One list query: $1 duration start, $2 duration end, then extra swctl flags. swctl reads the step from the
# time format: "yyyy-MM-dd HHmm" is MINUTE, "yyyy-MM-dd HHmmss" is SECOND.
list() {
  local start=$1 end=$2; shift 2
  sw ai-agent list --service-name "$SERVICE" --start "$start" --end "$end" "$@"
}

# One list query straight over GraphQL, for the conditions swctl has no flag for: $1 is the extra members of
# the condition, as JSON. Prints the rows' conversation ids as a JSON list.
list_where() {
  local extra=$1
  curl -sf -X POST -H 'Content-Type: application/json' "$OAP/graphql" --data @- <<EOF | yq -p=json -o=json '.data.listConversations.conversations | map(.conversation)'
{"query":"query(\$c: ConversationListCondition!, \$d: Duration!) { listConversations(condition: \$c, duration: \$d) { conversations { conversation } } }","variables":{"c":{"service":{"serviceName":"$SERVICE"},$extra},"d":{"start":"$wide_start","end":"$wide_end","step":"MINUTE"}}}
EOF
}

# How many files the export by id returns for one Session Data seq of the first conversation: 1 when the OAP
# stored it, 0 when it did not.
stored() {
  sw ai-agent files --service-name "$SERVICE" --conversation "$FIRST" --files "$FIRST/streams/main/transcript-20260101T000000.000000000Z-0000$1.sd" \
    | yq -p=json '.files | length'
}

# Push one file by hand as the Sessionizer would, one log record whose body is the file: $1 the record time in
# milliseconds, $2 the file, then the asz.* attributes as key=value. The body is embedded without yq, because a
# body of megabytes does not fit an environment variable, and the digest is the file's exact bytes.
push_record() {
  local at=$1 file=$2; shift 2
  local attrs="" kv
  for kv in "$@"; do attrs="$attrs"',{"key":"'"${kv%%=*}"'","value":{"stringValue":"'"${kv#*=}"'"}}'; done
  local dir; dir=$(mktemp -d)
  {
    printf '{"resourceLogs":[{"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"%s"}},{"key":"service.instance.id","value":{"stringValue":"%s"}},{"key":"service.layer","value":{"stringValue":"AI_AGENT"}},{"key":"telemetry.sdk.name","value":{"stringValue":"asz"}}]},"scopeLogs":[{"scope":{"name":"e2e"},"logRecords":[{"timeUnixNano":"%s","body":{"stringValue":"' "$SERVICE" "$INSTANCE" "$((at * 1000000))"
    # the body as one JSON string: backslashes and quotes escaped, every newline as the two characters \n
    sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' "$file" | awk '{printf "%s\\n", $0}'
    printf '"},"attributes":[%s]}]}]}]}' "${attrs#,}"
  } > "$dir/request.json"
  curl -sf -X POST -H 'Content-Type: application/json' "$OAP/v1/logs" --data-binary @"$dir/request.json" > /dev/null
  rm -rf "$dir"
}

now=$(date -u +%s)
wide_start=$(fmt $((now - 7200)) "%Y-%m-%d %H%M")
wide_end=$(fmt $((now + 7200)) "%Y-%m-%d %H%M")

case "$MODE" in
  list)
    list "$wide_start" "$wide_end" \
      | yq -P '.conversations | sort_by(.conversation) | map({"conversation": .conversation, "instance": .serviceInstanceName, "title": .title, "round": .round, "talks": .talks, "steps": .steps, "streams": .streams, "segments": .segments, "unresolved": .unresolved, "timed": (.from > 0 and .to >= .from)})'
    ;;
  list-limit)
    list "$wide_start" "$wide_end" --limit 1 \
      | yq -P '{"rows": (.conversations | length), "newest": .conversations[0].conversation}'
    ;;
  list-instance)
    hit=$(list "$wide_start" "$wide_end" --instance-name "$INSTANCE" | yq '.conversations | length')
    miss=$(list "$wide_start" "$wide_end" --instance-name nobody | yq '.conversations | length')
    printf 'hit: %s\nmiss: %s\n' "$hit" "$miss"
    ;;
  list-window)
    # The first conversation's own range, from its list row; the next one begins a second after it ends and
    # ends about ten seconds later, so a window closing one second after the first cannot hold it.
    row=$(list "$wide_start" "$wide_end" | yq -o=json ".conversations[] | select(.conversation == \"$FIRST\")")
    from=$(echo "$row" | yq '.from'); to=$(echo "$row" | yq '.to')
    start=$(fmt $(( from / 1000 - 1 )) "%Y-%m-%d %H%M%S")
    end=$(fmt $(( to / 1000 + 1 )) "%Y-%m-%d %H%M%S")
    list "$start" "$end" | yq -P '{"conversations": (.conversations | map(.conversation) | sort)}'
    ;;
  list-filters)
    # The two conditions the UI's search sends: one conversation by id, a storage lookup, and a title fragment,
    # matched after folding without regard to case.
    by_id=$(list_where '"conversation":"'"$FIRST"'"')
    printf 'by_id_rows: %s\nby_id: %s\nby_title_three: %s\nby_title_build_any_case: %s\nby_title_miss: %s\n' \
      "$(echo "$by_id" | yq 'length')" "$(echo "$by_id" | yq '.[0]')" \
      "$(list_where '"title":"three"' | yq 'length')" "$(list_where '"title":"BUILD"' | yq 'length')" "$(list_where '"title":"nothing here"' | yq 'length')"
    ;;
  views)
    ids=$(list "$wide_start" "$wide_end" | yq '.conversations[].conversation' | sort)
    total=0; equal=0; yaml_equal=0; h2_equal=0; gzip_equal=0; format=""; version=""
    for id in $ids; do
      total=$((total + 1))
      theirs=$(curl -sf "$ASZ/api/c/$id/view" | yq -o=json 'sort_keys(..)')
      # through swctl as JSON, the UI's path
      resp=$(view "$id")
      format=$(echo "$resp" | yq -p=json '.format'); version=$(echo "$resp" | yq -p=json '.version')
      ours=$(echo "$resp" | yq -p=json -o=json 'sort_keys(..)')
      if [ "$ours" = "$theirs" ]; then equal=$((equal + 1)); else echo "conversation $id differs" >&2; diff <(echo "$theirs") <(echo "$ours") >&2 || true; fi
      # through swctl as YAML, the human path
      [ "$(view "$id" --yaml | yq -o=json 'sort_keys(..)')" = "$theirs" ] && yaml_equal=$((yaml_equal + 1))
      # the route itself over cleartext HTTP/2, and with the body gzipped
      [ "$(route "$id" application/json --http2-prior-knowledge | yq -o=json 'sort_keys(..)')" = "$theirs" ] && h2_equal=$((h2_equal + 1))
      [ "$(route "$id" application/json --compressed | yq -o=json 'sort_keys(..)')" = "$theirs" ] && gzip_equal=$((gzip_equal + 1))
    done
    encoding=$(curl -s -o /dev/null -D - -H 'Accept-Encoding: gzip' "$OAP/ai-agent/conversations/$FIRST/v1/view?service=$SERVICE" | tr -d '\r' | awk -F': ' 'tolower($1) == "content-encoding" {print $2}')
    [ -n "$encoding" ] || encoding=none
    # the format and the version are on the wire too: the media type names the document, its version is a parameter
    json_type=$(curl -s -o /dev/null -w '%{content_type}' "$OAP/ai-agent/conversations/$FIRST/v1/view?service=$SERVICE")
    yaml_type=$(curl -s -o /dev/null -w '%{content_type}' -H 'Accept: application/vnd.skywalking.asz.view+yaml' "$OAP/ai-agent/conversations/$FIRST/v1/view?service=$SERVICE")
    # an error is a problem document (RFC 9457) that carries its status
    missing_type=$(curl -s -o /dev/null -w '%{content_type}' "$OAP/ai-agent/conversations/no-such-conversation/v1/view?service=$SERVICE")
    missing=$(curl -s "$OAP/ai-agent/conversations/no-such-conversation/v1/view?service=$SERVICE" | yq -p=json -o=json -I=0 '{"status": .status, "title": .title, "detail": .detail}')
    noservice=$(curl -s -o /dev/null -w '%{http_code}' "$OAP/ai-agent/conversations/$FIRST/v1/view")
    # swctl says the problem in words
    cli_missing=$( (view no-such-conversation 2>&1 || true) | grep -c "404 Not Found: no round of conversation no-such-conversation")
    printf 'conversations: %s\nequal: %s\nyaml_equal: %s\nh2_equal: %s\ngzip_equal: %s\nformat: %s\nversion: "%s"\njson_type: "%s"\nyaml_type: "%s"\nencoding: %s\nmissing_type: "%s"\nmissing: %s\nnoservice: %s\n' "$total" "$equal" "$yaml_equal" "$h2_equal" "$gzip_equal" "$format" "$version" "$json_type" "$yaml_type" "$encoding" "$missing_type" "$missing" "$noservice"
    printf 'cli_missing: %s\n' "$cli_missing"
    ;;
  raw-files)
    raw=$(sw ai-agent files --service-name "$SERVICE" --conversation "$FIRST" \
      | yq -p=json -o=json '.files | map({"file": .id, "digest": .digest}) | sort_by(.file)')
    named=$(view "$FIRST" | yq -p=json -o=json '.files | map({"file": .file, "digest": .digest}) | sort_by(.file)')
    match=false; [ "$raw" = "$named" ] && match=true
    # the export writes every body to its id path, and each lands with the digest the document names
    root=$(mktemp -d); sw ai-agent files --service-name "$SERVICE" --conversation "$FIRST" --export "$root" > /dev/null
    exported=$(cd "$root" && find . -type f | sed 's#^\./##' | while read -r f; do printf '{"file":"%s","digest":"%s"}\n' "$f" "$(sha256sum "$f" | cut -d' ' -f1)"; done | paste -sd, -)
    exported=$(echo "[$exported]" | yq -p=json -o=json 'sort_by(.file)')
    export_match=false; [ "$exported" = "$named" ] && export_match=true
    rm -rf "$root"
    printf 'files: %s\nmatch: %s\nexport_match: %s\n' "$(echo "$raw" | yq 'length')" "$match" "$export_match"
    ;;
  reject)
    # Two files beyond the head, pushed by hand: a good one, and one whose declared digest is not its body's. The
    # probe is the export of each by id, so it reads exactly the pushed seq; the good file proves the probe sees
    # what the OAP stored, and the bad one must not be there. Both are stamped inside the conversation's range.
    to=$(list "$wide_start" "$wide_end" | yq -o=json ".conversations[] | select(.conversation == \"$FIRST\") | .to")
    push_file() {
      local seq=$1 body=$2 digest=$3 lines=$4
      local id="$FIRST/streams/main/transcript-20260101T000000.000000000Z-0000$seq.sd"
      B="$body" yq -n -o=json '{"resourceLogs":[{"resource":{"attributes":[
        {"key":"service.name","value":{"stringValue":"'"$SERVICE"'"}},
        {"key":"service.instance.id","value":{"stringValue":"'"$INSTANCE"'"}},
        {"key":"service.layer","value":{"stringValue":"AI_AGENT"}},
        {"key":"telemetry.sdk.name","value":{"stringValue":"asz"}}]},
       "scopeLogs":[{"scope":{"name":"e2e"},"logRecords":[{"timeUnixNano":"'"$((to * 1000000))"'",
        "body":{"stringValue": strenv(B)},
        "attributes":[
         {"key":"asz.format","value":{"stringValue":"sd"}},
         {"key":"asz.file","value":{"stringValue":"'"$id"'"}},
         {"key":"asz.file.digest","value":{"stringValue":"'"$digest"'"}},
         {"key":"asz.lines","value":{"stringValue":"'"$lines"'"}},
         {"key":"asz.session","value":{"stringValue":"'"$FIRST"'"}},
         {"key":"asz.seq","value":{"stringValue":"'"$seq"'"}}]}]}]}]}' \
        | curl -sf -X POST -H 'Content-Type: application/json' "$OAP/v1/logs" --data @- > /dev/null
    }
    # $(...) would strip the file's final newline, and the OAP counts lines by newlines, so the bodies are built
    # with the newline kept and the digest is taken over exactly the bytes pushed
    nl=$'\n'
    good='{"h":1,"schema":"sd/1","seq":98,"kind":"transcript","session":"'"$FIRST"'","stream":"main"}'"$nl"'{"t":"end","records":0,"digest":"0"}'"$nl"
    push_file 98 "$good" "$(printf '%s' "$good" | sha256sum | cut -d' ' -f1)" 2
    bad='{"h":1,"schema":"sd/1","seq":99,"kind":"transcript","session":"'"$FIRST"'","stream":"main"}'"$nl"'{"t":"end","records":0,"digest":"0"}'"$nl"
    push_file 99 "$bad" "0000000000000000000000000000000000000000000000000000000000000000" 2
    sleep 8
    printf 'good_stored: %s\nbad_stored: %s\n' "$(stored 98)" "$(stored 99)"
    ;;
  multi-round)
    # Three rounds over files cut at each stage; the final document covers the whole session, verified.
    view "$THREE_ROUNDS" --yaml \
      | yq -P '{"title": .summary.title, "state": .summary.state, "problems": .summary.problems, "rounds": .summary.rounds, "talks": .summary.talks, "steps": .summary.steps, "streams": [.streams[] | {"name": .name, "role": .role, "label": .label, "parent": .parent}], "files": (.files | length), "windows": [.rounds[] | {"round": .round, "from_seq": .from_seq, "through_seq": .through_seq, "verified": .verified}]}'
    ;;
  lost-file)
    # The helper's transcript was deleted after a round bound to it: both rounds fold, the first is listed
    # unverified, the one file is named once, and the document equals the Sessionizer's, which the views case checks.
    view "$LOST" --yaml \
      | yq -P '{"title": .summary.title, "state": .summary.state, "problems": .summary.problems, "rounds": .summary.rounds, "verified": [.rounds[].verified], "talks": .summary.talks, "steps": .summary.steps, "files": (.files | length)}'
    ;;
  gap)
    # A round pushed by hand two past the head of the three-round conversation, naming two files that never
    # landed, with a previous digest nothing can check: the document still folds the three rounds it has, resumes
    # at the new one unverified, and names the absent rounds and the absent files once each. Pushed again on a
    # retry, the same round is kept once. This case runs after every read of the conversation as landed.
    doc=$(view "$THREE_ROUNDS" --yaml)
    session=$(echo "$doc" | yq '.sessions[0]'); parser=$(echo "$doc" | yq '.parser'); policy=$(echo "$doc" | yq '.policy')
    to=$(list "$wide_start" "$wide_end" | yq -o=json ".conversations[] | select(.conversation == \"$THREE_ROUNDS\") | .to")
    zero="0000000000000000000000000000000000000000000000000000000000000000"
    header='{"t":"header","schema":"sf/1","conversation":"'"$THREE_ROUNDS"'","session":"'"$session"'","round":6,"previous":"'"$zero"'","from_seq":10,"through_seq":11,"input_digest":"'"$zero"'","parser":"'"$parser"'","policy":"'"$policy"'"}'
    commit='{"t":"commit","digest":"'"$(printf '%s\n' "$header" | sha256sum | cut -d' ' -f1)"'"}'
    dir=$(mktemp -d)
    printf '%s\n%s\n' "$header" "$commit" > "$dir/round"
    push_record "$to" "$dir/round" asz.format=sf "asz.file=_conversations/$THREE_ROUNDS/rounds/r000006-000000000000.sf" \
      "asz.file.digest=$(sha256sum "$dir/round" | cut -d' ' -f1)" asz.lines=2 "asz.session=$session" "asz.conversation=$THREE_ROUNDS" asz.round=6
    rm -rf "$dir"
    sleep 8
    view "$THREE_ROUNDS" --yaml \
      | yq -P '{"state": .summary.state, "problems": .summary.problems, "head": .head.round, "rounds": .summary.rounds, "verified": [.rounds[].verified], "talks": .summary.talks, "steps": .summary.steps}'
    ;;
  size)
    # Two intact files beyond the head, one under this stack's maxFileBytes of 2 MiB and one over it, seen through
    # the export by id: the one over the limit is rejected at ingest and never stored, and the one under it proves
    # the probe and the boundary.
    to=$(list "$wide_start" "$wide_end" | yq -o=json ".conversations[] | select(.conversation == \"$FIRST\") | .to")
    padded() {
      # $1 the seq, $2 the bytes of padding: a header and an end frame that carries the padding, two lines
      printf '{"h":1,"schema":"sd/1","seq":%s,"kind":"transcript","session":"%s","stream":"main"}\n{"t":"end","records":0,"digest":"0","pad":"' "$1" "$FIRST"
      head -c "$2" /dev/zero | tr '\0' x
      printf '"}\n'
    }
    dir=$(mktemp -d)
    padded 96 1500000 > "$dir/under"
    padded 97 2500000 > "$dir/over"
    for f in under:96 over:97; do
      push_record "$to" "$dir/${f%%:*}" asz.format=sd "asz.file=$FIRST/streams/main/transcript-20260101T000000.000000000Z-0000${f##*:}.sd" \
        "asz.file.digest=$(sha256sum "$dir/${f%%:*}" | cut -d' ' -f1)" asz.lines=2 "asz.session=$FIRST" "asz.seq=${f##*:}"
    done
    rm -rf "$dir"
    sleep 8
    printf 'under_stored: %s\nover_stored: %s\n' "$(stored 96)" "$(stored 97)"
    ;;
  changes)
    # Every producer of a change record in one session: the plugin's records in a changes file beside each stream's
    # transcript, one for a shell command on the main stream and one inside a subagent, and the runtime's own patch
    # on the Edit's result record. The document lists them in time order, joined to their steps by tool-use id, each
    # step names its records, and the export lists the changes files like any landed file. The document equals the
    # Sessionizer's, which the views case checks; this case says in words what it holds.
    view "$WC" --yaml | yq -P '{
      "state": .summary.state, "changes": .summary.changes, "files": (.files | length),
      "changes_files": [.files[] | select(.kind == "changes") | {"seq": .seq, "stream": .stream, "lines": .lines, "timed": (.from_time != null)}],
      "entries": [.workspace_changes[] | {"step": .step, "ref": .ref, "captured_by": .captured_by, "basis": .basis, "tool_name": .tool_name, "changed_files": .changed_files,
                  "files": [.changes[] | {"path": .path, "operation": .operation, "additions": .additions, "deletions": .deletions, "hunks": (.hunks | length)}]}],
      "steps": ([.. | select(tag == "!!map" and has("kind") and has("changes")) | {"id": .id, "changes": .changes}] | sort_by(.id))
    }'
    # the file names carry the build's stamp, which differs on every run; the stream and the seq are what matter
    sw ai-agent files --service-name "$SERVICE" --conversation "$WC" \
      | yq -p=json -P '{"exported_changes_files": (.files | map(.id) | map(select(test("/changes-"))) | map(sub("changes-[^/]*-0", "changes-*-0")) | sort)}'
    ;;
  list-horizon)
    # The list exactly as Horizon's conversation page queries it: the same condition, the service and the sender,
    # and every field its columns read, the counts a round's header carries among them. swctl has no flag for the
    # counts, so this goes straight over GraphQL. Every scenario declares its calls, so every value is known.
    curl -sf -X POST -H 'Content-Type: application/json' "$OAP/graphql" --data @- <<GQL | yq -p=json -P '.data.listConversations.conversations | sort_by(.conversation) | map({"conversation": .conversation, "instance": .serviceInstanceName, "instanceIdSet": (.serviceInstanceId != ""), "title": .title, "round": .round, "talks": .talks, "steps": .steps, "streams": .streams, "segments": .segments, "unresolved": .unresolved, "changes": .changes, "linesAdded": .linesAdded, "linesRemoved": .linesRemoved, "llmCalls": .llmCalls, "subagents": .subagents, "bashRuns": .bashRuns, "timed": (.from > 0 and .to >= .from)})'
{"query":"query(\$c: ConversationListCondition!, \$d: Duration!) { listConversations(condition: \$c, duration: \$d) { conversations { conversation serviceInstanceId serviceInstanceName title round talks steps streams segments unresolved changes linesAdded linesRemoved llmCalls subagents bashRuns from to } } }","variables":{"c":{"service":{"serviceName":"$SERVICE"},"instance":{"serviceName":"$SERVICE","instanceName":"$INSTANCE"}},"d":{"start":"$wide_start","end":"$wide_end","step":"MINUTE"}}}
GQL
    ;;
  view-horizon)
    # The document exactly as Horizon's conversation page fetches it: the route with the service and the sender,
    # the asz.view JSON media type, and the browser's gzip; then what the page's header and its changes panel read.
    url="$OAP/ai-agent/conversations/$WC/v1/view?service=$SERVICE&instance=$INSTANCE"
    head=$(curl -s -o /dev/null -D - -H 'Accept: application/vnd.skywalking.asz.view+json' -H 'Accept-Encoding: gzip' "$url" | tr -d '\r')
    status=$(echo "$head" | awk 'NR == 1 {print $2}')
    media=$(echo "$head" | awk -F': ' 'tolower($1) == "content-type" {print $2}')
    encoding=$(echo "$head" | awk -F': ' 'tolower($1) == "content-encoding" {print $2}')
    printf 'status: %s\nmedia_type: "%s"\nencoding: %s\n' "$status" "$media" "$encoding"
    curl -s --compressed -H 'Accept: application/vnd.skywalking.asz.view+json' "$url" | yq -p=json -P '{
      "format": .format, "version": .version,
      "state": .summary.state, "rounds": .summary.rounds, "segments": .summary.segments, "streams": .summary.streams,
      "talks": .summary.talks, "steps": .summary.steps, "unresolved": .summary.unresolved, "changes": .summary.changes,
      "spanned": (.summary.to > .summary.from),
      "records": (.workspace_changes | length),
      "files_changed": ([.workspace_changes[].changes[].path] | unique | length),
      "lines_added": ([.workspace_changes[].changes[].additions] | .[] as $n ireduce (0; . + $n)),
      "lines_removed": ([.workspace_changes[].changes[].deletions] | .[] as $n ireduce (0; . + $n))
    }'
    ;;
  metrics)
    # The runtime's token metric: one delta point per minute per series, sent by the Sessionizer beside the files,
    # kept by the receiver as the point's value at the point's time, and summed by the rules over every session and
    # sender of a minute. Over the run the totals are what the scenarios declare, every call once: the three fixture
    # sessions, the three-round session, the lost-file session and the workspace-changes session, all on one model.
    m() { sw metrics exec --expression="$1" --service-name "$SERVICE" --start "$wide_start" --end "$wide_end" "${@:2}"; }
    total() { m "$@" | yq -p=json -o=json '.results[0].values[0].value | tonumber'; }
    by() { m "$1" | yq -p=json -o=json '[.results[] | {"labels": (.metric.labels | map({"key": .key, "value": .value}) | sort_by(.key)), "value": (.values[0].value | tonumber)}] | sort_by(.labels | map(.value) | join("/"))'; }
    share=$(total 'avg(meter_ai_agent_cache_read_share)')
    printf '{"tokens": %s, "instance_tokens": %s, "by_type": %s, "by_model": %s, "by_source": %s, "cache_read_share_in_range": %s}' \
      "$(total 'sum(meter_ai_agent_tokens)')" "$(total 'sum(meter_ai_agent_instance_tokens)' --instance-name "$INSTANCE")" \
      "$(by 'sum(meter_ai_agent_tokens_by_type)')" "$(by 'sum(meter_ai_agent_tokens_by_model)')" "$(by 'sum(meter_ai_agent_tokens_by_source)')" \
      "$(awk -v s="$share" 'BEGIN { print (s > 0 && s <= 100) ? "true" : "false" }')" \
      | yq -p=json -P
    ;;
  *)
    echo "unknown mode $MODE" >&2; exit 2
    ;;
esac
