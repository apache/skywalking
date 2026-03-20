# LogQL Expected File Patterns

SkyWalking implements a Loki-compatible LogQL API for log querying.

**Port:** `${oap_3100}` — Base URL: `http://${oap_host}:${oap_3100}/loki/api/v1/`

## Labels Query

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_3100}/loki/api/v1/labels \
  -d 'start='$(($(($(date +%s)-1800))*1000000000))'&end='$(($(date +%s)*1000000000))
```

**Expected:**
```yaml
status: success
data:
{{- contains .data }}
  - {{ notEmpty . }}
{{- end}}
```

**Notes:**
- Returns list of available label names (e.g., `service`, `service_instance`, `endpoint`, `trace_id`)
- Timestamps are in **nanoseconds** (Unix epoch × 10⁹)

## Label Values Query

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_3100}/loki/api/v1/label/service/values \
  -d 'start='$(($(($(date +%s)-1800))*1000000000))'&end='$(($(date +%s)*1000000000))
```

**Expected:**
```yaml
status: success
data:
{{- contains .data }}
  - e2e-service-provider
{{- end}}
```

## Log Stream Query (query_range)

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_3100}/loki/api/v1/query_range \
  -d 'query={service="e2e-service-provider"}&start='$(($(($(date +%s)-1800))*1000000000))'&end='$(($(date +%s)*1000000000))'&limit=100&direction=BACKWARD'
```

**Expected:**
```yaml
status: success
data:
  resultType: streams
  result:
  {{- contains .data.result }}
  - stream:
      service: e2e-service-provider
      service_instance: {{ notEmpty .stream.service_instance }}
      endpoint: {{ .stream.endpoint }}
      trace_id: {{ .stream.trace_id }}
    values:
    {{- contains .values }}
    - - "{{ notEmpty (index . 0) }}"
      - "{{ notEmpty (println (index . 1)) }}"
    {{- end}}
  {{- end}}
```

**Notes:**
- `resultType` is always `streams` for log queries
- Each stream has a `stream` metadata object and `values` array
- Values are `[timestamp, log_content]` tuples
- Use `(index . 0)` for timestamp and `(index . 1)` for log body
- `println` is used for log body to handle multiline content
- Stream metadata labels: `service`, `service_instance`, `endpoint`, `trace_id`

## Log Query with Instance Filter

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_3100}/loki/api/v1/query_range \
  -d 'query={service_instance="provider1"}&start='$(($(($(date +%s)-1800))*1000000000))'&end='$(($(date +%s)*1000000000))'&limit=100&direction=BACKWARD'
```

**Expected:** Same structure as above, with `service_instance` narrowed to the filter value.

## LogQL Query Syntax

LogQL filter expressions use label matchers inside `{}`:

```
{service="e2e-service-provider"}                           # exact match
{service_instance="provider1"}                             # exact match
{service="e2e-service-provider", endpoint="/users"}        # multiple labels
```

## Query Parameters

| Parameter | Description | Example |
|-----------|-------------|---------|
| `query` | LogQL filter expression | `{service="svc"}` |
| `start` | Start timestamp (nanoseconds) | `1700000000000000000` |
| `end` | End timestamp (nanoseconds) | `1700001800000000000` |
| `limit` | Max number of log entries | `100` |
| `direction` | Sort order: `FORWARD` or `BACKWARD` | `BACKWARD` |

## Timestamp Generation Pattern

The e2e tests use shell arithmetic for dynamic timestamps:
```bash
# Start: 30 minutes ago in nanoseconds
start='$(($(($(date +%s)-1800))*1000000000))'

# End: now in nanoseconds
end='$(($(date +%s)*1000000000))'
```
