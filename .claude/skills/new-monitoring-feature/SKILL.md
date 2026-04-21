---
name: new-monitoring-feature
description: Add a new monitoring target / layer to SkyWalking OAP. Orients you to the OAL / MAL / LAL / SpanListener / SegmentListener extension points, the UI template + submodule touchpoints, the docs set that must move together, and the cross-cutting traps that don't live in any one skill.
---

# Adding a New Monitoring Feature / Layer

SkyWalking monitoring for a new target (a new `Layer` — mobile OS, gateway, DB, language runtime, …) is composed from the same set of reusable extension points. This skill is the index: which extension point fits which signal, where its contract lives, which docs must be updated together, and which harness-wide traps the extension-specific docs don't cover.

There is one skill per narrow concern. This one is the wiring map.

---

## Working posture

- **Default scope for a new monitoring feature is narrow.** For most new monitoring targets, traces are **re-used as-is** — you do not write or modify a trace analyzer. Metrics and log extensions via OTLP (MAL + LAL) are the common shape. OTLP / Zipkin span-based metrics analysis (`SpanListener`) already exists and only needs a new implementation if your feature introduces genuinely new semantic-convention attributes to extract (e.g. `gen_ai.*`, `mcp.*` for the GenAI layer). Do **not** touch `CommonAnalysisListener` / `RPCAnalysisListener` / `SegmentAnalysisListener` unless the OAP core protocol gains a new `SpanLayer` enum value — details in §2.
- **If you find yourself editing more than this skill suggests, stop and confirm.** Scope creep on trace analysis, span-layer mapping, receiver handlers, or protobuf/proto contracts is a strong signal the framing is wrong. Ask the developer before committing that direction.
- **Verify every new feature locally before pushing to CI.** Compile + checkstyle + license-eye are minimums, but they only tell you the code builds — they do **not** tell you the new MAL rule compiles its closures at startup, the LAL layer:auto dispatch works, the OTLP payload shape matches the MAL labels, the UI template renders against real data, or the layer shows up in `swctl service ly <LAYER>`. Run the new e2e case end-to-end locally against a live OAP (see [run-e2e skill](../run-e2e/SKILL.md)) and fire every verify step with `swctl` by hand at least once. Pushing to CI without a local green run wastes shared CI cycles and slow-loops review.

---

## 0. Register the `Layer` — the feature's entry point

A `Layer` is how OAP slices services / instances / endpoints by data source. **Every new feature needs a new `Layer` enum value.** The UI, storage partitioning, menu navigation, and OAL aggregation all key off it.

**Only one place to edit** — `oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/analysis/Layer.java`. Add a new enum constant with a unique id and `normal` flag. Ids are never reused; pick the next integer. Examples: `IOS(47, true)`, `APISIX(27, true)`, `VIRTUAL_DATABASE(11, false)` for inferred/non-real services.

UI template folders are auto-discovered: `UITemplateInitializer.UI_TEMPLATE_FOLDER` is computed from `Layer.values()` + `"custom"` at class-init time. Drop a `ui-initialized-templates/<layer-name-lowercased>/` folder on disk and the initializer picks it up on the next boot. Missing folders are silently skipped. There is no allowlist to append to.

**Component ID lookup in Java code**: IDs declared in `component-libraries.yml` are loaded at runtime into `ComponentLibraryCatalogService`'s `componentName2Id` map — they are **not** exposed as Java enum constants. To look up by name in listener code, inject the catalog service and resolve once at construction:
```java
IComponentLibraryCatalogService catalog = moduleManager.find(CoreModule.NAME)
    .provider().getService(IComponentLibraryCatalogService.class);
int myComponentId = catalog.getComponentId("My-Component-Name");
```
Cache as an `int` field; runtime comparisons are then plain `componentId == myComponentId`. **Trap:** there is a `ComponentsDefine` class under `skywalking-trace-receiver-plugin/src/test/java/.../mock/ComponentsDefine.java` — it is a test-only mock holding five hand-picked constants (Tomcat, Dubbo, RocketMQ, MongoDB). Do not import or extend it from production code.

Emit the layer from every source object your feature produces:
```java
service.setLayer(Layer.<YOUR_LAYER>);
serviceInstance.setServiceLayer(Layer.<YOUR_LAYER>);
endpoint.setServiceLayer(Layer.<YOUR_LAYER>);
```

Downstream (the core OAL, `service ly <LAYER>` swctl query, topology filters, UI root dashboard's layer selector) all work off this single enum value.

---

## 1. Pick the right extension point

| Signal you want to process | Extension point | Register via |
|---|---|---|
| **SkyWalking native trace segments** (from language agents) | `AnalysisListener` (TraceSegmentListener / EntryAnalysisListener / ExitAnalysisListener / LocalAnalysisListener / FirstAnalysisListener) + factory in `agent-analyzer` | `META-INF/services/org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.AnalysisListenerFactory` |
| **OTLP / Zipkin trace spans** (OTel SDKs, Istio, Envoy, iOS/Android, …) | `SpanListener` in `core.trace` | `META-INF/services/org.apache.skywalking.oap.server.core.trace.SpanListener` |
| **OTel metrics** (Prometheus scrape, Envoy stats, Istio, …) | MAL rules — `otel-rules/<namespace>/*.yaml` | Append the rule path to `enabledOtelMetricsRules` in `application.yml` |
| **Logs** (OTLP, SkyWalking native, Fluent Bit, Envoy ALS) | LAL rules — `lal/<rule>.yaml` | Append to `SW_LOG_LAL_FILES` (env) or `lalFiles` (YAML) under `log-analyzer` |
| **Pre-computed meter / custom meter** (custom agent protocol, meter receiver) | MAL `meter-analyzer-config/*.yaml` | Append to `meter-analyzer-config` entries |
| **Aggregation from Source entities** (service, instance, endpoint, relation) | OAL rules — `oal/*.oal` | Already loaded by `OAL grammar`; just add lines |

All five DSLs (OAL, MAL, LAL, Hierarchy + SpanListener SPI) compile via ANTLR4 + Javassist at startup. Source-of-truth details are in:
- [`docs/en/concepts-and-designs/oal.md`](../../../docs/en/concepts-and-designs/oal.md)
- [`docs/en/concepts-and-designs/mal.md`](../../../docs/en/concepts-and-designs/mal.md)
- [`docs/en/concepts-and-designs/lal.md`](../../../docs/en/concepts-and-designs/lal.md)
- [`docs/en/concepts-and-designs/scope-definitions.md`](../../../docs/en/concepts-and-designs/scope-definitions.md)
- [`oap-server/analyzer/meter-analyzer/CLAUDE.md`](../../../oap-server/analyzer/meter-analyzer/CLAUDE.md) — MAL compiler internals
- [`oap-server/analyzer/log-analyzer/CLAUDE.md`](../../../oap-server/analyzer/log-analyzer/CLAUDE.md) — LAL compiler internals

---

## 2. Trace ingestion — two parallel pipelines

SkyWalking OAP ingests traces through two distinct entry points. Pick based on source.

> **Before writing any trace extension — ask "do I actually need one?"**
>
> For **most new layers you do not touch trace analysis at all.** Segments (native or
> OTLP) flow through the existing pipeline, `RPCAnalysisListener.parseExit` produces
> the outbound `ServiceRelation` edges, the backend-side agent produces the inbound
> metrics via its own `parseEntry`, and the new-layer Service / Instance / Endpoint
> entities come from **MAL + LAL**, not from trace analysis. This is how browser
> (`Layer.BROWSER`), iOS (`Layer.IOS`), mobile mini-programs
> (`Layer.WECHAT_MINI_PROGRAM` / `Layer.ALIPAY_MINI_PROGRAM`), and most
> scraped-metric layers work.
>
> Extend trace analysis **only** when the feature genuinely needs per-span
> interpretation that the existing pipeline cannot express:
>
> - **A new `SpanLayer`** — OAP core protobuf has a new `SpanLayer` enum value that
>   callers of `identifyServiceLayer` / `identifyRemoteServiceLayer` must dispatch on.
>   That means touching `CommonAnalysisListener` / `RPCAnalysisListener`.
> - **A genuinely new signal class not covered by existing listeners** — e.g. the GenAI
>   work added `GenAISpanListener` for LLM / MCP instrumentation because OTLP spans
>   carry semantic-convention attributes (`gen_ai.*`, `mcp.*`) that no prior listener
>   extracted. That is rare.
>
> **Anti-pattern we've been bitten by:** using a componentId mapping in
> `CommonAnalysisListener` to assign a client-side layer to exit-only segments. This
> does not produce inbound metrics anyway (no entry spans → no
> `service_cpm` / `endpoint_cpm` from OAL), and it is orthogonal to the Service entity
> layer (which MAL / LAL sets). Mirror the browser pattern instead: ship `Layer` +
> `component-libraries.yml` + MAL + LAL + dashboards, and leave the listener chain
> alone.

### 2.1 SkyWalking native segments — `AnalysisListener`

**Source protocol**: `apm-protocol/apm-network/src/main/proto/language-agent/Tracing.proto` (`TraceSegmentObject`).

**Receiver**: `oap-server/server-receiver-plugin/skywalking-trace-receiver-plugin`. After wire decoding, segments are handed to `SegmentParserServiceImpl` which fans out to registered `AnalysisListenerFactory` implementations.

**Extension**: implement one or more of
- `FirstAnalysisListener` — per-segment, before per-span phase
- `EntryAnalysisListener` — per-entry-span
- `ExitAnalysisListener` — per-exit-span
- `LocalAnalysisListener` — per-local-span
- `TraceSegmentListener` — whole-segment, after per-span phase

Register the **factory** in `META-INF/services/org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.AnalysisListenerFactory`. Existing examples: `RPCAnalysisListener`, `MultiScopesAnalysisListener`, `SegmentAnalysisListener` in `oap-server/analyzer/agent-analyzer/src/main/java/.../trace/parser/listener/`.

Use when: the data arrives on the SW native wire (language agents — Java, Python, Go, Node.js, .NET, PHP, Lua, etc.).

### 2.2 OTLP / Zipkin spans — `SpanListener`

**Source protocol**: OTLP proto (`opentelemetry/proto/trace/v1/trace.proto`) or Zipkin v2 JSON.

**Receiver**: `oap-server/server-receiver-plugin/otel-receiver-plugin/src/main/java/.../otlp/OpenTelemetryTraceHandler.java` (OTLP) and `oap-server/server-receiver-plugin/zipkin-receiver-plugin/.../ITZipkinTraceCollector` (Zipkin). After decoding, spans are iterated and passed to every registered `SpanListener` through `SpanListenerManager`.

**Extension**: implement `org.apache.skywalking.oap.server.core.trace.SpanListener`:

```java
public interface SpanListener {
    String[] requiredModules();
    void init(ModuleManager moduleManager);
    SpanListenerResult onOTLPSpan(OTLPSpanReader span, Map<String,String> resourceAttributes,
                                  String scopeName, String scopeVersion);
}
```

Register in `META-INF/services/org.apache.skywalking.oap.server.core.trace.SpanListener`. Reference implementations:
- `oap-server/analyzer/ios-analyzer/src/main/java/.../IOSHTTPSpanListener.java` — OTel Swift URLSession → OAL (Service / ServiceInstance / Endpoint)
- `oap-server/analyzer/ios-analyzer/src/main/java/.../IOSMetricKitSpanListener.java` — OTel Swift MetricKit → MAL samples via `OpenTelemetryMetricRequestProcessor.toMeter`
- `oap-server/analyzer/gen-ai-analyzer/src/main/java/.../GenAISpanListener.java` — LLM / MCP instrumentation

Return `SpanListenerResult.builder().shouldPersist(false).build()` to prevent a span being stored as a trace (useful for e.g. 24-hour aggregate spans that are not meaningful as traces).

**When both native and OTLP should feed the same metric**, write your extension against the downstream scope (Service / ServiceInstance / Endpoint + OAL) and emit those entities from **both** a SegmentListener and a SpanListener. The OAL layer is protocol-agnostic.

---

## 3. Metrics — MAL / OAL rules

### 3.1 OAL (for entities already produced by core sources)

`oap-server/server-starter/src/main/resources/oal/core.oal` already defines the standard `service_cpm`, `service_resp_time`, `service_sla`, `endpoint_cpm`, etc. — keyed off the `Service`, `ServiceInstance`, `Endpoint` source scopes. If your new layer emits those entities with the right `Layer`, you get the core metrics for free.

**Who emits those entities matters.** `service_cpm` / `service_resp_time` / `service_sla` / `endpoint_cpm` / `endpoint_resp_time` come from `RPCAnalysisListener.parseEntry` → `callingIn.toService()` / `toEndpoint()`. That only fires on **inbound entry spans**. Client-side / edge layers (browser, iOS, mini-programs) only emit exit spans, so these OAL metrics do **not** populate under those layers — their Service entities are produced by MAL / LAL, and their dashboards use MAL metrics (`meter_<layer>_*`) for load / latency, not `service_cpm` / `service_resp_time`. Don't wire dashboards to OAL-derived metrics that the layer never produces.

Additional OAL rules go in the same file. OAL grammar: `oap-server/oal-grammar/src/main/antlr4/OALParser.g4`. Syntax and examples: [`docs/en/concepts-and-designs/oal.md`](../../../docs/en/concepts-and-designs/oal.md).

### 3.2 MAL (for meter / metric samples)

**File location**: `oap-server/server-starter/src/main/resources/otel-rules/<namespace>/*.yaml` (OTel scrape) or `meter-analyzer-config/*.yaml` (SW native meter).

**Registration** (MANDATORY): append the rule path to the default in `oap-server/server-starter/src/main/resources/application.yml`:
```yaml
receiver-otel:
  default:
    enabledOtelMetricsRules: ${SW_OTEL_RECEIVER_ENABLED_OTEL_METRICS_RULES:"...,<your-namespace>/*"}
```
If you skip this, the rule isn't loaded in the default starter and downstream `converters.forEach(...)` is a silent no-op — samples just disappear.

**Also update** `test/e2e-v2/cases/storage/expected/config-dump.yml`. This e2e diffs `/debugging/config/dump` output against a static file; every default-value change in `application.yml` must mirror here or the storage e2e fails.

**Naming**: keep attribute scope in the metric name. If a metric reads from `foo.bar.foreground.exit_count`, call it `foo_foreground_exit_count`, not `foo_exit_count`. When two attributes (foreground vs background, ingress vs egress) share a theme, keep them as separate distinguishable metrics — don't flatten.

### 3.3 Histogram pitfalls (both OAL and MAL)

- **Per-bucket counts, not cumulative.** `histogram_percentile` accumulates internally. Feeding cumulative counts doubles the accumulator and shifts percentiles up. Each observation contributes `1` to exactly one `le` bucket.
- **`le` label scale.** `SampleFamily.histogram()` rescales `le` by `defaultHistogramBucketUnit.toMillis(1)` (default `SECONDS` ⇒ ×1000). If your raw labels are already in ms, use `SampleFamilyBuilder.newBuilder(samples).defaultHistogramBucketUnit(TimeUnit.MILLISECONDS).build()`.
- **Overflow bucket.** MAL parses `le="Infinity"` as `Double.POSITIVE_INFINITY` → stored as `Long.MAX_VALUE`. If a percentile lands there, the UI renders ≈ 9.2 × 10¹⁸. Prefer a finite sentinel (e.g., 30 s for hang/launch) so outliers remain visible without crashing the chart.
- **`NamingControl`** — every `service` / `serviceInstance` / `endpoint` label that ends up as an entity key must go through `namingControl.formatServiceName()` / `formatInstanceName()` / `formatEndpointName()`. Raw resource attributes (long git-SHA build metadata, path-style endpoints) will otherwise exceed storage limits.

---

## 4. Logs — LAL

**File location**: `oap-server/server-starter/src/main/resources/lal/<feature>.yaml`.

**Registration**: append to `SW_LOG_LAL_FILES` env var, or `log-analyzer.default.lalFiles` in `application.yml`.

**Grammar**: `oap-server/analyzer/log-analyzer/src/main/antlr4/LALParser.g4`. The compiler docs are thorough: [`oap-server/analyzer/log-analyzer/CLAUDE.md`](../../../oap-server/analyzer/log-analyzer/CLAUDE.md) — covers:
- `filter { … }` block with `parsed.*`, `tag()`, `sourceAttribute()`, `log.*`, `def` locals, `extractor {}`, `metrics {}`, `sink {}`
- `layer: auto` mode (script decides layer from source attrs)
- `inputType` / `outputType` SPI (`LALSourceTypeProvider`) to bind typed proto to `parsed.*`
- Test harness at `oap-server/analyzer/log-analyzer/src/test/resources/scripts/lal/test-lal/oap-cases/` — ship YAML + `.data.yaml` pair; `LALScriptExecutionTest` picks them up automatically.

**`layer: auto` gotcha**: the extractor's `layer "X"` assignment must propagate to `LogMetadata.layer` (not just the output builder) — `FilterSpec.doSink` reads metadata to decide drop/route. The current codegen (`LALBlockCodegen.generateFieldToOutput`) handles this correctly; if you extend codegen, preserve that invariant.

---

## 5. UI — templates, submodule, and menu

### 5.1 Dashboard templates

JSON files under `oap-server/server-starter/src/main/resources/ui-initialized-templates/<feature>/`. Conventional fileset:

- `<feature>-root.json` — `ServiceList` widget, layered service index.
- `<feature>-service.json` — per-service dashboard, usually a `Tab` with Overview / Instance / Endpoint / Log sub-tabs.
- `<feature>-instance.json` — per-instance dashboard.
- `<feature>-endpoint.json` — per-endpoint dashboard.
- Menu link in `oap-server/server-starter/src/main/resources/ui-initialized-templates/menu.yaml`.

> **Collaborate with the developer on dashboard design.** Dashboard layout and metric selection are product decisions, not derivable from the MAL rules alone. Before authoring the JSON, check in with the developer on: which metric goes on which panel, which percentiles / aggregations to show, what's on the root list vs. per-service vs. per-instance vs. per-endpoint pages, what stays in the Overview tab vs. a sub-tab, widget sizes / ordering, and whether Trace / Log tabs belong at all (depends on whether the feature emits traces and logs). Then **manually set up the feature against a live OAP** — emit a few real OTLP payloads, open each dashboard in the UI, confirm every widget renders non-empty, and walk the drill-down from root → service → instance → endpoint. Only after that eyes-on pass is the dashboard ready to ship.

Details that always bite:

- **Tab chrome eats ~4 rows.** Set outer `Tab.h = content_max_y + 4` or you get an inner scrollbar on top of the page scrollbar.
- **Widget heights consistent.** Use `h=15` per row for `Line` / `Bar` widgets to match mesh / general peer templates. Don't mix h=12 / h=15 / h=24 across a feature's dashboards.
- **List widgets (ServiceList / InstanceList / EndpointList)** render two pieces per cell:
  - `expressions` → the aggregated summary number (right side). Required. Wrap raw metrics in `avg(...)` / `sum(...)`. Omitting it leaves the column empty.
  - `subExpressions` → the sparkline trend (left side). Optional, falls back to `expressions` if absent.
  - `metricConfig` pairs 1-to-1 with `expressions` (not `subExpressions`). Use for `label` (column header) and `unit`.
  - Compound columns via MQE addition: `avg(metric_a + metric_b)` in `expressions`, `metric_a + metric_b` in `subExpressions`.
  - Normal widgets with multiple expressions on one chart — always give a human label per expression in `metricConfig`, otherwise legend renders raw metric names.

### 5.2 Hot-reload (edits to shipped templates take effect on restart)

`UITemplateInitializer` normally uses `addIfNotExist` (keyed on JSON `id`), so on a plain restart your edits to shipped templates are ignored — the seeded copy in storage wins. For dev / extension work, set the environment variable and restart:

```bash
SW_UI_TEMPLATE_FORCE_RELOAD=true
# then restart OAP — shipped templates overwrite the seeded copies via addOrReplace.
# Hard-refresh the browser (Cmd+Shift+R) because the booster UI caches config in memory.
```

This is a dev / extension knob only — read directly from the OS environment, not wired through `application.yml`. Unset or `false` preserves the default production behavior where operators' UI edits persist across restarts.

### 5.3 New top-level menu group (e.g. "Mobile", "Gateway")

New **child entries** only need template JSON + `menu.yaml`. A new **top-level group** needs submodule work in `skywalking-ui` (git submodule at `skywalking-ui/`):

1. `skywalking-ui/src/assets/icons/<name>.svg` — menu icon.
2. `skywalking-ui/src/locales/lang/menus/{en,es,zh}.ts` — i18n labels (`<name>`, `<name>_desc`, `<name>_<child>`, `<name>_<child>_desc`). Missing i18n falls back to raw keys on screen.
3. Commit those upstream in `apache/skywalking-booster-ui`, bump the submodule pointer here.
4. Rebuild with `-Pui,dist` (not `-Pbackend,dist`) and `make docker.ui`, otherwise the new assets aren't in the image. See [package skill](../package/SKILL.md).

---

## 6. Docs set — everything that moves together

| File | What to add |
|---|---|
| `docs/en/setup/backend/<feature>-monitoring.md` | User-facing setup guide. Required SDK / collector, required env vars, sample OTLP payload, metrics / log tags list. |
| `docs/menu.yml` | Add the guide to the docs navigation tree. |
| `docs/en/security/README.md` | **Client-side monitoring**: mobile / browser / mini-program endpoints accept data from the public internet without auth by default. Add a rate-limit + abuse-protection note. Agent-side monitoring over private VPC usually doesn't need this. |
| `docs/en/changes/changes.md` | Per-release changelog. Add under `#### OAP Server` for features/bug-fixes, `#### UI` if the submodule bumps, `#### Documentation` if new guides. |
| `docs/en/swip/SWIP-<N>.md` | Design doc for non-trivial features; keep reconciled with final code after review. |
| `docs/en/swip/readme.md` | Move SWIP from "Proposed" to "Accepted" once merged. |

---

## 7. E2E test

Folder: `test/e2e-v2/cases/<feature>/`. See [run-e2e skill](../run-e2e/SKILL.md) for harness basics (setup / trigger / verify phases, template functions, expected-file authoring via `contains`).

Feature-specific extras:

1. **Docker-compose**: extend `../../script/docker-compose/base-compose.yml` oap + storage. Set `SW_OTEL_RECEIVER_ENABLED_HANDLERS`, `SW_LOG_LAL_FILES`, and any feature-specific env. Don't list default rules here — rely on `application.yml` so the config-dump test stays consistent.
2. **Register in CI**: add an entry under the e2e matrix in `.github/workflows/skywalking.yaml`.
3. **OTLP data hygiene**:
   - `spanId` is exactly 16 hex chars; `traceId` is exactly 32. Protobuf-JSON decodes them as base64; other lengths return HTTP 400.
   - Setup-step curl loops must fail loudly: `curl -sS -f --retry 30 --retry-delay 5 --retry-connrefused --retry-all-errors --max-time 10 ...` + `set -e`. The pattern `for …; do curl && break || sleep 5; done` silently succeeds when every attempt connection-refused (the final `sleep` returns 0) because OAP takes ~50 s to start in CI.
4. **Verify each case by hand before pushing** and use a published simulator image where the upstream SDK has one. See [`run-e2e` skill §6–§9](../run-e2e/SKILL.md) for verify-query triage, expected-file authoring, and the list of expected-file traps (YAML quoting on colons, nested `contains` fragility, `timeUnixNano: "0"`, setup-step curl loops that swallow failure).
5. **Config dump**: when you modify any `application.yml` default (rule list, handler list, ...), also update `test/e2e-v2/cases/storage/expected/config-dump.yml`.

---

## 8. Pre-submit checklist

Run in order; each has a dedicated skill:

1. [`compile`](../compile/SKILL.md) — `./mvnw clean flatten:flatten install javadoc:javadoc -B -q -Pall -Dmaven.test.skip -Dcheckstyle.skip -Dgpg.skip`. Javadoc `[ERROR] ... error:` must be fixed; `[ERROR] ... warning:` is noise.
2. Tests: `./mvnw -pl oap-server/analyzer/log-analyzer -am test -Dtest=LALScriptExecutionTest` for LAL changes, equivalent for MAL, OAL unit tests where the rule lives.
3. [`license`](../license/SKILL.md) — `license-eye header check` (Apache 2.0 headers). If the UI submodule bumped npm deps, also `license-eye dependency resolve --summary ./dist-material/release-docs/LICENSE.tpl` and commit the `LICENSE` diff.
4. [`package`](../package/SKILL.md) — full `make docker` (or dist-rebuild + `make docker.oap` / `make docker.ui`). Don't trust `docker.oap` alone to pick up code changes; the dist tarball is a file-level prereq.
5. [`run-e2e`](../run-e2e/SKILL.md) — run the new case end-to-end locally before pushing.
6. [`gh-pull-request`](../gh-pull-request/SKILL.md) — commit and open PR with the template, attach UI screenshots.

---

## 9. Cross-cutting traps collected from real extensions

| Trap | Symptom | Fix |
|---|---|---|
| Layer folder named with hyphens | `ui-initialized-templates/my-layer/*.json` on disk but dashboard empty | Folder must be `Layer.name().toLowerCase()` (underscores). `wechat_mini_program/`, not `wechat-mini-program/` |
| Extending `CommonAnalysisListener` for a client-side layer | Added componentId → layer mapping "to get topology for free"; then `service_cpm` etc. don't populate anyway | Client-side (exit-only) layers don't need trace-analysis changes. Ship `Layer` + `component-libraries.yml` + MAL + LAL. See §2 header for when extending trace analysis is actually justified |
| Dashboards wired to OAL-derived metrics on a client-side layer | Charts stay empty — `service_cpm` / `service_resp_time` / `endpoint_cpm` never populate | Those come from inbound `parseEntry`, which mini-program / browser / iOS don't emit. Use MAL metrics (`meter_<layer>_*`) instead |
| LAL `#` comment in DSL body | OAP boot fails with `extraneous input 'metrics'` at the `#` line | LAL grammar accepts `//` and `/* */` only. No `#` comments |
| LAL bare def-var value access — `tag 'x': myvar` | OAP boot fails with `Cannot resolve expression '[myvar]'` | `LALValueCodegen` only treats def-vars as valid when there's a method chain (`myvar?.foo`). Inline `sourceAttribute(...)` or `tag(...)` in the three value-access sites instead of storing to a def, or add a no-op chain like `myvar.toString()`. Conditions (`if (myvar == ...)`) go through a different codegen path and DO handle bare def refs |
| MAL rule file with multi-doc YAML (`---` separator) | `MALFilterExecutionTest` fails with `expected a single document`; OAP would also fail at startup via `Rules.loadRules` (calls `Yaml.loadAs`, single-doc) | Split into one file per top-level `expSuffix` / `filter` / `metricPrefix` combo. Reference each file by name in `application.yml` (`lalFiles`, `malFiles`, `enabledOtelMetricsRules`) |
| Histogram bucket bounds in ms with default `.histogram()` call | Percentile values come out 1000× inflated (`P50 = 100000` for a 100 ms request) | MAL's default `defaultHistogramBucketUnit = SECONDS` multiplies `le` by 1000 when converting to stored ms. For SDKs that emit ms bounds directly (mini-program, most in-house JS SDKs), use `.histogram("le", TimeUnit.MILLISECONDS)`. `TimeUnit` is registered in `MALCodegenHelper.ENUM_FQCN` so rule YAML can reference it |
| `.rate()` / `.increase()` on a DELTA counter / histogram `_count` | Compute delta-of-delta — nonsense values | OTLP DELTA means each flush is already the per-flush count. MAL's per-minute aggregator sums directly to requests-per-minute. For CUMULATIVE (Prometheus default), use `.rate("PT1M")` for per-sec or `.increase("PT1M")` for per-min; they are **not** equivalent (differ by 60×) |
| MAL bucket-family name with `_histogram` suffix | Rule compiles, metric stays empty | `PrometheusMetricConverter` emits the bucket family as `<metric_name>` (no suffix), with `<metric_name>_count` and `<metric_name>_sum` as the companion families. Reference the bucket family by its bare name in the rule |
| LAL `tag("key") == null` check on a missing tag | Check never triggers — LAL's `tag()` returns empty string `""` for absent tags, not `null`, so conditional abort is a no-op | Guard on both: `tag("key") == null || tag("key") == ""`. Same for `sourceAttribute("key")` |

E2E-side authoring traps (log-content YAML quoting, nested `contains` fragility, `timeUnixNano: "0"`, setup-step curl swallowing failure) live in the [`run-e2e` skill §9](../run-e2e/SKILL.md).
| Missing layer-root template | Menu item lands on empty "no dashboard" view | `Layer.vue:41-44` requires a template with `isRoot: true`; ship a `<layer>-root.json` (precedent: `ios/ios-root.json`) |
| New layer not selectable in UI / swctl | `service ly <LAYER>` returns empty | Add the enum in `Layer.java` with a fresh id; see §0 |
| Stale dist in image | `make docker.oap` succeeds but behavior is old | See [`package` skill](../package/SKILL.md); rebuild dist first, then image |
| Shipped template edits don't render after restart | Edited JSON on disk, but OAP shows old copy | Set `SW_UI_TEMPLATE_FORCE_RELOAD=true` (environment variable, not `application.yml`) and restart; OAP writes `addOrReplace`. Unset in production so operator UI edits aren't clobbered. |
| MAL service-scoped rule fragmented by instance | "Overall app" metric has N series per version | Keep `service_instance_id` out of service-scoped `.sum`/`.avg`. Put instance-scoped metrics in a separate `<feature>-instance.yaml` with `expSuffix: instance(...)` — see `otel-rules/ios/` pattern. |
| Config dump drift | Storage e2e fails after `application.yml` edit | Mirror the change in `test/e2e-v2/cases/storage/expected/config-dump.yml` |
| LAL `layer:auto` WARN drop | Every log silently dropped | Codegen must set `metadata.layer` alongside output builder's layer |
| Histogram 1000× off | p50 = 2 000 000 ms | `defaultHistogramBucketUnit(MILLISECONDS)` on the `SampleFamily` |
| Histogram shifted up | p50 = 5 000 on 500–2500 ms data | Emit per-bucket, not cumulative |
| Empty list column | Dashboard instance table has no numbers | Add `expressions` (not just `subExpressions`) |
| Double scroll | Inner tab scroll + page scroll | `Tab.h = content_max + 4` chrome buffer |
| Raw metric name in legend | Multiple expressions on one widget | Add parallel `metricConfig[].label` entries |
| `spanKind` mismatch | Span skipped silently | `OTLPSpanReaderImpl.spanKind()` returns enum `.name()` like `SPAN_KIND_CLIENT`, not `CLIENT` |
| Negative latency | `*_sla` inflated | Clamp `rawLatency < 0 ? 0 : (int) min(Integer.MAX_VALUE, rawLatency)` |
| SLA too optimistic | Client-side DNS / timeout / connection-refused counted as success | Require a real response code for success: `status = code > 0 && code < 400`. Do not treat missing (0) as success. |
| Silent drop with no rules | `converters == null` NPE elsewhere | Init to empty list at declaration |
| `+Inf` bucket surfaces as `9.2e18` | UI shows garbage when a sample lands in overflow | Use finite sentinel bucket (e.g. 30 s) |
| OTel submodule bump misses UI image | New icons / i18n not visible | Rebuild with `-Pui,dist` + `make docker.ui` |
| LICENSE drift after submodule bump | CI `Dependency licenses` fails | `license-eye dependency resolve --summary ./dist-material/release-docs/LICENSE.tpl` and commit |
| Setup-step curl loop swallows failure | e2e passes metrics but fails log verify | `curl -f --retry-connrefused --retry-all-errors` + `set -e` |
| Verify case flag mismatch | CI fails early on `swctl` flag unknown to pinned commit | Fire every verify command locally first; bump `SW_CTL_COMMIT` if needed |

---

## 10. Reference extensions (read their code)

Browse these when you need a template to copy from. Grouped by the kind of data source, because the extension points differ significantly:

### SW native trace (language agents) — the "General" layer and friends

`Layer.GENERAL` is the default layer for services reporting via the SkyWalking native trace protocol (Java / Python / Go / Node.js / .NET / PHP / Lua / Rust agents). It's the richest reference because the native pipeline is where the **full topology** lives:

- **Service → service dependency** (topology): derived by `MultiScopesAnalysisListener` and `NetworkAddressAliasMappingListener` in `oap-server/analyzer/agent-analyzer/src/main/java/.../trace/parser/listener/`. Entry/exit spans produce `ServiceRelation`, `ServiceInstanceRelation` sources.
- **Endpoint → endpoint dependency**: `RPCAnalysisListener` emits `EndpointRelation` sources from cross-process refs and exit spans. This is unique to the native protocol — OTLP spans don't preserve upstream entry-span metadata the same way, so OTLP-only layers generally lose endpoint-level topology.
- OAL aggregation: `oap-server/server-starter/src/main/resources/oal/core.oal` — all `service_relation_*`, `service_instance_relation_*`, `endpoint_relation_*` metrics are already defined there; a new native-tracing layer just needs to produce the right sources with the right `Layer`.

Read order: `AnalysisListenerFactory` SPI registration → `SegmentParserServiceImpl` fan-out → individual listeners → OAL rules.

### Service-mesh (Istio / Envoy ALS) — topology from proxy-side signals

No agent in the service; Envoy emits HTTP/TCP access logs (ALS) and metrics. Service-to-service topology is reconstructed from ALS request/response pairs.

- `oap-server/server-receiver-plugin/envoy-metrics-receiver-plugin/` — ALS receiver + `EnvoyAccessLogBuilder` persistence (LAL output type).
- `otel-rules/istio-*`, `otel-rules/envoy/*` — MAL rules for proxy-emitted OTel metrics.
- `lal/envoy-als.yaml` — LAL rule that extracts topology-generating fields from ALS.
- Layer: `Layer.MESH`, plus `Layer.MESH_CP` (control plane) and `Layer.MESH_DP` (data plane) for split-plane topology.

Topology parity with native traces: ALS gives you service-level relations; endpoint-level relations are approximate (proxy sees inbound + outbound, not the full call-chain that a native agent observes).

### OTLP-only layers (OTel SDKs, iOS/Android, AI gateway, …)

OTLP spans feed a `SpanListener`. Topology is **partial by construction** — an OTel Swift SDK on a mobile device only knows about its own outbound requests; there's no upstream caller. So these layers typically produce only `Service` / `ServiceInstance` / `Endpoint` sources (no `*Relation`) and the Topology view shows isolated islands, by design.

- **iOS** (`Layer.IOS`, OTLP traces + MetricKit + LAL diagnostics): `oap-server/analyzer/ios-analyzer/` + `docs/en/swip/SWIP-11.md`.
- **Envoy AI Gateway** (`Layer.ENVOY_AI_GATEWAY`, LLM / MCP, OTLP metrics + Zipkin sampled traces + LAL access-log sampling): `oap-server/analyzer/gen-ai-analyzer/`, `otel-rules/envoy-ai-gateway/*`, `lal/envoy-ai-gateway.yaml`.

### OTel-metrics-only layers (scraped exporters)

No spans, no logs — just MAL rules converting OTel metric scrapes.

- **MySQL / PostgreSQL**: `otel-rules/mysql/*`, `otel-rules/postgresql/*`.
- **Kubernetes**: `otel-rules/k8s/*` — paired with `virtual-k8s-service` (`Layer.K8S_SERVICE`) sources for synthetic services.
- **AWS**: `otel-rules/aws-eks/*`, `otel-rules/aws-s3/*`, `otel-rules/aws-dynamodb/*`.

### SW native meter (custom protocol)

No OTel; the agent / plugin sends pre-built meter samples on the SW `MeterProtocol`.

- **Nginx Lua** (`Layer.NGINX`): `meter-analyzer-config/nginx-service.yaml`, with the Lua agent in `apache/skywalking-nginx-lua`.
- **APISIX** (`Layer.APISIX`): `meter-analyzer-config/apisix-service.yaml` + LAL trace extraction.

### Which to copy from

| You're adding | Closest reference |
|---|---|
| Language agent (new JVM / runtime) | The General / Java layer flow; extend `AnalysisListener` SPI. |
| Service mesh / proxy | `mesh-*` + `envoy-*` rules; LAL on ALS. |
| Mobile / browser / mini-program SDK (OTLP) | `ios-analyzer` (OTLP client-side). Expect no topology relations. |
| Database / middleware (metrics only) | `mysql`, `postgresql` — pure MAL rules. |
| Cloud service (no agent, just metrics) | `aws-eks` / `aws-s3`. |
| Custom protocol / home-grown telemetry | `apisix` / `nginx` — SW native meter. |
