# DSL Debug API — OAL

> Status: **shipped**. Operator reference for the OAL slice of the DSL Debug
> API. Design: [SWIP-13](../../../swip/SWIP-13.md). Index of related pages:
> [DSL Debug API overview](dsl-debugging.md).

## What it captures

OAL's gate is **per-metric**: each generated metric (e.g.
`service_relation_server_cpm`) has its own gate holder, and a debug
session attaches to one metric. Sibling rules under the same source
dispatcher stay silent.

A session against `(catalog=oal, name=<file>, ruleName=<metric>)`
captures every source event walked through that one metric's pipeline.
Every event produces one **record**; within a record, each probe stage
appends one **sample**:

```text
nodes[]
  records[]
    startedAtMs                  — record boundary timestamp (ms)
    dsl                          — verbatim per-metric `.oal` line
    rule                         — { ruleName, sourceLine }
    samples[]
      type                       — input | filter | aggregation | output
      sourceText                 — verbatim ANTLR slice from the `.oal` file
      continueOn                 — true = pipeline continued past this step
      payload                    — { type, scope, fields | timeBucket | … }
      sourceLine                 — 1-based line in the source `.oal` file
```

Sample types and the probes that emit them:

| `type`        | Probe                | Fired when                                                                                         |
|---------------|----------------------|----------------------------------------------------------------------------------------------------|
| `input`       | `captureSource`      | A source event arrives at the metric's pipeline. Payload = `ISource.toJson()` with all source columns. |
| `filter`      | `captureFilter`      | An OAL `.filter(...)` clause runs. **Both** kept (`continueOn=true`) **and** rejected (`continueOn=false`) branches are captured. |
| `aggregation` | `captureAggregation` | The metric's aggregation function runs (`cpm()`, `percentile2(10)`, ...). Carries the post-aggregation source view. |
| `output`      | `captureEmit`        | The metric is emitted to the persistence pipeline (terminal). Payload = `Metrics.toJson()` with `count` / `total` / `value` / `timeBucket` etc. |

### sourceText is the verbatim ANTLR slice

Pulled at `.oal` parse time via
`ctx.getStart().getInputStream().getText(Interval.of(...))`. Whitespace
and identifier spelling are byte-identical to the source file:

- `input.sourceText` — the source clause (e.g. `from(ServiceRelation.*)`).
- `filter.sourceText` — the filter clause **including** the leading `.`
  (the dot is part of the slice, e.g. `.filter(detectPoint == DetectPoint.SERVER)`).
- `aggregation.sourceText` — the aggregate function call
  (e.g. `cpm()` / `percentile2(10)`).
- `output.sourceText` — the metric name.

Operators can grep the captured `sourceText` against the original `.oal`
file directly.

### Both kept and rejected filter branches

Unlike MAL (where rejected executions are dropped to avoid tag-cardinality
noise), OAL captures both filter branches. OAL filters are deterministic
discriminators — CLIENT-vs-SERVER, layer matchers, status / latency
predicates — and seeing the rejected source samples (`continueOn=false`)
is the filter doing its job in plain view, useful for verifying partition
logic.

When no session is bound, the codegen-emitted probe call sites are single
volatile-bool reads — idle cost is effectively free.

## Enabling

Two selectors must be enabled — the shared admin HTTP host (`admin-server`)
and the DSL-debug feature on top of it:

```bash
SW_ADMIN_SERVER=default
SW_DSL_DEBUGGING=default
```

`injectionEnabled` is a **boot-time codegen switch**, default `true` once the
`dsl-debugging` module is enabled — the OAL dispatcher template emits
per-metric `GateHolder` fields and probe call sites, so debug sessions
capture samples. Set `false` only if the REST surface is wanted but no
codegen-side probe overhead is acceptable; with `false` the OAL bytecode is
byte-identical to a build without SWIP-13. Flipping the flag requires an
OAP restart:

```bash
SW_DSL_DEBUGGING_INJECTION_ENABLED=false   # default is true; set false to disable probes
```

> SECURITY: capture payloads include source-event contents (service names,
> endpoint names, span attributes). Treat the admin port as authenticated
> infrastructure — see
> [Admin API readme — Security Notice](readme.md#-security-notice).

## Picking the rule key

A session targets one OAL **metric**. The key tuple is
`(catalog=oal, name=<file>, ruleName=<metric>)`:

| Field      | Source                                                                |
|------------|-----------------------------------------------------------------------|
| `catalog`  | `oal`                                                                 |
| `name`     | The `.oal` file the metric is declared in (e.g. `core.oal`).          |
| `ruleName` | The metric name on the LHS of the `=` (e.g. `service_relation_server_cpm`). |

To list the metrics loaded on a node, query
`GET /runtime/oal/files` — each file's response lists its metrics. The
same endpoint also exposes the rules registered against each source —
useful when picking a metric whose filter clauses you want to inspect.

## End-to-end example

The shipped `core.oal` declares `service_relation_server_cpm` against
`ServiceRelation` with a SERVER detect-point filter:

```
service_relation_server_cpm = from(ServiceRelation.*)
    .filter(detectPoint == DetectPoint.SERVER).cpm();
```

### 1. Open a debug session

```bash
curl -s -X POST \
     'http://OAP:17128/dsl-debugging/session?catalog=oal&name=core.oal&ruleName=service_relation_server_cpm&clientId=alice'
```

### 2. Drive ingest

Send any agent traffic that produces inter-service spans (HTTP between
two SkyWalking-instrumented services, gRPC, etc.). The dispatcher fires
the metric's pipeline on every `ServiceRelation` source event.

### 3. Poll

```bash
curl -s 'http://OAP:17128/dsl-debugging/session/SESSION_ID'
```

A trimmed slice (one record = one source event):

```json
{
  "ruleKey": { "catalog": "oal", "name": "core.oal",
               "ruleName": "service_relation_server_cpm" },
  "nodes": [{
    "nodeId": "0.0.0.0_11800",
    "status": "ok",
    "records": [{
      "startedAtMs": 1778115085149,
      "dsl": "service_relation_server_cpm = from(ServiceRelation.*).filter(detectPoint == DetectPoint.SERVER).cpm();",
      "rule": { "ruleName": "service_relation_server_cpm", "sourceLine": "30" },
      "samples": [
        { "type": "input",
          "sourceText": "from(ServiceRelation.*)",
          "continueOn": true,
          "payload": {
            "type": "ServiceRelation", "scope": 4,
            "fields": {
              "sourceServiceName": "e2e-service-consumer",
              "destServiceName": "e2e-service-provider",
              "detectPoint": "SERVER",
              "endpoint": "POST:/users",
              "componentId": 1, "latency": 962, "status": true,
              "httpResponseStatusCode": 200,
              "timeBucket": 202605070051
            }
          },
          "sourceLine": 30 },
        { "type": "filter",
          "sourceText": ".filter(detectPoint == DetectPoint.SERVER)",
          "continueOn": true,
          "payload": { "type": "ServiceRelation", "fields": { /* same row */ } },
          "sourceLine": 30 },
        { "type": "aggregation",
          "sourceText": "cpm()",
          "continueOn": true,
          "payload": { "type": "ServiceRelationServerCpmMetrics",
                       "timeBucket": 202605070051,
                       "count": 1, "total": 1, "value": 1 },
          "sourceLine": 30 }
      ]
    }]
  }]
}
```

A single source event commonly produces a rejected `filter` sample on
sibling rules (e.g. the CLIENT-detect-point sibling). The rejected
sample shows `continueOn=false` and no `aggregation`/`output` follows —
the metric's pipeline stopped at the filter.

### 4. Stop

```bash
curl -s -X POST 'http://OAP:17128/dsl-debugging/session/SESSION_ID/stop'
```

## Cluster behaviour

- **Install** broadcasts to every reachable peer; each peer attaches its own
  recorder to its dispatcher.
- **Collect** broadcasts and concatenates per-node slices.
- **Stop** broadcasts; missed acks fall out via retention timeout.

No cross-node merge — each peer's slice is self-contained.

## Failure modes

| Response                     | Meaning                                                                   |
|------------------------------|---------------------------------------------------------------------------|
| `400 invalid_catalog`        | Catalog must be `oal`.                                                     |
| `400 missing_param`          | `name` or `ruleName` is missing.                                           |
| `404 rule_not_found`         | No metric for `(name, ruleName)` on this node — typo, or no `.oal` rule loaded. |
| `503 injection_disabled`     | `injectionEnabled=false`. Restart with the flag on to debug.               |

## Limits

| Field             | Default       | Purpose                                                |
|-------------------|---------------|--------------------------------------------------------|
| `recordCap`       | `1000`        | Max records before the recorder refuses appends.       |
| `retentionMillis` | `300000` (5m) | Wall-clock retention.                                  |

Override per-session in the install body:

```json
{ "recordCap": 200, "retentionMillis": 600000 }
```

## See also

- [DSL Debug API — MAL](dsl-debugging-mal.md)
- [DSL Debug API — LAL](dsl-debugging-lal.md)
- [SWIP-13](../../../swip/SWIP-13.md) — full design.
