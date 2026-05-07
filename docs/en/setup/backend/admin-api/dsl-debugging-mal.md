# DSL Debug API — MAL

> Status: **shipped**. Operator reference for the MAL slice of the DSL Debug
> API. Design: [SWIP-13](../../../swip/SWIP-13.md). Index of related pages:
> [DSL Debug API overview](dsl-debugging.md).

## What it captures

A MAL session attaches to one metric rule. Every scrape window that survives
the rule's file-level filter produces one **record** in the response;
within a record, each probe stage the expression executes appends one
**sample**. The wire shape is:

```text
nodes[]
  records[]
    startedAtMs                  — record boundary timestamp (ms)
    dsl                          — verbatim per-rule DSL text
    rule                         — rule envelope:
      metricPrefix
      name                       — per-rule name (no prefix)
      filter                     — file-level filter closure body, if any
      exp                        — `exp:` body verbatim
      expSuffix                  — file-level expSuffix verbatim, if any
    samples[]
      type                       — input | filter | function | output
      sourceText                 — verbatim DSL fragment for this probe
      continueOn                 — true (MAL captures kept-only; see overview)
      payload                    — SampleFamily.toJson() at this probe stage
      sourceLine                 — omitted for MAL (no per-line mapping)
```

Sample types and the probes that emit them:

| `type`     | Probe                | Fired when                                                                 |
|------------|----------------------|----------------------------------------------------------------------------|
| `filter`   | `captureFilter`      | The file-level `filter:` closure runs over the input samples (kept-only).   |
| `input`    | `captureInput`       | The metric reference at the head of the expression resolves a SampleFamily. |
| `function` | `captureStage`       | An in-expression chain op runs (`sum`, `tagEqual`, `service`, etc.).        |
| `function` | `captureDownsample`  | A downsampling op runs (e.g. `rate("PT1M")`).                                |
| `output`   | `captureMeterEmit`   | The metric is emitted to the persistence pipeline (terminal).               |

`sample.sourceText` is the **verbatim ANTLR slice** of the chain segment
from the original `exp:` body — operators can grep the captured text
against the source byte-for-byte. There is no leading `.` (the dot is
part of the chain context, not the `MethodCallContext` slice).

`sample.payload` is the structured `SampleFamily.toJson()` at that
probe stage — every sample's name, label set, value, and timestamp is
present; truncated at `maxSamplesPerCapture` (default 64) with a
`+N more` summary.

When no session is bound, the codegen-emitted probe call sites are single
volatile-bool reads that JIT eliminates after warm-up — idle cost is
effectively free.

## Enabling

Two selectors must be enabled — the shared admin HTTP host (`admin-server`)
and the DSL-debug feature on top of it:

```bash
SW_ADMIN_SERVER=default
SW_DSL_DEBUGGING=default
```

`injectionEnabled` is a **boot-time codegen switch**, default `true` once the
`dsl-debugging` module is enabled — the MAL generator emits per-rule
`GateHolder` fields and probe call sites, so debug sessions actually capture
samples. Set `false` only if the REST surface is wanted but no codegen-side
probe overhead is acceptable; with `false` the MAL bytecode is byte-identical
to a build without SWIP-13, and `POST /dsl-debugging/session` returns
`503 injection_disabled`. Flipping the flag requires an OAP restart:

```bash
SW_DSL_DEBUGGING_INJECTION_ENABLED=false   # default is true; set false to disable probes
```

> SECURITY: capture payloads include MAL builder state and sample-family
> contents. Treat the admin port as authenticated infrastructure — see
> [Admin API readme — Security Notice](readme.md#-security-notice).

## Picking the rule key

A session targets one MAL metric rule. The key tuple is
`(catalog, name, ruleName)`:

| Field      | Source                                                                                       |
|------------|----------------------------------------------------------------------------------------------|
| `catalog`  | One of `otel-rules`, `log-mal-rules`, `telegraf-rules` — the directory the rule file lives in |
| `name`     | The rule **file** name, without `.yaml`                                                       |
| `ruleName` | The full metric name (`metricPrefix` + `_` + per-rule `name`)                                 |

Example — the shipped `otel-rules/vm.yaml` declares a metric prefix
`vm` and per-rule name `cpu_total_percentage`. The full metric name is
`vm_cpu_total_percentage`. The session install call:

```
POST /dsl-debugging/session?catalog=otel-rules&name=vm&ruleName=vm_cpu_total_percentage
```

To list the metrics a runtime-rule MAL file exposes, query
`GET /runtime/rule/list` and pull the `ruleName`s associated with the
catalog/name pair (the runtime-rule receiver records every rule's metric
catalog).

## End-to-end example

The example uses a runtime-rule-applied MAL rule with a top-level filter
clause so all probe stages (`filter` → `input` → `function` → `output`)
appear in the captures.

### 1. Apply the rule

```yaml
# /tmp/mal-with-filter.yaml
filter: "{ tags -> tags.service_name == 'my-svc' }"
metricPrefix: e2e_demo
expSuffix: service(['service_name'], Layer.GENERAL)
metricsRules:
  - name: filtered_requests
    exp: e2e_demo_request_count_total.sum(['service_name'])
```

```bash
curl -s -X POST -H 'Content-Type: text/plain' \
     --data-binary '@/tmp/mal-with-filter.yaml' \
     'http://OAP:17128/runtime/rule/addOrUpdate?catalog=otel-rules&name=mal-with-filter'
```

### 2. Open a debug session

```bash
curl -s -X POST \
     'http://OAP:17128/dsl-debugging/session?catalog=otel-rules&name=mal-with-filter&ruleName=e2e_demo_filtered_requests&clientId=alice'
```

### 3. Drive ingest, then poll

```bash
curl -s 'http://OAP:17128/dsl-debugging/session/SESSION_ID'
```

A trimmed slice (one record = one scrape window):

```json
{
  "sessionId": "76b3266a-...",
  "capturedAt": 1777967923700,
  "ruleKey": { "catalog": "otel-rules", "name": "mal-with-filter",
               "ruleName": "e2e_demo_filtered_requests" },
  "nodes": [{
    "nodeId": "0.0.0.0_11800",
    "status": "ok",
    "records": [{
      "startedAtMs": 1777967921000,
      "dsl": "(e2e_demo_request_count_total.sum(['service_name'])).service(['service_name'], Layer.GENERAL)",
      "rule": {
        "metricPrefix": "e2e_demo",
        "name": "filtered_requests",
        "filter": "{ tags -> tags.service_name == 'my-svc' }",
        "exp": "e2e_demo_request_count_total.sum(['service_name'])",
        "expSuffix": "service(['service_name'], Layer.GENERAL)"
      },
      "samples": [
        { "type": "filter",
          "sourceText": "{ tags -> tags.service_name == 'my-svc' }",
          "continueOn": true,
          "payload": {
            "families": 1,
            "items": [ /* one entry per surviving SampleFamily — name, samples count, items[] */ ]
          } },
        { "type": "input",
          "sourceText": "e2e_demo_request_count_total",
          "continueOn": true,
          "payload": { /* head SampleFamily — name, samples, items[] */ } },
        { "type": "function",
          "sourceText": "sum(['service_name'])",
          "continueOn": true,
          "payload": { /* SampleFamily after sum */ } },
        { "type": "output",
          "sourceText": "e2e_demo_filtered_requests",
          "continueOn": true,
          "payload": { /* terminal meter sample — metric, entity, value, timeBucket */ } }
      ]
    }]
  }]
}
```

`sample.sourceText` is the verbatim ANTLR slice — match it against the
`exp:` body byte-for-byte. The record-level `rule` envelope echoes the
structured rule config so operators don't have to re-resolve the file.

### 4. Stop

```bash
curl -s -X POST 'http://OAP:17128/dsl-debugging/session/SESSION_ID/stop'
```

## Cluster behaviour

- **Install** broadcasts to every reachable peer; each peer binds its own
  recorder on its own holder so the slice reflects local L1 parsing.
- **Collect** broadcasts and concatenates per-node slices into `nodes[]`;
  unreachable peers appear as `status: "unreachable"` rather than being
  omitted.
- **Stop** broadcasts; missed acks fall out via per-node retention timeout
  (default 5 minutes).

No cross-node merge — each slice is self-contained.

## Failure modes

| Response                  | Meaning                                                                            |
|---------------------------|------------------------------------------------------------------------------------|
| `400 invalid_catalog`     | The wire `catalog` is not one of the MAL catalogs.                                  |
| `400 missing_param`       | `name` or `ruleName` is missing.                                                    |
| `404 rule_not_found`      | No live MAL artifact for the tuple on this node — rule never loaded, was inactivated, or this node hasn't compiled it yet. |
| `503 injection_disabled`  | `injectionEnabled=false`. Restart with the flag on to debug.                        |
| `500 registry_misconfigured` | A recorder factory wiring bug — file an issue.                                  |

## Limits

| Field                  | Default     | Purpose                                                                       |
|------------------------|-------------|-------------------------------------------------------------------------------|
| `recordCap`            | `1000`      | Max records before the recorder marks itself `captured` and refuses appends.   |
| `retentionMillis`      | `300000` (5m) | Wall-clock retention; the session is reaped after the deadline whether or not it was explicitly stopped. |

Override per-session in the `POST /dsl-debugging/session` body:

```json
{ "recordCap": 200, "retentionMillis": 600000 }
```

## See also

- [DSL Debug API — OAL](dsl-debugging-oal.md)
- [DSL Debug API — LAL](dsl-debugging-lal.md)
- [Runtime Rule Hot-Update API](runtime-rule.md) — apply / inactivate / delete MAL rules.
- [SWIP-13](../../../swip/SWIP-13.md) — full design.
