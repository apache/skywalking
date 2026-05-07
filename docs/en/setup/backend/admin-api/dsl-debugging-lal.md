# DSL Debug API — LAL

> Status: **shipped**. Operator reference for the LAL slice of the DSL Debug
> API. Design: [SWIP-13](../../../swip/SWIP-13.md). Index of related pages:
> [DSL Debug API overview](dsl-debugging.md).

## What it captures

A LAL session attaches to one compiled rule (one entry in a LAL YAML's
`rules:` list). Every log routed through that rule produces one **record**
in the response; within a record, each probe stage in the rule's pipeline
appends one **sample**. The wire shape is:

```text
nodes[]
  records[]
    startedAtMs                  — record boundary timestamp (ms)
    dsl                          — verbatim per-rule DSL text
    rule                         — { ruleName, sourceLine }
    samples[]
      type                       — input | function | output
      sourceText                 — verbatim DSL slice (statement-mode only;
                                   empty in block mode)
      continueOn                 — true = pipeline continued past this step;
                                   false on `abort()` paths
      payload                    — see below
      sourceLine                 — 1-based line in the rule body (statement-mode
                                   only; omitted in block mode)
```

Sample types and the probes that emit them:

| `type`     | Probe                | Fired when                                                                        |
|------------|----------------------|-----------------------------------------------------------------------------------|
| `input`    | `appendText`         | The pipeline begins processing a log — entry-point probe, fires once per record.  |
| `function` | `appendParser`       | A `json{}` / `yaml{}` / `text{}` parser block runs (block mode only).             |
| `function` | `appendExtractor`    | The `extractor{}` block finishes (block mode synopsis; skipped in statement mode).|
| `function` | `appendLine`         | Per-statement probe — only fires when `granularity=statement`.                    |
| `output`   | `appendOutputRecord` | A typed log builder reaches `complete()` after the sink kept the record.          |
| `output`   | `appendOutputMetric` | The LAL metric extractor produces a SampleFamily for hand-off to MAL.             |

### Sample payload — input vs output (LAL split)

The `input` sample (always the first sample on every record) carries the
**raw input only** — the `LogData` proto for the agent path, or whatever
typed input the layer's `LALSourceTypeProvider` declares. All subsequent
samples carry **only the output** — the `LALOutputBuilder`'s accumulated
state, shaped like the persisted DB row:

```text
samples[0].payload (input):
  { aborted, hasParsed, parsedKeys[], input: { LogData fields, body, tags[] } }

samples[≥1].payload (function/output):
  { aborted, hasParsed, parsedKeys[],
    output: {
      type, name,                         — builder type + short name (e.g. "Log")
      service, serviceInstance, endpoint,
      layer, traceId, segmentId, spanId, timestamp,
      contentType, content,               — read directly from input body
                                            (no `toLog()` allocation)
      tags: [                             — merged with status:
        { key, value, status: "original" },     — from input LogData.tags
        { key, value, status: "lal-added" },    — added by the rule (no key collision)
        { key, value, status: "lal-override" }  — added by the rule, key also in input
      ]
    } }
```

`status` reflects how each tag came to land in the persisted
`tagsRawData` column: `original` means it was already on the input log;
`lal-added` means the rule produced a new key; `lal-override` means the
rule added a tag whose key also exists on the input (the runtime
concatenates both — they are NOT clean replacements).

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
`dsl-debugging` module is enabled — the LAL generator emits per-rule
`GateHolder` fields and probe call sites, so debug sessions capture samples.
Set `false` only if the REST surface is wanted but no codegen-side probe
overhead is acceptable; with `false` the LAL bytecode is byte-identical to a
build without SWIP-13. Flipping the flag requires an OAP restart:

```bash
SW_DSL_DEBUGGING_INJECTION_ENABLED=false   # default is true; set false to disable probes
```

> SECURITY: capture payloads include raw log bodies, parsed maps, and
> output-builder field values. Treat the admin port as authenticated
> infrastructure — see
> [Admin API readme — Security Notice](readme.md#-security-notice).

## Picking the rule key

A LAL rule's key tuple is `(catalog=lal, name=<file>, ruleName=<rule>)`:

| Field      | Source                                                                                                                                       |
|------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `catalog`  | `lal`                                                                                                                                        |
| `name`     | The rule **file** — for static rules the YAML file name with extension (e.g. `default.yaml`); for runtime-rule entries the runtime-rule `name` (no `.yaml` suffix). |
| `ruleName` | The `name:` field of the rule entry within the file (one file may declare several rules).                                                    |

Examples:

| Source                                               | Install URL                                                                                                                  |
|------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| Shipped `lal/default.yaml`, rule `default`           | `?catalog=lal&name=default.yaml&ruleName=default`                                                                            |
| Runtime-rule LAL applied as `name=my-extractor`, rule entry `e2e-app-extractor` | `?catalog=lal&name=my-extractor&ruleName=e2e-app-extractor`                                                                  |

## Granularity

LAL is the only DSL with **per-statement** capture. Operators choose the mode
at install time:

| `granularity`  | Behaviour                                                                                                                       |
|----------------|---------------------------------------------------------------------------------------------------------------------------------|
| `block` (default) | One `input` sample + at most one `function` sample per block (parser / extractor synopsis) + one `output` sample.                 |
| `statement`    | One `input` sample + one `function` sample **per individual extractor statement** (with verbatim `sourceText` + `sourceLine`) + one `output` sample. |

The codegen call site for `appendLine` is unconditional in the bytecode; the
recorder short-circuits it in block mode. Statement mode is intended for
short interactive debugging — capture volume is roughly N× block mode where
N is the number of statements in the extractor.

Specify the mode either as a query parameter (wins over body) or in the
install body:

```bash
# Query param
curl -X POST '...?catalog=lal&name=...&ruleName=...&clientId=...&granularity=statement'

# Body field
curl -X POST '...?catalog=lal&name=...&ruleName=...&clientId=...' \
     -H 'Content-Type: application/json' \
     -d '{"granularity": "statement"}'
```

## End-to-end example — block mode

### 1. Open a debug session against the shipped `default` rule

```bash
curl -s -X POST \
     'http://OAP:17128/dsl-debugging/session?catalog=lal&name=default.yaml&ruleName=default&clientId=alice'
```

### 2. Drive log ingest, then poll

```bash
curl -s 'http://OAP:17128/dsl-debugging/session/SESSION_ID'
```

A trimmed slice (one record = one log):

```json
{
  "nodes": [{
    "records": [{
      "startedAtMs": 1778114804604,
      "dsl": "filter {\n  sink {\n  }\n}\n",
      "rule": { "ruleName": "default", "sourceLine": "3" },
      "samples": [
        { "type": "input", "sourceText": "", "continueOn": true,
          "payload": {
            "aborted": false, "hasParsed": true, "parsedKeys": [],
            "input": { "type": "LogData", "service": "demo-svc",
                       "serviceInstance": "demo-1",
                       "timestamp": 1778114804604,
                       "tags": [ {"key":"marker","value":"e2e"} ],
                       "body": { "format": "TEXT", "text": "hello world" } }
          } },
        { "type": "output", "sourceText": "", "continueOn": true,
          "payload": {
            "aborted": false, "hasParsed": true, "parsedKeys": [],
            "output": {
              "type": "LogBuilder", "name": "Log",
              "service": "demo-svc", "serviceInstance": "demo-1",
              "endpoint": "", "layer": "",
              "traceId": "", "segmentId": "", "spanId": 0,
              "timestamp": 1778114804604,
              "contentType": "TEXT", "content": "hello world",
              "tags": [ { "key":"marker","value":"e2e","status":"original" } ]
            }
          } }
      ]
    }]
  }]
}
```

The `default` rule has no extractor body and no parser block, so the only
samples are the entry-point `input` and the terminal `output`.

## End-to-end example — statement mode

### 1. Apply a rule with several extractor statements via runtime-rule

```yaml
# /tmp/lal-multi.yaml
rules:
  - name: app-extractor
    layer: GENERAL
    dsl: |
      filter {
        extractor {
          tag stage: 'extractor'
          tag emitter: 'demo'
          tag rule: 'multi-statement'
        }
        sink {
        }
      }
```

```bash
curl -s -X POST -H 'Content-Type: text/plain' \
     --data-binary '@/tmp/lal-multi.yaml' \
     'http://OAP:17128/runtime/rule/addOrUpdate?catalog=lal&name=lal-multi'
```

### 2. Open session with statement granularity

```bash
curl -s -X POST \
     'http://OAP:17128/dsl-debugging/session?catalog=lal&name=lal-multi&ruleName=app-extractor&clientId=alice&granularity=statement'
```

### 3. Poll — function samples appear, one per `tag` statement, with sourceLine

```json
{
  "nodes": [{
    "records": [{
      "samples": [
        { "type": "input",  "sourceText": "", "sourceLine": 0,
          "payload": { /* input LogData */ } },
        { "type": "function", "sourceText": "tag stage: 'extractor'",
          "sourceLine": 5,
          "payload": { /* output snapshot — 1 lalTag added */ } },
        { "type": "function", "sourceText": "tag emitter: 'demo'",
          "sourceLine": 6,
          "payload": { /* output snapshot — 2 lalTags */ } },
        { "type": "function", "sourceText": "tag rule: 'multi-statement'",
          "sourceLine": 7,
          "payload": { /* output snapshot — 3 lalTags */ } },
        { "type": "output", "sourceText": "",
          "payload": { /* final builder snapshot */ } }
      ]
    }]
  }]
}
```

Each `function` sample's `sourceLine` (1-based, relative to the DSL block)
and verbatim `sourceText` (ANTLR Interval slice of the
`ExtractorStatementContext`) identify the exact statement that fired.

### 4. Stop

```bash
curl -s -X POST 'http://OAP:17128/dsl-debugging/session/SESSION_ID/stop'
```

## Cluster behaviour

- **Install** broadcasts to every reachable peer; each peer binds its own
  recorder on its own holder.
- **Collect** broadcasts and concatenates per-node slices.
- **Stop** broadcasts; missed acks fall out via retention timeout.

No cross-node merge — each peer's slice is self-contained.

## Failure modes

| Response                     | Meaning                                                                 |
|------------------------------|-------------------------------------------------------------------------|
| `400 invalid_catalog`        | Catalog must be `lal`.                                                   |
| `400 missing_param`          | `name` or `ruleName` is missing.                                         |
| `404 rule_not_found`         | No live LAL artifact for the tuple on this node — typo in the file/rule name, or the rule is inactivated. |
| `503 injection_disabled`     | `injectionEnabled=false`. Restart with the flag on to debug.             |

## Limits

| Field             | Default       | Purpose                                          |
|-------------------|---------------|--------------------------------------------------|
| `recordCap`       | `1000`        | Max records before the recorder refuses appends. |
| `retentionMillis` | `300000` (5m) | Wall-clock retention.                            |
| `granularity`     | `block`       | `block` or `statement` (LAL only).               |

Override per-session in the install body:

```json
{ "recordCap": 200, "retentionMillis": 600000, "granularity": "statement" }
```

## See also

- [DSL Debug API — MAL](dsl-debugging-mal.md)
- [DSL Debug API — OAL](dsl-debugging-oal.md)
- [Runtime Rule Hot-Update API](runtime-rule.md) — apply / inactivate / delete LAL rules.
- [SWIP-13](../../../swip/SWIP-13.md) — full design.
