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

# Usage: check-trace-ref.sh <SKYWALKING_NATIVE|OTLP|ZIPKIN> <service name>
#
# Picks the newest Faithfulness record of the service, queries it again through relatedTrace, and prints
# which trace stores hold the referenced trace: the SkyWalking trace query, the TraceQL OTLP datasource and
# the Zipkin query API. Only the store the type names should hold it. A store that cannot be queried prints
# error:<status> instead of true or false, so the check fails and is retried.

set -u

TYPE=$1
SERVICE_ID="$(printf '%s' "$2" | base64).1"
GRAPHQL="http://${oap_host}:${oap_12800}/graphql"
START=$(date -u -v-1H '+%Y-%m-%d %H%M' 2>/dev/null || date -u -d '1 hour ago' '+%Y-%m-%d %H%M')
END=$(date -u '+%Y-%m-%d %H%M')
RECORD_QUERY='query ($condition: GenAIEvaluationRecordQueryCondition!) { queryGenAIEvaluationRecord(condition: $condition) { genAIEvaluationRecordList { traceRef { type traceId segmentId spanIndex spanId } serviceName } } }'

# $1: extra condition fields, starting with a comma.
records() {
  curl -s --max-time 10 -X POST "$GRAPHQL" -H 'Content-Type: application/json' \
    -d '{"query":"'"$RECORD_QUERY"'","variables":{"condition":{"providerId":"b3BlbmFp.0","serviceId":"'"$SERVICE_ID"'","taskName":"Faithfulness","valueType":"SCORE"'"$1"',"queryDuration":{"start":"'"$START"'","end":"'"$END"'","step":"MINUTE"},"paging":{"pageNum":1,"pageSize":1},"sortBy":"EVALUATION_TIME","queryOrder":"DES"}}}'
}

# $1: JSON document, $2: yq expression on it.
json() {
  printf '%s' "$1" | yq -p json "$2"
}

# $1: URL, $2: the statuses meaning the store does not hold the trace. Prints the body of a 200 response,
# nothing for those statuses, and error:<status> for anything else, including a request curl did not complete.
fetch() {
  local body status rc
  body=$(mktemp)
  status=$(curl -s --max-time 10 -o "$body" -w '%{http_code}' -H 'Accept: application/json' "$1")
  rc=$?
  if [ "$rc" -ne 0 ]; then
    printf 'error:curl-%s' "$rc"
  elif [ "$status" = 200 ]; then
    cat "$body"
  elif ! printf ' %s ' "$2" | grep -q " $status "; then
    printf 'error:%s' "$status"
  fi
  rm -f "$body"
}

# $1: input format, $2: fetched document, $3: yq expression. A missing document means the store does not hold
# the trace.
holds() {
  case "$2" in
    error:*) printf '%s' "$2" ;;
    "") printf 'false' ;;
    *) printf '%s' "$2" | yq -p "$1" "$3" ;;
  esac
}

RECORD=$(json "$(records '')" '.data.queryGenAIEvaluationRecord.genAIEvaluationRecordList[0] // {}' | yq -o json -I 0)
REF_TYPE=$(json "$RECORD" '.traceRef.type // ""')
SERVICE_NAME=$(json "$RECORD" '.serviceName // ""')
TRACE_ID=$(json "$RECORD" '.traceRef.traceId // ""')
SEGMENT_ID=$(json "$RECORD" '.traceRef.segmentId // ""')
SPAN_INDEX=$(json "$RECORD" '.traceRef.spanIndex // ""')
SPAN_ID=$(json "$RECORD" '.traceRef.spanId // ""')

# The span coordinates of the type, and the same reference pointing at a span that does not exist.
case "$TYPE" in
  SKYWALKING_NATIVE)
    RELATED='"traceId":"'"$TRACE_ID"'","segmentId":"'"$SEGMENT_ID"'","spanIndex":'"${SPAN_INDEX:-null}"
    MISMATCHED='"traceId":"'"$TRACE_ID"'","segmentId":"'"$SEGMENT_ID"'","spanIndex":99999'
    SAME_SPAN='.traceRef.traceId == "'"$TRACE_ID"'" and .traceRef.segmentId == "'"$SEGMENT_ID"'" and .traceRef.spanIndex == '"${SPAN_INDEX:--1}"
    ;;
  OTLP|ZIPKIN)
    RELATED='"traceId":"'"$TRACE_ID"'","spanId":"'"$SPAN_ID"'"'
    MISMATCHED='"traceId":"'"$TRACE_ID"'","spanId":"0000000000000000"'
    SAME_SPAN='.traceRef.traceId == "'"$TRACE_ID"'" and .traceRef.spanId == "'"$SPAN_ID"'"'
    ;;
  *)
    echo "unsupported trace ref type: $TYPE" >&2
    exit 1
    ;;
esac
LOOKUP=$(json "$(records ',"relatedTrace":{"type":"'"$TYPE"'",'"$RELATED"'}')" \
  '.data.queryGenAIEvaluationRecord.genAIEvaluationRecordList[0] // {}' | yq -o json -I 0)
# A failed query must not read as zero records.
MISMATCHED_RESPONSE=$(records ',"relatedTrace":{"type":"'"$TYPE"'",'"$MISMATCHED"'}')
MISMATCHED_LIST='.data.queryGenAIEvaluationRecord.genAIEvaluationRecordList'
if [ "$(json "$MISMATCHED_RESPONSE" '.errors == null and ('"$MISMATCHED_LIST"' | tag) == "!!seq"')" = true ]; then
  MISMATCHED_COUNT=$(json "$MISMATCHED_RESPONSE" "$MISMATCHED_LIST | length")
else
  MISMATCHED_COUNT="error:graphql"
fi

SKYWALKING_TRACE="error:no-trace-id"
OTLP_TRACE="error:no-trace-id"
ZIPKIN_TRACE="error:no-trace-id"
if [ -n "$TRACE_ID" ]; then
  if ! SKYWALKING_TRACE=$(swctl --display yaml --base-url="$GRAPHQL" trace "$TRACE_ID"); then
    SKYWALKING_TRACE="error:swctl"
  fi
  OTLP_TRACE=$(fetch "http://${oap_host}:${oap_3200}/otlp/api/v2/traces/${TRACE_ID}" "404")
  # Zipkin answers 400 for a trace ID that is not lower-hex, such as a SkyWalking native one.
  ZIPKIN_TRACE=$(fetch "http://${oap_host}:${oap_9412}/zipkin/api/v2/trace/${TRACE_ID}" "404 400")
fi

case "$TYPE" in
  SKYWALKING_NATIVE)
    SPAN_IN_STORE=$(holds yaml "$SKYWALKING_TRACE" \
      '[.spans[] | select(.segmentid == "'"$SEGMENT_ID"'" and .spanid == '"${SPAN_INDEX:--1}"')] | length > 0')
    ;;
  OTLP)
    SPAN_IN_STORE=$(holds json "$OTLP_TRACE" \
      '[.trace.resourceSpans[].scopeSpans[].spans[] | select(.spanId == "'"$SPAN_ID"'")] | length > 0')
    ;;
  ZIPKIN)
    SPAN_IN_STORE=$(holds json "$ZIPKIN_TRACE" '[.[] | select(.id == "'"$SPAN_ID"'")] | length > 0')
    ;;
esac

cat <<EOF
traceRefType: ${REF_TYPE:-null}
serviceName: ${SERVICE_NAME:-null}
lookupType: $(json "$LOOKUP" '.traceRef.type // "null"')
lookupSameSpan: $(json "$LOOKUP" "$SAME_SPAN")
mismatchedLookupRecords: ${MISMATCHED_COUNT:-null}
spanInItsStore: ${SPAN_IN_STORE}
traceInSkyWalking: $(holds yaml "$SKYWALKING_TRACE" '(.spans // []) | length > 0')
traceInOtlp: $(holds json "$OTLP_TRACE" '[.trace.resourceSpans[].scopeSpans[].spans[]] | length > 0')
traceInZipkin: $(holds json "$ZIPKIN_TRACE" 'length > 0')
EOF
