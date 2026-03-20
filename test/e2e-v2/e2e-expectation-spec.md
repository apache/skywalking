# E2E Expectation File Specification

## How Verification Works

The skywalking-infra-e2e framework uses a template-based verification approach:

1. **Query execution** — Run a shell command (e.g., `swctl`, `curl`) that produces YAML/JSON output
2. **Template rendering** — Render the expected file as a Go template, with the actual query result as the template context (`.`)
3. **Structural comparison** — Parse both rendered template and actual data as YAML, then compare using `go-cmp`
4. **Retry loop** — If comparison fails, retry up to `verify.retry.count` times with `verify.retry.interval` between attempts

The key insight: **template functions validate values during rendering**. When a function like `notEmpty` succeeds, it returns the actual value (so rendered output matches actual). When it fails, it returns an error string like `<"" is empty, wanted is not empty>` which causes a mismatch.

## Template Syntax

Expected files use [Go template syntax](https://pkg.go.dev/text/template). The actual data is available as `.` (dot).

### Whitespace Control

Use `{{-` and `-}}` to trim surrounding whitespace (critical for YAML formatting):

```yaml
{{- contains .items }}      # trim leading whitespace
- name: {{ .name }}
{{- end }}                   # trim leading whitespace
```

### Variable Assignment

```yaml
{{ $id := .id }}{{ notEmpty $id }}
{{ $svcID := (index .nodes 0).id }}{{ notEmpty $svcID }}
```

### Conditional Blocks

```yaml
{{if . }}
- id: {{ notEmpty .id }}
{{else}}
[]
{{end}}
```

### Context Switch (`with`)

```yaml
{{- with .retrievedtimerange }}
starttime: {{ gt .starttime 0 }}
endtime: {{ gt .endtime 0 }}
{{- end }}
```

### Indexing

```yaml
{{ index .value 0 }}          # first element of array
{{ (index .nodes 2).id }}     # id field of third node
```

## Template Functions

### Assertion Functions

These functions validate values. On success they return the actual value; on failure they return an error string that causes a mismatch.

| Function | Signature | Success | Failure |
|----------|-----------|---------|---------|
| `notEmpty` | `notEmpty <value>` | Returns the value | `<"" is empty, wanted is not empty>` |
| `gt` | `gt <value> <threshold>` | Returns value | `<wanted gt N, but was M>` |
| `ge` | `ge <value> <threshold>` | Returns value | `<wanted ge N, but was M>` |
| `lt` | `lt <value> <threshold>` | Returns value | `<wanted lt N, but was M>` |
| `le` | `le <value> <threshold>` | Returns value | `<wanted le N, but was M>` |
| `regexp` | `regexp <value> <pattern>` | Returns value | `<"X" does not match the pattern "Y">` |

### Encoding Functions

| Function | Description | Example |
|----------|-------------|---------|
| `b64enc` | Base64 encode a string | `{{ b64enc "service-name" }}` → `c2VydmljZS1uYW1l` |
| `sha256enc` | SHA256 hex hash | `{{ sha256enc "data" }}` |
| `sha512enc` | SHA512 hex hash | `{{ sha512enc "data" }}` |

### Utility Functions

| Function | Description | Example |
|----------|-------------|---------|
| `subtractor` | Subtract values from first arg | `{{ subtractor 100 10 20 }}` → `70` |
| `println` | Print with newline (useful for multiline values) | `{{ println .body }}` |

### Standard Go Template Functions

Available from Go's `text/template`: `and`, `or`, `not`, `eq`, `ne`, `len`, `index`, `slice`, `print`, `printf`, `hasPrefix`, `hasSuffix`.

## The `contains` Action

`contains` is a **custom template action** (not a function) for unordered list matching. It verifies that the actual array contains **all** items matching the specified patterns, regardless of order.

### Syntax

```yaml
{{- contains .arrayField }}
- field1: {{ notEmpty .field1 }}
  field2: {{ gt .field2 0 }}
- field1: expected-value
  field2: 42
{{- end }}
```

### Behavior

- Every item in the `contains` block must match at least one item in the actual array
- Items can appear in any order in the actual data
- The actual array may contain additional items not listed in the pattern
- Supports nesting: `contains` inside `contains` for nested arrays
- Each pattern item is rendered with each actual item as context until a match is found

### Common Pattern — Nested Contains

```yaml
results:
  {{- contains .results }}
  - metric:
      labels:
        {{- contains .metric.labels }}
        - key: {{ .key }}
          value: {{ notEmpty .value }}
        {{- end }}
    values:
      {{- contains .values }}
      - id: {{ notEmpty .id }}
        value: {{ notEmpty .value }}
      {{- end }}
  {{- end }}
```

## SkyWalking ID Encoding Convention

SkyWalking encodes entity IDs using base64. Common patterns:

```yaml
# Service ID: base64(serviceName).isNormal
id: {{ b64enc "e2e-service-provider" }}.1     # normal=true → .1
id: {{ b64enc "localhost:-1" }}.0              # normal=false → .0

# Instance ID: serviceID_base64(instanceName)
id: {{ b64enc "e2e-service-provider" }}.1_{{ b64enc "provider1" }}

# Endpoint ID: serviceID_base64(endpointName)
id: {{ b64enc "e2e-service-provider" }}.1_{{ b64enc "POST:/users" }}

# Dependency ID: sourceServiceID-targetServiceID
id: {{ b64enc "e2e-service-consumer" }}.1-{{ b64enc "e2e-service-provider" }}.1
```

## e2e.yaml Configuration

```yaml
setup:
  env: compose                                    # or "kind" for Kubernetes
  file: docker-compose.yml
  timeout: 20m
  init-system-environment: ../../script/env

trigger:
  action: http
  interval: 3s
  times: -1                                       # infinite until verify completes
  url: http://${provider_host}:${provider_9090}/users
  method: POST
  body: '{"name": "test"}'

verify:
  retry:
    count: 20
    interval: 3s
  fail-fast: true                                 # stop on first failure
  cases:
    - includes:
        - simple-cases.yaml                       # relative path to cases file

cleanup:
  on: always                                      # always | success | failure | never
```

## Cases YAML

```yaml
cases:
  - query: swctl --display yaml --base-url=http://${oap_host}:${oap_12800}/graphql service ls
    expected: expected/service.yml

  - query: |
      curl -s http://${oap_host}:${oap_9090}/api/v1/query \
        -d 'query=service_sla{service="e2e-service-provider"}'
    expected: expected/metrics.yml
```

## License Header

All `.yml` expected files must include the Apache 2.0 license header (see project `HEADER` file).

## Debugging Failed Verifications

When a verification fails, the framework outputs:
```
✘ failed to verify case[expected/service.yml], retried 20 time(s)
  the actual data is:
  [actual YAML]

  mismatch (-want +got):
  [go-cmp diff showing expected vs actual]
```

The `-want +got` diff shows exactly what the template rendered vs what was received. Look for error strings like `<"" is empty, wanted is not empty>` in the diff to identify which assertions failed.
