# TraceQL & Zipkin Expected File Patterns

SkyWalking implements both a TraceQL search API and a Zipkin v2-compatible trace API.

## TraceQL API

**Port:** `${oap_3200}` — Base URL: `http://${oap_host}:${oap_3200}/zipkin/api/`

### Build Info

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_3200}/zipkin/api/status/buildinfo
```

**Expected:**
```yaml
status: experimental
version: {{ notEmpty .version }}
```

### Search Tags (v1)

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_3200}/zipkin/api/search/tags \
  -d 'start='$(($(date +%s)-1800))'&end='$(date +%s)
```

**Expected:**
```yaml
{{- contains . }}
- resource.service.name
- span.http.method
- span.http.path
- span.net.peer.name
{{- end}}
```

### Search Tags (v2 — Scoped)

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_3200}/zipkin/api/v2/search/tags \
  -d 'start='$(($(date +%s)-1800))'&end='$(date +%s)
```

**Expected:**
```yaml
scopes:
  {{- contains .scopes }}
  - name: resource
    tags:
      {{- contains .tags }}
      - service
      {{- end }}
  - name: span
    tags:
      {{- contains .tags }}
      - {{ notEmpty . }}
      {{- end }}
  {{- end }}
```

### Tag Values

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_3200}/zipkin/api/v2/search/tag/resource.service.name/values \
  -d 'start='$(($(date +%s)-1800))'&end='$(date +%s)
```

**Expected:**
```yaml
tagValues:
  {{- contains .tagValues }}
  - type: string
    value: frontend
  - type: string
    value: backend
  {{- end }}
```

### Search Traces by Service Name

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_3200}/zipkin/api/search \
  -d 'q={resource.service.name="frontend"}&start='$(($(date +%s)-1800))'&end='$(date +%s)'&limit=10'
```

**Expected:**
```yaml
traces:
  {{- contains .traces }}
  - traceID: {{ notEmpty .traceID }}
    rootServiceName: {{ notEmpty .rootServiceName }}
    rootTraceName: {{ notEmpty .rootTraceName }}
    startTimeUnixNano: "{{ notEmpty .startTimeUnixNano }}"
    durationMs: {{ gt .durationMs -1 }}
    spanSets:
      {{- contains .spanSets }}
      - matched: {{ gt .matched 0 }}
        spans:
          {{- contains .spans }}
          - spanID: {{ notEmpty .spanID }}
            startTimeUnixNano: "{{ .startTimeUnixNano }}"
            durationNanos: "{{ .durationNanos }}"
            attributes:
              {{- contains .attributes }}
              - key: {{ notEmpty .key }}
                value:
                  stringValue: {{ notEmpty .value.stringValue }}
              {{- end }}
          {{- end }}
      {{- end }}
  {{- end }}
```

### Search Traces by Duration

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_3200}/zipkin/api/search \
  -d 'q={duration>1ms}&start='$(($(date +%s)-1800))'&end='$(date +%s)'&limit=1'
```

**Expected:** Same structure as service name search above.

### Complex TraceQL Query

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_3200}/zipkin/api/search \
  -d 'q={resource.service.name="frontend" && span.http.method="GET"}&start='$(($(date +%s)-1800))'&end='$(date +%s)'&limit=10'
```

**Expected:** Same structure as service name search above.

### Trace by ID (JSON — OTLP Format)

**Query:**
```bash
TRACE_ID=$(curl -X GET http://${oap_host}:${oap_3200}/zipkin/api/search \
  -d 'q={resource.service.name="frontend"}&start=...&end=...&limit=1' | jq -r '.traces[0].traceID // empty')
curl -X GET http://${oap_host}:${oap_3200}/zipkin/api/v2/traces/${TRACE_ID} \
  -H "Accept: application/json"
```

**Expected (OTLP format):**
```yaml
trace:
  resourceSpans:
    {{- contains .trace.resourceSpans }}
  - resource:
      attributes:
          - key: service.name
            value:
              stringValue: backend
      scopeSpans:
        {{- contains .scopeSpans }}
        - scope:
            name: zipkin-tracer
            version: "0.1.0"
          spans:
            {{- contains .spans }}
            - traceId: {{ notEmpty .traceId }}
              spanId: {{ notEmpty .spanId }}
              parentSpanId: {{ notEmpty .parentSpanId }}
              name: get /api
              kind: SPAN_KIND_SERVER
              startTimeUnixNano: "{{ notEmpty .startTimeUnixNano }}"
              endTimeUnixNano: "{{ notEmpty .endTimeUnixNano }}"
              attributes:
                {{- contains .attributes }}
                - key: http.method
                  value:
                    stringValue: GET
                - key: http.path
                  value:
                    stringValue: /api
                - key: net.host.ip
                  value:
                    stringValue: {{ notEmpty .value.stringValue }}
                {{- end }}
              events:
                - timeUnixNano: "{{ notEmpty .timeUnixNano }}"
                  name: wr
                  attributes: []
                - timeUnixNano: "{{ notEmpty .timeUnixNano }}"
                  name: ws
                  attributes: []
              status:
                code: STATUS_CODE_UNSET
            {{- end }}
        {{- end }}
  {{- end }}
```

### TraceQL Query Syntax

```
{resource.service.name="frontend"}                              # service filter
{duration>1ms}                                                   # duration filter
{resource.service.name="frontend" && span.http.method="GET"}    # compound filter
```

### TraceQL Search Parameters

| Parameter | Description | Example |
|-----------|-------------|---------|
| `q` | TraceQL query expression | `{resource.service.name="frontend"}` |
| `start` | Start timestamp (seconds) | `1700000000` |
| `end` | End timestamp (seconds) | `1700001800` |
| `limit` | Max number of traces | `10` |

---

## Zipkin v2 API

**Port:** `${oap_9412}` — Base URL: `http://${oap_host}:${oap_9412}/zipkin/api/v2/`

### Service List

**Query:**
```bash
curl http://${oap_host}:${oap_9412}/zipkin/api/v2/services
```

**Expected:**
```yaml
[
  "backend",
  "frontend"
]
```

### Span Names

**Query:**
```bash
curl "http://${oap_host}:${oap_9412}/zipkin/api/v2/spans?serviceName=frontend"
```

**Expected:**
```yaml
{{- contains . }}
- get
- post /
{{- end }}
```

### Autocomplete Keys

**Query:**
```bash
curl http://${oap_host}:${oap_9412}/zipkin/api/v2/autocompleteKeys
```

**Expected:**
```yaml
{{- contains . }}
- http.method
- http.path
{{- end }}
```

### Autocomplete Values

**Query:**
```bash
curl "http://${oap_host}:${oap_9412}/zipkin/api/v2/autocompleteValues?key=http.method"
```

**Expected:**
```yaml
{{- contains . }}
- GET
- POST
{{- end }}
```

### Trace Search

**Query:**
```bash
curl "http://${oap_host}:${oap_9412}/zipkin/api/v2/traces?serviceName=frontend&remoteServiceName=backend&spanName=get&annotationQuery=wr&limit=1"
```

**Expected (Zipkin span format):**
```yaml
{{- contains . }}
- traceId: {{ notEmpty .traceId }}
  id: {{ notEmpty .id }}
  kind: SERVER
  name: get /api
  timestamp: {{ ge .timestamp 0 }}
  duration: {{ ge .duration 0 }}
  localEndpoint:
    serviceName: backend
    ipv4: {{ notEmpty .localEndpoint.ipv4 }}
  remoteEndpoint:
    ipv4: {{ notEmpty .remoteEndpoint.ipv4 }}
    port: {{ ge .remoteEndpoint.port 0 }}
  annotations:
    {{- contains .annotations }}
    - timestamp: {{ ge .timestamp 0 }}
      value: wr
    - timestamp: {{ ge .timestamp 0 }}
      value: ws
    {{- end }}
  tags:
    http.method: GET
    http.path: /api
{{- end }}
```

### Zipkin Span Structure

| Field | Description |
|-------|-------------|
| `traceId` | 128-bit trace identifier |
| `id` | Span identifier |
| `parentId` | Parent span identifier (absent for root spans) |
| `kind` | `SERVER`, `CLIENT`, `PRODUCER`, `CONSUMER` |
| `name` | Operation name |
| `timestamp` | Start time in **microseconds** |
| `duration` | Duration in **microseconds** |
| `localEndpoint` | `{serviceName, ipv4, port}` |
| `remoteEndpoint` | `{serviceName, ipv4, port}` |
| `annotations` | Array of `{timestamp, value}` events |
| `tags` | Key-value map of span tags |

### Zipkin Search Parameters

| Parameter | Description |
|-----------|-------------|
| `serviceName` | Filter by service |
| `remoteServiceName` | Filter by downstream service |
| `spanName` | Filter by span operation |
| `annotationQuery` | Filter by annotation value |
| `minDuration` | Minimum span duration |
| `maxDuration` | Maximum span duration |
| `limit` | Max traces returned |
| `endTs` | End timestamp (milliseconds) |
| `lookback` | Lookback period (milliseconds) |

## Key Differences Between TraceQL and Zipkin

| Aspect | TraceQL (port 3200) | Zipkin v2 (port 9412) |
|--------|--------------------|-----------------------|
| Response format | OTLP-like (`resourceSpans`, `scopeSpans`) | Zipkin native (flat span array) |
| Timestamp unit | Nanoseconds | Microseconds |
| Search syntax | TraceQL expressions: `{attr="val"}` | URL query parameters |
| Trace detail | `resourceSpans` with `scopeSpans` nesting | Flat list of spans |
| Span kind | `SPAN_KIND_SERVER`, `SPAN_KIND_CLIENT` | `SERVER`, `CLIENT` |
| Tags/attributes | `attributes: [{key, value: {stringValue}}]` | `tags: {key: value}` |
