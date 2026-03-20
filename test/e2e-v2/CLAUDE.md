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

### Core Policy: Verify Data Accuracy, Not Just Existence

The purpose of e2e expected files is to **verify that data is correct**, not merely that something exists.

**Use exact literal values for all meaningful fields** — service names, endpoint names, metric names, label keys/values, span names, component names, layer names, scope names, tag keys/values, etc. These are all known from the test setup and must be verified precisely.

**Only use `notEmpty`/`gt`/`ge` for genuinely dynamic values** — timestamps, UUIDs, generated IDs, durations, IP addresses — values that change every test run and cannot be predicted.

### When Uncertain: Ask the Developer

When writing expected files, you often cannot determine from the raw query output alone which fields
are meaningful domain values (must be exact) vs dynamic runtime values (use `notEmpty`/`gt`).
**Do not guess — ask the developer to decide.**

**Workflow:**

1. **Run the query** and show the complete raw output to the developer
2. **Propose an expected file** with your best guess at exact vs dynamic fields
3. **For each field, explain your reasoning** — why you chose exact literal vs `notEmpty`/`gt`
4. **Ask the developer to confirm or correct** which fields should be exact

**Example interaction:**

```
The query `swctl service ls` returns:
---
- id: ZTJlLXNlcnZpY2UtcHJvdmlkZXI=.1
  name: e2e-service-provider
  group: ""
  shortname: e2e-service-provider
  normal: true
  layers:
    - GENERAL

Proposed expected file:
---
{{- contains . }}
- id: {{ b64enc "e2e-service-provider" }}.1    # exact: derived from known service name
  name: e2e-service-provider                    # exact: known from docker-compose OTEL_SERVICE_NAME / SW_AGENT_NAME
  group: ""                                     # exact: default group
  shortname: e2e-service-provider               # exact: derived from name
  normal: true                                  # exact: agent-reported service is always normal
  layers:
    - GENERAL                                   # exact: standard agent layer
{{- end }}

All fields are deterministic here. Please confirm, or let me know
if any field should use notEmpty instead.
```

**Why this matters:** Only the developer knows the test's intent — e.g., whether a tag value
is a fixed protocol value that must be verified, or an environment-dependent value that just
needs to be non-empty. Making the wrong choice either misses real bugs (too loose) or creates
flaky tests (too strict on dynamic data).

**Decision guide for common field types:**

| Field type | Approach | Example |
|------------|----------|---------|
| Service/endpoint/span name | Exact literal | `name: e2e-service-provider` |
| Layer, scope, kind, type | Exact literal | `layer: GENERAL`, `kind: SERVER` |
| Tag/label keys | Exact literal | `key: http.method` |
| Tag/label values (known) | Exact literal | `value: POST` |
| Metric name | Exact literal | `__name__: service_sla` |
| Component name | Exact literal | `component: Tomcat` |
| Boolean fields | Exact literal | `iserror: false`, `normal: true` |
| Entity IDs | `b64enc` with exact name | `id: {{ b64enc "e2e-service-provider" }}.1` |
| Timestamps | `gt .field 0` | `starttime: {{ gt .starttime 0 }}` |
| Durations | `ge .field 0` | `duration: {{ ge .duration 0 }}` |
| UUIDs / trace IDs | `notEmpty` | `uuid: {{ notEmpty .uuid }}` |
| IP addresses | `notEmpty` | `ipv4: {{ notEmpty .ipv4 }}` |
| Instance UUIDs | `notEmpty` | `instanceuuid: {{ notEmpty .instanceuuid }}` |
| Metric values (known) | Exact literal | `value: "10000"` |
| Metric values (variable) | `notEmpty` or `gt` | `value: {{ gt .value 0 }}` |
| **Uncertain fields** | **Ask the developer** | Show raw output + your proposal |

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

#### Service list — verify exact names and properties

```yaml
{{- contains . }}
- id: {{ b64enc "e2e-service-provider" }}.1
  name: e2e-service-provider
  group: ""
  shortname: e2e-service-provider
  normal: true
  layers:
    - GENERAL
- id: {{ b64enc "e2e-service-consumer" }}.1
  name: e2e-service-consumer
  group: ""
  shortname: e2e-service-consumer
  normal: true
  layers:
    - GENERAL
{{- end }}
```

#### Topology — verify exact node types, components, and relationships

```yaml
nodes:
{{- contains .nodes }}
- id: {{ b64enc "e2e-service-provider" }}.1
  name: e2e-service-provider
  type: Tomcat
  isreal: true
  layers:
    - GENERAL
- id: {{ b64enc "localhost:-1" }}.0
  name: localhost:-1
  type: H2
  isreal: false
  layers:
    - VIRTUAL_DATABASE
{{- end }}
calls:
{{- contains .calls }}
- source: {{ b64enc "e2e-service-provider" }}.1
  sourcecomponents:
    - h2-jdbc-driver
  target: {{ b64enc "localhost:-1" }}.0
  targetcomponents: []
  id: {{ b64enc "e2e-service-provider" }}.1-{{ b64enc "localhost:-1" }}.0
  detectpoints:
    - CLIENT
{{- end }}
```

#### Traces — verify exact span names, services, components; dynamic for timestamps/IDs

```yaml
- traceid: {{ .traceid }}
  segmentid: {{ .segmentid }}
  spanid: 0
  parentspanid: -1
  refs: []
  servicecode: e2e-service-consumer
  serviceinstancename: consumer1
  starttime: {{ gt .starttime 0 }}
  endtime: {{ gt .endtime 0 }}
  endpointname: POST:/users
  type: Entry
  peer: ""
  component: Tomcat
  iserror: false
  layer: Http
  tags:
    {{- contains .tags }}
    - key: http.method
      value: POST
    - key: http.status_code
      value: "200"
    - key: url
      value: {{ notEmpty .value }}
    {{- end }}
```

#### Metrics — verify exact label keys/values; dynamic for timestamps and variable values

```yaml
type: TIME_SERIES_VALUES
results:
  {{- contains .results }}
  - metric:
      labels:
        {{- contains .metric.labels }}
        - key: p
          value: "50"
        {{- end }}
    values:
      {{- contains .values }}
      - id: {{ notEmpty .id }}
        value: {{ notEmpty .value }}
        owner: null
        traceid: null
      {{- end }}
  {{- end }}
error: null
```

#### Alarms — verify exact scope, name, tag values, expression

```yaml
msgs:
  {{- contains .msgs }}
  - starttime: {{ gt .starttime 0 }}
    scope: Service
    id: {{ b64enc "e2e-service-provider" }}.1
    name: e2e-service-provider
    tags:
      - key: level
        value: WARNING
    events:
      {{- contains .events }}
      - uuid: {{ notEmpty .uuid }}
        source:
          service: e2e-service-provider
          serviceinstance: ""
          endpoint: ""
        name: Alarm
        message: {{ notEmpty .message }}
        starttime: {{ gt .starttime 0 }}
        endtime: {{ gt .endtime 0 }}
        layer: GENERAL
      {{- end }}
  {{- end }}
```

#### List must have at least N items matching a condition

List N patterns in `contains` — each must match a different actual item:

```yaml
values:
  {{- contains .values }}
  - id: {{ notEmpty .id }}
    value: {{ notEmpty .value }}
  - id: {{ notEmpty .id }}
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

#### Variable assignment and reuse

```yaml
- id: {{ $svcID := (index .nodes 0).id }}{{ notEmpty $svcID }}
  name: service-name
- target: {{ $svcID }}
```

#### Prometheus-style timestamp+value tuples

```yaml
# timestamp is dynamic, metric value is exact
value:
  - "{{ index .value 0 }}"
  - '10000'
```

#### Handle metric time series with data gaps

```yaml
values:
  {{- contains .values }}
  - id: {{ notEmpty .id }}
    value: {{ notEmpty .value }}     # at least one non-null value
  - id: {{ notEmpty .id }}
    value: null                       # data gaps are OK
  {{- end }}
```

#### Verify a field is explicitly null

```yaml
debuggingtrace: null
error: null
owner: null
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
