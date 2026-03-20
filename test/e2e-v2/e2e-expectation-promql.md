# PromQL Expected File Patterns

SkyWalking implements a Prometheus-compatible PromQL API for metrics querying.

**Port:** `${oap_9090}` — Base URL: `http://${oap_host}:${oap_9090}/api/v1/`

## Service Traffic (Series Query)

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_9090}/api/v1/series \
  -d 'match[]=service_traffic{layer="GENERAL"}&start='$(($(date +%s)-1800))'&end='$(date +%s)
```

**Expected:**
```yaml
status: success
data:
{{- contains .data }}
  - __name__: service_traffic
    layer: GENERAL
    scope: Service
    service: {{ notEmpty .service }}
{{- end}}
```

## Labels Query

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_9090}/api/v1/labels \
  -d 'match[]=service_traffic{layer="GENERAL"}&start='$(($(date +%s)-1800))'&end='$(date +%s)
```

**Expected:**
```yaml
status: success
data:
{{- contains .data }}
  - __name__
  - layer
  - scope
  - service
{{- end}}
```

## Label Values Query

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_9090}/api/v1/label/__name__/values \
  -d 'match[]=service_traffic{layer="GENERAL"}&start='$(($(date +%s)-1800))'&end='$(date +%s)
```

**Expected:**
```yaml
status: success
data:
{{- contains .data }}
  - service_traffic
{{- end}}
```

## Instant Query — Vector Result

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_9090}/api/v1/query \
  -d 'query=service_sla{service="e2e-service-consumer", layer="GENERAL"}'
```

**Expected:**
```yaml
status: success
data:
  resultType: vector
  result:
    {{- contains .data.result }}
    - metric:
        __name__: service_sla
        layer: GENERAL
        scope: Service
        service: e2e-service-consumer
      value:
        - "{{ index .value 0 }}"
        - '10000'
    {{- end}}
```

**Notes:**
- `resultType: vector` for instant queries
- `value` is a `[timestamp, value]` tuple
- Use `{{ index .value 0 }}` for timestamp (dynamic) and literal for expected value

## Range Query — Matrix Result

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_9090}/api/v1/query \
  -d 'query=service_sla{service="e2e-service-consumer", layer="GENERAL"}[30m]'
```

**Expected:**
```yaml
status: success
data:
  resultType: matrix
  result:
    {{- contains .data.result }}
    - metric:
        __name__: service_sla
        layer: GENERAL
        scope: Service
        service: e2e-service-consumer
      values:
        {{- contains .values }}
        - - "{{ index . 0 }}"
          - '10000'
        {{- end}}
    {{- end}}
```

**Notes:**
- `resultType: matrix` for range queries (bracket suffix like `[30m]`)
- `values` is an array of `[timestamp, value]` tuples

## Labeled Metrics (Percentile) — Matrix

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_9090}/api/v1/query \
  -d 'query=endpoint_percentile{service="e2e-service-consumer", layer="GENERAL", p="50,75,90"}[30m]'
```

**Expected:**
```yaml
status: success
data:
  resultType: matrix
  result:
    {{- contains .data.result }}
    - metric:
        __name__: endpoint_percentile
        p: 50
        layer: GENERAL
        scope: Endpoint
        service: e2e-service-consumer
        endpoint: POST:/users
      values:
        {{- contains .values }}
        - - "{{ index . 0 }}"
          - "{{ index . 1 }}"
        {{- end}}
    - metric:
        __name__: endpoint_percentile
        p: 75
        layer: GENERAL
        scope: Endpoint
        service: e2e-service-consumer
        endpoint: POST:/users
      values:
        {{- contains .values }}
        - - "{{ index . 0 }}"
          - "{{ index . 1 }}"
        {{- end}}
    {{- end}}
```

**Notes:**
- Percentile label `p` expands into separate metric series
- Each percentile value produces its own result entry

## Range Query

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_9090}/api/v1/query_range \
  -d 'query=service_sla{service="e2e-service-consumer", layer="GENERAL"}&start='$(($(date +%s)-1800))'&end='$(date +%s)'&step=300'
```

**Expected:** Same structure as matrix result above.

## Metadata Query

**Query:**
```bash
curl -X GET http://${oap_host}:${oap_9090}/api/v1/metadata \
  -d 'metric=service_sla'
```

**Expected:**
```yaml
status: success
data:
  service_sla:
    - type: gauge
      help: ""
      unit: ""
```

## PromQL Query Syntax

Label selectors use `{}` with key-value pairs:

```
service_sla{service="svc", layer="GENERAL"}          # instant query
service_sla{service="svc", layer="GENERAL"}[30m]     # range query (30 min)
```

## Common Metric Labels

| Label | Description | Example |
|-------|-------------|---------|
| `__name__` | Metric name | `service_sla` |
| `layer` | SkyWalking layer | `GENERAL` |
| `scope` | Metric scope | `Service`, `Endpoint`, `ServiceInstance` |
| `service` | Service name | `e2e-service-provider` |
| `endpoint` | Endpoint name | `POST:/users` |
| `p` | Percentile value | `50`, `75`, `90`, `95`, `99` |

## Timestamp Format

PromQL timestamps are in **seconds** (Unix epoch), unlike LogQL which uses nanoseconds:
```bash
start='$(($(date +%s)-1800))'    # 30 minutes ago in seconds
end='$(date +%s)'                 # now in seconds
```
