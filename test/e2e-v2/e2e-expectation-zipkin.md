# Zipkin v2 Query Expected File Patterns

SkyWalking implements the native Zipkin v2 query API, compatible with the Zipkin ecosystem (Zipkin UI, Zipkin clients).

**Port:** `${oap_9412}` — Base URL: `http://${oap_host}:${oap_9412}/zipkin`
**Module:** `zipkin-query-plugin` (`query-zipkin`)
**Handler:** `ZipkinQueryHandler` — reference: `zipkin2.server.internal.ZipkinQueryApiV2`

## Endpoints Reference

| Path | Method | Parameters | Response |
|------|--------|------------|----------|
| `/api/v2/services` | GET | — | `[String]` — service names |
| `/api/v2/remoteServices` | GET | `serviceName` (required) | `[String]` — remote service names |
| `/api/v2/spans` | GET | `serviceName` (required) | `[String]` — span names |
| `/api/v2/trace/{traceId}` | GET | `traceId` (path) | `[Span]` — Zipkin v2 spans |
| `/api/v2/traces` | GET | `serviceName`, `remoteServiceName`, `spanName`, `annotationQuery`, `minDuration`(ms), `maxDuration`(ms), `endTs`(ms), `lookback`(ms), `limit`(default 10) | `[[Span]]` — list of traces |
| `/api/v2/traceMany` | GET | `traceIds` (comma-separated) | `[[Span]]` — batch trace query |
| `/api/v2/autocompleteKeys` | GET | — | `[String]` — available tag keys |
| `/api/v2/autocompleteValues` | GET | `key` (required) | `[String]` — values for tag key |
| `/config.json` | GET | — | UI configuration object |

## Zipkin v2 Span Format

Each span in the response follows the Zipkin v2 JSON format:
```
{
  traceId: String               # 128-bit trace identifier
  id: String                    # span identifier
  parentId: String              # parent span (absent for root)
  kind: SERVER|CLIENT|PRODUCER|CONSUMER
  name: String                  # operation name
  timestamp: Long               # start time in MICROSECONDS
  duration: Long                # duration in MICROSECONDS
  localEndpoint: {serviceName, ipv4, port}
  remoteEndpoint: {serviceName, ipv4, port}
  annotations: [{timestamp, value}]    # timestamped events
  tags: {key: value, ...}              # string key-value map
}
```

## Service List

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

## Remote Services

**Query:**
```bash
curl "http://${oap_host}:${oap_9412}/zipkin/api/v2/remoteServices?serviceName=frontend"
```

**Expected:**
```yaml
{{- contains . }}
- backend
{{- end }}
```

## Span Names

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

## Trace Search

**Query:**
```bash
curl "http://${oap_host}:${oap_9412}/zipkin/api/v2/traces?serviceName=frontend&remoteServiceName=backend&spanName=get&annotationQuery=wr&limit=1"
```

**Expected:**
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

## Trace Search with Duration Filter

**Query:**
```bash
curl "http://${oap_host}:${oap_9412}/zipkin/api/v2/traces?minDuration=1000&limit=1"
```

**Expected:** Same Zipkin span structure as above.

## Autocomplete Keys

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

## Autocomplete Values

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

## Key Differences from TraceQL (port 3200)

| Aspect | Zipkin v2 (this doc, port 9412) | TraceQL (port 3200) |
|--------|--------------------------------|---------------------|
| API style | Zipkin v2 native REST | Grafana Tempo-compatible |
| Response format | Flat span array, Zipkin JSON | OTLP-like (`resourceSpans` / `scopeSpans`) |
| Timestamp unit | Microseconds | Nanoseconds |
| Search | URL query parameters | TraceQL expressions `{attr="val"}` |
| Span kind values | `SERVER`, `CLIENT` | `SPAN_KIND_SERVER`, `SPAN_KIND_CLIENT` |
| Tags format | `tags: {key: value}` (flat map) | `attributes: [{key, value: {stringValue}}]` (array) |
| Attached events | Annotations + extra `attachedEvents` field | Not applicable |

## SpanAttachedEvent Support

The Zipkin query handler can append `SpanAttachedEvent` data (from SkyWalking Rover/eBPF) as extra annotations and tags on Zipkin spans. When querying `/api/v2/trace/{traceId}`, if attached events exist:
- Events are appended as annotations with their event names
- Event summaries and tags are encoded in span tags

## Configuration

| Property | Env Var | Default | Description |
|----------|---------|---------|-------------|
| `restHost` | `SW_QUERY_ZIPKIN_REST_HOST` | `0.0.0.0` | HTTP server host |
| `restPort` | `SW_QUERY_ZIPKIN_REST_PORT` | `9412` | HTTP server port |
| `restContextPath` | `SW_QUERY_ZIPKIN_REST_CONTEXT_PATH` | `/zipkin` | URL context path |
| `lookback` | — | — | Default lookback duration (ms) |
| `uiQueryLimit` | — | — | Max query limit for UI |
