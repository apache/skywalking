# SWIP-13 Live Debugger for MAL / LAL / OAL

> **Status:** implemented in 10.5.0. Operator reference:
> [DSL Debug API](../setup/backend/admin-api/dsl-debugging.md).

## Motivation

SkyWalking has three DSLs that turn ingested telemetry into L1 outputs (metrics,
logs, records ready for downstream aggregation/persistence): **MAL** (meter analyzer),
**LAL** (log analyzer), and **OAL** (observability analysis). All three are compiled at
OAP boot or via runtime-rule hot-update; the operator authoring the rule then has to
infer correctness from downstream effects — "did the metric appear in the dashboard?",
"did the log get classified into the right layer?", "why is `endpoint_cpm` reading zero
on this endpoint?".

This SWIP is scoped to **data-parsing debugging** — we capture how each DSL transforms
its input into the L1 output that the rule emits. We do **not** follow the data past
L1: there is no L2 aggregation, no cross-node merge, no storage round-trip in scope.
The boundary is the `store` / emit stage at the tail of each DSL's pipeline. End-to-end
debugging (L1 → L2 → query → UI) is out of scope.

When a rule misbehaves the operator's only tools today are:

- read the YAML / `.oal` source,
- guess which clause is wrong,
- enable DEBUG logs and grep the OAP log for hints,
- adjust + redeploy + wait.

This is acceptable for static rules edited once a release. With **runtime-rule
hot-update** (PR #13851) shipping per-file edits in seconds, the iteration loop
becomes the main cost. We need a debugger that shows what the live ingest actually
does at every clause of the rule.

This SWIP proposes a **passive sampling debugger** that captures the actual data
flowing through MAL / LAL / OAL pipelines on demand — from the raw input the DSL
receives, through every clause, to the L1 output the rule emits — returns a
step-by-step visualization payload to the UI, and costs effectively nothing when not
in use.

## Architecture Graph

```
                                ┌─────────── operator UI / swctl / curl ───────────┐
                                │   POST /dsl-debugging/session  { clientId, ... } │
                                │   GET  /dsl-debugging/session/{id}  → payload    │
                                └──────────────────────────┬───────────────────────┘
                                                           │   admin port (default 17128)
                  ┌────────────────────────────────────────┼──────────────────────────────────┐
                  │                                        │                                  │
                  ▼                                        ▼                                  ▼
      ┌────────────────────────┐    bus     ┌────────────────────────┐    bus     ┌────────────────────────┐
      │  OAP node A (entry)    │ ─────────► │  OAP node B (peer)     │ ─────────► │  OAP node C (peer)     │
      │                        │            │                        │            │                        │
      │  ┌──────────────────┐  │            │  ┌──────────────────┐  │            │  ┌──────────────────┐  │
      │  │   admin-server     │  │            │  │   admin-server     │  │            │  │   admin-server     │  │
      │  │  (Armeria HTTP)  │  │            │  │  (Armeria HTTP)  │  │            │  │  (Armeria HTTP)  │  │
      │  │  /runtime/rule/* │  │            │  │  /runtime/rule/* │  │            │  │  /runtime/rule/* │  │
      │  │  /dsl-debugging/*│  │            │  │  /dsl-debugging/*│  │            │  │  /dsl-debugging/*│  │
      │  └──┬─────────┬─────┘  │            │  └──┬─────────┬─────┘  │            │  └──┬─────────┬─────┘  │
      │     │         │        │            │     │         │        │            │     │         │        │
      │     ▼         ▼        │            │     ▼         ▼        │            │     ▼         ▼        │
      │  runtime-  dsl-        │            │  runtime-  dsl-        │            │  runtime-  dsl-        │
      │  rule      debugging   │            │  rule      debugging   │            │  rule      debugging   │
      │  plugin    plugin      │            │  plugin    plugin      │            │  plugin    plugin      │
      │              │         │            │              │         │            │              │         │
      │              ▼         │            │              ▼         │            │              ▼         │
      │   GateHolder per rule  │            │   GateHolder per rule  │            │   GateHolder per rule  │
      │   ─ gate (volatile)    │            │   ─ gate (volatile)    │            │   ─ gate (volatile)    │
      │   ─ recorders[] (CoW)  │            │   ─ recorders[] (CoW)  │            │   ─ recorders[] (CoW)  │
      │              │         │            │              │         │            │              │         │
      │  ┌───────────▼──────┐  │            │  ┌───────────▼──────┐  │            │  ┌───────────▼──────┐  │
      │  │ generated MAL /  │  │            │  │ generated MAL /  │  │            │  │ generated MAL /  │  │
      │  │ LAL / OAL byte-  │  │            │  │ LAL / OAL byte-  │  │            │  │ LAL / OAL byte-  │  │
      │  │ code calls       │  │            │  │ code calls       │  │            │  │ code calls       │  │
      │  │ {MAL,LAL,OAL}-   │  │            │  │ {MAL,LAL,OAL}-   │  │            │  │ {MAL,LAL,OAL}-   │  │
      │  │ Debug.capture*   │  │            │  │ Debug.capture*   │  │            │  │ Debug.capture*   │  │
      │  └───────────┬──────┘  │            │  └───────────┬──────┘  │            │  └───────────┬──────┘  │
      │              │         │            │              │         │            │              │         │
      │              ▼         │            │              ▼         │            │              ▼         │
      │       per-session      │            │       per-session      │            │       per-session      │
      │       JSON record list │            │       JSON record list │            │       JSON record list │
      └────────────────────────┘            └────────────────────────┘            └────────────────────────┘
              ▲
              │  collect partials over admin-internal gRPC bus on GET
              │
      operator's GET request (UI / curl)
```

A new `oap-server/server-admin/` parent groups the admin-side modules.
Three sibling modules under it cooperate, plus `server-core` provides
the classpath contract:

- **`server-admin/admin-server`** — a new shared module providing one
  Armeria HTTP server, one admin port (default `17128`), and a
  route-registration SPI. Hosts every *admin* REST surface
  (write/dangerous APIs that operators run from a jumphost or CI).
  Replaces the runtime-rule plugin's own embedded HTTP server.
- **`server-admin/runtime-rule`** — the existing runtime-rule plugin,
  *moved* from `server-receiver-plugin/skywalking-runtime-rule-receiver-plugin/`
  into `server-admin/` as a sibling of `admin-server`. URL paths and YAML
  block name stay the same; only the Maven module location changes.
  Registers its REST handlers on `admin-server`.
- **`server-core`** (existing module, additive only) — gains the
  cross-DSL primitives shared by every generated rule class:
  `GateHolder`, the marker `DebugRecorder` interface,
  and the **OAL-specific** `OALDebug` static
  probes + `OALDebugRecorder` interface (OAL's runtime types — `ISource`,
  `Metrics` — are already in server-core, so its probe surface can live
  here). MAL- and LAL-specific probes can NOT live here because their
  signatures take types owned by the analyzer modules
  (`SampleFamily`, `Sample`, `ExecutionContext`); putting them in
  server-core would reverse the existing analyzer→server-core dependency
  direction. They live in their own DSL's analyzer module instead.
- **`analyzer/meter-analyzer`** (existing module, additive only) — gains
  the `MALDebug` static probe class and the `MALDebugRecorder`
  interface, both typed to `SampleFamily`, `Sample`, and `MeterEntity`.
  MAL-generated bytecode references `MALDebug` directly; the analyzer
  module is already on the classpath wherever MAL classes are loaded.
- **`analyzer/log-analyzer`** (existing module, additive only) — gains
  the `LALDebug` static probe class and the `LALDebugRecorder`
  interface, both typed to `ExecutionContext` and `LALOutputBuilder`.
  LAL-generated bytecode references `LALDebug` directly.
- **`server-admin/dsl-debugging`** — a new feature module that provides
  the runtime *implementation* behind the per-DSL recorder interfaces.
  It depends on `server-core`, `analyzer/meter-analyzer`, and
  `analyzer/log-analyzer` (this is the natural feature → analyzer →
  core direction). Holds: `MALDebugRecorderImpl` /
  `LALDebugRecorderImpl` / `OALDebugRecorderImpl` (one per DSL, each
  with hand-written JSON serializers for that DSL's artifacts), the
  per-node session registry (`Map<SessionId, DebugSession>` +
  `Map<ClientId, SessionId>`), the install / uninstall paths that
  mutate the holders' recorder arrays, the admin-internal gRPC fan-out
  RPCs, and the REST handler. Registers its routes on the
  `admin-server` HTTP host and its cluster gRPC service on the
  admin-internal gRPC bus (both owned by `admin-server`).

Capture lives **inside** the DSL-generated bytecode (one extra
gate-guarded static call per stage boundary). The cross-DSL primitives
(`GateHolder`, `RuleKey`, the `DebugRecorder` marker interface) live
in `server-core`. Each DSL's probe class lives in that DSL's home
module: `MALDebug` in `analyzer/meter-analyzer`, `LALDebug` in
`analyzer/log-analyzer`, `OALDebug` in `server-core` (OAL runtime
types are already there). Recorder implementations and the session
registry live in `dsl-debugging`. Generated MAL bytecode references
`MALDebug` from its own module, generated LAL bytecode references
`LALDebug` from its own module, and generated OAL bytecode references
`OALDebug` from `server-core` — every `invokestatic` resolves through
the existing analyzer → server-core dependency direction; no reverse
deps are introduced.

Cluster fan-out rides the **admin-internal gRPC bus** (the same
transport runtime-rule registers its `RuntimeRuleClusterServiceImpl`
on); `dsl-debugging` ships its **own gRPC service implementation**
(`DSLDebuggingClusterServiceImpl`) for its four RPCs
(`InstallDebugSession` / `CollectDebugSamples` / `StopDebugSession` /
`StopByClientId`).
Runtime-rule's `RuntimeRuleClusterServiceImpl` stays exactly where it is
— the two services are independent, register independently, and either
module can be enabled or disabled without affecting the other. Both ride
the **admin-internal gRPC bus** owned by `admin-server` (default port
`17129`) — a dedicated transport separate from the public agent /
cluster gRPC port (`core.gRPCPort`, default `11800`) so privileged admin
RPCs stay out of the agent network's blast radius. Peer discovery is
handled by `AdminClusterChannelManager` (admin-server module service),
which dials each peer's admin-internal port via the cluster module's
existing peer registry.

The `status-query-plugin`'s existing `/debugging/*` routes (config dump,
MQE step-through, trace step-through) stay where they are on the query
port — those are read-only query-time inspection helpers and have a
different audience from admin write APIs.

## Proposed Changes

### 1. The data contract returned to the UI

The Claude Design handoff (`Runtime Rule Admin` canvas) pinned three shapes the
UI expects. The backend is the source of truth — UI is a strict visualizer.

The capture target is shaped *per DSL*, not generic — each DSL has its own
workflow with its own terminal artifact handed to L1, and the data contract
mirrors those terminal artifacts. **L1 / L2 / cross-node aggregation /
persistence are explicitly out of scope.** The debugger always stops *before*
the L1 hand-off — at the point where the rule has produced its final L1-bound
object but hasn't yet entered the streaming/aggregation kernel.

#### Kept-only capture for MAL (the filter is high-cardinality)

**MAL** is the only DSL where rejected executions are dropped from
`records[]`. MAL filters discriminate on tag values (`service_name`,
`kind`, `component`) and in a multi-tenant or multi-component flow a
rule routinely rejects 99% of the traffic routed to it by metric
name. Publishing every rejected execution would burn `recordCap`
(default 100) on noise within seconds and never reach a row that
demonstrates the rule's actual processing. So for MAL the contract
is: **every record represents one `SampleFamily` that passed the
rule's filter and walked through to `meterEmit`**. Implementation:
`MALDebugRecorderImpl.appendFilter(kept=false)` calls
`discardCurrentExecution()`; the in-flight execution is silently
dropped.

**OAL** captures both kept and rejected executions. OAL filters are
deterministic discriminators (CLIENT-vs-SERVER, layer matchers,
status / latency predicates) — not high-cardinality tag noise.
Rejected-source samples (`continueOn=false`) are the filter doing its
job in plain view, useful for verifying that the partition logic
fires the way the operator expects. Implementation: each
`appendFilter` call adds a sample regardless of `kept`; on
`kept=false` the recorder also calls `publishCurrentExecution()`
because the FTL's `return` ends the per-source pipeline at that
point, so the next source's `appendSource` mustn't bleed into this
record.

**LAL** has no analogue (`abort()` isn't a filter — it's a
per-statement short-circuit); aborted logs publish their accumulated
samples with `continueOn=false` on the abort point so operators can
see where the rule gave up.

#### MAL — two-half pipeline: `SampleFamily` transforms → `MeterSystem` build

MAL is the only DSL whose terminal artifact is **not** a `SampleFamily`.
`expression.run(samples)` returns a final SampleFamily, but the rule's L1-bound
output is what `Analyzer.doAnalysis` builds **after** that — one
`AcceptableValue<Long | DataTable | BucketedValues | PercentileArgument>` per
`MeterEntity`, populated via `meterSystem.buildMetrics(...).accept(...)`. That
`AcceptableValue` is what `meterSystem.doStreamingCalculation(...)` consumes;
that call is L1 entry, so capture stops one line before it.

### Wire shape

The collect response is a hierarchy of `nodes[].records[].samples[]`. One
record equals one full execution of the rule's pipeline (one
`SampleFamily` for MAL, one `ISource` for OAL, one log for LAL); samples
within a record are the probe firings in execution order.

**Per-record fields** (rule-pipeline execution):

| Field          | Meaning                                                                                                |
|----------------|--------------------------------------------------------------------------------------------------------|
| `startedAtMs`  | Wall-clock millis when the record opened (the first probe of the execution).                          |
| `dsl`          | Verbatim per-rule DSL text. Stamped per record so a session that spans a hot-update can render the right rule version per record. |
| `rule`         | Structured rule envelope: MAL carries `{metricPrefix, name, filter, exp, expSuffix}`; LAL/OAL carry `{ruleName, sourceLine}`. Lets operators read the rule's intent without re-resolving the source file. |
| `samples[]`    | Probe-emitted samples in execution order. |

**Per-sample fields**:

| Field         | Meaning                                                                                          |
|---------------|--------------------------------------------------------------------------------------------------|
| `type`        | `input` / `filter` / `function` / `aggregation` / `output` — the probe class.                    |
| `sourceText`  | The verbatim ANTLR slice of the DSL fragment that produced this sample (chain ops, filter clauses, aggregation calls, LAL statements). Lets operators grep against the source byte-for-byte. |
| `continueOn`  | `true` when the pipeline continued past this step; `false` on rejected filter branches (OAL) or `abort()` paths (LAL). MAL is kept-only — no `false` rows. |
| `payload`     | DSL-specific structured snapshot — see per-DSL contract below.                                   |
| `sourceLine`  | 1-based line number in the source `.yaml` / `.oal` / LAL DSL block. Omitted when `0` (e.g. label-only probes — see "Line-number granularity in MAL" below). |

**Per-DSL probe → sample-type mapping**, in pipeline order:

| Catalog | Probe (call site)                | `type`        | Payload                                                                                                                                       |
|---------|----------------------------------|---------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| MAL     | `captureFilter` (file-level)     | `filter`      | `SampleFamily.toJson()` after the file-level closure (kept-only: rejected branches are dropped).                                              |
| MAL     | `captureInput`                   | `input`       | head `SampleFamily` of the `exp:` body.                                                                                                       |
| MAL     | `captureStage` × N               | `function`    | `SampleFamily` after each chain op (`sum`, `tagEqual`, `service`, …); `sourceText` is the verbatim segment without the leading dot.            |
| MAL     | `captureDownsample`              | `function`    | `SampleFamily` after downsampling; `sourceText` is the function call (e.g. `rate("PT1M")`).                                                    |
| MAL     | `captureMeterEmit` (terminal)    | `output`      | terminal meter sample with metric name + entity + value + timeBucket.                                                                          |
| OAL     | `captureSource`                  | `input`       | `ISource.toJson()` — full source columns under `fields{}` (e.g. `ServiceRelation` source attributes).                                          |
| OAL     | `captureFilter`                  | `filter`      | post-filter source view; **both** kept (`continueOn=true`) **and** rejected (`continueOn=false`) branches are emitted (filters are deterministic discriminators). |
| OAL     | `captureAggregation`             | `aggregation` | `Metrics.toJson()` after the aggregation function (`cpm()`, `percentile2(10)`, …).                                                             |
| OAL     | `captureEmit` (terminal)         | `output`      | `Metrics.toJson()` with `count` / `total` / `value` / `timeBucket` from the metric family's `appendDebugFields` hook.                          |
| LAL     | `appendText` (entry)             | `input`       | raw input only (`LogData` rendered via `LogDataDebugDump`); fires before `LALOutputBuilder.init()`.                                           |
| LAL     | `appendParser` / `appendExtractor` | `function`  | block-mode synopses — `LALOutputBuilder.outputToJson()` snapshot of the builder so far.                                                       |
| LAL     | `appendLine`                     | `function`    | statement-mode probe (one per extractor statement); `sourceText` carries the verbatim statement; payload is the builder snapshot.              |
| LAL     | `appendOutputRecord` (terminal)  | `output`      | final builder snapshot — DB-bound row with merged `tags[]` (each carries `status: original | lal-added | lal-override`).                       |
| LAL     | `appendOutputMetric` (terminal)  | `output`      | the SampleFamily handed off to MAL.                                                                                                            |

**LAL input/output payload split.** The first sample on every LAL record
carries only the raw input; all subsequent samples carry only the
output (`LALOutputBuilder.outputToJson()`). The framework renders raw
input directly off `ctx.input()` on the input probe — the
`LALOutputBuilder` is not consulted there because `init()` runs at the
sink stage. This keeps the wire small (no redundant input on every
probe) and avoids the stale-cache empty-input bug that would arise if
the dispatch went through a not-yet-initialised builder.

The terminal `output` capture is the boundary; the debugger does not
follow the persisted entity further into L1. For MAL meter emits, the
typed `T` (one of `Long` / `DataTable` / `BucketedValues` /
`PercentileArgument`) is exposed in the payload so the UI can render
the correct shape per metric type.

**Downsampling — explicit vs. default.** A rule's `downsampling:` field is
optional; when the operator omits it MAL fills in a default based on the
metric type (typically `AVG` for gauges, `SUM` for counters; see the MAL
compiler's metric-type resolution rules). The UI must distinguish the two
cases so operators don't read a defaulted `AVG` as a value they declared.
The `MalDownsample` capture carries an `origin` field — `"explicit"` when
the YAML's `downsampling:` is present, `"default"` when MAL filled it in
— and the UI renders the row with a "default" badge in the latter case
(e.g., `downsampling: AVG (default)`).

The UI renders the per-rule debug payload as a table whose left column is
`sourceText` (with `sourceLine` as a small line-number gutter) and right
column is the stage's result snapshot. Operators read the rule top-to-bottom
in the source they wrote, with the output of each line beside it — no
translating from synthetic stage ids back to source.

**Line-number granularity in MAL.** The MAL `exp:` field in YAML is one
scalar string — often line-folded across two or three physical lines. ANTLR
parses the whole expression as a single source unit, so every chain stage
emitted from that expression carries the **same `sourceLine`** (the YAML
line where the `exp:` key sits). This is honest rather than synthetic;
attempting to back-compute per-stage line numbers from sub-positions inside
a folded scalar would be brittle and misleading. Per-stage discrimination
comes from `sourceText` — the verbatim fragment is unique per stage even
when lines collide. The UI line-gutter shows the same number twice when
that's the truth, and operators recognise the fragments anyway because
they wrote them.

LAL is different — its DSL has one statement per line in the YAML body
(`json {}`, `service parsed.x as String`, `tag 'k': v`, etc.), so each
captured LAL statement has a distinct `sourceLine`. OAL captures **per
clause** with each clause's actual ANTLR-derived line; same-line clauses
share a number, distinct-line clauses each get their own. `sourceText`
carries the fragment-level detail regardless. (See the OAL data
contract below for the full per-clause table.)

**File-level filter fires before `expression.run()`.** The probe ordering
within one ingest pass is:

```
MALDebug.captureFilter          (file `filter:` block — once, on the whole input map)
MALDebug.captureInput          (per metric reference inside expression.run — already filtered)
MALDebug.captureStage × N        (each chain stage)
MALDebug.captureScope          (after the expSuffix scope-binding stage)
MALDebug.captureDownsample      (rule-level metadata, fires once)
MALDebug.captureMeterBuild      (per MeterEntity, in Analyzer post-chain)
MALDebug.captureMeterEmit      (per MeterEntity, immediately before doStreamingCalculation)
```

`MALDebug.captureInput` therefore reports the **post-filter** sample count, not
the raw count the receiver delivered — the file-level filter has already
trimmed the input map by the time the generated `run()` reads it.

#### LAL — typed output, not "unified log"

LAL's terminal artifact is **the typed output builder** the rule produces.
The codegen calls `ctx.setOutput(new <OutputType>())` at the start of
`execute()`; the extractor populates fields via typed setters; then
`RecordSinkListener.parse()` calls `builder.init(metadata, input,
moduleManager)` to fill in standard fields, and `build()` calls
`builder.complete(sourceReceiver)`. The L1 boundary is `complete(...)`;
capture is the populated builder *just before* that call.

`OutputType` is per-rule and varies — `LogBuilder`, `EnvoyAccessLogBuilder`,
`DatabaseSlowStatementBuilder`, `SampledTraceBuilder`, custom subclasses
contributed by `LALSourceTypeProvider` SPI. The UI must show the actual type
name and its full populated state, not flatten everything to "LogData".

A LAL rule may also emit metrics into MAL via `MetricExtractor.submitMetrics
(ctx, sampleBuilder)`. The `Sample` produced is wrapped into a `SampleFamily`
and either appended to `ctx.metricsContainer()` (when called inside a MAL
fanout) or fed to `provider.getMetricConverts().forEach(it -> it.toMeter(...))`.
This second emission is independent of the typed record output — some rules
emit both, some only one, some neither.

Per-record blocks (operator toggles per-block sampling; off blocks return
`null` cells):

| Block           | Captured artifact                                                                                                 |
|-----------------|-------------------------------------------------------------------------------------------------------------------|
| `text`          | raw log body string + body-type (`json` / `yaml` / `text` / `none`)                                               |
| `parser`        | parsed map / typed proto object — the structure `parsed.*` resolves against                                       |
| `extractor`     | populated typed output builder (snapshot mid-flight) + extracted tags + resolved `def` variables                  |
| `sink`          | branch taken (`enforcer` / `sampler` / `rateLimit`) + `kept` / `dropped` + reason                                 |
| `output_record` | the **typed** output builder right before `complete()` — includes `outputType` class name + every populated field |
| `output_metric` | the `SampleFamily` (≥ 1 `Sample`) about to be handed to MAL — only present when the rule has a `metrics{}` block  |

`output_record` and `output_metric` are independent — each is `null` if the
rule didn't produce that output (`output_record` is `null` on a sink-dropped
record; `output_metric` is `null` for any rule without `metrics{}`).
`output_metric` does **not** chain into a separate MAL debug session.

Per-record envelope:

```
{ id, ts, svc, body_type, text, parsed, extracted, def_vars,
  sink: { branch, kept, note },
  output_record: { outputType, builderState } | null,
  output_metric: { sampleFamily } | null }
```

##### Granularity — block vs. statement (opt-in)

The block view above (5 fixed blocks + 2 outputs) answers most operator
questions: did the parser succeed → did the extractor populate fields →
did the sink keep the record. For "step through every DSL line in my
extractor" investigations, sessions can opt into **statement-level**
capture via a request-time flag.

| Granularity          | Capture points per log record                                                                                  | Record-cap math                                  |
|----------------------|----------------------------------------------------------------------------------------------------------------|--------------------------------------------------|
| `block` (default)    | 5 fixed blocks + 2 output emit points = 7 cells per log record                                                 | 8 records × 7 = 56 cells worst case              |
| `statement` (opt-in) | 7 block points **plus** one cell per meaningful DSL statement (each `service`/`tag`/`def`/`if`/`metrics` line) | 8 records × (7 + N statements) cells worst case  |

`statement` mode burns the per-session record cap faster — a rule with 12
extractor statements + sink branches produces ≈ 19 cells per log record
versus 7 in block mode. The
[per-session record cap](#5-session-lifecycle-storage-and-memory-control)
treats each cell uniformly, so an operator selecting `statement` should
lower `recordCap` in the request body to stay within the session's
worst-case heap budget. The session payload reports
`granularity: "block" | "statement"` so the UI knows which renderer to
use.

Statement-level capture maps each cell to its source DSL line via
`sourceLine` carried through `LALScriptModel`'s AST nodes (see
[LAL hooks](#34-generator-hook-insertion--concrete-code-diffs)) — the UI
fetches the rule YAML once at session start (via `/runtime/rule/list`)
and reads line `sourceLine` directly. **No `sourceText` is duplicated in
the captured payload.** LAL's one-statement-per-line convention guarantees
the line is unique per captured statement, so a line number alone is
enough — unlike MAL chain stages and OAL clauses, which often share a
single line and therefore must carry their verbatim fragment as
`sourceText`.

This granularity flag applies to **LAL only**. MAL is already
statement-level for free — every method-chain stage is one DSL statement
and the chain capture (`MALDebug.captureStage`) records it. OAL is single-rule
already (`do<Metric>(source)` is one logical statement), so the
distinction does not apply.

#### OAL — Source row → typed `Metrics` object before `in()`

OAL is dispatcher-style: each `do<MetricsName>(source)` method takes one
`Source` row, optionally filters it, builds a `<MetricsName>Metrics` object,
copies fields from the source, calls the entry method (`combine` /
`accept` / `add` depending on the function class), and finally calls
`MetricsStreamProcessor.getInstance().in(metrics)`. That `in(...)` call is
the L1 boundary; capture stops one line before it.

Operator selects **one OAL line**
(e.g. `endpoint_cpm = from(Endpoint.*).cpm();`) and configures
`(maxRows, windowSec)`. The picker is fed by the
[OAL read-only management API](#41-oal-read-only-management-api--the-catalog-the-debugger-picks-from)
introduced in this SWIP — without it the UI has no way to discover what
OAL rules exist at runtime.

OAL probe → sample-type mapping (one source event = one record;
samples flow source → filter → aggregation → output in execution order):

| Probe (call site)        | `type`        | Origin             | Captured artifact                                                                                                 |
|--------------------------|---------------|--------------------|-------------------------------------------------------------------------------------------------------------------|
| `captureSource`          | `input`       | OAL clause         | `ISource.toJson()` — the inbound source row (concrete subtype: `Endpoint`, `Service`, `ServiceRelation`, …) under `payload.fields{}`. `sourceText` = `from(...)` clause. |
| `captureFilter`          | `filter`      | OAL clause (× N)   | post-filter source view; both kept (`continueOn=true`) and rejected (`continueOn=false`) branches are emitted. One sample per filter clause in the rule.            |
| `captureAggregation`     | `aggregation` | OAL clause         | `Metrics.toJson()` after the aggregation function. `sourceText` = the verbatim clause (`cpm()`, `percentile2(10)`, `apdex(name, status)`).                       |
| `captureEmit` (terminal) | `output`      | compiler-implicit  | `Metrics.toJson()` of the L1-ready metric (`count` / `total` / `value` / `timeBucket` from `Metrics.appendDebugFields`); fired immediately before `MetricsStreamProcessor.in(metrics)`. |

**Per-clause is the natural OAL granularity, not an opt-in.** OAL is one
logical rule per line in the `.oal` file, but each rule is composed of
distinct clauses (`from(Source.*)`, optionally `.filter(...)`, ending in
`.<aggregationFn>(...)`). Every user-written clause becomes its own captured
row carrying its verbatim OAL fragment as `sourceText` — same principle as
MAL chain stages, just naturally bounded (≤ 5 stages per rule). There is no
`granularity: "block" | "statement"` flag for OAL because the only useful
view is per-clause; there's nothing finer to opt into.

Some rules write the whole expression on one physical line
(`endpoint_cpm = from(Endpoint.*).cpm();`); others split it across lines
for readability. Per-clause `sourceLine` reports each clause's actual
position from ANTLR's source location — operators see distinct lines when
they wrote them on distinct lines, and the same line repeated when they
wrote the rule on one line. `sourceText` discriminates clauses regardless.

The `output` sample is the terminal capture (compiler-emitted, no
`sourceText` — render with an `(implicit)` badge in the UI). The
aggregation function (`CPMFunction`, `LongAvgFunction`,
`PercentileFunction`, etc.) determines what fields the `Metrics`
instance carries at emit time. `Metrics.appendDebugFields(JsonObject)`
is the per-family hook (`CPMMetrics` emits `count` / `total` / `value`,
`PercentileMetrics` emits its bucket array, etc.); the UI sees the
concrete class name plus the populated state. The debugger does **not**
follow the metric into the cross-node L2 / persistence path; cross-node
aggregation is out of scope.

For the per-DSL examples and full payload key reference see the
operator pages:
[MAL](../setup/backend/admin-api/dsl-debugging-mal.md),
[LAL](../setup/backend/admin-api/dsl-debugging-lal.md),
[OAL](../setup/backend/admin-api/dsl-debugging-oal.md).

### 2. Capture mechanism — per-rule-instance `GateHolder` (no ThreadLocal, no shared registry)

Captures live entirely in the rule's runtime state, not in a per-thread
context. **Each compiled rule class generates its own `GateHolder`
instance at construction time**, with `contentHash` set as a constructor
argument and made immutable for that holder's lifetime. Probes embedded
in the generated bytecode read the holder directly:

```java
public final class GateHolder {
    // Hot-path field: read by every probe call site. Public + volatile so
    // the generated bytecode reads it directly with one volatile load and
    // the JIT can hoist the load when the method is hot.
    public volatile boolean gate;

    // Immutable for this holder's lifetime — set by constructor at codegen,
    // never overwritten. Hot-update creates a brand-new holder with the
    // new content's hash; the old holder retains its own hash until GC.
    private final String contentHash;

    // CoW snapshot of currently-bound recorders; mutated only on session
    // install / uninstall via the synchronized methods below. Public +
    // volatile so generated probe code reads it with one volatile load —
    // symmetric with `gate`. Readers MUST treat the array as immutable;
    // writers always replace the reference, never mutate the array in place.
    public volatile DebugRecorder[] recorders = EMPTY;
    private static final DebugRecorder[] EMPTY = new DebugRecorder[0];

    public GateHolder(String contentHash) { this.contentHash = contentHash; }

    public String getContentHash() { return contentHash; }

    /** Called by `DebugSessionRegistry.install(...)`. */
    public synchronized void addRecorder(DebugRecorder r) {
        DebugRecorder[] old = recorders;
        DebugRecorder[] neu = Arrays.copyOf(old, old.length + 1);
        neu[old.length] = r;
        recorders = neu;
        if (old.length == 0) gate = true;       // 0→1 transition flips the gate
    }

    /** Called by `DebugSessionRegistry.uninstall(...)`. */
    public synchronized void removeRecorder(DebugRecorder r) {
        DebugRecorder[] old = recorders;
        DebugRecorder[] neu = removeFromArray(old, r);
        recorders = neu;
        if (neu.length == 0) gate = false;      // 1→0 transition flips the gate
    }
}
```

`contentHash` is `final` because the **holder belongs to one specific
generated class instance**. There is no scenario in which the same
holder serves two different rule contents: hot-update produces a new
class, the new class's constructor produces a new holder with the new
hash, and the old holder continues to exist (with the old hash) for as
long as the old class instance is referenced by in-flight ingest. Each
captured record stamps `record.contentHash = holder.getContentHash()`
at append time and inherently picks up the right value because the
probe fires on the holder owned by whichever class the receiver thread
is running.

```java
// idiom emitted by codegen at every probe call site:
if (DEBUG.gate) {                                    // public volatile field — direct read
    MALDebug.captureStage(DEBUG, rule, sourceLine, sourceText, family);
}
```

`DEBUG` is **not `static final`** for MAL and LAL — those DSLs each
have a singleton runtime instance per rule (`MalExpression`,
`LalExpression`), so the holder lives as a regular instance field on
that singleton:

```java
// inside a generated MAL rule class:
public final GateHolder DEBUG = new GateHolder("7c3a91…");

// inside a generated LAL rule class:
public final GateHolder DEBUG = new GateHolder("9b12a0…");
```

For OAL, the per-metric runtime artifact (`<MetricName>Metrics`) is
constructed *per source row* — not a singleton. Instead, the
dispatcher singleton (e.g., `EndpointDispatcher`) holds **per-metric
holder instance fields**, one per metric the dispatcher routes to:

```java
// inside a generated OAL dispatcher (singleton, held by SourceReceiverImpl):
public final GateHolder gate_endpoint_cpm       = new GateHolder("8a21f0…");
public final GateHolder gate_endpoint_resp_time = new GateHolder("8a21f0…");
// ... one per metric in this scope's `do<Metric>` method set

private void do_endpoint_cpm(Endpoint source) {
    if (gate_endpoint_cpm.gate) {
        OALDebug.captureSource(gate_endpoint_cpm, "endpoint_cpm", ...);
    }
    // ... existing build / aggregation / emit ...
}
```

(All metrics in one OAL file share the file's `contentHash`, so the
constructor argument is the same for every per-metric holder created
from that file.)

The install / uninstall paths call `addRecorder(...)` /
`removeRecorder(...)` directly — no reflection. The methods are
`synchronized` to serialise the array copy-on-write against concurrent
mutations; the receiver hot-path read takes no lock (it just reads the
volatile array reference). The gate is auto-managed inside
`addRecorder` / `removeRecorder` on 0↔1 transitions, so the install
path doesn't have to remember to flip it separately.

**No shared `Map<RuleKey, GateHolder>` registry.** Sessions find the
live holder by asking the rule's *existing* analyzer / dispatcher
infrastructure for the live runtime artifact (the `MalExpression` /
`LalExpression` / dispatcher singleton) and then calling
`.debugHolder(...)` on it — see
[Install / uninstall](#33-install--uninstall--direct-gateholder-mutation-no-per-thread-context).
This is what makes content-hash-overwrite-on-hot-update structurally
impossible: there is no shared map entry to overwrite, no swap window,
no race. Old captures continue on the old holder until the old class
goes out of scope (typically a few hundred ms during drain); new
captures fire on the new holder. Records carry whichever
`contentHash` was on the holder they were stamped from — the UI sees a
clean version boundary in the session payload.

When no session is bound to the rule, `gate=false` and JIT eliminates the
call site entirely — ~1 ns idle cost (the volatile load + branch). When
sessions are bound, the probe iterates the (small) recorders array and
each recorder appends to its own session's payload. **Multiple sessions
on the same rule are first-class:** each is one entry in the array, all
fed by the same probe call.

The receiver path is unchanged. **There is no ThreadLocal**, no
analyzer-side wrapper, no per-thread context to install or propagate.
Sessions register themselves on the rule's holder; receiver threads
running the rule's generated code read the holder directly. This sits
inside the project's "no ThreadLocal side-channels" rule documented in
`CLAUDE.md` — the holder is rule-scoped state, not a thread-local
side-channel.

### 3. Capture API and per-DSL hooks

This section nails down (a) the static surface generated code calls, (b) the
recorder interface that owns per-session state, (c) the receiver-side scope
wrapper that installs / uninstalls a recorder for one ingest pass, and (d)
exactly what the MAL / LAL / OAL generators emit.

#### 3.1 Per-DSL probe classes — `MALDebug`, `LALDebug`, `OALDebug`

The probe surface splits into three DSL-specific classes, each living
in the same module that owns the DSL's runtime types. There is no
single unified `DSLDebug` class — generated bytecode for each DSL
calls into its own DSL's probe class. This is forced by Java's
classpath direction: `SampleFamily` and `Sample` are owned by
`analyzer/meter-analyzer`; `ExecutionContext` is owned by
`analyzer/log-analyzer`; both modules depend on `server-core`, not the
other way around.

| Probe class    | Module                     | Generated bytecode origin   | Imports analyzer types |
|----------------|----------------------------|------------------------------|------------------------|
| `MALDebug`     | `analyzer/meter-analyzer`  | MAL classes (compiled by this module) | `SampleFamily`, `Sample` (own module) |
| `LALDebug`     | `analyzer/log-analyzer`    | LAL classes (compiled by this module) | `ExecutionContext` (own module)        |
| `OALDebug`     | `server-core`              | OAL classes (compiled by `oal-rt`, deps server-core) | none — `ISource`, `Metrics` are in server-core |

Each probe takes the per-rule `GateHolder` (server-core) as its first
argument and fans the capture out to every recorder registered on the
holder. **No ThreadLocal**, no per-thread context propagation. The
generated bytecode wraps each call site with the gate check
(`if (DEBUG.gate)`) so the call is only emitted once a session is active
for this rule.

The probe surface is intentionally **not** generic. Each DSL has its own
workflow with its own typed L1-bound artifact, and we want generated code
to call methods whose types match the artifact rather than boxing
everything through a `StageData` wrapper.

##### `MALDebug` (in `analyzer/meter-analyzer`, package `org.apache.skywalking.oap.meter.analyzer.v2.dsldebug`)

```java
public final class MALDebug {
    private MALDebug() {}

    // ── Part A: chain probes (emitted from MALMethodChainCodegen / MALExprCodegen)
    public static void captureInput(GateHolder h, String rule, int sourceLine,
                                     String metricRef, SampleFamily f)                              { fanOut(h, r -> ((MALDebugRecorder) r).appendInput(rule, sourceLine, metricRef, f)); }
    public static void captureFilter(GateHolder h, String rule, int sourceLine,
                                      String filterExpr, SampleFamily f)                            { fanOut(h, r -> ((MALDebugRecorder) r).appendFilter(rule, sourceLine, filterExpr, f)); }
    public static void captureStage(GateHolder h, String rule, int sourceLine,
                                     String sourceText, SampleFamily f)                             { fanOut(h, r -> ((MALDebugRecorder) r).appendStage(rule, sourceLine, sourceText, f)); }
    public static void captureScope(GateHolder h, String rule, int sourceLine, String scopeExpr,
                                     SampleFamily f, Map<MeterEntity,Sample[]> meterSamples)        { fanOut(h, r -> ((MALDebugRecorder) r).appendScope(rule, sourceLine, scopeExpr, f, meterSamples)); }
    public static void captureDownsample(GateHolder h, String rule, String function,
                                          String origin, SampleFamily f)                            { fanOut(h, r -> ((MALDebugRecorder) r).appendDownsample(rule, function, origin, f)); }

    // ── Part B: post-chain probes (emitted from Analyzer.doAnalysis, hand-written)
    public static void captureMeterBuild(GateHolder h, String rule, MeterEntity entity,
                                          String metricName, String valueType,
                                          AcceptableValue<?> v)                                     { fanOut(h, r -> ((MALDebugRecorder) r).appendMeterBuild(rule, entity, metricName, valueType, v)); }
    public static void captureMeterEmit(GateHolder h, String rule, MeterEntity entity,
                                         String metricName, AcceptableValue<?> v,
                                         long timeBucket)                                            { fanOut(h, r -> ((MALDebugRecorder) r).appendMeterEmit(rule, entity, metricName, v, timeBucket)); }

    private static void fanOut(GateHolder h, java.util.function.Consumer<DebugRecorder> body) {
        DebugRecorder[] rs = h.recorders;
        for (int i = 0; i < rs.length; i++) {
            DebugRecorder r = rs[i];
            if (!r.isCaptured()) body.accept(r);
        }
    }
}
```

##### `LALDebug` (in `analyzer/log-analyzer`, package `org.apache.skywalking.oap.log.analyzer.v2.dsldebug`)

```java
public final class LALDebug {
    private LALDebug() {}

    public static void captureText(GateHolder h, String rule, ExecutionContext ctx)                  { fanOut(h, r -> ((LALDebugRecorder) r).appendText(rule, ctx)); }
    public static void captureParser(GateHolder h, String rule, ExecutionContext ctx)                { fanOut(h, r -> ((LALDebugRecorder) r).appendParser(rule, ctx)); }
    public static void captureExtractor(GateHolder h, String rule, ExecutionContext ctx)             { fanOut(h, r -> ((LALDebugRecorder) r).appendExtractor(rule, ctx)); }
    public static void captureSink(GateHolder h, String rule, ExecutionContext ctx,
                                    String branch, boolean kept)                                     { fanOut(h, r -> ((LALDebugRecorder) r).appendSink(rule, ctx, branch, kept)); }
    public static void captureLine(GateHolder h, String rule, int sourceLine,
                                    ExecutionContext ctx)                                            { fanOut(h, r -> ((LALDebugRecorder) r).appendLine(rule, sourceLine, ctx)); }
    public static void captureOutputRecord(GateHolder h, String rule, ExecutionContext ctx,
                                            LALOutputBuilder builder)                                { fanOut(h, r -> ((LALDebugRecorder) r).appendOutputRecord(rule, ctx, builder)); }
    public static void captureOutputMetric(GateHolder h, String rule, ExecutionContext ctx,
                                            SampleFamily handedToMal)                          { fanOut(h, r -> ((LALDebugRecorder) r).appendOutputMetric(rule, ctx, handedToMal)); }

    private static void fanOut(GateHolder h, java.util.function.Consumer<DebugRecorder> body) {
        DebugRecorder[] rs = h.recorders;
        for (int i = 0; i < rs.length; i++) {
            DebugRecorder r = rs[i];
            if (!r.isCaptured()) body.accept(r);
        }
    }
}
```

`captureOutputMetric` takes `SampleFamily` directly:
`analyzer/log-analyzer` already depends on `analyzer/meter-analyzer`
(see `oap-server/analyzer/log-analyzer/pom.xml`), so this is the
existing dep direction, not a new one. The whole probe API stays
typed end-to-end with no `Object` leaks.

##### `OALDebug` (in `server-core`, package `org.apache.skywalking.oap.server.core.dsldebug`)

OAL's runtime types (`ISource`, `Metrics`) are already owned by
`server-core`, so OAL's probe class can live there too:

```java
public final class OALDebug {
    private OALDebug() {}

    public static void captureSource(GateHolder h, String rule, int sourceLine,
                                      String fromClauseText, ISource src)                           { fanOut(h, r -> ((OALDebugRecorder) r).appendSource(rule, sourceLine, fromClauseText, src)); }
    public static void captureFilter(GateHolder h, String rule, int sourceLine,
                                      String filterClauseText, ISource src,
                                      String left, String op, String right, boolean kept)           { fanOut(h, r -> ((OALDebugRecorder) r).appendFilter(rule, sourceLine, filterClauseText, src, left, op, right, kept)); }
    public static void captureBuild(GateHolder h, String rule, Metrics built)                       { fanOut(h, r -> ((OALDebugRecorder) r).appendBuild(rule, built)); }
    public static void captureAggregation(GateHolder h, String rule, int sourceLine,
                                           String aggregationClauseText, Metrics afterEntryFn)      { fanOut(h, r -> ((OALDebugRecorder) r).appendAggregation(rule, sourceLine, aggregationClauseText, afterEntryFn)); }
    public static void captureEmit(GateHolder h, String rule, Metrics readyForL1)                   { fanOut(h, r -> ((OALDebugRecorder) r).appendEmit(rule, readyForL1)); }

    private static void fanOut(GateHolder h, java.util.function.Consumer<DebugRecorder> body) {
        DebugRecorder[] rs = h.recorders;
        for (int i = 0; i < rs.length; i++) {
            DebugRecorder r = rs[i];
            if (!r.isCaptured()) body.accept(r);
        }
    }
}
```

These methods are the **only** symbols generated bytecode references —
generators never see `DebugRecorder` or the ThreadLocal. That keeps the
generated class identical across builds whether or not a session is active.

The probe set is intentionally **closed**. New DSL stages introduced later
(say, OAL adds a new dispatcher stage) get a new typed probe; we do not
push it through a generic `capture(stageId, Object)` because that loses
type information at the recorder and forces every `appendXxx` to deal
with `instanceof` switching. Typed probes keep the recorder narrow and
the JSON renderer per-artifact compact.

#### 3.2 `DebugRecorder` — marker interface + per-DSL extensions

`DebugRecorder` is split the same way as the probe classes: a marker
interface in `server-core` (so `GateHolder.recorders[]` can be typed
without dragging in analyzer modules), plus three extension interfaces
in their respective DSL modules carrying the typed `appendXxx` methods.

##### `DebugRecorder` marker (in `server-core`)

```java
package org.apache.skywalking.oap.server.core.dsldebug;

public interface DebugRecorder {
    String sessionId();
    String catalog();
    String name();
    String ruleName();

    /** Narrow gate — called by `DebugSessionRegistry` before bind. */
    boolean matches(String catalog, String name, String ruleName);

    /** Becomes true once recordCap is hit; further appends are no-ops. */
    boolean isCaptured();
}
```

This is the only type `GateHolder.recorders[]` is parameterised on. It
contains no `append*` methods — those live on the per-DSL extensions
below. Probes downcast to the right extension interface based on which
DSL's class is calling.

##### `MALDebugRecorder` (in `analyzer/meter-analyzer`, extends `DebugRecorder`)

```java
package org.apache.skywalking.oap.meter.analyzer.v2.dsldebug;

public interface MALDebugRecorder extends DebugRecorder {
    void appendInput(String rule, int sourceLine, String metricRef, SampleFamily f);
    void appendFilter(String rule, int sourceLine, String filterExpr, SampleFamily f);
    void appendStage(String rule, int sourceLine, String sourceText, SampleFamily f);
    void appendScope(String rule, int sourceLine, String scopeExpr,
                     SampleFamily f, Map<MeterEntity, Sample[]> meterSamples);
    void appendDownsample(String rule, String function, String origin, SampleFamily f);
    void appendMeterBuild(String rule, MeterEntity entity, String metricName,
                          String valueType, AcceptableValue<?> v);
    void appendMeterEmit(String rule, MeterEntity entity, String metricName,
                         AcceptableValue<?> v, long timeBucket);
}
```

##### `LALDebugRecorder` (in `analyzer/log-analyzer`, extends `DebugRecorder`)

```java
package org.apache.skywalking.oap.log.analyzer.v2.dsldebug;

public interface LALDebugRecorder extends DebugRecorder {
    void appendText(String rule, ExecutionContext ctx);
    void appendParser(String rule, ExecutionContext ctx);
    void appendExtractor(String rule, ExecutionContext ctx);
    void appendSink(String rule, ExecutionContext ctx, String branch, boolean kept);
    void appendLine(String rule, int sourceLine, ExecutionContext ctx);
    void appendOutputRecord(String rule, ExecutionContext ctx, LALOutputBuilder builder);
    void appendOutputMetric(String rule, ExecutionContext ctx, SampleFamily handedToMal);
}
```

##### `OALDebugRecorder` (in `server-core`, extends `DebugRecorder`)

```java
package org.apache.skywalking.oap.server.core.dsldebug;

public interface OALDebugRecorder extends DebugRecorder {
    void appendSource(String rule, int sourceLine, String fromClauseText, ISource src);
    void appendFilter(String rule, int sourceLine, String filterClauseText,
                      ISource src, String left, String op, String right, boolean kept);
    void appendBuild(String rule, Metrics built);
    void appendAggregation(String rule, int sourceLine, String aggregationClauseText,
                           Metrics afterEntryFn);
    void appendEmit(String rule, Metrics readyForL1);
}
```

##### Implementation: one impl class per DSL in `dsl-debugging`

Each session targets one rule which is one DSL. The session's recorder
implements only the corresponding extension interface:

- `MALDebugRecorderImpl implements MALDebugRecorder` — for sessions on `otel-rules` / `log-mal-rules`.
- `LALDebugRecorderImpl implements LALDebugRecorder` — for sessions on `lal`.
- `OALDebugRecorderImpl implements OALDebugRecorder` — for sessions on `oal`.

`server-admin/dsl-debugging` depends on `server-core`,
`analyzer/meter-analyzer`, and `analyzer/log-analyzer`, so it can
construct any of the three impls. The `instanceof` downcast inside the
probe is a compile-time-trivial check on a tagged type — JIT specialises
it after warm-up.

`DebugSession` (in `dsl-debugging`) is the recorder factory plus the
`Map<SessionId, DebugSession>` and `Map<ClientId, SessionId>`
book-keeping from
[Session lifecycle, storage, and memory control](#5-session-lifecycle-storage-and-memory-control).
Serialization happens at capture time inside each `appendXxx`
implementation — payload JSON is built once per probe so polls during
the session return identical bytes every time.

#### 3.3 Install / uninstall — direct `GateHolder` mutation, no per-thread context

There is no analyzer-side wrapper. Sessions are installed and removed
by mutating the per-rule `GateHolder` from the session-management code
in `dsl-debugging`; receiver threads observe the new state via the
volatile `recorders` field on the next probe. The receiver code path
itself is unchanged from today.

**Rule identity is a typed object, not a slash-encoded string.**
A small immutable class lives in `server-core` and is the single
identifier the install/uninstall path uses — every method takes
`RuleKey`, never an encoded `String`:

```java
public record RuleKey(Catalog catalog, String name, String ruleName) {
    public enum Catalog { OTEL_RULES, LOG_MAL_RULES, LAL, OAL }
}
```

`RuleKey.equals/hashCode` are auto-derived from the three fields; no
delimiter, no escaping, no parsing. Each DSL's runtime artifact
exposes its own `RuleKey ruleKey()` method (filled in at codegen
time), so the install path can inspect the live artifact's identity
without re-parsing strings.

**Each `DebugSession` stores the exact `GateHolder` it bound into.**
That lets uninstall remove the recorder from the *original* holder
even if the rule has since been hot-updated and the live artifact now
references a different holder. Without this, install would bind to V1
and uninstall would call `removeRecorder` on V2 (the new live holder),
leaving V1's recorder array and gate stuck on until the old class is
garbage-collected.

```java
public final class DebugSession {
    final SessionId   sessionId;
    final ClientId    clientId;
    final RuleKey     ruleKey;
    final GateHolder  boundHolder;   // ← captured at install time, never updated
    // ... payload, lifecycle state ...
}

// inside DebugSessionRegistry.install(key, clientId, sessionSpec):
GateHolder h = liveHolderFor(key);   // current live holder (see lookup below)
DebugRecorder recorder = newRecorderFor(key, sessionSpec);
DebugSession session = new DebugSession(sessionId, clientId, key, h, ...);
sessions.put(sessionId, session);
clientIdIndex.put(clientId, sessionId);
h.addRecorder(recorder);              // synchronized; auto-flips gate on 0→1

// inside DebugSessionRegistry.uninstall(sessionId):
DebugSession session = sessions.remove(sessionId);
clientIdIndex.remove(session.clientId, sessionId);
session.boundHolder.removeRecorder(session.recorder); // ← original holder
```

Hot-update interactions stay correct:

- Install runs against the live holder of the moment. `session.boundHolder` is
  pinned to that instance.
- A subsequent runtime-rule swap creates a new live holder; the registry's
  next `liveHolderFor(key)` returns the new one. New session installs bind
  to the new holder.
- The old session's `uninstall` (manual stop / retention / replaced-by-client)
  walks through `session.boundHolder` — i.e., the V1 holder — so V1's
  recorder array shrinks correctly and V1's gate flips back to `false` when
  its last recorder leaves. The new live holder is untouched.
- The old V1 class's drainage continues hitting V1's holder; once V1's
  recorder array is empty its gate is `false` and probes are JIT-eliminated
  again on V1 too.

The `liveHolderFor(key)` lookup goes through each DSL's existing
runtime registry — no new shared `Map<RuleKey, GateHolder>`:

```java
private GateHolder liveHolderFor(RuleKey key) {
    switch (key.catalog()) {
        case OTEL_RULES:
        case LOG_MAL_RULES:
            // The meter-analyzer Provider already maintains the live
            // MalExpression instance per rule, replaced on hot-update.
            return malProvider.liveExpression(key).debugHolder();

        case LAL:
            // The log-analyzer Provider already maintains live LalExpression
            // instances per rule, replaced on hot-update.
            return lalProvider.liveExpression(key).debugHolder();

        case OAL:
            // The dispatcher is held by SourceReceiverImpl; per-metric holder
            // lives on it as `gate_<ruleName>` instance field.
            return dispatcherManager.dispatcherFor(key)
                                    .debugHolder(key.ruleName());
    }
    throw new IllegalArgumentException(key.toString());
}
```

No reflection. `addRecorder` / `removeRecorder` are typed methods on
the `GateHolder` instance reachable through each DSL's existing live-
artifact registry — direct method calls. Hot-update for MAL or LAL
replaces the live `MalExpression` / `LalExpression` instance with a
new one carrying its own fresh `GateHolder`; pre-update sessions stay
on the old holder (which the old instance still references) until
their session window ends or the old class drains; post-update
sessions install on the new holder. There is no shared registry to
overwrite mid-flight.

`recorders` is a copy-on-write snapshot — readers iterate whatever
snapshot was visible when they read the volatile reference; writers
publish a new snapshot atomically. The per-rule lock serialises
concurrent install/uninstall against each other (rare events; one
operator-driven action at a time per rule). The receiver hot path takes
no lock and incurs no TL access.

Why no analyzer-side wrapper:

- The receiver doesn't need to know which sessions match the rule —
  the holder *is* the per-rule binding. Probes consult it directly.
- No indirection to add at every analyzer call site (no diff per
  analyzer beyond the codegen change inside the rule's own class).
- Multi-session support is built into the recorder array: install
  appends, uninstall removes; the probe iterates whatever's there. No
  composite-recorder type, no per-thread state to install or
  propagate.

#### 3.4 Generator hook insertion — concrete code diffs

##### MAL — two halves: chain codegen + Analyzer hand-off to MeterSystem

The MAL pipeline splits cleanly across two files. The chain (filter / closure /
scope / tag / downsampling stages) is emitted by `MALMethodChainCodegen`; the
post-chain MeterSystem build + emit lives in `Analyzer.doAnalysis`. The
debugger hooks both halves.

**Chain codegen** ([source][mal-chain]). The current loop emits one
`_var = _var.method(args);` line per stage. The hook is one extra line per
stage:

```java
// before:
for (final MethodCall mc : chain) {
    if (mc.isExtension()) { emitExtensionCall(sb, var, mc); continue; }
    sb.append("  ").append(var).append(" = ")
      .append(var).append('.').append(mc.getName()).append('(');
    emitBuiltinArgs(sb, var, mc);
    sb.append(");\n");
}

// after:
for (final MethodCall mc : chain) {
    if (mc.isExtension()) {
        emitExtensionCall(sb, var, mc);
        emitMalCapture(sb, var, mc, /*op=*/mc.getNamespace() + "::" + mc.getName());
        continue;
    }
    sb.append("  ").append(var).append(" = ")
      .append(var).append('.').append(mc.getName()).append('(');
    emitBuiltinArgs(sb, var, mc);
    sb.append(");\n");
    emitMalCapture(sb, var, mc, mc.getName());
}

private static void emitMalCapture(StringBuilder sb, String var, MethodCall mc) {
    // Emit a literal-string call. `mc.toMalSource()` rebuilds the verbatim
    // MAL fragment from the AST: e.g. "tagEqual('region', 'us-east-1')",
    // ".sum(['svc', 'instance'])", "myext::scale(2.0)". The compiled string
    // literal goes into the constant pool once per generated class.
    sb.append("  ").append(DSL_DEBUG_FQN)
      .append(".MALDebug.captureStage(\"").append(currentRuleName).append("\", ")
      .append(mc.getLine()).append(", \"")
      .append(escapeJava(mc.toMalSource())).append("\", ")
      .append(var).append(");\n");
}
```

`MALDebug.captureInput` is emitted once at the top of `run()` (in
`MALExprCodegen.generate*`, where `_var` first reads from `samples.get(...)`)
with `sourceLine` = the line where the metric reference appeared and
`metricRef` = the reference text (e.g. `node_memory_MemAvailable_bytes`).

Filter (file-level, applied in `Analyzer` *before* `expression.run()`), tag,
binary operations (`* 100`, `/ otherMetric`, etc., emitted by
`MALExprCodegen` as their own `_var = _var.multiply(...)` / `_var.divide(...)`
lines), and built-in chain stages each get a `MALDebug.captureStage(...)` line
carrying their MAL fragment. Scope binding emits `MALDebug.captureScope(...)` with
the `expSuffix` text (e.g. `instance(['host'], Layer.VM)`) plus the freshly
populated `Map<MeterEntity, Sample[]>`. Closure companion classes
(`MALClosureCodegen`) need no changes — the chain stage that owns the
closure (`tag({...})`, `forEach({...})`) already captures it at its method
call line; the closure's own line shows up as the `sourceText` of that
parent stage.

**File-level filter, also in `Analyzer.doAnalysis`** ([source][mal-analyzer]).
The `filter:` block at the top of the YAML file (`{ tags -> tags.job ==
'node-exporter' }`) is applied to the input map *before* `expression.run()`
fires. One probe captures the filtered SampleFamily with the filter's verbatim
text:

```java
// existing:
if (filterExpression != null) {
    input = filterExpression.filter(input);
    if (input.isEmpty()) return;
}

// after — every probe takes the rule's GateHolder as its first argument:
GateHolder DEBUG = expression.debugHolder();   // resolved once per ingest pass
if (filterExpression != null) {
    input = filterExpression.filter(input);
    if (DEBUG.gate) {
        MALDebug.captureFilter(DEBUG, ruleName, fileFilterLine,
            fileFilterSource /* "{ tags -> tags.job == 'node-exporter' }" */,
            SampleFamily.flatten(input) /* lightweight view across the input map */);
    }
    if (input.isEmpty()) return;
}
```

`fileFilterLine` and `fileFilterSource` are stored on the rule definition
when the YAML is parsed; they're per-rule constants, captured once per
ingest pass.

**Post-chain hand-off in `Analyzer.doAnalysis`**. The existing code iterates
`meterSamples` and calls `meterSystem.buildMetrics(...).accept(...)` followed
by `send(...)` (which calls `meterSystem.doStreamingCalculation(...)`). Two
probes wrap the build / emit boundary:

```java
// existing:
meterSamples.forEach((meterEntity, ss) -> {
    generateTraffic(meterEntity);
    switch (metricType) {
        case single:
            AcceptableValue<Long> sv = meterSystem.buildMetrics(metricName, Long.class);
            sv.accept(meterEntity, getValue(ss[0]));
            send(sv, ss[0].getTimestamp());        // ← doStreamingCalculation = L1 entry
            break;
        // ... labeled / histogram / histogramPercentile branches
    }
});

// after — every probe takes the rule's GateHolder as its first argument:
final GateHolder DEBUG = expression.debugHolder();
meterSamples.forEach((meterEntity, ss) -> {
    generateTraffic(meterEntity);
    switch (metricType) {
        case single:
            AcceptableValue<Long> sv = meterSystem.buildMetrics(metricName, Long.class);
            sv.accept(meterEntity, getValue(ss[0]));
            if (DEBUG.gate) {
                MALDebug.captureMeterBuild(DEBUG, ruleName, meterEntity, metricName, "Long", sv);
                MALDebug.captureMeterEmit(DEBUG, ruleName,  meterEntity, metricName, sv,
                    TimeBucket.getMinuteTimeBucket(ss[0].getTimestamp()));
            }
            send(sv, ss[0].getTimestamp());
            break;
        // ... same shape for labeled (DataTable), histogram (BucketedValues),
        //     histogramPercentile (PercentileArgument)
    }
});
```

Both probes fire **before** `send(...)`. Note that `send(...)` itself
mutates `v` by setting `v.timeBucket` immediately before
`doStreamingCalculation`, so at the moment `MALDebug.captureMeterEmit` runs
`v.timeBucket` is still 0. The probe takes the time-bucket as a separate
parameter, computed from the same `ss[0].getTimestamp()` that `send` will
use; the recorder serializes `(v fields, computed timeBucket)` so the UI
sees the exact `(value, timeBucket)` pair that's about to be streamed,
without needing the post-mutation snapshot. The debugger does not follow
the value past `doStreamingCalculation`.

##### LAL — `LALClassGenerator.generateExecuteMethod` + Analyzer-side terminal

LAL's `execute(filterSpec, ctx)` already passes `ctx` explicitly — no
ThreadLocal needed. The codegen places per-block probes inside `execute()`
([source][lal-exec]); the **terminal** typed-output capture lives in
`RecordSinkListener.parse` so it fires once `init(metadata, ...)` has
populated standard fields and right before the listener's `build()`
delegates to `complete(sourceReceiver)`.

```java
// generated execute() body — hooks are gate-guarded and pass `this.DEBUG`:
public final GateHolder DEBUG = new GateHolder("...sha...");

public void execute(FilterSpec filterSpec, ExecutionContext ctx) {
    LalRuntimeHelper h = new LalRuntimeHelper(ctx);
    h.ctx().setOutput(new <OutputType>());
    if (DEBUG.gate) LALDebug.captureText(DEBUG, ruleName, ctx);          // >>> raw body
    filterSpec.json(ctx);
    if (DEBUG.gate) LALDebug.captureParser(DEBUG, ruleName, ctx);        // >>> parsed map
    if (!ctx.shouldAbort()) {
        _extractor(filterSpec.extractor(), h);
        if (DEBUG.gate) LALDebug.captureExtractor(DEBUG, ruleName, ctx); // >>> typed builder mid-flight
    }
    filterSpec.sink(ctx);
    if (DEBUG.gate) LALDebug.captureSink(DEBUG, ruleName, ctx,           // >>> kept / dropped + branch
                                          h.lastSinkBranch(),
                                          !h.lastSinkDropped());
}
```

`h.lastSinkBranch()` / `h.lastSinkDropped()` are tiny additions to
`LalRuntimeHelper` that capture which sink branch (`enforcer` / `sampler` /
`rateLimit` / pass-through) ran last and whether it dropped the record;
they're populated by the existing sink spec methods on the same hot path.

**Terminal record output** is captured one frame up the stack in
`RecordSinkListener` ([source][lal-sink]) — i.e., not in generated bytecode
but in the analyzer wrapper that drives it:

```java
// before:
public LogSinkListener parse(LogMetadata md, Object input, ExecutionContext ctx) {
    if (ctx == null || !(ctx.output() instanceof LALOutputBuilder)) return this;
    builder = ctx.outputAsBuilder();
    builder.init(md, input, moduleManager);
    return this;
}

// after:
public LogSinkListener parse(LogMetadata md, Object input, ExecutionContext ctx) {
    if (ctx == null || !(ctx.output() instanceof LALOutputBuilder)) return this;
    builder = ctx.outputAsBuilder();
    builder.init(md, input, moduleManager);
    GateHolder DEBUG = ctx.lalExpression().debugHolder();   // resolved per call
    if (DEBUG.gate) {
        LALDebug.captureOutputRecord(DEBUG, ruleName, ctx, builder);  // >>> typed builder, before complete()
    }
    return this;
}
```

That captures the **typed** builder (`LogBuilder`, `EnvoyAccessLogBuilder`,
`DatabaseSlowStatementBuilder`, `SampledTraceBuilder`, custom subclasses) with
all standard fields populated. The recorder serializes the class name plus
every populated bean property — the UI shows the operator the actual record
shape that's about to be persisted.

**Metric output** (only present for rules with a `metrics{}` block) fires from
`MetricExtractor.submitMetrics` ([source][lal-extractor]) right after
`SampleFamilyBuilder` produces the family that's about to be handed to MAL:

```java
// inside submitMetrics(...):
final Sample sample = builder.build();
final SampleFamily sampleFamily = SampleFamilyBuilder.newBuilder(sample).build();
GateHolder DEBUG = ctx.lalExpression().debugHolder();
if (DEBUG.gate) {
    LALDebug.captureOutputMetric(DEBUG, ruleName, ctx, sampleFamily);  // >>> handed to MAL
}
// ... existing dispatch into MetricConverts ...
```

The capture fires once whether the family is appended to `ctx.metricsContainer()`
(MAL fanout case) or fed straight to `provider.getMetricConverts().forEach(...)`.
The debugger does **not** chain into the MAL pipeline that consumes this
family — that boundary is the L1-bound hand-off from LAL's perspective, even
though MAL would itself become L1-bound only after its own `meterSystem`
build/emit. From this LAL session's view, MAL is downstream and out of scope.

The two helpers `LALBlockCodegen` and `LALDefCodegen` are unchanged for
block-level capture — block content emission is the same; only the
orchestration in `generateExecuteMethod` adds the capture lines.

**Statement-level capture — always emitted, recorder-side filter.**
LAL codegen **unconditionally** emits one
`LALDebug.captureLine(rule, sourceLine, ctx)` call after every
meaningful AST statement (`OutputFieldAssignment`, `TagAssignment`,
`DefStatement`, `IfBlock` predicate evaluations, `MetricsInline`
blocks). The probes are present in every compiled LAL class regardless
of how the operator will later debug the rule. **There is no
recompile-on-session-install path** — the rule's class is generated
once at boot or runtime-rule apply time, with all probe call sites in
place, and stays that class until a structural runtime-rule update
replaces it.

The session's `granularity` field is purely a **recorder-side filter**:

| Granularity      | Recorder behaviour                                                                                          |
|------------------|-------------------------------------------------------------------------------------------------------------|
| `block` (default)| `appendLalLine(...)` returns immediately — statement records are dropped. Only block-level appends accumulate. |
| `statement`      | `appendLalLine(...)` serializes and appends the record. Block-level appends accumulate alongside.              |

Idle-path cost remains zero — `DEBUG.gate=false` eliminates *all* probe
call sites, statement-level included. Active-path cost in block mode is
slightly higher than the previous design (~7 ns per statement probe to
do the holder load + recorder iteration + recorder's
"granularity != statement → return" check), but the absolute cost is
bounded: a typical LAL extractor block has 10-15 statements, so
~100-150 ns of additional cost per log record on a block-mode session.
Well inside the active-path budget. The
[performance ladder](#36-performance-plan--idle-path-effectively-free-active-path-bounded) updates
to reflect this; idle and capturing rows are unchanged.

**AST plumbing required (one-time, not per session).** Add a
`int sourceLine` field to every relevant statement node in
`LALScriptModel`. The ANTLR listener already has
`ctx.getStart().getLine()` per parsed statement; thread it into the
AST. The codegen reads it when emitting the `LALDebug.captureLine(...)` call.
The bytecode `LineNumberTable` already produced by
`LALClassGenerator.addLineNumberTable` tracks **generated-Java** lines
and is unrelated — `sourceLine` here is the original DSL line in the
YAML so the UI can highlight the corresponding source line side-by-side
with the captured value.

**Concurrency** — multiple sessions of any granularity mix on the same
rule: each registers as one entry on the rule's `GateHolder.recorders`
array (see [Performance plan](#36-performance-plan--idle-path-effectively-free-active-path-bounded)).
Each statement probe iterates the array; recorders running in `block`
mode drop the record; recorders running in `statement` mode keep it.
No cross-session interference; no class swap; no DSLClassLoaderManager
involvement.

##### OAL — FreeMarker template `dispatcher/doMetrics.ftl`

OAL emits one `do<Metric>(source)` method per metric per dispatcher class,
filter as a guard, then `<Metric>Metrics metrics = new ...`, source-field
copy, `metrics.<entryFn>(...)`, then `MetricsStreamProcessor.in(metrics)`
([source][oal-tpl]). Hooks bracket each transition; the final probe fires
**before** the L1-entry `in(...)` call, not after:

The template uses the full per-clause probe signatures (rule, sourceLine,
sourceText, ...) as defined in
[the probe surface](#31-per-dsl-probe-classes--maldebug-laldebug-oaldebug).

###### OAL AST deliverable — source-position fields don't exist today

The current OAL model classes do not carry source positions:

- `oap-server/oal-rt/src/main/java/.../v2/model/SourceReference.java` —
  has no `location` / `sourceLine` field.
- `oap-server/oal-rt/src/main/java/.../v2/model/FunctionCall.java` —
  same; no source location.
- `oap-server/oal-rt/src/main/java/.../v2/model/FilterExpression.java` —
  declares a `SourceLocation location` field (line 42), but
  `OALListenerV2.enterFilterStatement` (line 99) does not populate it
  on construction; the field is currently always null.

This SWIP makes the source-position carrying an **explicit deliverable**
of Phase 3 (OAL single-node):

1. Add `int sourceLine` (or `SourceLocation`) to `SourceReference` and
   `FunctionCall` model classes (analogous to `FilterExpression`'s
   existing field).
2. Update `OALListenerV2.enterSourceReference` / `enterFunctionCall` /
   `enterFilterStatement` to populate the source position from
   `ctx.getStart().getLine()` on each AST node it constructs.
3. Add `String sourceText()` accessors that pretty-print the verbatim
   OAL fragment from each node (`from(Endpoint.*)`, `.cpm()`,
   `.filter(detectPoint == DetectPoint.SERVER)`, etc.) — used by the
   FreeMarker model for `${from.sourceText}`, `${f.sourceText}`,
   `${aggregationSourceText}`.

These are pure model-side additions; no behavioural change to the OAL
runtime, no breaking change to the parser. The SourceReference /
FunctionCall changes are tracked under Phase 3 in
[Phased delivery](#10-phased-delivery).

Once the AST changes land, `${from.line}`, `${aggregationLine}`,
`${f.line}` populate from `MetricDefinition.SourceReference` /
`FilterExpression` / `FunctionCall` nodes through the FreeMarker
model.

Each per-metric holder lives on the dispatcher singleton as
`gate_<metricsName>` (declared once in the dispatcher's class body
alongside the other instance fields the template emits). The
`do<MetricsName>` body reads that holder and passes it as the first arg
to every `OALDebug.captureXxx` call:

```ftl
private void do${metricsName}(${sourcePackage}${from.sourceName} source) {
    if (gate_${metricsName}.gate) {
        org.apache.skywalking.oap.server.core.dsldebug.OALDebug
            .captureSource(gate_${metricsName}, "${metricsName}",
                ${from.line}, "${from.sourceText}", source);
    }

<#if filters.filterExpressions??>
    <#list filters.filterExpressions as f>
        if (!new ${f.expressionObject}().match(${f.left}, ${f.right})) {
            if (gate_${metricsName}.gate) {
                org.apache.skywalking.oap.server.core.dsldebug.OALDebug
                    .captureFilter(gate_${metricsName}, "${metricsName}",
                        ${f.line}, "${f.sourceText}",
                        source, "${f.left}", "${f.opSymbol}", "${f.right}", false);
            }
            return;
        }
        if (gate_${metricsName}.gate) {
            org.apache.skywalking.oap.server.core.dsldebug.OALDebug
                .captureFilter(gate_${metricsName}, "${metricsName}",
                    ${f.line}, "${f.sourceText}",
                    source, "${f.left}", "${f.opSymbol}", "${f.right}", true);
        }
    </#list>
</#if>

    ${metricsClassPackage}${metricsName}Metrics metrics =
        new ${metricsClassPackage}${metricsName}Metrics();
    <#-- ... existing source-field copy ... -->
    if (gate_${metricsName}.gate) {
        org.apache.skywalking.oap.server.core.dsldebug.OALDebug
            .captureBuild(gate_${metricsName}, "${metricsName}", metrics);
    }

    metrics.${entryMethod.methodName}(${entryMethod.argsList});
    if (gate_${metricsName}.gate) {
        org.apache.skywalking.oap.server.core.dsldebug.OALDebug
            .captureAggregation(gate_${metricsName}, "${metricsName}",
                ${aggregationLine}, "${aggregationSourceText}", metrics);
        org.apache.skywalking.oap.server.core.dsldebug.OALDebug
            .captureEmit(gate_${metricsName}, "${metricsName}", metrics);
    }
    org.apache.skywalking.oap.server.core.analysis.worker
        .MetricsStreamProcessor.getInstance().in(metrics);
}
```

`OALDebug.captureEmit` fires immediately **before** `MetricsStreamProcessor.in(metrics)`
— that call is L1 entry, and the debugger stops there. The dispatcher template
`dispatch.ftl` is unchanged: it just routes a `Source` row to its `do<Metric>`
methods; capture is per-metric, inside each method.

No receiver-side wrapping is needed for OAL — the dispatcher
singleton holds per-metric `GateHolder` instance fields
(`gate_endpoint_cpm`, `gate_endpoint_resp_time`, …), and each
`do<Metric>(source)` reads its own metric's holder directly. Same
self-resolving probe model as MAL and LAL; the receiver code path
(`SourceReceiverImpl.receive` → `DispatcherManager.forward` →
`dispatcher.dispatch(source)` → the generated `do<Metric>(source)`)
is unchanged.

[mal-chain]: ../../../oap-server/analyzer/meter-analyzer/src/main/java/org/apache/skywalking/oap/meter/analyzer/v2/compiler/MALMethodChainCodegen.java
[mal-analyzer]: ../../../oap-server/analyzer/meter-analyzer/src/main/java/org/apache/skywalking/oap/meter/analyzer/v2/Analyzer.java
[lal-exec]: ../../../oap-server/analyzer/log-analyzer/src/main/java/org/apache/skywalking/oap/log/analyzer/v2/compiler/LALClassGenerator.java
[lal-sink]: ../../../oap-server/analyzer/log-analyzer/src/main/java/org/apache/skywalking/oap/log/analyzer/v2/provider/log/listener/RecordSinkListener.java
[lal-extractor]: ../../../oap-server/analyzer/log-analyzer/src/main/java/org/apache/skywalking/oap/log/analyzer/v2/dsl/spec/extractor/MetricExtractor.java
[oal-tpl]: ../../../oap-server/oal-rt/src/main/resources/code-templates-v2/dispatcher/doMetrics.ftl

#### 3.5 Recap of what changes vs. what doesn't

| Component                               | Change                                                                                                                                                                                    |
|-----------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| MAL / LAL / OAL grammar + AST           | unchanged                                                                                                                                                                                 |
| MAL / LAL / OAL generator orchestrators | one extra capture-emission helper called per stage                                                                                                                                        |
| Generated bytecode                      | adds N static-method calls to the per-DSL probe class (`MALDebug.capture*` / `LALDebug.capture*` / `OALDebug.capture*`)                                                                  |
| Receiver / analyzer call sites          | unchanged — probes self-resolve through the per-rule `GateHolder`                                                                                                                          |
| New plumbing                            | `GateHolder`, `DebugRecorder` marker, `OALDebug` + `OALDebugRecorder` (in `server-core`); `MALDebug` + `MALDebugRecorder` (in `analyzer/meter-analyzer`); `LALDebug` + `LALDebugRecorder` (in `analyzer/log-analyzer`); recorder impls + `DebugSessionRegistry` + REST handler (in `dsl-debugging`); HTTP host shared via `admin-server`. **No new shared `Map<RuleKey, GateHolder>` registry** — sessions reach holders through each DSL's existing live-artifact lookup. |
| Behavior when no session is installed   | per-rule `GateHolder` (singleton) gates every probe via its `gate` volatile-boolean field; JIT eliminates the call when off (~1 ns idle). See [Performance plan](#36-performance-plan--idle-path-effectively-free-active-path-bounded). |

Compile-failure handling is unchanged — capture hooks only fire if the rule
compiled successfully. The chat clarification is honored: "if it is parsed,
there is no grammar issue".

#### 3.6 Performance plan — idle path effectively free, active path bounded

Capture lives in the data hot path. A receiver-side LAL pipeline can run
100K log lines/sec; a busy OAL dispatcher fans dozens of `Source` rows per
trace. Probe overhead must be near-zero when no session is active and
strictly bounded when one is. Three layers handle this:

##### Layer 1 — per-rule-instance `GateHolder`, gate flipped through interface methods

Every generated rule artifact carries a `GateHolder` field constructed
fresh per class generation. The holder is bound to *that specific
class instance*, not shared across class versions. Concrete shape per
DSL:

- **MAL / LAL**: `public final GateHolder DEBUG = new GateHolder("...sha...");`
  on the generated rule class. The class has a singleton runtime
  instance (`MalExpression` / `LalExpression`); the holder lives as an
  instance field on that singleton.
- **OAL**: per-metric `public final GateHolder gate_<metric> = new GateHolder("...sha...");`
  on the dispatcher singleton (one holder per metric the dispatcher
  routes to). The metric class itself is per-source-row, so the holder
  cannot live there; the dispatcher is the per-rule control point.

In both cases, hot-update replacing the rule's runtime artifact also
replaces the holder — the new artifact's constructor builds a brand-new
`GateHolder` with the new content's hash. The old holder lingers as
long as the old artifact is referenced by drainage and session state;
its `contentHash` is `final` and never overwritten, so records captured
on the old artifact carry the right (old) hash.

The DSL codegen wraps every probe call site:

```java
// emitted in MALMethodChainCodegen / LALClassGenerator / OAL FreeMarker:
if (DEBUG.gate) {
    MALDebug.captureStage(DEBUG, ruleName, sourceLine, sourceText, _var);
}
```

`DEBUG.gate` is a public volatile boolean field on the holder, default
`false`. When `DEBUG.gate == false` (the steady state on every node not
currently debugging the rule), the JIT compiles the branch as
never-taken: the capture call site becomes effectively dead code, and
the only runtime cost per probe is one volatile-load + branch (~1 ns).
On a hot LAL rule with statement-level probes and no session running,
the per-log-line idle overhead is `~15 probes × 1 ns = 15 ns` — well
under the 1% budget.

**Session install flips the gate through `GateHolder`'s typed
interface methods — never reflection.** The session code calls
`holder.addRecorder(r)`; the holder atomically appends the recorder to
its CoW array and flips `gate=true` if it just transitioned 0→1:

```java
// inside DebugSessionRegistry.install(ruleKey, recorder):
GateHolder h = liveHolderFor(key);
h.addRecorder(recorder);          // synchronized; auto-flips gate on 0→1
```

Symmetric on uninstall: `h.removeRecorder(recorder)` removes the entry
and flips `gate=false` on 1→0. The holder owns its own state
transitions; callers don't have to remember to flip the gate
separately.

The `gate` field is one writer-side write per 0↔1 transition, many
reader-side loads per probe; `volatile` is the correct primitive. The
`recorders` array is replaced via volatile write on each
addRecorder/removeRecorder, so receiver threads always see a coherent
snapshot.

##### Layer 2 — holder-resident recorder array, fan-out at the probe

When `gate` is `true`, the probe call site fires and reaches
the relevant per-DSL probe (`MALDebug.captureXxx` / `LALDebug.captureXxx`
/ `OALDebug.captureXxx`, depending on which DSL's class is calling).
That method reads the holder's
`recorders` array and iterates:

```java
DebugRecorder[] rs = h.recorders;            // one volatile load
for (int i = 0; i < rs.length; i++) {
    DebugRecorder r = rs[i];
    if (!r.isCaptured()) body.accept(r);     // each recorder appends to its session
}
```

No `ThreadLocal`. No per-thread state to install or maintain. Multiple
sessions on the same rule sit in the array and each receives the
capture independently. The receiver thread doesn't know or care which
sessions exist — it just iterates the holder's snapshot.

##### Layer 3 — per-session `isCaptured()` short-circuit

Once a session hits its record cap, `isCaptured()` flips to `true`. The
fan-out loop still iterates over that recorder, but skips the
serialization. With a single capped session in a 1-recorder array, the
total cost stays in the few-ns range (gate + array read + isCaptured
check + skip).

##### Cost summary

| State                                                        | Cost per probe                                                          |
|--------------------------------------------------------------|-------------------------------------------------------------------------|
| Idle — no session anywhere on this rule (`gate=false`)       | ~1 ns (volatile load + branch; JIT eliminates the call site)            |
| Active — 1 recorder, all captured (cap reached)              | ~7 ns (gate + array load + isCaptured + skip)                           |
| Active — N recorders, all captured                           | ~7 ns + ~3 ns per extra recorder                                        |
| Active — capturing (real append, single recorder)            | ~500–2000 ns (JSON serialize + append)                                  |
| Active — capturing across N recorders                        | N × ~500–2000 ns (independent per recorder)                             |

Active-path serialization uses per-DSL streaming renderers — direct
`StringBuilder` walks of `SampleFamily` / `ExecutionContext` /
`LALOutputBuilder` / `Metrics` rather than reflection-based JSON. CPU is
bounded by the per-record figure in the table above; memory is bounded
by the two-dimension session contract (`MAX_ACTIVE_SESSIONS=200` per
node × per-session `recordCap`, see
[Session lifecycle, storage, and memory control](#5-session-lifecycle-storage-and-memory-control)).

##### Bytecode + memory overhead when not running

| Resource                    | Cost                                                                              |
|-----------------------------|-----------------------------------------------------------------------------------|
| Generated bytecode per rule | ~20 bytes per probe call site (gate-load + branch + invokestatic + args)          |
| Constant-pool entries       | One String entry per `sourceText` literal; one int per `sourceLine`               |
| Per-class field             | One static-final `GateHolder` reference (instance carries `volatile boolean gate`, `volatile String contentHash`, `volatile DebugRecorder[] recorders`) |
| Session registry state      | Empty when no sessions exist — no idle memory                                     |

Across the bundled rule set (~50 MAL rules, ~14 LAL rules, ~12 OAL files
× many rules each), the cumulative bytecode increase is small — single-
digit MiB. The OAP image size is dominated by other concerns; this is
not a constraint.

##### Validation gate

JMH benchmarks added under `oap-server-bench`:

- `DSLDebugIdleBench` — measures probe call cost with no session installed.
  CI gate: regress build if mean cost exceeds **2× the no-probe baseline**
  on the same JIT.
- `DSLDebugActiveBench` — measures probe call cost during an active
  session. CI gate: regress build if mean cost exceeds **5 μs per
  capture** at the median.
- Tail-latency check (`p99 < 50 μs per capture`) under contended load
  (8 receiver threads × hot rule).

Benchmarks run on every PR that touches `oap-server/server-core/.../dsldebug/`,
the DSL generators, or the analyzer call sites. A fail blocks merge.

##### Why not a single global gate

A single global "any session active anywhere" flag would also eliminate
idle cost — but it would force every receiver thread to enter the
fan-out path (read the recorders array, iterate, even if empty) the
moment any session on any rule exists anywhere. With per-class gates,
only threads processing the *specifically debugged* rule pay attention;
everyone else stays on the JIT-eliminated call-site path.

##### What this means for deployment

- Operators leaving DSL-debugging enabled in `application.yml` with no
  active session pay essentially zero — confirmed by the JMH idle bench.
- Operators starting a session on rule X impose cost only on the receiver
  threads that processed rule X's data — and only for the duration of
  the session capture window plus retention.
- A misbehaving operator script that opens many sessions concurrently is
  bounded by `maxActiveSessions: 200` (rejected with HTTP 429 above the
  cap), and even at the cap the cost on any one rule's hot path is
  identical to one session.

#### 3.7 Implementation strategy — hard-coded probes, hand-written serializers, no reflection

The capture pipeline has three layers; only the call sites are auto-
emitted by codegen, the rest is hand-written Java in `server-core` and
`dsl-debugging` with no reflection on the hot path.

##### Layer 1 — call sites: codegen-emitted constants

Probe call sites (`if (DEBUG.gate) {MAL,LAL,OAL}Debug.captureXxx(DEBUG, ...)`) are
**hard-coded as bytecode constants** by the existing DSL codegens:

| Codegen owner                               | Emits                                                                                   |
|---------------------------------------------|-----------------------------------------------------------------------------------------|
| `MALMethodChainCodegen.emitChainStatements` | one `MALDebug.captureStage(...)` per chain stage; `sourceText` = `mc.toMalSource()` written into the constant pool as a String literal |
| `MALExprCodegen` (input read)                | one `MALDebug.captureInput(...)` at each `samples.get(...)` site                              |
| `Analyzer.doAnalysis` (hand-written, not codegen) | the `MALDebug.captureFilter` / `MALDebug.captureScope` / `MALDebug.captureDownsample` / `MALDebug.captureMeterBuild` / `MALDebug.captureMeterEmit` calls |
| `LALClassGenerator.generateExecuteMethod`   | per-block `LALDebug.captureText` / `LALDebug.captureParser` / `LALDebug.captureExtractor` / `LALDebug.captureSink` |
| `LALBlockCodegen.generate*Statement`         | `LALDebug.captureLine(...)` after each statement (always emitted; recorder filters by `granularity`) |
| `RecordSinkListener.parse` (hand-written)    | `LALDebug.captureOutputRecord(...)` after `builder.init(...)`                                  |
| `MetricExtractor.submitMetrics` (hand-written) | `LALDebug.captureOutputMetric(...)` after `SampleFamilyBuilder.build()`                       |
| `dispatcher/doMetrics.ftl` FreeMarker        | per-clause OAL captures with line numbers from `MetricDefinition.SourceReference` etc.  |

Each emitted call expands to 4-8 bytecode instructions plus the
constant-pool entries for the literal arguments (rule name, source line,
source text). No reflection, no method-handle lookup, no allocation —
the JVM's invokestatic instruction dispatches directly to
the matching per-DSL probe class.

##### Layer 2 — `{MAL,LAL,OAL}Debug.captureXxx`: hand-written probes in their DSL's home module

Each probe is a few-line static method that fans out to the holder's
recorder array:

```java
public static void MALDebug.captureStage(GateHolder h, String rule, int sourceLine,
                                    String sourceText, SampleFamily f) {
    DebugRecorder[] rs = h.recorders;       // one volatile load
    for (int i = 0; i < rs.length; i++) {
        DebugRecorder r = rs[i];
        if (!r.isCaptured()) r.appendMalStage(rule, sourceLine, sourceText, f);
    }
}
```

No reflection. No `ThreadLocal`. No allocation on the no-active-recorder
path (the receiver hits this method only when `h.gate == true`, which
implies `recorders.length >= 1`; the array load + length check is
unconditional). When all recorders are `isCaptured()`, the loop runs
through them in a few ns.

##### Layer 3 — `DebugRecorderImpl.appendXxx`: hand-written per-DSL serializers in `dsl-debugging`

Each `appendXxx` method has a hand-written JSON serializer for *that
specific artifact type*. No `ObjectMapper`, no reflection, no annotation
processing — direct `StringBuilder` writes that know the artifact's
shape:

```java
@Override public void appendMalStage(String rule, int line, String text, SampleFamily f) {
    if (++recordCount > recordCap) { captured = true; return; }
    StringBuilder sb = new StringBuilder(256);
    sb.append("{\"kind\":\"stage\",\"line\":").append(line)
      .append(",\"text\":").append(jsonEscape(text))
      .append(",\"samples\":[");
    Sample[] samples = f.samples;
    for (int i = 0; i < samples.length; i++) {
        if (i > 0) sb.append(',');
        appendSampleJson(sb, samples[i]);   // hand-written walk of labels + value
    }
    sb.append("]}");
    serializedRecords.add(sb.toString());
}
```

`appendSampleJson`, `appendMeterEntityJson`, `appendBucketedValuesJson`,
`appendLALOutputBuilderJson` (per known subclass), `appendISourceJson`
(per scope) are all hand-written walks of the artifact's fields. Each
artifact type has a known shape; we don't need to discover fields at
runtime.

For `LALOutputBuilder` subclasses (`LogBuilder`, `EnvoyAccessLogBuilder`,
`DatabaseSlowStatementBuilder`, `SampledTraceBuilder`, custom subclasses
contributed by `LALSourceTypeProvider`), the recorder dispatches via a
small `Map<Class<? extends LALOutputBuilder>, BuilderSerializer>`
populated at `dsl-debugging`'s startup. Custom builders contribute their
own serializer through the `LALSourceTypeProvider` SPI alongside their
existing `outputType()` contribution. If no serializer is registered the
recorder falls back to a generic Java-bean walk that uses
`PropertyDescriptor` reflection — only on this fallback path, not
hot-path.

##### Why no reflection on the hot path

A `Jackson ObjectMapper.writeValueAsString(family)` would be 5-10× slower
than the hand-written walk for `SampleFamily` because it would walk
Sample's reflection metadata per call. With statement-level LAL active
on a busy rule (8 records × 15 statements × 60s window), that adds up to
visible CPU. Hand-written serializers keep us at ~500-1000 ns per
capture, which is the active-path budget validated by the JMH active
bench.

##### Per-rule-instance `GateHolder` — codegen mechanics

Every generated rule artifact gets one `GateHolder` field constructed
in the class's constructor (or constructor-like initializer for
dispatcher metric fields). The holder's `contentHash` is supplied as a
constructor argument and stored in a `final` field, so it is fixed
for that holder's entire lifetime:

```java
// MAL / LAL — instance field on the rule's singleton runtime class:
public final GateHolder DEBUG = new GateHolder(/* contentHash = */ "7c3a91…");

// OAL — per-metric instance field on the dispatcher singleton:
public final GateHolder gate_endpoint_cpm = new GateHolder("8a21f0…");
```

The `contentHash` literal is computed at codegen time (SHA-256 over the
content bytes the codegen consumed) and embedded directly in the
generated bytecode's constant pool. There is no separate registry of
holders, no `Map<RuleKey, GateHolder>` to dedup or replace, no
`computeIfAbsent` semantics — each generated class instance simply
constructs its own holder once.

Session-install code finds the holder by asking each DSL's existing
live-artifact registry (`MalExpression` / `LalExpression` /
dispatcher singleton) for the rule and calling `.debugHolder(...)` —
typed method, not reflection. Mutation goes through the holder's typed
interface methods (`addRecorder`, `removeRecorder`); the gate
transitions are encapsulated inside those methods, so callers can't
forget to flip the gate. **No `Class.getField(...)`, no
`setBoolean(null, ...)`, no reflection anywhere on the install /
uninstall path.**

Per-rule holder access on the hot path is the cheapest possible flag:
a single `final` reference load + one volatile boolean read + branch.
The `final` `DEBUG` reference is JIT-inlinable across the receiver's
hot loop; the volatile read on `gate` is the actual gate check.

##### Net summary

- **Codegen emits**: probe call sites + `public final GateHolder DEBUG = new GateHolder("...sha...")` (or `gate_<metric> = new GateHolder(...)` per metric, for OAL dispatchers) + the `RuleKey ruleKey()` accessor. Hard-coded
  bytecode literals; nothing computed at runtime.
- **`{MAL,LAL,OAL}Debug` static methods**: hand-written probes. No
  reflection.
- **`DebugRecorderImpl` per-DSL appenders**: hand-written JSON
  serializers per artifact type. No reflection on the hot path; only
  fallback for unknown LAL output-builder subclasses.
- **All allocation on the active path**: one `StringBuilder` per
  capture (recycled? — no, fresh per capture; the per-capture lifetime
  is short and ZGC/G1 handle it efficiently).

#### 3.8 Interaction with rule changes — content-hash stamping

Runtime-rule hot-update can replace a rule's class while a debug session
is active. The captured `sourceLine` / `sourceText` describe the **rule
content as of the class that produced them** — once the class swaps, a
later record's source positions refer to a different content. The UI
needs to know which content each record describes so it can render the
right YAML alongside.

The capture **carries the rule's content hash directly**, computed by
the DSL engine at content-load time. The hash is **not persisted
anywhere** — not in the runtime-rule DB row, not in any registry on
disk. It lives only in memory: in the `GateHolder`, in the generated
class's constant pool (as a String literal), and in the captured
records that quote it. Storing it persistently would just denormalize
content that's already in hand whenever it matters.

Static rules loaded from disk at boot have no DB row at all — their
content is read once, hashed in memory, and the hash flows into the
generated class. Runtime-managed rules have a DB row carrying the rule
content (status, content bytes, updateTime); the runtime-rule applier
already loads that content into memory before compiling, so the hash
falls out for free from the same in-memory bytes the codegen consumes.
Both paths converge on the same SHA-256 from the same content with no
extra DB column required.

##### Extended `GateHolder`

The `GateHolder` introduced in the
[Performance plan](#36-performance-plan--idle-path-effectively-free-active-path-bounded)
grows one field:

The `GateHolder` carries `contentHash` as a `final` constructor argument
(see [the canonical `GateHolder` definition](#2-capture-mechanism--per-rule-instance-gateholder-no-threadlocal-no-shared-registry)).

Codegen emits the constructor call at the rule's instance-field
initializer:

```java
// MAL / LAL — generated rule class:
public final GateHolder DEBUG = new GateHolder(/* contentHash = */ "7c3a91…");

// OAL — generated dispatcher:
public final GateHolder gate_endpoint_cpm = new GateHolder("8a21f0…");
```

`contentHash` is computed once per class generation:

| Origin                      | Where the hash is computed                                                                              |
|-----------------------------|---------------------------------------------------------------------------------------------------------|
| Static-disk rules at boot   | the analyzer reads the YAML from disk; codegen hashes those bytes locally before generating the class   |
| Runtime-rule managed rules  | the runtime-rule applier loads content from the DB; codegen hashes those bytes locally — same code path  |

Both paths feed the codegen the same content bytes; codegen runs
`SHA-256` over them once and embeds the hex digest as a String literal
in the constant pool of the generated class. The DB row stores **only
the content** (plus status + updateTime); the hash is recomputed
whenever the content is loaded into memory, never read from a stored
column.

The runtime-rule plugin's `/runtime/rule/list` response includes a
`contentHash` field for operator visibility — that response field is
computed on demand from the in-memory content the plugin already has,
not read from a persisted column. Same convention here: derive on
load, don't store.

##### What the recorder writes

Every captured record reads `DEBUG.contentHash` at append time and stores
it in the per-record JSON:

```json
{
  "id": "r17",
  "contentHash": "7c3a91…",
  "stages": [
    {"sourceLine": 7, "sourceText": "tagEqual('region', 'us-east-1')", "samples": [...]},
    {"sourceLine": 7, "sourceText": "* 100", "samples": [...]}
  ]
}
```

A `volatile` field read is one cheap load; this is added to the active
path's per-capture cost (already 500-2000 ns), not the idle path.

##### What the session payload returns

Top-level alongside `nodes[]`, the session response includes a
`ruleSnapshots` map keyed by content hash:

```json
{
  "sessionId": "d-abc",
  "ruleSnapshots": {
    "7c3a91…": {
      "capturedFirstAt": 1745329200000,
      "content": "filter: ...\nexpSuffix: ...\nmetricsRules:\n  - ..."
    },
    "b1d402…": {
      "capturedFirstAt": 1745329485000,
      "content": "..."
    }
  },
  "nodes": [...]
}
```

Snapshots are populated by the `DebugSessionRegistry` the first time it
sees a record carrying a hash it hasn't recorded yet. The content for a
snapshot comes from the rule's source — runtime-rule's DB row for
managed rules, the in-memory disk-read for static rules — neither of
which is on the hot path.

##### One hash, three places it surfaces — all the same value

The same `contentHash` appears in three places, computed by the same
SHA-256 over the same content bytes:

| Place                                                                  | Field                                          | Purpose                                                       |
|------------------------------------------------------------------------|------------------------------------------------|---------------------------------------------------------------|
| Each captured record in a debug-session response                       | `record.contentHash`                            | Identify which rule version emitted this record               |
| `ruleSnapshots[hash]` in the same response                              | the map key + `.content`                        | Carry the rule text that matches `record.contentHash`         |
| Rule-content management APIs (existing `/runtime/rule/list`, new `/runtime/oal/files`, etc.) | `contentHash` field on each rule        | Same identifier the UI uses to correlate captures with rules  |

The UI's invariant: `record.contentHash == ruleSnapshots[h].contentHash
== /runtime/.../list.contentHash` for the same applied content. UI
correlation is "look up by SHA prefix"; no parallel identifier scheme
to map across.

##### How hot-update interacts

| Runtime-rule action                | Holder state                                | Captured records                                   |
|------------------------------------|---------------------------------------------|----------------------------------------------------|
| Filter-only update (same identity) | new `<clinit>` writes new `contentHash`; gate stays as-is | Pre-swap records keep their original hash; post-swap records carry the new hash |
| Structural update                  | same — new hash on the holder                | Records before vs. after the swap reference different snapshots; UI labels the boundary |
| LAL block ↔ statement recompile    | content unchanged — `contentHash` re-computes to the same SHA                | All records carry one hash; `ruleSnapshots` has one entry |
| Inactivate / delete                | holder removed; sessions auto-terminate     | Final session payload still has all snapshots already captured |
| Static-only rule (no runtime-rule) | hash set once at boot; no further swaps    | Single-hash session, single snapshot               |

##### Why content-hash, not a counter

- **Static rules have no DB-side revision number.** Computing a SHA at
  boot is the only honest identity the recorder can stamp.
- **Idempotence.** A re-apply with byte-identical content produces the
  same SHA → same `ruleSnapshots` entry → no spurious "rule changed"
  boundary in the UI when nothing changed.
- **Cluster agreement is guaranteed by the algorithm, not by
  coordination.** Hashing rules:
  - Algorithm: **SHA-256** over the rule's content bytes, hex-encoded
    lowercase (64 chars).
  - Byte source: the **exact bytes the DSL engine compiled** —
    UTF-8-decoded YAML/`.oal` text from disk for static rules, the
    UTF-8 bytes from the runtime-rule DB row for managed rules. **No
    normalization** (no whitespace stripping, no line-ending rewrites,
    no comment removal). Two nodes that compile the same content bytes
    deterministically produce the same digest.
  - Convergence path: runtime-rule's reconciler already converges
    content cluster-wide within one tick (≤ 30 s). Within that
    convergence window peers may briefly carry different content for
    the same rule — and therefore different hashes. The session
    payload's `nodes[]` array shows each peer's hash explicitly so the
    UI sees the divergence honestly during the window; after
    convergence all peers' captures stamp the same hash.
  - Cross-version cluster: if OAP binaries differ across nodes, their
    shipped static-rule bytes can differ, producing different hashes
    for the "same" static rule. This is the same issue runtime-rule
    documents as out of scope; deployment discipline is the fix.
- **No counter to wrap, drift, or be surprising.** The hash IS the
  identifier.

#### 3.9 Permanent injection disable — boot-time bytecode opt-out

This flag is **additive** to the existing runtime controls — not a
replacement. Three orthogonal layers govern whether captures can run, in
order of escalating strength:

| Layer                                          | Type                              | What it controls                                                                          |
|------------------------------------------------|-----------------------------------|-------------------------------------------------------------------------------------------|
| `admin-server.enabled`                            | runtime (require restart to load) | Whether the admin HTTP server runs at all. If false, no REST endpoints exist.             |
| `dsl-debugging.enabled`                         | runtime                            | Whether the dsl-debugging module installs. If false, sessions can't be created.           |
| Per-rule `GateHolder.gate`                       | runtime                            | Whether captures fire on this rule for an active session. Flipped on session install/end. |
| **`dsl-debugging.injectionEnabled` (NEW)**     | **boot-time codegen**              | **Whether probe call sites are emitted into generated bytecode at all.**                  |

The first three already exist (or are introduced earlier in this SWIP)
and remain valid — they handle the normal "operator pauses or disables
the feature" cases without forcing a restart or a re-codegen. The fourth
is what's new in this section: a stronger, more permanent guarantee for
deployments that want **no probe call sites in the bytecode at all**.

Why such a flag is useful even when the runtime flags exist:

- High-assurance / regulated environments where any debug
  instrumentation in production-loaded bytecode is a no-go regardless
  of whether it would currently fire.
- Defense in depth — even an admin who somehow flipped a `GateHolder`
  cannot trigger captures because there are no call sites to fire.
- Performance-paranoid sites that want absolute zero overhead past JIT
  elimination (the `~1 ns volatile load` of the runtime-gate idle path
  is not "absolute zero").

A boot-time configuration flag governs this:

```yaml
dsl-debugging:
  default:
    # When false, the DSL codegens emit MAL/LAL/OAL classes WITHOUT any
    # MALDebug / LALDebug / OALDebug call sites and WITHOUT the GateHolder static
    # field. Generated bytecode is byte-identical to a build without
    # SWIP-13. Default is true (capability preserved). Once set false at
    # boot, debug captures cannot be enabled until restart with the flag
    # back to true.
    injectionEnabled: ${SW_DSL_DEBUGGING_INJECTION_ENABLED:true}
```

**What changes when `injectionEnabled: false`:**

| Aspect                                                | `injectionEnabled: true` (default)                          | `injectionEnabled: false`                                          |
|-------------------------------------------------------|-------------------------------------------------------------|--------------------------------------------------------------------|
| Generated class — chain-stage call sites              | `if (DEBUG.gate) MALDebug.captureStage(...)`             | absent — line not emitted                                          |
| Generated class — `GateHolder DEBUG` static field      | present, registered via `<clinit>`                          | absent                                                             |
| live-artifact lookup (`liveHolderFor(key)`)            | resolves to a real holder                                    | unused — feature isn't installed                                   |
| `DebugSessionRegistry.install(...)` (mutates holder)    | normal install path                                         | `DebugSessionRegistry` is unbound; install rejected with 503     |
| `POST /dsl-debugging/session`                          | accepts and starts a session                                 | rejects with `503 injection_disabled` and a body explaining why    |
| Existing telemetry / data-path behaviour              | identical                                                    | identical                                                          |

**The flag is boot-time only, by design.** Flipping it at runtime would
require regenerating every MAL/LAL/OAL class to either add or strip the
probe call sites, and the runtime-rule reload infrastructure works on
content changes, not on codegen-flag changes. Operators changing the
flag must restart OAP for it to take effect. This is enforced by the
provider: at startup the flag is read once into a `final boolean
injectionEnabled` and threaded into every DSL generator's `GenCtx`. No
hot-update path exists, no API mutates it.

##### Status visibility — `GET /dsl-debugging/status`

A new admin endpoint on `admin-server` exposes the current posture so
operators / CI / compliance auditors can confirm a node's state without
parsing YAML:

| Method | Path                       | Effect                                                 |
|--------|----------------------------|--------------------------------------------------------|
| GET    | `/dsl-debugging/status`    | Return the current injection + session-runtime state   |

Response shape:

```json
{
  "adminHostEnabled":        true,
  "dslDebuggingEnabled":     true,
  "injectionEnabled":        false,
  "injectionEnabledSource":  "SW_DSL_DEBUGGING_INJECTION_ENABLED=false (boot)",
  "sessionsAcceptingNewRequests": false,
  "activeSessions": 0,
  "maxActiveSessions": 200,
  "ruleClassesWithProbes": 0,
  "ruleClassesTotal": 87
}
```

Field semantics:

- `injectionEnabled` — the boot-resolved value of the flag. Always
  reflects what the codegen actually did.
- `injectionEnabledSource` — human-readable provenance: which env var or
  YAML key set the value, and that it was resolved at boot.
- `sessionsAcceptingNewRequests` — `false` whenever **any** of the four
  layers above (`admin-server.enabled`, `dsl-debugging.enabled`, all rule
  `GateHolder` references, or `injectionEnabled`) puts the system into
  a state that can't actually capture. The flag composes the runtime
  posture into a single yes/no for clients.
- `ruleClassesWithProbes` / `ruleClassesTotal` — quick visual confirmation
  that the codegen actually applied the flag. With
  `injectionEnabled: false` the first count is 0; with `true` it equals
  the total. Disagreement signals a bug or partial reload.

`GET /dsl-debugging/status` is read-only, returns immediately, has no
session-cap interaction, and is safe to scrape at high frequency from
monitoring tools.

##### Cluster behaviour

`injectionEnabled` is per-node — it's a boot-time codegen choice, not a
clustered configuration. A cluster running with mixed
`injectionEnabled` values has some nodes that can capture and others
that cannot. The `nodes[]` array in a session response surfaces this
honestly: nodes with `injectionEnabled: false` contribute
`{status: "injection_disabled"}` slices instead of records. Operators
who need uniform behaviour set the flag uniformly across the cluster
(deployment discipline; the SWIP doesn't synchronise it).

#### 3.10 Security — out of scope; delegated to the deployment

The capture API is read-side and can expose sensitive payloads (raw
log bodies, parsed maps, extracted fields) when the operator debugs
the LAL surface. **Securing access is out of scope for this SWIP.**
`admin-server` ships unauthenticated this iteration; the deployment is
responsible for putting it behind a sidecar / gateway / IP allow-list.
SWIP-13 does not introduce auth, redaction, or masking primitives —
those concerns live in the dedicated *Admin API* documentation
(see [`docs/en/setup/backend/admin-api/`](../setup/backend/admin-api/readme.md)),
which carries the security notice for `admin-server`, the runtime-rule
update API, and the DSL debug API together. The notice is the single
canonical place operators should read before opening the admin port to
their cluster.

### 4. REST surfaces on the shared `admin-server`

`admin-server` is the runtime / on-demand admin surface. It hosts exactly
two concerns in this SWIP — runtime-rule (existing, migrated onto the
shared server) and DSL debugging (new). They share the same Armeria
server, the same admin port (default 17128), and the same security
posture (no auth in this release, gateway-protect, ships disabled by
default):

| Subsection                                                                                   | Purpose                                                                                | Origin                                                                  |
|----------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| [4.1 OAL read-only management API](#41-oal-read-only-management-api--the-catalog-the-debugger-picks-from) | List loaded `.oal` files / rules so the OAL debugger has a picker                       | NEW — this SWIP                                                          |
| [4.2 DSL debug session API](#42-dsl-debug-session-api)                                       | Start / poll / stop debug sessions across MAL / LAL / OAL                              | NEW — this SWIP                                                          |
| existing `/runtime/rule/*` (migrated)                                                        | Runtime-rule hot-update endpoints, hosted on admin-server post-migration                 | MIGRATED — runtime-rule plugin's own HTTP server is removed             |

**Not on `admin-server`** (intentionally): the `status-query-plugin`
endpoints `GET /status/config/ttl` and `GET /debugging/config/dump`,
plus all `/debugging/query/*` step-through helpers, stay where they are
on the query port (default 12800). They are released APIs serving a
read-only telemetry-inspection audience and we do not move them. The
`admin-server` URI namespace is reserved for write/admin endpoints
(runtime-rule writes, dsl-debugging session control). Operators who
want config inspection in addition to admin operations open both ports
on their gateway, exactly as today.

#### 4.1 OAL read-only management API — the catalog the debugger picks from

Today MAL and LAL rules are runtime-managed (the runtime-rule plugin
exposes `/runtime/rule/list`), but **OAL rules are not** — they are loaded
from `oap-server/server-starter/src/main/resources/oal/*.oal` at boot and
have no runtime listing API. That gap blocks the OAL debugger: the
operator has to pick "one OAL rule line" to sample, and there is currently
no way for the UI or `swctl` to discover what rules exist without reading
the source `.oal` files off disk.

This SWIP closes that gap with a **read-only** OAL management API.
Read-only because OAL hot-update (add/update/inactivate/delete at runtime)
is a much larger feature with its own classloader-isolation, alarm-reset,
and cluster-coordination concerns — that is explicitly **out of scope**
here, scoped for a future SWIP. What lands now is just the listing,
sufficient to drive the OAL debugger's rule picker.

The API lives in a new small module `oal-management` (or as a
sub-package inside `oal-rt` — the implementer picks based on dependency
hygiene), registers its handler on `admin-server`, and is structured so that
adding write endpoints later is purely additive.

| Method | Path                            | Effect                                                                                                                                            |
|--------|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| GET    | `/runtime/oal/files`            | List loaded `.oal` files. One row per file: `{ name, path, ruleCount, status, contentHash }`                                                      |
| GET    | `/runtime/oal/files/{name}`     | Single file detail: `{ name, path, content, rules: [...] }` where `content` is the raw `.oal` text and `rules` are the parsed rule definitions    |
| GET    | `/runtime/oal/rules`            | Flat list of every rule across every file: `[{ file, ruleName, line, sourceScope, expression, function, filters }, ...]`                          |
| GET    | `/runtime/oal/rules/{ruleName}` | Single rule detail: `{ file, ruleName, line, sourceScope, expression, function, filters, persistedMetricName }`                                   |

Response shape per rule:

```json
{
  "file": "core",
  "ruleName": "endpoint_cpm",
  "line": 14,
  "sourceScope": "Endpoint",
  "expression": "endpoint_cpm = from(Endpoint.*).cpm();",
  "function": "cpm",
  "filters": [],
  "persistedMetricName": "endpoint_cpm",
  "contentHash": "8a21f0…"
}
```

`contentHash` is the same SHA-256 the debugger stamps on captured records
([content-hash stamping](#38-interaction-with-rule-changes--content-hash-stamping)) — UI
matches `record.contentHash` against the `contentHash` returned here to
fetch the right content for rendering. The hash is computed on demand
from the in-memory file content the OAL engine already holds; not stored
in the DB.

The fields come from the existing `oal-rt` parsing pipeline (parser →
`MetricDefinition` → `MetricDefinitionEnricher` → `CodeGenModel`),
extended with `contentHash` carried alongside on the
`OALRuleRegistry.OalFileSnapshot` record (defined below).

###### `OALRuleRegistry` — explicit retention contract

**Today's loader does not retain the parsed structures**:
[`OALEngineV2.start`](../../../oap-server/oal-rt/src/main/java/org/apache/skywalking/oal/v2/OALEngineV2.java)
parses, enriches, generates classes, and lets the local `OALScriptParserV2`
and `List<CodeGenModel>` go out of scope.
[`OALEngineLoaderService`](../../../oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/oal/rt/OALEngineLoaderService.java)
only stores the `OALDefine` set; the parsed AST is discarded after class
generation. Naively re-parsing on each REST call would work but is fragile
(two engines see different shapes if one diverges) and slow.

This SWIP introduces a small new service in `server-core` that the
loader populates at boot:

```java
package org.apache.skywalking.oap.server.core.oal.rt;

public interface OALRuleRegistry extends Service {
    /** Snapshot loaded at boot; updated only by future OAL hot-update. */
    List<OalFileSnapshot> files();
    Optional<OalFileSnapshot> file(String fileName);
    Optional<OalRuleSnapshot> rule(String fileName, String ruleName);

    record OalFileSnapshot(
        String name,                     // "core"
        String path,                     // "core.oal"
        String content,                  // raw .oal text
        String contentHash,              // SHA-256 of `content` bytes — matches captures (see content-hash stamping section)
        List<OalRuleSnapshot> rules,
        Status status                    // LOADED | DISABLED | COMPILE_FAILED
    ) {}

    record OalRuleSnapshot(
        String file, String ruleName, int line,
        String sourceScope,              // e.g. "Endpoint"
        String expression,               // verbatim line(s)
        String function,                 // "cpm" | "longAvg" | "apdex" | …
        List<FilterSnapshot> filters,
        String persistedMetricName,
        String contentHash               // copy of file's contentHash for convenience
    ) {}
}
```

`OALEngineV2.start()` calls `registry.put(file, snapshot)` after class
generation succeeds. Memory cost is bounded by the OAL rule set (~200
rules across ~12 files = a few hundred KiB of String + small objects);
retained for the process lifetime.

When OAL hot-update lands in a future SWIP, the same registry receives
updates through the same `put()` path — the read API doesn't change.
The DSLDebuggingProvider's REST handler reads from this registry; no
re-parsing on the request path.

##### How the debugger picks a rule

OAL debugging requires the operator to pick `(file, ruleName)` because OAL
captures sample source rows for **one** rule line, not the whole file. The
flow is:

1. UI calls `GET /runtime/oal/rules` to populate the picker.
2. Operator picks a rule (e.g. `endpoint_cpm` from `core.oal`).
3. UI calls `POST /dsl-debugging/session` with
   `catalog: "oal"`, `name: "core"` (the file), `ruleName: "endpoint_cpm"`.
4. Backend appends the recorder to that rule's `GateHolder.recorders`
   array and flips its `gate` if it was the first session — only that
   rule's `do<Metric>(source)` probes capture.

For MAL and LAL the picker is fed by the existing
`/runtime/rule/list` (since those catalogs are already runtime-managed).
The OAL picker is the only one that needs this new API.

##### Phase-0 scope reminder

In this SWIP we ship **only the GET endpoints listed above**. POSTs that
would mutate OAL state are deferred to a follow-up SWIP. The path prefix
`/runtime/oal/*` is reserved so that future write endpoints (hot-update,
inactivate, delete) can land without a URL break.

#### 4.2 DSL debug session API

REST routes live in the new `dsl-debugging` module and register on
`admin-server`'s Armeria server. URI prefix is `/dsl-debugging/*` — clean
and self-describing, no overload of the query port's existing
`/debugging/*` prefix.

| Method | Path                                                                                                | Body (optional JSON)                                                          | Effect                                                                                                                              |
|--------|-----------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| POST   | `/dsl-debugging/session?catalog=&name=&ruleName=&clientId=&granularity=`                            | `{ recordCap?, retentionMillis?, granularity?: "block" \| "statement" }`      | Start session; **also stops any prior session bound to `clientId`**; broadcast install to peers; return `{ sessionId, createdAt, retentionDeadline, installed: {created, total}, peers[], priorCleanup }` |
| GET    | `/dsl-debugging/session/{id}`                                                                       | —                                                                             | Return aggregated payload (see [data contract](#1-the-data-contract-returned-to-the-ui)). 404 `session_not_found` if no node knows the id. |
| POST   | `/dsl-debugging/session/{id}/stop`                                                                  | —                                                                             | Force close; idempotent.                                                                                                            |
| GET    | `/dsl-debugging/sessions`                                                                           | —                                                                             | List active sessions on this node (JSON).                                                                                           |
| GET    | `/dsl-debugging/status`                                                                             | —                                                                             | Module posture: `{module, phase, nodeId, injectionEnabled, activeSessions}`.                                                        |

`clientId` is a stable identifier the client mints **once per debug
context**. The UI decides what counts as a debug context — typically
one per browser tab when the page hosts a single debug widget, or one
per widget when the UI embeds several debug views on the same page.
The CLI mints one per invocation. The server doesn't model the tab /
page / widget distinction; it only enforces "one `clientId` →
at most one active session, and a new POST replaces the prior". See
[Session lifecycle, storage, and memory control](#5-session-lifecycle-storage-and-memory-control)
for how it interacts with the lifecycle.

All catalogs require `(catalog, name, ruleName)` — the receiver returns
`400 missing_param` otherwise. For OAL sessions, `name` is the `.oal`
file (without extension) and `ruleName` is picked from `/runtime/oal/rules`.
For MAL sessions, `name` is the rule file under `metricPrefix` (e.g.
`vm`) and `ruleName` is the metric name within the file (e.g.
`vm_cpu_total_percentage`). For LAL sessions, `name` is the layer-keyed
rule file (e.g. `default`) and `ruleName` is the rule name within. All
three tuples are queryable via `/runtime/rule/list` (and OAL via
`/runtime/oal/rules`).

Response payload is shaped per-DSL exactly as the
[data contract](#1-the-data-contract-returned-to-the-ui) describes; the
receiving node merges its own slice with peer slices into one `nodes[]`
array under the top-level shape so the UI can show "captured 4 of 6 nodes
responded · oap-03 timed out".

Security: `admin-server` inherits and centralises the security caveats already
applied to the runtime-rule REST endpoints — no auth in this release, the
host module ships **disabled by default**, must be gateway-protected, must
not be exposed to the public internet. Runtime-rule, OAL management, and
DSL-debugging routes are all gated by the same enable flag on `admin-server`.
No new auth mechanism is introduced.

#### `admin-server` — what the shared module provides

```
oap-server/server-admin/admin-server/                 # NEW (under new server-admin/ parent)
  src/main/java/.../admin/
    AdminServerModule.java        # ModuleDefine
    AdminServerProvider.java      # ModuleProvider — owns Armeria server, port, host, lifecycle
    AdminRouteRegistry.java     # Service — sub-modules register handler classes
    AdminAuthDocumentation.java # one place to document the no-auth caveat
```

Sub-modules call `registry.register(handlerInstance)` on `prepare()`;
`admin-server` aggregates them into one Armeria server bound at boot. No
sub-module owns its own HTTP server.

#### `dsl-debugging` — what the new feature module provides

```
oap-server/server-admin/dsl-debugging/              # NEW (sibling of admin-server under server-admin/)
  src/main/java/.../dsldebugging/
    DSLDebuggingModule.java     # ModuleDefine
    DSLDebuggingProvider.java   # ModuleProvider — registers REST handler on admin-server
    rest/DSLDebuggingHandler.java   # Armeria @Get/@Post routes for /dsl-debugging/*
    session/DebugSessionRegistry.java  # Map<SessionId, DebugSession> + Map<ClientId, SessionId>
    session/DebugSession.java   # frozen List<String> + lifecycle states
    session/DebugRecorderImpl.java   # implements DebugRecorder, JSON-serialize-on-capture
    bus/InstallDebugSessionRpc.java  # cluster fan-out RPCs
    bus/CollectDebugSamplesRpc.java
    bus/StopDebugSessionRpc.java
    bus/StopByClientIdRpc.java       # cluster-scope clientId cleanup (load-balancer safe)
```

The marker `DebugRecorder` interface, `GateHolder`, `RuleKey`, and the
OAL probe class `OALDebug` live in
`server-core` (so the MAL/LAL/OAL generators can reference them without
pulling the whole feature module — keeps the dependency graph clean).
`dsl-debugging` provides the recorder implementation, registry, and REST
host registration.

#### `runtime-rule` migration — module relocation + HTTP server merge

Two moves in one release:

1. **Module relocation.** The existing runtime-rule plugin lives under
   `oap-server/server-receiver-plugin/skywalking-runtime-rule-receiver-plugin/`
   today, but it doesn't receive telemetry data — it serves an admin
   surface. Under this SWIP the module relocates to
   `oap-server/server-admin/runtime-rule/`, becoming a sibling of
   `admin-server` and `dsl-debugging` under the new `server-admin/`
   parent:

   ```
   oap-server/server-admin/                       # NEW Maven sub-module group
     admin-server/                                  # shared Armeria HTTP server
     runtime-rule/                                # MOVED from server-receiver-plugin/
     dsl-debugging/                               # NEW DSL debugging feature
   ```

   The selector key in `application.yml` stays as `receiver-runtime-rule:`
   for backward compatibility (operators upgrading don't have to rename
   the YAML block); the Java package stays as
   `org.apache.skywalking.oap.server.receiver.runtimerule.*` — only the
   Maven module path changes. A future cleanup can rename both, but
   that's not in scope here.

2. **HTTP server merge.** The runtime-rule plugin's own embedded
   Armeria server is removed; its REST handlers register on
   `admin-server`'s shared server. URL paths stay the same:

   ```
   /runtime/rule/addOrUpdate
   /runtime/rule/fix
   /runtime/rule/inactivate
   /runtime/rule/delete
   /runtime/rule/list
   /runtime/rule/dump
   ```

   Existing CLI scripts (`runtime-rule.sh`) and operator muscle memory
   keep working — same routes, same response codes, same body semantics,
   just hosted on the shared HTTP server.

Both moves happen in Phase 0 (see [Phased delivery](#10-phased-delivery)) so
`server-admin/` lands as a coherent group rather than dribbling in
piece by piece.

### 5. Session lifecycle, storage, and memory control

Captured data is **per-session, immutable once captured, and never overwritten**.
Operator stability comes first: once a sample lands in the session, repeated polls
return the same bytes until the session is explicitly stopped or hits its timeout.
There is no ring buffer, no drop-oldest — when the cap is reached, the session
simply stops accepting new samples.

#### Lifecycle states

```
  [absent] ──POST /dsl-debugging/session──► CAPTURING ──cap-or-window──► CAPTURED
                                                │  │                         │
                                                │  └──manual /stop───────────┤
                                                │                             │
                                                └──── timeout ────────────► EXPIRED ──► [absent]
                                                                              ▲
                                                                       CAPTURED + ttl
                              ── new POST with same clientId  ⇒ prior session forced to EXPIRED ──
```

- **CAPTURING** — session is installed; capture hooks append samples. Polling is
  allowed and returns the partial payload accumulated so far (`status:
  "capturing"`).
- **CAPTURED** — capture is complete (record cap hit, window elapsed, or operator
  called `/stop` mid-capture). The payload is now frozen. Polling returns the
  same bytes on every call (`status: "captured"`).
- **EXPIRED** — the session's timeout fired. Stored payload is dropped from
  memory; further polls return 404. Operator must start a fresh session.

Three events terminate a session and free its memory:

1. **Manual trigger** — `POST /dsl-debugging/session/{id}/stop` (mid-capture or
   post-capture). The session moves directly to EXPIRED on the next sweep —
   manual stop is interpreted as "I have what I need, throw it away".
2. **Implicit cleanup on a new sampling request — cluster-scope.**
   When a `POST /dsl-debugging/session` arrives carrying a `clientId`,
   the receiving node first **broadcasts a `StopByClientId(clientId)`
   RPC to every peer** (including itself) and waits for ack within the
   per-peer fanout deadline. Each node looks up `clientId` in its own
   local `Map<ClientId, SessionId>`; if a prior session is bound, the
   peer terminates it locally (decrements the rule's recorder
   ref-count, drops its payload). Once the broadcast settles (or the
   per-peer deadline expires), the receiving node allocates the new
   session and runs the normal `InstallDebugSession` fan-out.

   The cluster broadcast is **load-balancer safe**: because every node
   receives `StopByClientId(clientId)`, it doesn't matter which node
   the load balancer routes the next POST to — the previous session
   bound to that `clientId` (wherever it lives) gets terminated before
   the new one is allocated. Without this, a load balancer that bounced
   between nodes would leave the prior session alive on its original
   node until retention timeout, and the active-session-cap math would
   silently double-count.

   Implementation note: the broadcast is **best-effort** with a short
   deadline (default 2 s, same as runtime-rule's `Suspend` fanout). A
   peer that doesn't ack within the deadline is logged in the
   response's `peers[]` array. **No durable management-storage marker
   is introduced** — the prior session on the missed peer self-cleans
   up via the same retention timeout that handles forgotten sessions
   in general (default 5 min from creation, hard cap 1 hour). The
   trade is honest: under partition the active-session count on the
   missed peer is slightly inflated (by the prior session) until
   retention elapses, but the peer's own `MAX_ACTIVE_SESSIONS = 200`
   cap protects it from accumulating unbounded stragglers.
3. **Timeout** — see the cap table below.

A session that has finished capturing (`CAPTURED`) is **not** auto-discarded just
because the capture window ended. It lingers until either the operator stops it
or the timeout fires, so the UI can poll without racing against a fast
disappearance.

#### Caps

| Cap                                  | Default                              | Mechanism                                                              |
|--------------------------------------|--------------------------------------|------------------------------------------------------------------------|
| **Max active sessions per node**     | **200**                              | hard ceiling; `POST /dsl-debugging/session` returns 429 too_many_sessions when full |
| **Records per session**              | **default 100, hard cap 100**        | recorder stops appending once the count is hit and moves the session to CAPTURED. Out-of-range request returns 400 invalid_limits. The cap is deliberately small — operators inspect a handful of executions, not a paginated firehose; per-session heap stays at ~1 MiB and the rendered UI page stays readable. |
| Capture window (retention)           | default 5 min, hard cap 1 hour       | per-session retention timeout; sweeper drops the payload at window end |
| Capture-call cost when idle          | one volatile-load + branch (gate)    | JIT eliminates the call site when gate is false                        |

The capture surface is bounded by **two** dimensions only: how many
sessions can be active concurrently, and how many records each session
can retain. There is intentionally **no per-session byte cap and no
per-record byte budget** — operators sizing for memory look at
`maxActiveSessions × recordCap` and the per-DSL average payload size.
JSON serialization for the wire happens at probe time so polls return
the same bytes every time and the retention sweeper can free them on a
fixed schedule, but the byte total is reported (not enforced) so
operators can verify their own budget assumptions against real
captures.

#### Why 200 active sessions is the right ceiling

200 sessions is a defensible upper bound for an OAP node. With the
hard `recordCap = 100` and a typical per-record JSON payload of
~10 KiB (MAL is the largest of the three), the worst-case footprint
across all active sessions on one node is roughly:

```
worst-case heap ≈ MAX_ACTIVE_SESSIONS × MAX_RECORD_CAP × per-record-bytes
                ≈ 200 × 100 × 10 KiB ≈ 200 MiB (theoretical)
```

That's the worst-case product; realistic usage is well below it
because most captures stop on retention (5 min default) or operator
stop long before hitting `recordCap`, and most records are several
KiB rather than the worst-case 10. The 200 default leaves
headroom for many concurrent operators (one debug context = one
sessionId; the UI maintains a single session per debug widget and
reuses it for polls) without letting a runaway script exhaust the
heap. Operators tuning for tighter heap budgets stop sessions sooner
or lower per-session `recordCap` in the request body — the per-node
ceiling itself is fixed by `MAX_ACTIVE_SESSIONS`.

The session-id is the unit of accounting; the **clientId** is the unit of
deduplication. The UI is expected to:

- Mint a stable `clientId` per debug context — typically one per browser
  tab when the page hosts a single debug widget; one per widget when
  the page embeds multiple. UUID stored in `sessionStorage` (per-tab) or
  in a per-widget keyed map on the page.
- Send `clientId` on every `POST /dsl-debugging/session`. The server cleans up
  the prior session for that `clientId` before allocating a new one, so a
  forgetful operator who clicks Start sampling repeatedly does not exhaust the
  active-session ceiling — they hold one slot at a time.
- Poll the returned `sessionId` until the user navigates away or hits **Stop**.
- Send `POST /dsl-debugging/session/{id}/stop` on tab close (best-effort
  `beforeunload`).

A future CLI follows the same pattern: `start` allocates one session under a
stable client ID, `tail` reuses it, `stop` releases it. Stale sessions that
the client forgot to stop are reaped by the retention timeout, and any new
`start` from the same client implicitly cleans up the prior one regardless.

#### Configuration block — `application.yml`

Two top-level blocks: `admin-server` (the shared HTTP server) and
`dsl-debugging` (the feature). `admin-server` is also where the existing
runtime-rule plugin reads its host/port now — the HTTP-server keys move
out of `receiver-runtime-rule:` and into `admin-server:`. The runtime-rule
plugin keeps only its non-HTTP knobs (reconciler interval, self-heal
threshold).

The structure is **one shared block + two feature blocks on top of it**:

```
admin-server:           ← shared HTTP server (host, port, security posture)
  ↑
  ├─ receiver-runtime-rule:   ← feature 1 (DSL hot-update). Routes register on admin-server.
  └─ dsl-debugging:            ← feature 2 (DSL debug sessions). Routes register on admin-server.
```

Either feature requires `admin-server.selector=default`. **All three
selectors default to empty (disabled).** Operators explicitly opt in
to whichever surface they want; out-of-the-box OAP starts with no
admin port, no debug feature, no runtime-rule.

The "fail fast if a feature is enabled without admin-server" guarantee
uses the **OAP module system's existing `requiredModules()`
mechanism** — no new SWIP machinery needed:

```java
// in DSLDebuggingProvider:
@Override public String[] requiredModules() {
    return new String[] { CoreModule.NAME, AdminServerModule.NAME };
}

// in RuntimeRuleProvider (after Phase 0 migration):
@Override public String[] requiredModules() {
    return new String[] { CoreModule.NAME, AdminServerModule.NAME, ... };
}
```

`ModuleManager.bootstrap()` walks every loaded provider, resolves
`requiredModules()` against the provider set, and fails the OAP boot
with a `ModuleNotFoundException` if any required module is absent. So:

| Operator config                                                        | Boot result                                                                  |
|------------------------------------------------------------------------|------------------------------------------------------------------------------|
| All three selectors empty (default)                                    | OAP starts; no admin port; no debug; no runtime-rule.                        |
| `admin-server=default`, `dsl-debugging=` empty                          | OAP starts; admin-server runs (with no consumers); idle.                     |
| `admin-server=default`, `dsl-debugging=default`                         | OAP starts; both bind; routes register on admin-server.                      |
| `admin-server=` empty, `dsl-debugging=default`                          | **Boot fails fast** with `ModuleNotFoundException: admin-server` — operator sees the error and adds `SW_ADMIN_SERVER=default`. |
| `admin-server=` empty, `receiver-runtime-rule=default`                  | Same — boot fails fast with the same exception path.                         |

This matches every other receiver / admin module in OAP that depends
on shared infrastructure (every receiver depends on `CoreModule` the
same way; every storage-using module depends on `StorageModule`, etc.).
SWIP-13 is reusing the established mechanism, not introducing a new one.

```yaml
# Shared HTTP server for admin / write APIs (runtime-rule, dsl-debugging).
# DISABLED BY DEFAULT. Enable when any consumer is needed.
# SECURITY NOTICE: no authentication this iteration — gateway-protect
# with IP allow-lists and never expose to the public internet.
admin-server:
  selector: ${SW_ADMIN_SERVER:-}
  default:
    host: ${SW_ADMIN_SERVER_HOST:0.0.0.0}
    port: ${SW_ADMIN_SERVER_PORT:17128}
    contextPath: ${SW_ADMIN_SERVER_CONTEXT_PATH:/}
    idleTimeOut: ${SW_ADMIN_SERVER_IDLE_TIMEOUT:30000}
    acceptQueueSize: ${SW_ADMIN_SERVER_QUEUE_SIZE:0}
    httpMaxRequestHeaderSize: ${SW_ADMIN_SERVER_HTTP_MAX_REQUEST_HEADER_SIZE:8192}

# Runtime-rule plugin — non-HTTP knobs only after SWIP-13.
# HTTP server config moves to `admin-server:` above. Enabling
# receiver-runtime-rule requires admin-server to also be enabled
# (selector=default); startup fails with a clear error otherwise.
receiver-runtime-rule:
  selector: ${SW_RECEIVER_RUNTIME_RULE:-}
  default:
    refreshRulesPeriod: ${SW_RECEIVER_RUNTIME_RULE_REFRESH_RULES_PERIOD:30}
    selfHealThresholdSeconds: ${SW_RECEIVER_RUNTIME_RULE_SELF_HEAL_THRESHOLD_SECONDS:60}

dsl-debugging:
  selector: ${SW_DSL_DEBUGGING:-}
  default:
    # Boot-time codegen switch — enable / disable per-rule probe call sites
    # in the generated MAL / LAL / OAL bytecode. Default true once the
    # module is enabled; set false to keep the REST surface but emit
    # bytecode byte-identical to a build without SWIP-13.
    injectionEnabled: ${SW_DSL_DEBUGGING_INJECTION_ENABLED:true}
```

The active-session ceiling and per-session record cap are NOT operator
knobs — they are hard-coded SWIP contract values:

- `MAX_ACTIVE_SESSIONS = 200` per node — `POST /dsl-debugging/session`
  returns `429 too_many_sessions` once the count is reached. The
  receiving node also runs the prior-session cleanup pass for the
  supplied `clientId` BEFORE counting toward this ceiling, so a single
  client repeatedly clicking Start sampling cannot itself trigger 429.
- `MAX_RECORD_CAP = 100` per session — request bodies asking for more
  return `400 invalid_limits`. The default also resolves to 100 (capped
  by the hard ceiling). Session retention is similarly capped at 1 hour
  (`MAX_RETENTION_MILLIS`).

There is intentionally **no per-session byte cap and no structural
char / sample / label sub-caps**. Operators sizing for memory pressure
look at `MAX_ACTIVE_SESSIONS × recordCap × per-record-payload-size`.
Per-DSL average payload size is reported as `totalBytes` on every GET
response so operators can verify their budget against real captures.

#### Storage location

Per-session payloads live in a process-local `Map<SessionId, DebugSession>`
inside the `dsl-debugging` module, alongside a sibling
`Map<ClientId, SessionId>` index used by the implicit-cleanup path. **No
persistence layer.** Sessions are lost on OAP restart by design — they are
debugging snapshots, not durable artefacts. Operators who need to keep a
result snapshot the response body client-side (the API already returns the
full payload; the CLI's `tail` command saves it to a file).

### 6. Cluster behaviour — best-effort fan-out (no cross-node merge)

OTLP / log / native-trace data lands on whichever OAP receives the push, so a
single-node debug session would only catch the slice of traffic that node owns.
The debugger therefore broadcasts the session install to every peer **so each peer
captures its own slice of L1 parsing**.

Important: the fan-out is **not** an attempt to follow data across nodes. There is no
L2 join in scope — every peer's slice is a self-contained "what arrived here, what
the DSL did with it, what L1 the rule emitted on this node". The aggregator on the
receiving node concatenates per-node slices into a `nodes[]` array; it does **not**
merge or reconcile samples between nodes.

The fan-out rides the **admin-internal gRPC bus** owned by
`admin-server` (default port `17129`); `dsl-debugging` ships
`DSLDebuggingClusterServiceImpl` and registers it alongside
runtime-rule's `RuntimeRuleClusterServiceImpl` on the same admin-
internal port. Four RPC types: `InstallDebugSession` /
`StopDebugSession` / `CollectDebugSamples` / `StopByClientId`. Peer
discovery is handled by `AdminClusterChannelManager`, which dials each
peer's admin-internal port via the cluster module's existing peer
registry. The default per-call timeout is configurable via
`admin-server.internalCommunicationTimeout` (default `5000` ms).

#### Install (POST `/dsl-debugging/session`)

1. **Cluster-scope clientId cleanup first.** Receiving node sweeps any
   locally-bound prior session for the same `clientId` and broadcasts
   `StopByClientId(clientId)` to every peer with the configured
   admin-internal-RPC deadline. Each peer looks up `clientId` in its
   own session map; if found, terminates the bound session locally
   (calls `session.boundHolder.removeRecorder`, drops payload, frees
   active-session slot). The fan-out is best-effort — a missed peer
   is logged in `peers[]`; its stale prior session for that
   `clientId` self-cleans-up via retention timeout. The install does
   not block on slow peers.
2. Receiving node generates the new `sessionId` and computes
   `retentionDeadline = createdAt + retentionMillis`.
3. It iterates the sorted peer list (same view
   `AdminClusterChannelManager` exposes to the runtime-rule
   main-selection logic) and sends
   `InstallDebugSession(sessionId, ruleKey, limits)` to each peer with
   the configured admin-internal-RPC deadline.
4. A peer that does not ack within the deadline is recorded as
   {@code FAILED} for that peer (with the failure reason in `detail`)
   and the fanout continues — no peer can stall the operator.
5. On the receiving node and on every peer that acked `INSTALLED`, the
   session is installed by mutating the rule's `GateHolder`
   (`addRecorder(...)`); receiver threads on that node read the holder
   directly.
6. The receiving node returns `200 { sessionId, createdAt,
   retentionDeadline, granularity, localInstalled, installed:
   {created, total}, peers: [{peer, nodeId, ack: INSTALLED |
   ALREADY_INSTALLED | NOT_LOCAL | TOO_MANY_SESSIONS | REJECTED |
   FAILED, detail?}], priorCleanup: {local: {nodeId, stoppedCount,
   stoppedSessionIds}, peers: [...]} }`. The `installed.created` /
   `installed.total` summary lets the operator see at a glance "session
   live on N of M OAPs"; per-node detail (with each node's id and ack)
   stays in `peers[]`. `priorCleanup` reports every prior session
   stopped during the cluster-scope `clientId` sweep.

If **no peer** acks (full partition), the session still runs on the receiving
node; the response simply reports zero healthy peers. This is the documented
"best effort" contract.

#### Collect (GET `/dsl-debugging/session/{id}`)

1. Receiving node sends `CollectDebugSamples(sessionId)` to every peer with a
   short deadline (default 3 s).
2. Each peer returns its local payload slice (or an empty slice + reason if
   the session never installed there).
3. The receiving node merges slices into the `nodes[]` array under the shape
   from the [data contract](#1-the-data-contract-returned-to-the-ui) and
   returns `200`. Peers that timed out are recorded as
   `nodes[].status = "timeout"` rather than being omitted, so the UI shows
   partial coverage honestly.

#### Stop (POST `/dsl-debugging/session/{id}/stop`)

Best-effort broadcast of `StopDebugSession(sessionId)`; missing acks are logged but
do not block the response. The TTL sweeper is the backstop for a peer that missed
the stop.

#### Why fan-out and not a single main

Unlike runtime-rule, debug sessions are not destructive — duplicate installs are
idempotent (same `sessionId` ⇒ no-op), there is no DDL, and a peer that never gets
the install simply contributes nothing to the result. There is no correctness reason
to serialize through a single main, and the fan-out gives the operator visibility
into traffic on every node.

#### Consistency bounds

| Event                                              | Bound                                                                                                  |
|----------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| Healthy install                                    | every reachable peer receives within fanout deadline (default 2 s)                                     |
| Peer partitioned during install                    | session simply does not capture there; reported in `peers[]`                                           |
| Peer crashes mid-session                           | session expires via retention timeout on remaining nodes; collect reports `node_unreachable`           |
| Operator polls collect during fanout               | aggregator returns partial result; UI shows progress                                                   |
| Two operators install concurrently (different `clientId`s) | independent `sessionId`s, no shared state, independent caps                                    |
| Same client's POSTs split across nodes by load balancer    | new POST broadcasts `StopByClientId(clientId)` to every peer before allocating; reachable peers terminate the prior session immediately; a missed peer's stale session falls out via retention timeout (~10 min); no durable storage marker required |

### 7. UI surface (recap)

The Claude Design handoff already locked the three views:

- **MAL** Waterfall · entity-scope selector · per-stage SampleFamily tables
- **LAL** records-as-columns × blocks-as-rows grid · split `output·log` /
  `output·metric` rows · per-block sampling toggle
- **OAL** rule-name picker fed by `/runtime/oal/rules` · `(maxRows, windowSec)`
  ▶ Start sampling button · 5-stage waterfall (`source · filter · build_metrics ·
  aggregation · emit`) with source-row tables

Plus a "Live debugger" entry in the existing left-nav.

### 8. CLI surface

The shipping CLI for this feature is **`swctl`** (or whatever the eventual
`debugging-dsl` subcommand looks like once the CLI surface is finalized). No
binary script is shipped in the OAP distribution for this feature.

For e2e tests we keep a thin temporary helper (`test/e2e-v2/cases/.../dsl-debug.sh`
or equivalent) so the test harness can drive the API without depending on the
yet-to-land CLI. That helper is internal to the test tree, not packaged.

Operators can also drive the API directly with `curl` against
`/dsl-debugging/*` — the routes are stable and JSON in/out (see
[Session API](#42-dsl-debug-session-api) and
the walkthrough in *General usage docs* below).

### 9. What is explicitly out of scope

- **L2 aggregation, cross-node merge, storage round-trip, query path.** The
  debugger stops at the L1 emit (`store`) stage. End-to-end debugging (L1 → L2 →
  persistence → query → dashboard) is a separate problem and not part of this
  SWIP.
- **DSL chaining across debug sessions.** A LAL rule's `output_metric` flows into
  MAL in production, but a debug session does not transparently follow it. The
  operator picks one DSL per session.
- **Behavioural changes to the data path.** Rule grammar and semantics unchanged.
  DSL compilers' data-path behavior on a non-debug ingest is bytecode-identical
  to today modulo one additional capture call.
- **Storage.** Captured samples never persist; they live in the per-session ring
  buffer until TTL.
- **Runtime-rule hot-update.** Unchanged. The debug plugin is a sibling, not a
  layer.

### 10. Phased delivery

1. **Phase 0 — `admin-server` extraction + read-only OAL management.**
   - Pure refactor portion: stand up the `admin-server` module, migrate
     `receiver-runtime-rule`'s embedded HTTP server onto it, delete the
     old runtime-rule HTTP server class. Endpoints and behaviour
     unchanged.
   - New surface: ship the read-only OAL management endpoints
     (`GET /runtime/oal/files`, `GET /runtime/oal/files/{name}`,
     `GET /runtime/oal/rules`, `GET /runtime/oal/rules/{ruleName}`) on
     `admin-server`. This is the prerequisite for the OAL debugger's rule
     picker (Phase 3).
   - This phase is mergeable on its own — it gives operators a runtime
     view of OAL rules even before any DSL debugging lands — and unblocks
     the later phases.
2. **Phase 1 — MAL single-node.** Add `GateHolder` + `RuleKey` + `DebugRecorder` marker (server-core); add `MALDebug` + `MALDebugRecorder` (analyzer/meter-analyzer);
   `GateHolder` + `RuleKey` in `server-core`. Stand up the
   `dsl-debugging` module
   with single-node `POST/GET/STOP` registered on `admin-server`. Chain
   capture hooks in `MALMethodChainCodegen.emitChainStatements` plus
   file-level filter and post-chain `meter_build` / `meter_emit` hooks
   in `Analyzer.doAnalysis`. UI MAL Waterfall lights up. e2e-only helper
   script under `test/e2e-v2/`; shipped CLI lands later via `swctl`.
3. **Phase 2 — LAL single-node.** Block-level hooks in
   `LALClassGenerator.generateExecuteMethod`; terminal-output capture in
   `RecordSinkListener.parse`; metric-output capture in
   `MetricExtractor.submitMetrics`. Statement-level capture is opt-in via
   `granularity: "statement"` and emits `LALDebug.captureLine(...)` per
   meaningful AST statement under a compile-time flag. UI LAL grid lights
   up.
4. **Phase 3 — OAL single-node.** Per-clause hooks in the OAL FreeMarker
   template `dispatcher/doMetrics.ftl` — `source` / `filter[i]` / `build` /
   `aggregation` / `emit`. UI OAL waterfall lights up. Picker fed by the
   read-only OAL management API shipped in Phase 0.
5. **Phase 4 — Cluster fan-out.** `InstallDebugSession` /
   `CollectDebugSamples` / `StopDebugSession` / `StopByClientId` on the
   admin-internal gRPC bus, aggregation in the receiving node, `nodes[]`
   in the response shape.
6. **Phase 5 — UI polish + docs.** End-to-end e2e test (push → debug
   session → payload assertion),
   `docs/en/setup/backend/dsl-debug-session-api.md`, screenshot
   walkthrough.

Phase 0 is independent. Phases 1-3 then deliver per-DSL value independently;
Phase 4 unlocks cluster usage; Phase 5 lands the ops story.

## Imported Dependencies libs and their licenses

None. Implementation stays inside the existing module set (Javassist for codegen
already depended on, Apache 2.0; Armeria HTTP host reused from `admin-server`,
Apache 2.0; admin-internal gRPC bus reused from `admin-server`).

## Compatibility

> ### Operator-facing change — runtime-rule HTTP keys consolidate onto `admin-server`
>
> This SWIP introduces **one** operator-facing config break, deliberately
> scoped to the recently-shipped runtime-rule preview (PR #13851). No
> released telemetry/query APIs are moved or removed.
>
> | What changes                                                                                                                            | What operators do                                                                                                                          |
> |-----------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
> | Runtime-rule's HTTP-server keys (`receiver-runtime-rule.rest*` / `SW_RECEIVER_RUNTIME_RULE_REST_*`) are removed; their values move to `admin-server:` (`SW_ADMIN_SERVER_*`). The runtime-rule URL paths and the port number (default 17128) are unchanged. | Operators using `SW_RECEIVER_RUNTIME_RULE=default` must also set `SW_ADMIN_SERVER=default`. OAP fails fast at startup if runtime-rule is enabled without admin-server. |
>
> **Released APIs left untouched:**
>
> - `GET /status/config/ttl` stays on the query port (12800) via
>   `status-query-plugin/TTLConfigQueryHandler`.
> - `GET /debugging/config/dump` and the other `/debugging/query/*`
>   step-through endpoints stay on the query port via
>   `status-query-plugin/DebuggingHTTPHandler`.
> - `/runtime/rule/*` URL paths and the runtime-rule admin port (17128)
>   are preserved; only the YAML config keys feeding them change.

- **Storage structure**: no change. Sessions live in memory only.
- **Network protocols**: additive only. New `/dsl-debugging/*` HTTP
  endpoints on the new shared `admin-server` Armeria server (default port
  `17128`). The existing `/runtime/rule/*` endpoints migrate to the same
  server with identical paths and behaviour — operator-visible URLs are
  unchanged. The `status-query-plugin`'s existing query-port `/debugging/*`
  routes (config dump, MQE step-through, trace step-through) are not moved
  and not affected. New admin-internal RPC types `InstallDebugSession` /
  `CollectDebugSamples` / `StopDebugSession` / `StopByClientId` — peers
  running an older version simply do not register the handler and reply
  `unimplemented`; the fanout records this as `install_failed` and
  continues.
- **Configuration — clean migration.** `admin-server` is brand new in this
  SWIP (no prior release; nothing to be backward-compatible with). The
  runtime-rule plugin's HTTP-server keys (`receiver-runtime-rule.restHost`,
  `restPort`, `restContextPath`, `restIdleTimeOut`, `restAcceptQueueSize`,
  `httpMaxRequestHeaderSize` and their `SW_RECEIVER_RUNTIME_RULE_REST_*`
  env vars) are removed in this release; their equivalents move to the
  new `admin-server:` block (`SW_ADMIN_SERVER_*`). The runtime-rule plugin
  keeps only its non-HTTP config (reconciler interval, self-heal
  threshold). Operators upgrading from the runtime-rule preview release
  must add `SW_ADMIN_SERVER=default` (or the YAML equivalent) alongside
  their existing `SW_RECEIVER_RUNTIME_RULE=default`. If
  `receiver-runtime-rule.selector=default` is set without `admin-server`
  enabled, OAP fails fast at startup with an explicit error pointing
  operators at the new block — no silent endpoint disappearance.
- **`docs/en/changes/changes.md`** documents the rename in the upgrade
  notes section so the change is not surprise-discovered.
- **DSL behavior**: bytecode-identical to today on the non-debug path.
- **Wire breakage**: none.

## General usage docs

Two short walkthroughs — first the Phase-0 OAL discovery, then a Phase-1
MAL debug session. Final version will move to
`docs/en/setup/backend/dsl-debug-session-api.md`.

```bash
# 0. The endpoint lives on the shared admin-server port (default 17128) — the
#    same port runtime-rule already uses. admin-server must be enabled in
#    application.yml; ships disabled by default. Same security caveats as
#    runtime-rule (no auth — gateway-protect, never expose publicly).
```

**(a) Browse loaded OAL rules** (Phase 0, read-only):

```bash
# List loaded .oal files.
curl -s http://localhost:17128/runtime/oal/files | jq .
# → [
#     {"name":"core","path":"core.oal","ruleCount":86,"status":"LOADED"},
#     {"name":"browser","path":"browser.oal","ruleCount":14,"status":"LOADED"},
#     ...
#   ]

# Drill into one file's rules.
curl -s http://localhost:17128/runtime/oal/files/core | jq '.rules[0:3]'
# → [
#     {"ruleName":"service_resp_time","line":2,"sourceScope":"Service",
#      "expression":"service_resp_time = from(Service.latency).longAvg();",
#      "function":"longAvg","filters":[]},
#     {"ruleName":"endpoint_cpm","line":14,"sourceScope":"Endpoint", ...},
#     ...
#   ]

# Or pull the flat picker list the UI uses directly.
curl -s http://localhost:17128/runtime/oal/rules | jq 'map(.ruleName)' | head
```

**(b) Run a DSL debug session** (Phase 1, MAL single-node):

```bash
CLIENT_ID=$(uuidgen)   # one per debug context — for the CLI that's typically per shell invocation

# 1. Start a session for the OTEL VM rule. catalog/name/ruleName/clientId go
#    in the query string; the optional JSON body tunes per-session limits.
#    Any prior session bound to $CLIENT_ID is auto-cleaned up before this
#    one is allocated.
curl -s -X POST \
  "http://localhost:17128/dsl-debugging/session?catalog=otel-rules&name=vm&ruleName=vm_cpu_total_percentage&clientId=$CLIENT_ID" \
  -H 'Content-Type: application/json' \
  -d '{"recordCap":1000,"retentionMillis":300000}'
# → {"sessionId":"76b3266a-...",
#    "createdAt":1777967921000,"retentionDeadline":1777968221000,
#    "installed":{"created":3,"total":5},
#    "peers":[{"peer":"10.0.1.12:17129","ack":"INSTALLED",...}, ...],
#    "priorCleanup":{"local":{"stoppedSessionIds":["..."]},"peers":[...]}}

# 2. Poll the result while traffic flows.
curl -s http://localhost:17128/dsl-debugging/session/76b3266a-... | jq .
# → MAL-shaped payload with steps[] and per-step samples[],
#   plus nodes[] showing per-peer capture coverage.

# 3. Stop early (optional — the session also auto-expires at retention TTL,
#    or implicitly when the same clientId starts another session).
curl -s -X POST http://localhost:17128/dsl-debugging/session/76b3266a-.../stop
```

For an OAL debug session (Phase 3), step 1 is preceded by step (a) above —
the operator picks a `ruleName` from `/runtime/oal/rules` and posts
`{"clientId": "...", "catalog": "oal", "name": "core", "ruleName": "endpoint_cpm", ...}`.

The UI `Live debugger` view drives the same endpoints and renders the result with
the Waterfall / grid / waterfall views from the Runtime Rule Admin design.

---

## Appendix — Open questions for review

1. **Per-rule capture cost when idle.** Benchmarked target: ≤ 1 % overhead per stage
   on a no-op `if (DEBUG.gate)` JIT-eliminated path. To be measured under
   `oap-server-bench` during Phase 1.
2. **Cap behavior.** Hitting the record cap or the capture window moves the
   session from CAPTURING to CAPTURED. Per-field truncation does not terminate
   the session — it just shortens the offending value with a `… +N chars
   truncated` marker so the rest of the record remains useful. Captured samples
   are immutable — no ring buffer, no drop-oldest, no overwrite. The payload
   stays stable across polls until the operator stops the session or the
   retention timeout fires. UI surfaces the terminal cause (`reason:
   "record_cap" | "window_elapsed" | "manual_stop"`). Acceptable?
3. **Reaffirming scope.** This proposal is parsing/transformation debugging only —
   bounded by the L1 emit at the tail of each DSL. No L2, no cross-node merge, no
   storage / query round-trip. Anything beyond L1 (e.g. "why did this metric
   appear differently after L2 aggregation?") is a separate problem and stays
   outside this SWIP. Confirm.
