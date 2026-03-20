# E2E Test Guide

## Overview

SkyWalking uses [skywalking-infra-e2e](https://github.com/apache/skywalking-infra-e2e) for end-to-end testing.
Tests follow **Setup → Trigger → Verify → Cleanup**. Expected files use Go templates rendered
with actual query results as context, then structurally compared via `go-cmp`.

## Finding Files

| What | Where |
|------|-------|
| Test entry point | `cases/<feature>/e2e.yaml` or `cases/<feature>/<storage>/e2e.yaml` |
| Query + expected pairs | `cases/<feature>/*-cases.yaml` |
| Expected result templates | `cases/<feature>/expected/*.yml` |
| Docker infrastructure | `cases/<feature>/docker-compose.yml` |
| Shared env variables | `script/env` |
| GraphQL schema definitions | `oap-server/server-query-plugin/query-graphql-plugin/src/main/resources/query-protocol/*.graphqls` |

## Query Protocols & Ports

| Protocol | Port | Query Tool | Schema/Spec |
|----------|------|------------|-------------|
| GraphQL / MQE | `${oap_12800}` | `swctl --display yaml` | `query-protocol/*.graphqls` |
| Status / Debug | `${oap_12800}` | `curl` | `status-query-plugin` handlers |
| PromQL | `${oap_9090}` | `curl` | Prometheus API v1 |
| LogQL | `${oap_3100}` | `curl` | Loki API v1 |
| TraceQL | `${oap_3200}` | `curl` | Tempo API |
| Zipkin v2 | `${oap_9412}` | `curl` | Zipkin v2 API |

---

## How to Write Expectation Files

### How Verification Works

1. Execute query (swctl/curl) → get **actual** YAML/JSON
2. Render expected file as Go template with actual data as `{{ . }}` context
3. Unmarshal both rendered expected and actual as YAML
4. Compare with `go-cmp` deep structural equality — arrays are **ordered**, maps are **key-matched**

The key insight: template functions return the **actual value on success** (so rendered output equals actual → match).
On failure they return an **error string** like `<"" is empty, wanted is not empty>` which won't match actual → diff shown.

### Available Functions

#### `notEmpty` — Assert value is present and non-blank

Accepts only `nil` or `string`. Trims whitespace to check, returns original untrimmed string on success.

```yaml
name: {{ notEmpty .name }}          # fails if name is nil, "", or whitespace-only
id: {{ notEmpty .id }}
```

| Input | Result |
|-------|--------|
| `"hello"` | `"hello"` (pass) |
| `""` | `<"" is empty, wanted is not empty>` (fail) |
| `"   "` | `<"   " is empty, wanted is not empty>` (fail) |
| `nil` | `<nil is empty, wanted is not empty>` (fail) |
| `123` (non-string) | `<notEmpty only supports nil or string type, but was int>` (fail) |

#### `gt`, `ge`, `lt`, `le` — Numeric comparisons

Returns the left-hand value on success. Supports int, uint, float. Cross-type comparison (int vs float) works via float64 coercion. **String vs number is an error.**

```yaml
starttime: {{ gt .starttime 0 }}     # must be > 0
duration: {{ ge .duration 0 }}       # must be >= 0
value: {{ le .value 10000 }}         # must be <= 10000
count: {{ lt .count 100 }}           # must be < 100
```

| Expression | Result |
|------------|--------|
| `gt 10 5` | `10` (pass) |
| `gt 3 5` | `<wanted gt 5, but was 3>` (fail) |
| `ge 5 5` | `5` (pass, equal satisfies >=) |
| `gt "10" 5` | error: incompatible types |

#### `regexp` — Regex pattern matching

```yaml
name: {{ regexp .name "^service-.*" }}
id: {{ regexp .id "[a-f0-9]{32}" }}
```

Returns original string on match. Pattern compiled each call (standard Go regex syntax).

#### `b64enc` — Base64 encode literal strings

Used for SkyWalking entity IDs:

```yaml
# Service ID = base64(name) + ".1" (normal) or ".0" (virtual)
id: {{ b64enc "e2e-service-provider" }}.1

# Instance ID = serviceID + "_" + base64(instanceName)
id: {{ b64enc "e2e-service-provider" }}.1_{{ b64enc "provider1" }}

# Endpoint ID = serviceID + "_" + base64(endpointName)
id: {{ b64enc "e2e-service-provider" }}.1_{{ b64enc "POST:/users" }}

# Dependency ID = sourceServiceID + "-" + targetServiceID
id: {{ b64enc "e2e-service-consumer" }}.1-{{ b64enc "e2e-service-provider" }}.1
```

#### `subtractor` — Arithmetic subtraction

```yaml
value: {{ subtractor 100 10 20 }}    # 100 - 10 - 20 = 70
```

#### `sha256enc`, `sha512enc` — Hash functions

```yaml
hash: {{ sha256enc "data" }}
```

---

### `contains` — Unordered Subset Matching (Critical)

Regular YAML array comparison is **ordered and exact**. `contains` enables **unordered subset matching** — all expected items must exist somewhere in the actual array, but order doesn't matter and extra actual items are ignored.

```yaml
# WITHOUT contains: arrays compared element-by-element, ordered, exact
# WITH contains: unordered, all expected items must be found in actual

{{- contains .services }}
- name: service-a
- name: service-b
{{- end }}
```

**Algorithm:** For each actual array element, render the block body with that element as context,
then check if any expected pattern matches. ALL expected patterns must find a match.

**Nesting:** `contains` inside `contains` works for nested arrays:

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

**Duplicates:** Each expected item must match a **different** actual item (cardinality preserved).

---

### Recipes: Common Verification Patterns

#### Verify a key exists with any non-empty value

```yaml
name: {{ notEmpty .name }}
```

#### Verify a numeric field is positive

```yaml
starttime: {{ gt .starttime 0 }}
```

#### Verify a field equals an exact value

Just write the literal — no template function needed:

```yaml
scope: Service
layer: GENERAL
iserror: false
```

#### Verify a field can be null or have a value

Use the raw accessor (no assertion function):

```yaml
value: {{ .value }}          # outputs whatever value is, including null
parentId: {{ .parentId }}    # outputs the value or empty
```

#### Verify a list has at least N items matching a condition

List N items in the `contains` block — each must match a different actual item:

```yaml
# At least 2 items with value > 0
values:
  {{- contains .values }}
  - value: {{ gt .value 0 }}
  - value: {{ gt .value 0 }}
  {{- end }}
```

#### Verify a list contains specific named items (unordered)

```yaml
{{- contains . }}
- name: e2e-service-provider
- name: e2e-service-consumer
{{- end }}
```

#### Verify an array contains items with mixed exact and dynamic fields

```yaml
tags:
  {{- contains .tags }}
  - key: http.method
    value: POST
  - key: url
    value: {{ notEmpty .value }}
  {{- end }}
```

#### Verify nested object fields

Use `with` to scope into a nested object:

```yaml
{{- with .retrievedtimerange }}
starttime: {{ gt .starttime 0 }}
endtime: {{ gt .endtime 0 }}
{{- end }}
```

#### Verify a field matches a regex pattern

```yaml
message: {{ regexp .message ".*alarm.*" }}
```

#### Use variable assignment and reuse

```yaml
- id: {{ $svcID := (index .nodes 0).id }}{{ notEmpty $svcID }}
  name: service-name
- target: {{ $svcID }}
```

#### Verify array index access

```yaml
# Prometheus-style [timestamp, value] tuples
value:
  - "{{ index .value 0 }}"      # timestamp (dynamic)
  - '10000'                      # value (exact)
```

#### Conditional rendering

```yaml
{{if . }}
- id: {{ notEmpty .id }}
{{else}}
[]
{{end}}
```

#### Verify a field is explicitly null

```yaml
debuggingtrace: null
error: null
owner: null
```

#### Handle value that may be null or non-empty

Mix null and notEmpty in a `contains` to assert "at least some values exist":

```yaml
values:
  {{- contains .values }}
  - id: {{ notEmpty .id }}
    value: {{ notEmpty .value }}     # at least one non-null value
  - id: {{ notEmpty .id }}
    value: null                       # data gaps are OK
  {{- end }}
```

---

### Pitfalls

1. **`notEmpty` only accepts string or nil** — passing an integer (e.g., `{{ notEmpty .port }}`) will fail with type error. Use `{{ ge .port 0 }}` for numeric fields.

2. **`gt`/`ge`/`lt`/`le` cannot compare string to number** — `{{ gt .value 0 }}` fails if `.value` is a string like `"100"`. Make sure the actual data type matches.

3. **Without `contains`, arrays are compared ordered and exact** — if the actual list could have items in any order, you MUST use `contains`.

4. **Whitespace control matters** — use `{{-` and `-}}` to trim whitespace around template directives, otherwise extra blank lines break YAML structure.

5. **`contains` requires ALL expected items to match** — it's "actual contains all of expected", not "any of expected".

6. **`println` for multiline values** — log content or multiline strings need `println` to preserve newlines:
   ```yaml
   - - "{{ notEmpty (index . 0) }}"
     - "{{ notEmpty (println (index . 1)) }}"
   ```

7. **License header** — all `.yml` expected files must include the Apache 2.0 license header. `.md` files are excluded.
