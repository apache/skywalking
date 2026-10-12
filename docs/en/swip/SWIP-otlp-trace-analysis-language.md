# Trace Analysis Language (TAL) for OTLP Spans

## Motivation

OTLP spans reach OAP over `TraceService/Export` and `POST /v1/traces`, are handed to the phase-one `SpanListener`s,
and are then stored, either as Zipkin spans today or natively as
[the native OTLP trace storage proposal](SWIP-otlp-native-trace.md) describes. Nothing in that path turns a span
into a service, an instance, an endpoint, a topology edge or a metric. An OpenTelemetry SDK user sees traces and
nothing else.

[The source-mapping proposal](SWIP-otlp-trace-metrics.md) closes the gap by mapping each span onto the `Source`
objects the agent trace analyzer emits, `Service`, `ServiceRelation`, `DatabaseAccess` and the rest, so the
unchanged `core.oal` produces the agent metrics for OTLP services too. The mapping is driven by a YAML rule file
whose vocabulary, `aliases`, `requestType.precedence`, `peers.virtual`, `callers`, is a purpose-built schema: every
question the mapping must answer is a fixed key with fixed semantics, and every question it does not anticipate is
a Java change. The set of things a span can produce is fixed to the sources `core.oal` knows, the metric set is
fixed to what `core.oal` defines, and the rule file is loaded once at boot with no hot update and no debugger.

SkyWalking already has the shape this problem needs. Logs arriving from agents, Fluent Bit, Envoy ALS and OTLP are
not mapped by a schema; they are analyzed by LAL, a small compiled language in which a rule reads the record,
decides what it is, extracts fields, emits metrics and votes on persistence. LAL rules are hot-updated by the
runtime-rule plugin and inspected live by the DSL debugger. Underneath every non-OAL metric in OAP sits one engine,
`MeterSystem`: a metric is a name, a function such as `sum`, `avg` or `sumHistogramPercentile`, and an entity
scope; MAL is one rule surface over it, and `Analyzer` is the code that builds the `MeterEntity`, emits the entity
traffic and hands the value to `MeterSystem`.

This proposal gives OTLP spans the same treatment: a **Trace Analysis Language (TAL)** whose unit of evaluation is
one span with its resource and instrumentation scope, exactly as LAL's unit is one log record. A TAL rule reads
the span through typed accessors and the semantic-convention attributes through helper functions, decides whether
the span is its concern, extracts identity, and declares its metrics directly: name, function, entity scope and
value, in the rule, compiled to direct `MeterSystem` calls. No `Source` is emitted, `core.oal` is not involved, no
MAL rule file is needed, and the semantic-convention knowledge lives in a rule file an operator can read, edit,
hot-update and debug.

Why not route the values through MAL, as LAL does with `log-mal-rules`: a span yields one point per metric, not a
family of samples. MAL's strength is family algebra over scrape-shaped data, `rate`, `increase`, relabeling,
combining series, and none of it applies to a single point. What a span metric needs is a function, a scope and,
for percentiles, a bucket list, and the core implements all three. Putting them in the rule removes a second file,
a second syntax, the `SampleFamily` intermediate and its edge cases, and makes every metric a compile-time fact the
runtime-rule and debugging tooling can reason about.

Two LAL mechanisms carry over unchanged in spirit. `inputType` makes the OTLP span the default input rather than
the only one: a rule can declare the Zipkin span as its input and the same language, routing and tooling apply to
traces arriving over the Zipkin protocol, through the same typed-input SPI that lets Envoy access logs and TCP
access logs share one LAL layer. `outputType` lets a rule write a record instead of, or as well as, metrics: a slow
database statement from a span that carried `db.query.text`, through the same `SlowSQL` builder the MySQL and
PostgreSQL slow-log LAL rules use today.

What TAL is not: it is per-span. It never sees two spans of a trace together, so a server span cannot learn its
caller from the client span, and a consumer cannot see its producer.

This proposal is an alternative to the source-mapping proposal, not a companion: both close the same gap and a
deployment should adopt one. It is independent of the native storage proposal and works with either
`otlpTraceStorage` mode, because it runs before conversion and before storage.

## Architecture Graph

```text
OTLP/gRPC :11800  TraceService/Export ──┐                 Zipkin HTTP :9411 / Kafka ──┐
OTLP/HTTP :12800  POST /v1/traces      ──┤                                              │
                                         v                                              v
   OpenTelemetryTraceHandler.processExport   (receiver-otel)          SpanForward.send   (receiver-zipkin)
      │ per resource: attributes -> strings, service name,                │ before head sampling, per zipkin2.Span:
      │   instance, service.layer                                         │ doAnalysis(SpanMetadata, zipkin2.Span)  inputType: zipkin
      │ per span:                                                         │ drop vetoed spans, putTag the injected tags
      ├─ SpanListenerManager.notifyOTLPPhase   (existing listeners)       │ (reserved: the Zipkin path is not wired in this proposal)
      ├─ doAnalysis(SpanMetadata, Span, resourceAttributes)   inputType: otlp (default)
      │        │                                                          │
      │        v                                                          v
      │   trace-analyzer module   oap-server/analyzer/trace-analyzer   ITraceAnalyzerService
      │     SpanFilterListener.Factory: Layer -> (ruleName -> compiled TAL rule), plus `layer: auto` rules
      │        │  at compile: every `metrics {}` -> MeterSystem.create(name, function, scopeType)
      │        │              `outputType` -> TALOutputBuilder or Source subclass, setters validated
      │        │  per span, for every rule of the span's layer whose inputType matches the object:
      │        │    execute(FilterSpec, ExecutionContext)      compiled bytecode, no reflection
      │        │      filter { if (...) abort {}
      │        │               extractor { service/instance/endpoint/layer, tag, <output fields>, metrics {} }
      │        │               sink { ... }        // the output record
      │        │               persist { ... } }   // the span itself
      │        │
      │        ├─ metrics {} ──> MeterEntity ──> EntityTrafficGenerator.generate(entity)   (moved to core, shared with MAL)
      │        │                       │              ServiceTraffic / InstanceTraffic / EndpointTraffic
      │        │                       │              ServiceRelationClientSideMetrics / ServerSideMetrics   (topology)
      │        │                       v
      │        │               MeterSystem.buildMetrics(name, type).accept(entity, value) -> doStreamingCalculation
      │        │                       otlp_* metrics, minute buckets, L1 aggregation as for MAL
      │        │
      │        ├─ sink {} ──> outputType builder.complete(SourceReceiver)   e.g. SlowSQL -> DatabaseSlowStatement
      │        │
      │        └─ persist {} votes + tag statements ──> SpanAnalysisResult{persist, enforced, additionalTags}
      │
      ├─ persist = listenerResult.shouldPersist && analysisResult.persist
      ├─ tags    = listenerResult.additionalTags + analysisResult.additionalTags
      ├─ enforced = persist && analysisResult.enforced       // the forward skips the trace-id head sampling for it
      v
   Zipkin conversion -> SpanForward.send           |   OTLPSpanForward -> otlp_span     (enforced spans skip head sampling, not the rate limit)

Operator surfaces (existing modules, extended):
  runtime-rule   POST /runtime/rule/addOrUpdate?catalog=tal      hot update, metric and output shape guard, cluster convergence
  dsl-debugging  POST /dsl-debugging/session?catalog=tal          live capture of input, statements, emitted metrics and records
  SW_DYNAMIC_CLASS_ENGINE_DEBUG=true                              {skywalking}/tal-rt/*.class + *.java
```

## Proposed Changes

### 1. Module and configuration

A new analyzer module, `oap-server/analyzer/trace-analyzer`, `TraceAnalyzerModule.NAME = "trace-analyzer"`,
built the way `log-analyzer` is built. Its services:

| Service | Purpose |
|---|---|
| `ITraceAnalyzerService` | `SpanAnalysisResult doAnalysis(SpanMetadata meta, Span span, Map<String, String> resourceAttributes)`, called by the OTLP trace handler per span |
| `SpanFilterListener.Factory` | the registry of compiled TAL rules keyed by `(Layer, ruleName)` plus the `layer: auto` set; `compile`, `addOrReplace`, `remove`, `suspend`, `resume`, `find`, the same surface `LogFilterListener.Factory` exposes to the runtime-rule plugin |

`requiredModules()` is `CoreModule.NAME`, `ConfigurationModule.NAME` and `StorageModule.NAME`, for the same reasons
`LogAnalyzerModuleProvider` declares them: `MeterSystem`, `NamingControl` and `IComponentLibraryCatalogService`
come from core, `MeterSystem.create` at compile needs the storage model installer, and the runtime-rule override
resolver consulted while loading static rules needs the management table to exist. The module depends on
`receiver-proto` for the OTLP `Span` proto, as `log-analyzer` already does for the logging protos.

```yaml
trace-analyzer:
  selector: ${SW_TRACE_ANALYZER:default}
  default:
    # TAL rule files under `tal/`, without extension, comma separated. Empty disables span analysis.
    talFiles: ${SW_TRACE_TAL_FILES:otlp-default}
    # Spans seen within this window (seconds) are analyzed once, so an exporter that retries a batch after a
    # transport timeout does not count a request twice. 0, the default, keeps no set at all and treats every
    # arrival as new, which is what OTLP logs do today. Set it longer than the exporter's retry budget; the OTLP
    # SDKs give up after about 30 s. The set is per OAP node: a retry that reaches another node is counted again.
    dedupWindow: ${SW_TRACE_TAL_DEDUP_WINDOW:0}
    # The most (traceId, spanId) pairs the set holds on this node while dedupWindow is above 0; ignored at 0.
    # Size it as spans per second on this node times dedupWindow, with headroom: once full, the oldest entries
    # drop before they expire and a late retry of one of them is counted. About 150 bytes per entry.
    dedupCapacity: ${SW_TRACE_TAL_DEDUP_CAPACITY:1000000}
    # In a cluster, the percentage of traces whose client and server spans are routed to the trace's owner node to
    # learn a network address alias from the pair; both spans of a call share the trace id, so a pair is never
    # split. An address only has to be learned once, after which every node resolves it from its cache, so a
    # sample is enough: at 25 a new address is learned after about four calls. 100 routes every trace, at one
    # small message per SERVER span across the cluster. A single node pairs every call and ignores this.
    pairingSamplePercent: ${SW_TRACE_TAL_PAIRING_SAMPLE_PERCENT:25}
```

`talPath` exists as `lalPath` does and defaults to `tal`. The module is present by default so that the shipped rule
produces entities and metrics for OTLP traces out of the box; setting `talFiles` empty turns the analysis off while
keeping the module, and `selector: -` removes it, which `receiver-otel` tolerates by storing the spans without
analysis. The new default is added to
`test/e2e-v2/cases/storage/expected/config-dump.yml`.

`OtelMetricReceiverProvider.requiredModules()` gains `TraceAnalyzerModule.NAME`, declared only when the module is
loaded, so that the analyzer starts before the receiver when present and a deployment that removed it still boots;
the trace handler resolves `ITraceAnalyzerService` on first use and skips the analysis when the module is absent.
Today the OTLP handler finds `ZipkinReceiverModule` lazily without declaring it, which is the pattern this
repository asks not to repeat.

Input kinds are contributed through an SPI, `TALSourceTypeProvider`, discovered with `ServiceLoader` the way
`LALSourceTypeProvider` is. The module registers the OTLP provider, which is the only one this proposal ships; a
Zipkin provider is the reserved extension of [section 2.2](#22-zipkin--reserved-not-in-scope). See
[section 3.2](#32-what-a-rule-can-read).

### 2. Receiver integration

#### 2.1 OTLP

`OpenTelemetryTraceHandler.processExport` already computes the resource attribute map, the service name with
its fallbacks and the scope name and version, and already runs `notifyOTLPPhase` per span. It gains one call per
span, after the listeners and before conversion:

```java
final SpanMetadata meta = SpanMetadata.of(serviceName, resourceTags, scopeSpans.getScope(), span);
final SpanAnalysisResult analysis = traceAnalyzerService().doAnalysis(meta, span, resourceTags);
if (!otlpResult.isShouldPersist() || !analysis.isPersist()) {
    continue;
}
// merge otlpResult.getAdditionalTags() and analysis.getAdditionalTags() into spanTags, last writer wins
```

`analysis.isEnforced()` travels with the span to the forward: `OTLPSpanForward.send` gains an `enforced` argument,
and in the Zipkin storage mode the converted span is forwarded as today. The OTLP forward skips the trace-id
head sampling for an enforced span and still count it against `maxSpansPerSecond`, see
[section 3.3](#33-filter-extractor-sink-persist).

`SpanMetadata` lives in `server-core`, package `org.apache.skywalking.oap.server.core.trace`, next to
`OTLPSpanReader`, and is the span's counterpart of `LogMetadata`: the derived, protocol-neutral view a rule reads
as `meta.*` and the extractor may overwrite.

| Field | Populated by the receiver as |
|---|---|
| `service` | `service.name`, then the fallbacks `extractZipkinServiceName` already applies, `faas.name` and `process.executable.name` |
| `serviceInstance` | `service.instance.id`, then `service.instance`, the order the OTLP logs handler uses; empty when absent |
| `endpoint` | `Span.name` |
| `layer` | the `service.layer` resource attribute, empty when absent; drives rule routing, see [section 3.1](#31-rule-files-and-layer-routing) |
| `kind` | `SERVER`, `CLIENT`, `PRODUCER`, `CONSUMER`, `INTERNAL`, `UNSPECIFIED`: the proto enum name without its prefix |
| `statusCode`, `statusMessage` | `UNSET`, `OK`, `ERROR`, and `Status.message` |
| `startTime`, `timestamp` | `start_time_unix_nano / 1_000_000` and `end_time_unix_nano / 1_000_000`; `timestamp` is the default metric time |
| `latency` | `end - start` in nanoseconds rounded to milliseconds, clamped to `[0, Integer.MAX_VALUE]`, as `setPublicAttrs` does for agents |
| `traceId`, `spanId`, `parentSpanId` | lowercase hex; `parentSpanId` empty for a root |
| `root` | `parentSpanId` is empty |
| `scopeName`, `scopeVersion` | `InstrumentationScope.name` and `version` |

`SpanAnalysisResult` carries `persist`, `enforced` and `additionalTags`. The merge rule with the listeners is the
one `SpanListenerManager` uses among listeners: any veto drops the span, tags merge last-writer-wins. `enforced` is
true when a rule's `enforcer {}` fired and the span is persisted; it asks the forward to skip the trace-id head
sampling. TAL runs on every span regardless of head sampling and rate limiting, which `SpanForward` and
`OTLPSpanForward` apply afterwards, so metrics count what the SDK sent, not what OAP chose to store. That is the
ordering the agent path has.

A resource without a resolvable service name is skipped before TAL, as it is skipped today. In the Zipkin storage
mode the converted spans go to `SpanForwardService.send` unchanged; nothing analyzes them twice, because the
Zipkin receiver runs no rules — see [section 2.2](#22-zipkin--reserved-not-in-scope).

#### 2.2 Zipkin — reserved, not in scope

**This section describes a capability the design reserves, not work this proposal delivers.** TAL ships analyzing
OTLP spans only; the Zipkin receiver is untouched, and a Zipkin span reaches no rule. What follows is the shape
the integration takes when a deployment asks for it, recorded here so nothing in the engine has to change then.

What already makes it possible: `inputType` is a list resolved against the registered `TALSourceTypeProvider`
aliases, so a Zipkin provider declaring `alias() = "zipkin"` and `inputType() = zipkin2.Span` plugs in through
`META-INF/services` alone, and a rule opts in by writing `inputType: otlp, zipkin`. `SpanMetadata.of(zipkin2.Span)`,
the adapter below, is already in `server-core`. Neither the grammar, the compiler nor the runtime knows which
protocol a span arrived over.

What the integration would add: `SpanForward.send` — what the Zipkin HTTP and Kafka collectors call — gains one
loop before `getSampledTraces`, so that, as on the OTLP side, metrics count what was sent rather than what was
stored:

```java
final List<Span> analyzed = new ArrayList<>(spanList.size());
final Set<Span> enforced = new HashSet<>();
for (final Span span : spanList) {
    final SpanMetadata meta = SpanMetadata.of(span);                              // the Zipkin adapter
    final SpanAnalysisResult analysis = traceAnalyzerService().doAnalysis(meta, span, Collections.emptyMap());
    if (!analysis.isPersist()) {
        continue;
    }
    final Span stored = analysis.getAdditionalTags().isEmpty() ? span : withTags(span, analysis.getAdditionalTags());
    analyzed.add(stored);
    if (analysis.isEnforced()) {
        enforced.add(stored);
    }
}
final var sampledTraces = getSampledTraces(analyzed, enforced);
```

`withTags` is `span.toBuilder().putTag(k, v).build()`, because `zipkin2.Span` is immutable. When no rule declares
the Zipkin input, the factory returns no rules for the object and the loop costs one `SpanMetadata` per span.
`getSampledTraces` gains the enforced set: a span in it passes the trace-id check the way a span with Zipkin's
`debug` flag does today, but unlike `debug` it still takes a permit from the rate limiter. `sendAnalyzed` is the
same method without the loop, taking the analyzed spans and the enforced set. The phase-two `notifyZipkinPhase` for
the existing listeners stays where it is, after conversion to `ZipkinSpan`.

The Zipkin adapter fills `SpanMetadata` from the wire object:

| Field | From `zipkin2.Span` |
|---|---|
| `service` | `localServiceName()`; `Unknown` when empty, as `SpanForward` names it today |
| `serviceInstance` | empty; Zipkin has no instance identity |
| `endpoint` | `name()` |
| `layer` | empty, so `layer: auto` rules apply; Zipkin carries no `service.layer` |
| `kind` | `kind().name()`; `UNSPECIFIED` when absent, which is how an OTLP `INTERNAL` span reads after conversion |
| `statusCode`, `statusMessage` | `ERROR` when the `error` tag is present, the Zipkin convention, and its value; `UNSET` otherwise |
| `startTime`, `timestamp`, `latency` | `timestampAsLong()` and `duration()` in microseconds, converted to milliseconds; `timestamp` is start plus duration |
| `traceId`, `spanId`, `parentSpanId`, `root` | `traceId()`, `id()`, `parentId()`, already hex; root when `parentId()` is null |
| `scopeName`, `scopeVersion` | empty |

`attr()` and `hasAttr()` read `tags()`; `resource()`, `scopeAttr()`, `event()` and `eventAttr()` return empty for
this input, since Zipkin has no resource, no scope and annotations are not events. A rule written against `meta`
and `attr()` therefore runs unchanged on both inputs, which is what the shipped rule does.

### 3. The language

TAL is LAL with the log-specific parts removed and span-specific parts added. Anyone who has written a LAL rule
can read a TAL rule.

#### 3.1 Rule files and layer routing

```yaml
# tal/<file>.yaml
metricPrefix: otlp     # every metric declared in this file is stored as <metricPrefix>_<name>
layerDefinitions:            # optional, same contract as LAL and MAL: registered through Layer.register before compile
  - name: IOT_FLEET
    ordinal: 1000
    normal: true
rules:
  - name: otlp-service       # unique within its layer
    layer: auto              # a Layer name, or `auto`
    inputType: otlp          # optional; a provider alias or FQCN, or a comma-separated list of them. Default `otlp`
    outputType: SlowSQL      # optional; a TALOutputBuilder name, or the FQCN of a Source subclass. No default
    dsl: |
      filter { ... }
```

Routing follows LAL exactly. A span whose resource carries `service.layer` is evaluated by the rules declared for
that layer, and by nothing else; an unknown layer name is logged and the span is not analyzed. A span without
`service.layer` is evaluated by the `layer: auto` rules; a rule that does not `abort {}` has claimed the span and
must set `layer` in its extractor, or the rule's output is discarded with a warning. If no auto rule claims the
span, no analysis happens. There is no fallback to `GENERAL`: `GENERAL` is the agent layer, and putting OTLP spans
there without `core.oal` metrics would produce services with empty dashboards, see
[section 6](#6-the-otlp_service-layer).

Rules for one layer run in file order, each with its own `ExecutionContext` and its own copy of `SpanMetadata`, so
a rule that overwrites `service` for its record does not change what the next rule reads. Each rule decides
independently.

#### 3.2 What a rule can read

**Inputs.** `inputType` names the object the rule reads. Each input kind is a `TALSourceTypeProvider`:

```java
public interface TALSourceTypeProvider {
    String alias();                          // `otlp`, `zipkin`; what a rule writes in inputType
    Class<?> inputType();                    // io.opentelemetry.proto.trace.v1.Span, zipkin2.Span
    SpanAttributeReader attributes(Object input);   // backs attr(), hasAttr(), resource(), scopeAttr(), event(), eventAttr()
    String renderInput(Object input);        // the debugger's input payload, OTLP/JSON or Zipkin JSON
}
```

The module ships the `otlp` provider; a `zipkin` one is reserved, see [section 2.2](#22-zipkin--reserved-not-in-scope).
A rule without `inputType` is an `otlp` rule, and a rule names exactly one input, because the `span` root's type
is the input's type; `compile` refuses a rule that lists more. Serving one rule to several inputs, for a rule that
reads the span only through `meta` and the functions, is reserved with the Zipkin provider.
At analysis time, `SpanFilterListener.parse` runs a rule only when the incoming object is an instance of one of its
declared input types, the `isInstance` guard `LogFilterListener.parse` uses to keep HTTP and TCP access-log rules
apart under one layer. Routing by layer and by input compose: a Zipkin span, with its empty layer, reaches the auto
rules that declare `zipkin`.

An agent segment's `SpanObject` could be a third provider through the same SPI. It is not proposed: the agent path
has `RPCAnalysisListener` and `core.oal`, and nothing in this design needs to touch them.

**Roots.** Two roots replace LAL's `parsed` and `log`:

| Root | Type | Resolved | Examples |
|---|---|---|---|
| `span` | the declared input type; `io.opentelemetry.proto.trace.v1.Span` by default | at compile time, getter and method chains generated as direct calls, the way LAL resolves `parsed.*` on `HTTPAccessLogEntry` | OTLP: `span.name`, `span.traceState`, `span.status.message`, `span.eventsCount`, `span.linksCount`; Zipkin: `span.remoteServiceName()`, `span.localEndpoint()?.ipv4()`, `span.shared()` |
| `meta` | `SpanMetadata` | at compile time | `meta.service`, `meta.kind`, `meta.statusCode`, `meta.latency`, `meta.root`, `meta.scopeName` |

`span.kind` and `span.status.code` are proto enums and read awkwardly; `meta.kind` and `meta.statusCode` are the
string forms meant for conditions, and they mean the same thing on every input.

**Functions.** Attributes are `repeated KeyValue` on the OTLP wire and a tag map on the Zipkin wire, so they are
reached through functions the input's provider backs, all null-safe and returning `""` when absent:

| Function | Returns |
|---|---|
| `attr(key, ...)` | the first present span attribute among the keys, rendered as a string with the receiver's `AnyValue` rules: scalars as text, arrays joined by `,`, kvlists as JSON, bytes as base64. Several keys let one rule serve SDKs on different semantic-convention versions: `attr("db.system.name", "db.system")` |
| `hasAttr(key, ...)` | whether any of the keys is present on the span |
| `resource(key, ...)` | the same over the resource attributes; `resource("telemetry.sdk.language")` |
| `scopeAttr(key, ...)` | the same over the instrumentation scope attributes |
| `event(name)` | the first `Span.Event` with that name, or `null`; `event("exception")?.timeUnixNano` |
| `eventAttr(name, key, ...)` | an attribute of that event; `eventAttr("exception", "exception.type")` |
| `component(name)` | the component id from `component-libraries.yml` as an `int`, matched exactly and then ignoring case through the catalog's `getComponentIdIgnoreCase`, so `attr("rpc.system.name", "rpc.system")`, `grpc`, finds `GRPC`, and `postgresql` finds `PostgreSQL`; `0`, the `Unknown` component, when nothing matches. Meant for the relation scopes |
| `resolveAlias(address)` | the service the address names: a host, or its first DNS label, that is the name of a service this OAP has seen spans from; else the service a SERVER span registered the address for through the `alias` statement, read from the core's `NetworkAddressAliasCache`; else the service whose SERVER span this CLIENT span is the parent of, when that span has already been analyzed here, which also learns the alias row; else the address unchanged, and the span waits for its SERVER span. See [section 6](#6-the-otlp_service-layer) |
| `urlHost(url)` | the authority of a URL, `host` or `host:port`, without scheme, user info, path, query or fragment; empty when the value has none. The shipped rule's last fallback for a CLIENT span's peer, for SDKs that name the target only as `url.full` or `http.url`, the Python `requests` instrumentation among them |
| `toJson(expr)`, `toJsonArray(expr)` | as in LAL |

The runtime helper indexes a span's attributes into a map on the first `attr()` call and hands the index to the
next rule that runs on the same span, so a span costs one pass over its attribute list however many rules and
lookups read it.

#### 3.3 Filter, extractor, sink, persist

```text
filter {
  <if / def / abort>*
  extractor {
    def <name> = <expr> [as <Type>]                     // any value, including an entity from section 3.4
    service | instance | endpoint | layer <expr>        // overwrite meta.*; also the record's standard fields
    <field> <expr> [as <Type>]                          // output field of the outputType, section 3.5
    tag '<key>': <expr>                                 // injected into the persisted span
    alias <expr>                                        // the address this span was dialed at, section 6
    metrics { ... }                                     // section 3.4
    if (...) { ... } else if (...) { ... } else { ... }
    abort {}
  }
  sink {                                                // the record declared by outputType, as in LAL
    sampler { rateLimit("<id>") { rpm <n> } }           // shared by id across rules and spans, as in LAL
    dropper {}
    enforcer {}
    if (...) { ... }
  }
  persist {                                             // the span itself; same statements as sink
    ...
  }
}
```

Differences from LAL, all deliberate:

- **No parser blocks.** `text {}`, `json {}` and `yaml {}` exist to give a log body structure; a span is already
  structured. `inputType` selects among typed inputs, it does not select a parser.
- **No `traceId`, `segmentId`, `spanId`, `timestamp` statements.** The identifiers are facts of the span, readable
  as `meta.traceId` and friends, not things to extract. An output builder that needs them reads `meta`.
- **`abort {}` is allowed anywhere**, at filter level and inside `extractor`, `sink` and `persist` bodies, and stops
  the rule for this span. In `layer: auto` mode it means "not mine", as in LAL.
- **`tag` injects into the span, not into a record.** The key and value are appended to the persisted span as a
  string attribute, the way `SpanListenerResult.additionalTags` are appended today. This is how GenAI's estimated
  cost reaches the stored span; a TAL rule can do the same for a derived field, for instance a normalized route.
- **`alias <address>` registers a network address alias for the span's service**: the `NetworkAddressAliasSetup`
  row the agent path derives from the `sw8` header, so a CLIENT span's `resolveAlias(peer)` lands its edge on this
  service rather than on a node named by the address. An address whose host names another service is skipped.
  Allowed at filter level and in `extractor`. See [section 6](#6-the-otlp_service-layer).
- **`metrics {}` declares a metric and emits one value**, see [section 3.4](#34-the-metrics-block). It does not
  emit a sample for another engine to aggregate.
- **`sink {}` keeps LAL's meaning and needs an `outputType`.** It persists the record the rule builds, and without
  it no record is written. A rule with no `outputType` has no record, so `sink {}` there is a compile error rather
  than a silent no-op.
- **`persist {}` is new and decides the span's own fate.** Persistence of the incoming span is the receiver's
  default, and most TAL rules only want metrics, so a rule without `persist {}` abstains. A rule with `persist {}`
  votes: `dropper {}` and an unsampled `rateLimit` vote drop, `enforcer {}` votes keep regardless of the samplers in
  the same rule and marks the span enforced, an empty `persist {}` votes keep. The span is persisted when every rule
  that voted said keep and no phase-one listener vetoed; a drop vote from another rule therefore beats an
  `enforcer {}`, and `enforced` is only set on a span that is persisted. A rule that aborted did not vote. The two
  blocks are separate because a slow-statement rule wants both: write the record, and force-keep the span that
  produced it.
- **`persist {}` samples spans, not traces.** `rateLimit` is LAL's `RateLimitingSampler`: one counter per id, kept
  while the counter is below `rpm`, reset every minute, shared by every rule and span that uses the same id. It
  counts spans, so two spans of one trace can fall on different sides of the budget and a sampled trace can be
  stored with spans missing. The receiver's head sampling keeps or drops whole traces because it hashes the trace
  id; `persist {}` does not, and a rule that needs whole traces has to key its decision on something trace-wide,
  such as `enforcer {}` on a condition every span of the trace carries, or accept partial traces.
- **`enforcer {}` also bypasses the receiver's head sampling.** TAL votes before `SpanForward` and
  `OTLPSpanForward` apply `sampleRate` and `maxSpansPerSecond`. An enforced span skips the trace-id check, so
  a span an `enforcer {}` kept is stored whatever `sampleRate` says, the way a Zipkin `debug` span is today. It
  does not skip the rate limit: `maxSpansPerSecond` is the operator's ceiling on what the OAP accepts at all, and
  a rule must not be able to lift it. Two limits follow. The bypass is per span: the other spans of the same trace
  still follow the trace-id hash, so at a low `sampleRate` an enforced slow statement is stored while the rest of
  its trace may not be; a whole trace can only be kept by enforcing every span of it, which a head decision cannot
  know about in advance. And an `enforcer {}` on a broad condition turns head sampling off for everything it
  matches, so it belongs behind a condition that is rare by construction, an error status or a latency threshold.
- **Statements may be separated by `;`** as well as by newlines, so a `metrics {}` block fits on one line.

Everything else, `def` with type inference and Java casts, `if` / `else if` / `else`, the condition operators,
arithmetic and string concatenation with `+`, safe navigation `?.`, string interpolation in `rateLimit` ids, is
LAL's grammar unchanged, with one precedence fix: `!` binds tighter than `&&` and `||`, so `!peer && hasAttr("x")`
reads as `(!peer) && hasAttr("x")`, as in Java. LAL's grammar lists `AND` before `NOT`, which in an ANTLR
left-recursive rule parses the same text as `!(peer && hasAttr("x"))`; the shipped LAL rules do not combine the two,
and that grammar is left as it is.

#### 3.4 The `metrics` block

```text
metrics {
  name "<metric>"                    // literal; stored as <metricPrefix>_<metric>
  scope <entity>                     // an entity constructor, or a `def` holding one
  function <function>                // literal, see the table
  value <expr>                       // the observation; a number, or a string that parses as one. Not used by percent
  [match <condition>]                // percent only, required: the span counts as a match when true
  [status <condition>]               // apdex only, required: the span counts as successful when true
  [labels k: v, ...]                 // required by the *Labeled functions, forbidden otherwise
  [buckets [n, n, ...]]              // required by histogram and percentile; ascending, in the unit of `value`
  [ranks [50, 75, 90, 95, 99]]       // percentile only; this is the default
  [timestamp <expr>]                 // milliseconds; default meta.timestamp, the span's end
}
```

`name`, `function` and the constructor used in `scope` are literals, so the compiler knows every metric a rule
declares and its shape before a span arrives. The entity constructors, usable inline in `scope` or in a `def`:

| Constructor | Builds | Notes |
|---|---|---|
| `service(name, layer)` | `MeterEntity.newService` | `layer` is a string expression resolved at runtime through `Layer.valueOf`; a name that is not registered skips the block, see below |
| `instance(service, instance, layer)` | `newServiceInstance` | properties are a follow-up |
| `endpoint(service, endpoint, layer)` | `newEndpoint` | |
| `serviceRelation(CLIENT \| SERVER, source, sourceLayer, dest, destLayer, componentId)` | `newServiceRelation` with two layers, see [section 5](#5-metrics-through-metersystem) | `componentId` is an `int`, normally `component(...)` |
| `serviceInstanceRelation(CLIENT \| SERVER, sourceService, sourceInstance, sourceLayer, destService, destInstance, destLayer, componentId)` | `newServiceInstanceRelation` with two layers | |

Every constructor applies `NamingControl` to its names, because `MeterEntity` does. An entity held in a `def` has
a compile-time scope type; reassigning it with a different constructor is a compile error, so `scope svc` always
has one shape.

The functions are the `MeterSystem` functions a span point can feed, named for what they do to the values of one
minute:

| `function` | `MeterSystem` function | Value type | Meaning per minute per entity |
|---|---|---|---|
| `cpm` | `sumPerMin` | `Long` | total of the values divided by the bucket's length in minutes, so hour and day rows read as a rate; `value 1` is calls per minute. `sumPerMin` computes `total / getDurationInMinute()`, which is what `CPMMetrics` computes for `core.oal`, so `cpm` is an alias and `sumPerMin` stays accepted |
| `sum` | `sum` | `Long` | raw total of the values; at hour and day level the totals add rather than average |
| `avg` | `avg` | `Long` | mean of the values; `value meta.latency` is mean latency |
| `max`, `min`, `latest` | same | `Long` | |
| `percent` | `percent`, new, see [section 5](#5-metrics-through-metersystem) | `Boolean` from `match` | `matches * 10000 / total`, the `core.oal` `percent(status == true)` scale; `match meta.statusCode != "ERROR"` is `service_sla` |
| `apdex` | `apdex`, new, see [section 5](#5-metrics-through-metersystem) | `ApdexArgument` from `value` and `status` | the `core.oal` `apdex(name, status)`: thresholds from `service-apdex-threshold.yml` looked up by the entity's service name, satisfied under `T`, tolerated under `4T`, `(s * 10000 + t * 5000) / total` |
| `sumLabeled`, `avgLabeled`, `maxLabeled`, `minLabeled`, `latestLabeled`, `sumPerMinLabeled` | same | `DataTable` keyed by the `labels` map | one series per label set, the shape MAL's labeled metrics have; `labels status: attr("http.response.status_code", "http.status_code")` splits a count by status code |
| `histogram` | `sumHistogram` | `BucketedValues` | the observation counted in the bucket it falls into; buckets add across spans |
| `percentile` | `sumHistogramPercentile` | `PercentileArgument` | the same buckets, reduced to the `ranks` at query time. `core.oal`'s `percentile2(10)` buckets by a fixed width instead; explicit buckets are coarser in the tail and cheaper to store |

With `cpm`, `percent` and `apdex` a TAL metric named like an agent metric holds the same number in the same unit, so
an alarm rule or a dashboard widget written for `service_sla` ports to `otlp_service_sla` by renaming the
metric.

`histogram` and `percentile` place the observation in the first bucket whose bound is not below the value and
count `1` there; a value above the last bound lands in the last bucket, so the last bound is a finite overflow
sentinel, the convention `IOSMetricKitSpanListener` follows. Percentiles use the summing function because counts
from different spans add; the averaging variant MAL defaults to gives the same percentiles for one span per
delivery and different ones for several, and there is no reason to prefer it here.

The generated code for a block is the sequence `Analyzer` runs for one MAL entity, without the family in between:

```java
MeterEntity entity = MeterEntity.newService(h.toStr(...), h.layer(...));   // or the def variable
h.traffic(entity);                                                          // once per distinct entity per execution
AcceptableValue<Long> v = h.meterSystem().buildMetrics("otlp_service_cpm", Long.class);
v.accept(entity, h.toLong(1));
h.send(v, h.ctx().meta().getTimestamp());                                   // minute bucket, doStreamingCalculation
```

A `layer` expression that resolves to an unknown name at runtime skips the block, with one warning per name rather
than per span; `Layer.nameOf` is not used for it because it answers `UNDEFINED` for an unknown name, and `UNDEFINED`
is a layer a rule may name, for the `User` end of a relation. A `value` that does not parse as a number skips the
block the same way. Both are visible in a debug session.

#### 3.5 Output records: `outputType`

A rule that declares `outputType` builds one record per span and writes it in `sink {}`. The mechanism is LAL's:
the generated `execute()` creates the output object before the extractor runs, every extractor statement whose name
is not a keyword becomes a setter call on it, validated at compile time, and the sink stage runs the builder's
lifecycle. Two output paths exist, as in LAL.

**Builder path.** `outputType` names a `TALOutputBuilder`, by its `name()` or its FQCN:

```java
public interface TALOutputBuilder {
    String name();                                                        // `SlowSQL`
    default void bindInput(SpanMetadata meta, Object input) { }           // called right after creation
    void init(SpanMetadata meta, Object input, ModuleManager moduleManager); // sink stage: NamingControl, standard fields
    void complete(SourceReceiver sourceReceiver);                         // validate and dispatch the source(s)
    String outputToJson();                                                // the debugger's output payload
}
```

It is `LALOutputBuilder` with `SpanMetadata` in place of `LogMetadata`, in `server-core` next to it, discovered by
`ServiceLoader` into the same kind of name registry. A builder that serves both DSLs implements both interfaces;
`DatabaseSlowStatementBuilder`, today's `SlowSQL`, gains the second one and reads `serviceName`, `layer`,
`timestamp` and `traceId` from `meta` in its span-side `init`, which is the only span-specific code the record
needs. The standard fields, `service`, `instance`, `endpoint`, `layer`, are taken from `meta` at `init`, so the
`service` statement in the extractor names the record's service by overwriting `meta.service`, exactly as a LAL
slow-SQL rule does with `service parsed.service`. The available output fields are the builder's setters:
`id`, `statement` and `latency` for `SlowSQL`.

**Source path.** `outputType` names a `Source` subclass by FQCN. The sink sets `timeBucket` from `meta.timestamp`
and calls `SourceReceiver.receive`; the extractor's field statements are the subclass's setters. This is the escape
hatch to `core.oal`: a builder that fills `ServiceRelation` or `DatabaseAccess` from a span would give an OTLP
service the agent metrics, which is the source-mapping proposal expressed in TAL. The shipped rules do not use it,
and this proposal does not recommend it, because mixing `core.oal` entities with `otlp_*` ones for the same
fleet doubles the identity problem [section 6](#6-the-otlp_service-layer) exists to avoid. It is documented so that
a deployment that wants parity for one record type can have it without a Java change.

The shipped slow-statement rule, part of `tal/otlp-default.yaml`:

```yaml
  - name: otlp-slow-sql
    layer: auto
    inputType: otlp
    outputType: SlowSQL
    dsl: |
      filter {
        if (meta.kind != "CLIENT" || !hasAttr("db.query.text", "db.statement")) {
          abort {}
        }
        // Milliseconds. The agent path keys this by database type (slowDBAccessThreshold); edit per deployment.
        if (meta.latency < 200) {
          abort {}
        }
        extractor {
          layer "OTLP_VIRTUAL_DATABASE"
          // The record belongs to the database service, named as the topology names it.
          def peer = attr("service.peer.name", "peer.service")
          if (!peer) {
            // The port is optional; without it the bare address is the name, not a name ending in a colon.
            def addr = attr("server.address", "net.peer.name")
            def port = attr("server.port", "net.peer.port")
            def peer = addr
            if (port) {
              def peer = addr + ":" + port
            }
          }
          service peer
          id meta.spanId
          statement attr("db.query.text", "db.statement")
          latency meta.latency
        }
        sink {}
        persist {
          enforcer {}          // keep the span behind every slow statement, past this rule's samplers and the receiver's head sampling
        }
      }
```

`DatabaseSlowStatement.traceId` carries the span's trace id in hex, and the record shows on the virtual database's
slow-statement panel because `databaseServiceId` is built from the same name and layer the relation in
[section 7](#7-shipped-rule) gives the peer. The trace link resolves where the service's trace tab does, the same UI
dependency the storage proposal records. `CacheSlowAccess` has no builder today; adding one is the same shape and
a follow-up.

Ordering inside one rule: the extractor runs first and may set metrics, fields and tags in any order; `sink {}`
then decides the record; `persist {}` then decides the span. A rule that aborted anywhere writes nothing and votes
nothing.

#### 3.6 Grammar

`TALLexer.g4` and `TALParser.g4` live in `trace-analyzer/src/main/antlr4`, derived from the LAL grammars:
`PARSED`, `LOG`, `PROCESS_REGISTRY`, the parser-block rules and the four identifier statements are removed; `SPAN`,
`META`, `SCOPE`, `FUNCTION`, `BUCKETS`, `RANKS`, `MATCH`, `STATUS`, `CLIENT`, `SERVER`, `PERSIST`, `ALIAS` and a numeric list literal
`L_BRACKET NUMBER (COMMA NUMBER)* R_BRACKET` are added; `outputFieldStatement` stays as LAL defines it;
`persistBlock` mirrors `sinkBlock`; `abortBlock` joins `extractorStatement`, `sinkStatement` and
`persistStatement`; `SEMI` becomes an optional statement separator. The entity constructors are ordinary
`functionInvocation`s the compiler recognizes by name. Keywords stay usable as identifiers in label names and
getter chains through `anyIdentifier`, so `labels service: meta.service` parses.

### 4. Compiler and runtime

The pipeline is LAL's: ANTLR4 parses the DSL into an immutable model, a Javassist generator emits one class per
rule implementing `TalExpression.execute(FilterSpec, ExecutionContext)`, extractor and sink become private methods
guarded by the abort flag, every spec method takes the context explicitly, no `ThreadLocal`.

The value and condition codegen a TAL rule needs is the shape `LALValueCodegen` and `LALDefCodegen` already have:
getter chains resolved by reflection at compile time, `def` with type inference, casts, arithmetic with JLS-style
numeric promotion, safe navigation, comparisons and the built-in functions. **TAL carries its own copy**, in
`trace-analyzer`'s `compiler` package: `TALValueCodegen`, `TALDefCodegen`, `TALGenCtx`, `TALCodegenHelper`,
`ExprType` and the model interfaces the engine reads a parsed expression through. `log-analyzer` is not touched by
this proposal and not depended on.

Sharing one module between the two DSLs was the earlier plan and was rejected: it would put every future TAL change
on log analysis's path, where a regression in span metrics becomes a regression in log parsing. The isolation is
worth more than the duplication, and the duplication is the cost this design accepts — roughly nineteen hundred
lines that will drift, so a fix in one engine has to be checked against the other. `TALValueCodegen` names
`LALValueCodegen` as its sibling in a header comment for that reason.

What the copy does not duplicate is TAL's own vocabulary, which stays behind one seam, `TALRootResolver`: the
`span` and `meta` roots, `attr()` and the rest of [section 3.2](#32-what-a-rule-can-read), and the entity
constructors. The expression engine calls the resolver and knows nothing about spans. Block structure, spec classes
and the generated-class scaffolding are TAL's own throughout.

Compiling a rule resolves its input and output types first. Each `inputType` entry is matched against the
registered `TALSourceTypeProvider` aliases, then tried as a FQCN that a provider declares; an entry that is
neither is a compile error, because the functions need the provider's attribute reader. The `span` root is typed
to the single declared input, and getter and method chains are resolved on it by reflection at compile time.
`outputType` is matched against the `TALOutputBuilder` name registry, then tried as a FQCN that is a
`TALOutputBuilder` or a `Source` subclass; the output object is created at the top of `execute()`, and every
output-field statement is checked against a setter on it, as LAL does. There is no per-layer default output type:
`outputType` is per rule or absent.

Compiling a rule registers its metrics. The generator collects every `metrics {}` block, including those inside
`if` branches, into a set of `(fullName, function, scopeType)` declarations and calls
`MeterSystem.create(fullName, function, scopeType)` for each before the class is loaded. Two blocks declaring the
same name with the same shape, in one rule or across rules, share one metric, which is how `if` / `else` branches
feed one series; the same name with a different function or scope type is a compile error naming both rules. On
the boot path `create` is create-if-absent, as for MAL; on the runtime-rule path the per-file `ClassPool`,
`ClassLoader` and `StorageManipulationOpt` overload is used, `withSchemaChange()` on the node that applies and
`withoutSchemaChange()` on peers, exactly as `MalFileApplier` does today.

Generated classes are named `org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.{yaml}_L{line}_{rule}` with a
`LocalVariableTable` and a `SourceFile` of `tal/<file>.yaml`, through the shared `DslClassNaming`,
`DslSourceRef` and `DslGeneratedFileWriter`. `SW_DYNAMIC_CLASS_ENGINE_DEBUG=true` dumps them, with the Java
source Javassist compiled, to `{skywalking}/tal-rt/`.

Compile-time validation fails OAP startup, or rejects the runtime-rule push, on: a syntax error, an `inputType`
entry no provider claims, a `span.*` root in a rule with more than one input, a `span.*` or `meta.*` chain with no
matching getter, a function with the wrong arity, a `layer` literal `Layer.nameOf` does not know, an `outputType`
that is neither a registered builder name nor a `TALOutputBuilder` or `Source` class, an output-field statement
with no matching setter, an output-field statement or a `sink {}` in a rule without `outputType`, a `metrics {}`
block missing `name`, `scope` or `function`, `value` missing from any block but `percent` or present on one,
`match` missing from `percent` or present elsewhere, `status` missing from `apdex` or present elsewhere, `labels`
without a labeled function or a labeled function without `labels`, `buckets` missing for `histogram` and
`percentile` or present for anything else, a bucket list
that is empty or not ascending, a metric name declared with two shapes, an extractor `layer` missing from a
`layer: auto` rule that never aborts, and a duplicate rule name within a layer or within the auto set.

Per span, the cost is: one `SpanMetadata`, one attribute index on first use, and the compiled rules of one layer.
There is no reflection, no map-of-maps binding and no closure allocation on the hot path; a `metrics {}` block
allocates one `AcceptableValue` and, for histograms, one `BucketedValues`. Entity traffic is generated once per
distinct entity per execution, not once per block.

The optional `dedupWindow` keeps a bounded seen-set of `(traceId, spanId)` in front of the rules, `SeenSpans` in
`TraceAnalyzerServiceImpl`, so an exporter that retries a batch after a transport timeout does not count a request
twice; a retried span runs no rule, joins no pair and casts no vote. It is off by default because OTLP logs feeding
LAL have the same exposure and no such set today; a deployment that wants exact counts turns it on, knowing the set
is per OAP node and holds at most `dedupCapacity` entries.

### 5. Metrics through `MeterSystem`

Nothing new is needed to store or query a TAL metric. `MeterSystem.create` generates the metric class and installs
its storage model at compile, `buildMetrics` and `doStreamingCalculation` carry each value into the same L1
aggregation and minute, hour and day downsampling MAL metrics get, and the query side reads `otlp_*` as it
reads every meter metric. Three core changes are required, all additive and all shared with MAL afterwards.

**Entity traffic moves to core.** `Analyzer.generateTraffic` and its helpers `toService`,
`serviceRelationTraffic`, `serviceRelationServerSide`, `serviceRelationClientSide`, `serviceInstanceRelationTraffic`
and the process-relation pair are the only code that turns a `MeterEntity` into `ServiceTraffic`, `InstanceTraffic`,
`EndpointTraffic`, `ServiceRelationClientSideMetrics` and `ServiceRelationServerSideMetrics`, the rows the service
lists and `TopologyQueryService` read. They are private to `meter-analyzer`. They move to `server-core` as
`EntityTrafficGenerator.generate(MeterEntity)` next to `MeterSystem`, `Analyzer` delegates to it, and TAL calls
it. No behavior changes for MAL; the MAL `.data.yaml` tests are the guard.

**The component catalog answers a case-insensitive lookup.** `IComponentLibraryCatalogService` gains
`getComponentIdIgnoreCase(name)`, exact match first and then the catalog names folded to lower case, because the
semantic conventions spell systems in lower case, `grpc`, `postgresql`, `kafka`, while `component-libraries.yml`
spells them `GRPC`, `PostgreSQL`, `Kafka`; `getComponentId` is untouched. The profile exporter's mock catalog
implements it too. `component-libraries.yml` gains `Connect_RPC` (id 181), which folds to the conventions'
`connect_rpc`, the system flagd's callers report in the demo; the other `rpc.system` values the catalog lacks,
`java_rmi`, `dotnet_wcf`, `onc_rpc`, and the `apache_dubbo` spelling of `Dubbo`, still resolve to `Unknown`.

**Relations get a layer per side.** `MeterEntity.newServiceRelation` takes one `Layer` for both ends and `id()`
builds both service ids with that layer's `normal` flag. A call from an OTel service to PostgreSQL has a normal
source and a conjectured destination, `OTLP_VIRTUAL_DATABASE`, and the layer of the destination is only known per span.
`MeterEntity` gains `destLayer`, new factory overloads take `sourceLayer` and `destLayer`, `sourceServiceId()` and
`destServiceId()` use their own side's `normal` flag, and for entities built with two layers
`EntityTrafficGenerator` emits `ServiceTraffic` for both ends in their own layers, so a virtual database appears in
the `OTLP_VIRTUAL_DATABASE` list the way the agent path's `ServiceMeta` puts it there. A two-layer relation takes
each layer's own `normal` flag, `UNDEFINED` included, the way the agent path's `ServiceRelation` source builds the
same ids, and an `UNDEFINED` end gets no `ServiceTraffic` row: that is what makes `User` in
[section 6](#6-the-otlp_service-layer) a node without a service list entry. The single-layer factories and
their traffic behavior are untouched; MAL does not use the new overloads unless a later change gives it a syntax
for them.

**Two functions ported from OAL, one alias.** The `core.oal` metrics the dashboards and alarm rules were built on
use four functions a span point can feed: `cpm`, `percent`, `apdex` and `longAvg`. `MeterSystem` has `avg` for
the last, and `sumPerMin` is `cpm` under another name: both compute `total / getDurationInMinute()`, so TAL spells
it `cpm` and maps it to `sumPerMin`. The other two do not exist on the `MeterSystem` side and are added as
`@MeterFunction` classes in `core.analysis.meter.function`, ported from their OAL counterparts:

| Class | Accepts | Fields | `calculate()` |
|---|---|---|---|
| `PercentFunction`, `percent` | `Boolean` | `total`, `match`, `percentage` | `match * 10000 / total`, as `PercentMetrics` |
| `ApdexFunction`, `apdex` | `ApdexArgument{int latency, String serviceName, boolean status}` | `total_num`, `s_num`, `t_num`, `int_value` | `(s_num * 10000 + t_num * 10000 / 2) / total_num`, as `ApdexMetrics`; `t = DICT.lookup(serviceName)`, satisfied when `latency <= t`, tolerated when `latency <= 4t`, unsuccessful spans count only in `total_num` |

Both follow the `AvgFunction` shape: `entityId`, `serviceId`, `combine(Metrics)`, `toHour()`, `toDay()`, a
`StorageBuilder`, `createNew()`. `ApdexFunction` reads the same `ConfigurationDictionary` `ApdexMetrics` reads:
`CoreModuleProvider` already builds `ApdexThresholdConfig` from `service-apdex-threshold.yml` with its dynamic
configuration watcher and hands it to `ApdexMetrics.setDICT`; it hands the same instance to `ApdexFunction`. The
generated TAL code fills `ApdexArgument.serviceName` from `MeterEntity.getServiceName()`, so a service, instance
or endpoint scope gets the service's threshold, as `service_apdex = from(Service.latency).apdex(name, status)`
does. Both classes register in `MeterSystem.functionRegister` by annotation scan like every other function, so MAL
can reach them once it has a syntax for a boolean or an apdex argument; that is not part of this proposal.

**The service catalogs stay separate.** Nothing above touches the trace side: TAL's entity traffic and the
service-name catalogs of [the native OTLP trace storage](SWIP-otlp-native-trace.md) are different rows answering
different questions. `otlp_service_traffic`, `otlp_service_span_traffic` and `otlp_service_relation_traffic` are
TraceQL tag-value dictionaries, keyed by the raw service name and read only by the `resource.service.name`, `name`
and `resource.remote.service` value lists; they carry neither a layer nor a service id. A metric row is found by its
`entity_id`, which the query side builds as `IDManager.ServiceID.buildId(serviceName, normal)`, and the service list
a dashboard is scoped by reads `ServiceTraffic.layer`, so the metrics path needs its own `ServiceTraffic` row. The
two are filled at different points as well: `OTLPSpanForward.send` writes its catalogs after head sampling and the
rate limit, so they hold only the spans that were stored, while TAL runs ahead of the forward on every span, and
folding one into the other would tie the service list and the metric denominators to `otlpTraceSampleRate`. Both
names pass through `NamingControl.formatServiceName`, so a service appears under the same spelling in both and the
jump from an `OTLP_SERVICE` dashboard to its traces resolves.

### 6. The `OTLP_SERVICE` layer

A new normal layer, `Layer.OTLP_SERVICE = register("OTLP_SERVICE", 52, true)`, "a service whose spans arrived over
OTLP and were analyzed by TAL", is where the shipped rule puts them. `GENERAL` is not an option: its dashboards read
`service_cpm`, `service_sla` and the other `core.oal` metrics, which this design does not produce, and a `GENERAL`
service list with empty columns is worse than a separate list.

Three more layers carry the conjectured peers: `OTLP_VIRTUAL_DATABASE = register(..., 53, false)`,
`OTLP_VIRTUAL_CACHE` (54) and `OTLP_VIRTUAL_MQ` (55). They are separate from the agent path's `VIRTUAL_DATABASE`
and friends because a peer's metrics come from a different engine and carry different names, so mixing the two in
one list gives half its rows empty columns. `VIRTUAL_GENAI` is the precedent for a conjectured layer filled from
OTLP spans.

A peer is not just a node in the topology: the rule gives it `otlp_database_access_cpm`, `_resp_time` and `_sla`,
and the cache and MQ counterparts, named after the agent metrics of the same shape so a dashboard widget written
for `database_access_cpm` ports by renaming. Without them the layer's service list would have nothing to show.

All three are **non-normal**, and that is a correctness requirement rather than a style: the layer is only ever a
property of the `ServiceTraffic` row — `ServiceTraffic.id0()` is name plus layer, while a metric's `entityId` is name plus the
`normal` flag — so the layer a peer is listed under never splits its metrics or its topology edges. A peer layer
must therefore stay non-normal; registering one as normal would change the peer's service id and stop it merging
with the agent-observed node.

A service id is its name plus the `normal` flag, not the layer, so a service that reports both agent segments and
OTLP spans is one entity listed under both layers, each with its own dashboards, which is how a service today can
be both `GENERAL` and `K8S_SERVICE`. A process running an agent and an SDK at once is counted in both places; that
is documented, not prevented. Rules that want another layer pass it to the entity constructors; a fleet that
stamps `service.layer` on its resources writes its rules under that layer.

**Address-named peers and `NetworkAddressAliasSetup`.** A CLIENT span names its peer by what it dialed,
`server.address:server.port`, and the callee names itself by `service.name`; nothing in OTLP ties the two, since
W3C `traceparent` carries no address. A fleet that does not set `peer.service` therefore gets
`productcatalogservice:3550` beside `productcatalogservice` in the `OTLP_SERVICE` list and in the topology, which is
what OpenObserve's service graph shows for the same demo, `oteldemo.CartService` beside `cart`. The agent path
solves this with the alias the `sw8` header lets the callee register: `NetworkAddressAliasMappingListener` emits a
`NetworkAddressAliasSetup` for the address the caller used, the row lands in `network_address_alias`,
`CacheUpdateTimer` reloads it into `NetworkAddressAliasCache` every 10 seconds, and both `RPCAnalysisListener` at
analysis time and `ServiceTopologyBuilder` at query time substitute the represented service for the address. TAL
reuses the row, the table, the timer and both readers; only the source of the address differs. A SERVER span's
`server.address` and `server.port` are the `Host` or `:authority` the caller sent, which the HTTP semantic
conventions define as the same value on both sides, so the shipped rule's `alias <address>` statement registers
that address for `meta.service`, with the `normal` flag of `meta.layer` so the id is the one
`service(meta.service, meta.layer)` gave the SERVER span's own metrics, and `resolveAlias(peer)` on the CLIENT
branch returns the represented service's name when the cache has the address, or the address unchanged. The rows
travel through storage and the timer, so the first calls to a freshly seen address are still address-named, and
the query-time fold covers those.

Two guards keep the alias honest, both backed by `KnownServices`, a bounded per-OAP registry of the service
names seen on analyzed spans that `TraceAnalyzerServiceImpl` feeds before any rule runs. First, `alias` skips an
address whose host names another service: an Envoy that keeps the `Host` header hands `frontend-proxy:8080` to
the `frontend` behind it, whose SERVER span would otherwise register the proxy's address as its own and pull
every edge to the proxy onto itself, which is what the demo run showed before the guard. Second, `resolveAlias`
asks the registry before the cache: a host, or its first DNS label, that is a known service's name resolves to
that service, so `product-catalog:3550` lands on `product-catalog` although its Go server reports its IP, and a
row a proxy did manage to poison cannot redirect the edge. That is OpenObserve's first-label rule and its
self-key override in one place. The registry is per OAP node; a node that has not yet seen a service's spans
applies the guard late, not wrongly, since the fallback is the address-named node. Both `alias` and the pairing
below write their rows through one `AliasRegistrar`, so the guard covers a learned row as well. Coverage of the
alias row itself follows the SDK: HTTP servers fill `server.address` from the request
headers; gRPC servers only when the instrumentation does, and the demo's Go server does not, so a callee that
reports no address is only ever reached by name. The rest is covered by the pairing.

**Pairing a call's two spans.** A peer that neither the registry nor the cache can name, a callee dialed by IP,
or one whose SDK reports its own address under another spelling or not at all, is resolved from the trace context:
the SERVER span's `parentSpanId` is the CLIENT span's id, which needs no attribute convention on either side.
Each half of a call is a `PairingFact`, a `StreamData` of the trace id, span id, parent span id, service,
`normal` flag, instance, timestamp, and for the CLIENT side the address it dialed. `SpanPairing`, a module
service, sends every fact to the node that owns the trace through the core's `RemoteSenderService` with
`Selector.HashCode` on the trace id, the channel and the selector `MetricsRemoteWorker` uses to bring every value
of one metric to one node; `PairingWorker`, registered under `TAL_SPAN_PAIRING` with the core's worker registry,
is the receiving end. The two spans arrive in separate batches, in either order, and at different nodes, and both
facts still meet at the owner. The owner keeps two bounded buffers of 60 seconds: callers whose peer `resolveAlias`
could not name, keyed by their own span id, and callees, keyed by their parent span id, which
`TraceAnalyzerServiceImpl` sends for every SERVER span with a parent before any rule runs; a CONSUMER span is left
out, because its parent is the PRODUCER span and pairing the two would register the broker's address as an alias of
the consumer, which the Intro-to-MLTP run did before the exclusion. A match
writes `NetworkAddressAliasSetup(peer -> callee)` and the owner remembers the answer for ten minutes, so its own
next callers of that address resolve before the row has come back through storage; every other node resolves it
through its cache once the timer has loaded the row. When the CLIENT side is analyzed on the owner and the SERVER
span is already there, `resolveAlias` returns the callee at once and that very edge lands on it; otherwise the
edge is address-named this once. A single node, and a cluster whose membership has not loaded yet, pairs locally.
`pairingSamplePercent`, 25 by default, limits a cluster's channel traffic to a share of traces chosen by a second
hash of the trace id, separate from the one that picks the owner so that the sampled share is spread over every node
rather than over the residues the owner selection shares with it, and which both halves compute alike; an address only has to be learned once, so a busy edge still learns within
its first few calls, and a single node ignores the rate. Root spans have nothing to pair with and are not kept.
This is the join Tempo's service-graph processor and OpenObserve's pairing learner run over stored spans, done at
analysis time instead and without their tables: one row per address is all the alias mechanism needs. The
buffers cost at most 200,000 entries each; a call whose SERVER span never arrives, an uninstrumented callee or a
dropped trace context, leaves its caller to expire and its edge address-named, which is the honest answer. While
the cluster membership changes, the hash re-targets and a pair in flight can split, which delays learning that
address by one more call, the same window the metrics channel has.

**The `User` node.** A root SERVER span was called from outside the fleet. The shipped rule draws that as
`serviceRelation(SERVER, "User", "UNDEFINED", meta.service, meta.layer, comp)` with one
`service_relation_server_cpm` value, the edge `RPCAnalysisListener` draws for an entry span without a reference.
`User`'s id is built from `Layer.UNDEFINED`'s own flag, non-normal, which is the id `ServiceTopologyBuilder`
recognizes and labels, and `EntityTrafficGenerator` writes no `ServiceTraffic` for an `UNDEFINED` end, so `User` is
a node of the topology and not a service in any list, as on the agent path.

### 7. Shipped rule

`tal/otlp-default.yaml` is the whole feature: the semantic-convention knowledge, with the current attribute names
first and the deprecated spellings older SDKs still emit as fallbacks, and the metric set the `OTLP_SERVICE`
dashboards use. It holds two rules, `otlp-service` below and `otlp-slow-sql` from
[section 3.5](#35-output-records-outputtype). Both declare `inputType: otlp`; a deployment that wants the same
analysis for Zipkin-reported spans would add `zipkin` to that line once the reserved Zipkin provider of
[section 2.2](#22-zipkin--reserved-not-in-scope) exists. It is written to be edited.

```yaml
metricPrefix: otlp

rules:
  - name: otlp-service
    # `auto` means: see the spans whose resource carries no `service.layer`, and name the layer yourself.
    layer: auto
    inputType: otlp
    dsl: |
      filter {
        // Spans with a dedicated analyzer: MetricKit payloads and GenAI calls are handled by their SpanListeners.
        if (meta.scopeName == "MetricKit" || hasAttr("gen_ai.provider.name", "gen_ai.system")) {
          abort {}
        }
        // A messaging poll is transport bookkeeping, not a request anyone can be measured on. The OpenTelemetry
        // Java agent reports a blocking JMS receive as a CONSUMER span as long as the wait, so the kind alone cannot
        // tell a poll from a handled message; the operation can.
        if ((meta.kind == "CLIENT" || meta.kind == "CONSUMER")
            && attr("messaging.operation.type", "messaging.operation") == "receive") {
          abort {}
        }

        extractor {
          layer "OTLP_SERVICE"

          // Inbound: SERVER and CONSUMER spans are requests this service answered.
          if (meta.kind == "SERVER" || meta.kind == "CONSUMER") {
            // `{method} {route}` when the framework reports a route; otherwise the span name stays the endpoint.
            if (hasAttr("http.route")) {
              endpoint attr("http.request.method", "http.method") + " " + attr("http.route")
            }
            def svc = service(meta.service, meta.layer)
            metrics { name "service_cpm";        scope svc; function cpm; value 1 }
            metrics { name "service_resp_time";  scope svc; function avg; value meta.latency }
            metrics { name "service_sla";        scope svc; function percent; match meta.statusCode != "ERROR" }
            metrics { name "service_apdex";      scope svc; function apdex; value meta.latency; status meta.statusCode != "ERROR" }
            metrics { name "service_percentile"; scope svc; function percentile; value meta.latency
                      buckets [10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000] }

            def ep = endpoint(meta.service, meta.endpoint, meta.layer)
            metrics { name "endpoint_cpm";        scope ep; function cpm; value 1 }
            metrics { name "endpoint_resp_time";  scope ep; function avg; value meta.latency }
            metrics { name "endpoint_sla";        scope ep; function percent; match meta.statusCode != "ERROR" }
            metrics { name "endpoint_percentile"; scope ep; function percentile; value meta.latency
                      buckets [10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000] }

            if (meta.serviceInstance) {
              def inst = instance(meta.service, meta.serviceInstance, meta.layer)
              metrics { name "instance_cpm";       scope inst; function cpm; value 1 }
              metrics { name "instance_resp_time"; scope inst; function avg; value meta.latency }
              metrics { name "instance_sla";       scope inst; function percent; match meta.statusCode != "ERROR" }
            }
          }

          // A consumer was called by its broker.
          if (meta.kind == "CONSUMER" && hasAttr("messaging.system")) {
            def broker = attr("server.address", "net.peer.name")
            if (broker && hasAttr("server.port", "net.peer.port")) {
              def broker = broker + ":" + attr("server.port", "net.peer.port")
            }
            // A consumer that reports no broker address, as the JMS API gives its clients none, lands on the node
            // named after the messaging system, the same one the producer side draws below, so one queue has one
            // broker. The port alone would be a worse name than that.
            if (!broker) {
              def broker = attr("messaging.system")
            }
            def fromBroker = serviceRelation(SERVER, broker, "OTLP_VIRTUAL_MQ", meta.service, meta.layer,
                                             component(attr("messaging.system")))
            metrics { name "service_relation_server_cpm"; scope fromBroker; function cpm; value 1 }
          }

          // Inbound, SERVER only: what this service was dialed as, and who dialed it.
          if (meta.kind == "SERVER") {
            def comp = component("http")
            if (hasAttr("rpc.system.name", "rpc.system")) {
              def comp = component(attr("rpc.system.name", "rpc.system"))
            }
            // The address a SERVER span reports is the `Host` / `:authority` its caller used, so registering it as
            // an alias lets a CLIENT span's resolveAlias(peer) below land the edge on this service instead of on a
            // second node named by the address. A callee that reports no address is only ever reached by name.
            // An address whose host is another service's name, a proxy's `Host` passed through, is not registered.
            if (hasAttr("server.address", "net.host.name")) {
              def addr = attr("server.address", "net.host.name")
              def port = attr("server.port", "net.host.port")
              def dialed = addr
              if (port) {
                def dialed = addr + ":" + port
              }
              alias dialed
            }
            // A root span was called from outside the fleet, which the agent path draws as `User`.
            if (meta.root) {
              def fromUser = serviceRelation(SERVER, "User", "UNDEFINED", meta.service, meta.layer, comp)
              metrics { name "service_relation_server_cpm"; scope fromUser; function cpm; value 1 }
            }
          }

          // Outbound: CLIENT and PRODUCER spans are calls this service made. The peer decides the edge.
          if (meta.kind == "CLIENT" || meta.kind == "PRODUCER") {
            // The peer is named first, because the metrics below are scoped to it. `peer.service` is the
            // deprecated spelling of `service.peer.name`; SDKs on either side of that rename still emit one of them.
            def peer = attr("service.peer.name", "peer.service")
            if (!peer && hasAttr("server.address", "net.peer.name")) {
              // The port is optional. Without it the node is the bare address, the spelling the callee's own
              // service name takes, rather than a third one ending in a colon.
              def addr = attr("server.address", "net.peer.name")
              def port = attr("server.port", "net.peer.port")
              def peer = addr
              if (port) {
                def peer = addr + ":" + port
              }
            }
            // Envoy, and other instrumentations of OpenTracing descent, name the peer as `peer.address`, host and
            // port in one string, and report no server.address at all.
            if (!peer && hasAttr("peer.address")) {
              def peer = attr("peer.address")
            }
            // A client that names its target only as a URL, the Python requests instrumentation among them, still
            // carries the host and port there.
            if (!peer && hasAttr("url.full", "http.url")) {
              def peer = urlHost(attr("url.full", "http.url"))
            }
            // A peer whose host names a service, or that some SERVER span registered through `alias`, resolves to
            // that service, so the edge lands on the callee's own node instead of one named by the address.
            def peer = resolveAlias(peer)
            // A peer reached without an address, an embedded database or a broker the JMS API hides, is named
            // after what the span does know: the database system and its namespace, or the messaging system alone,
            // with the destination carried as an endpoint of that node below. Neither is an address, so the alias
            // cache and the pairing have nothing to fold and are not asked. A client span that knows none of these
            // still names no peer and draws no edge.
            if (!peer && hasAttr("db.system.name", "db.system")) {
              def peer = attr("db.system.name", "db.system")
              if (hasAttr("db.namespace", "db.name")) {
                def peer = peer + "/" + attr("db.namespace", "db.name")
              }
            }
            if (!peer && hasAttr("messaging.system")) {
              def peer = attr("messaging.system")
            }
            if (peer) {
              def peerLayer = "OTLP_SERVICE"
              def comp = component("http")
              if (hasAttr("rpc.system.name", "rpc.system")) {
                def comp = component(attr("rpc.system.name", "rpc.system"))
              }
              if (hasAttr("db.system.name", "db.system")) {
                def system = attr("db.system.name", "db.system")
                def peerLayer = "OTLP_VIRTUAL_DATABASE"
                if (system == "redis" || system == "valkey" || system == "memcached") {
                  def peerLayer = "OTLP_VIRTUAL_CACHE"
                }
                def comp = component(system)
              }
              if (hasAttr("messaging.system")) {
                def peerLayer = "OTLP_VIRTUAL_MQ"
                def comp = component(attr("messaging.system"))
              }

              // A conjectured peer gets metrics of its own, not just a node in the topology, so its layer's service
              // list has columns to fill. Named after the agent metrics of the same shape, so a widget written for
              // `database_access_cpm` ports by renaming.
              def peerSvc = service(peer, peerLayer)
              if (peerLayer == "OTLP_VIRTUAL_DATABASE") {
                metrics { name "database_access_cpm";       scope peerSvc; function cpm; value 1 }
                metrics { name "database_access_resp_time"; scope peerSvc; function avg; value meta.latency }
                metrics { name "database_access_sla";       scope peerSvc; function percent
                          match meta.statusCode != "ERROR" }
              }
              if (peerLayer == "OTLP_VIRTUAL_CACHE") {
                metrics { name "cache_access_cpm";       scope peerSvc; function cpm; value 1 }
                metrics { name "cache_access_resp_time"; scope peerSvc; function avg; value meta.latency }
                metrics { name "cache_access_sla";       scope peerSvc; function percent
                          match meta.statusCode != "ERROR" }
              }
              if (peerLayer == "OTLP_VIRTUAL_MQ") {
                metrics { name "mq_access_cpm";       scope peerSvc; function cpm; value 1 }
                metrics { name "mq_access_resp_time"; scope peerSvc; function avg; value meta.latency }
                metrics { name "mq_access_sla";       scope peerSvc; function percent
                          match meta.statusCode != "ERROR" }
                // The queue or topic is an endpoint of its broker, so the virtual layer's list stays one row per
                // broker while the destinations are still there to rank.
                if (hasAttr("messaging.destination.name", "messaging.destination")) {
                  def dest = endpoint(peer, attr("messaging.destination.name", "messaging.destination"), peerLayer)
                  metrics { name "mq_destination_cpm";       scope dest; function cpm; value 1 }
                  metrics { name "mq_destination_resp_time"; scope dest; function avg; value meta.latency }
                  metrics { name "mq_destination_sla";       scope dest; function percent
                            match meta.statusCode != "ERROR" }
                }
              }

              def edge = serviceRelation(CLIENT, meta.service, meta.layer, peer, peerLayer, comp)
              metrics { name "service_relation_client_cpm";       scope edge; function cpm; value 1 }
              metrics { name "service_relation_client_resp_time"; scope edge; function avg; value meta.latency }
              metrics { name "service_relation_client_sla";       scope edge; function percent
                        match meta.statusCode != "ERROR" }
            }
          }
        }
        // No outputType and no persist block: this rule writes no record and takes no part in the span's persistence.
      }

  - name: otlp-slow-sql
    layer: auto
    inputType: otlp
    outputType: SlowSQL
    dsl: |
      filter {
        if (meta.kind != "CLIENT" || !hasAttr("db.query.text", "db.statement")) {
          abort {}
        }
        // Milliseconds. The agent path keys this by database type (slowDBAccessThreshold); edit per deployment.
        if (meta.latency < 200) {
          abort {}
        }
        extractor {
          layer "VIRTUAL_DATABASE"
          // The record belongs to the database service, named as the topology names it.
          def peer = attr("service.peer.name", "peer.service")
          if (!peer) {
            // The port is optional; without it the bare address is the name, not a name ending in a colon.
            def addr = attr("server.address", "net.peer.name")
            def port = attr("server.port", "net.peer.port")
            def peer = addr
            if (port) {
              def peer = addr + ":" + port
            }
          }
          // A statement with no database to file it under would land on a service named `_blank`, where no
          // panel reads it.
          if (!peer) {
            abort {}
          }
          service peer
          id meta.spanId
          statement attr("db.query.text", "db.statement")
          latency meta.latency
        }
        sink {}
        persist {
          enforcer {}          // keep the span behind every slow statement, past this rule's samplers and the receiver's head sampling
        }
      }
```

Compiling the `otlp-service` rule registers sixteen metrics: `otlp_service_cpm`, `_service_resp_time`,
`_service_sla`, `_service_apdex`, `_service_percentile`, the four endpoint counterparts, the three instance
counterparts, `_service_relation_client_cpm`, `_resp_time`, `_sla`, and `_service_relation_server_cpm`. Each is
the function `core.oal` uses for the agent metric of the same name, `cpm`, `avg`, `percent`, `apdex`, so the
values and units match: `otlp_service_sla` is in ten-thousandths like `service_sla`, and
`otlp_service_cpm` reads as a rate at hour and day level like `service_cpm`. The `otlp_` prefix keeps
the two sets apart in storage and in queries.

After this one file, an OpenTelemetry SDK service appears under `OTLP_SERVICE` with load, latency, success rate
and percentiles at service, instance and endpoint level, the topology shows the client-side edge of every call
with its protocol as the line type, databases, caches and brokers it calls appear as `OTLP_VIRTUAL_DATABASE`,
`OTLP_VIRTUAL_CACHE` and `OTLP_VIRTUAL_MQ` nodes, consumers hang off their broker, and database calls slower than the
threshold show as slow statements on the virtual database. Alarm rules reference the `otlp_*` names.

#### 7.1 What a span can produce

The sixteen metrics above rest on data every span carries, except the two relation families, which need a peer
attribute. What else a rule can derive depends on which conventions the SDK emits, and the tables below are the
catalog a rule author reads before adding a `metrics {}` block. They describe the wire format, not this design.

The floor, from data every span carries:

| Span data | Read as | Metric |
|---|---|---|
| the span itself, plus `service.name`, `service.instance.id`, `Span.name` | `meta.service`, `meta.serviceInstance`, `meta.endpoint` | call count, `function cpm`, at service, instance and endpoint |
| `end_time_unix_nano - start_time_unix_nano` | `meta.latency` | `avg`, `max`, `min`, `percentile`, `histogram`, `apdex` |
| `Status.code` | `meta.statusCode` | `percent`, the success rate |
| `kind` | `meta.kind` | which side of the entity a value lands on, not a metric of its own |
| an empty `parentSpanId` | `meta.root` | traces per minute and end-to-end trace duration, by restricting a block to roots |

Beyond the floor, one attribute family at a time:

| Attributes | What one more block buys |
|---|---|
| `service.peer.name`, its deprecated spelling `peer.service`, `server.address`, `server.port` | the relation metrics the shipped rule writes; per-peer metrics with `scope service(peer, peerLayer)` |
| `http.request.method`, `http.route` | the endpoint name the shipped rule builds, and with it every endpoint-scoped metric |
| `http.response.status_code` | counts split by status with `labels status: attr(...)`, hence a 4xx and a 5xx rate |
| `http.request.body.size`, `http.response.body.size` | request and response bytes, `function sum` or `avg` |
| `rpc.system.name`, `rpc.system`, `rpc.grpc.status_code` | the component id of the edge, current spelling first; counts split by gRPC status |
| `peer.address` | the peer of an Envoy egress span, host and port in one string, read when a CLIENT span reports no `server.address` or `net.peer.name` |
| `db.system.name`, `db.operation.name`, `db.collection.name`, `db.namespace` | the `OTLP_VIRTUAL_DATABASE` edge; counts and latency split by operation or by table |
| `messaging.system`, `messaging.destination.name`, `messaging.operation.type` | the `OTLP_VIRTUAL_MQ` edge; produce and consume counts split by destination |
| `messaging.message.body.size` | message bytes |
| `db.query.text`, `db.statement` | a `SlowSQL` record rather than a metric, see [section 3.5](#35-output-records-outputtype) |
| `exception` events, through `eventAttr("exception", "exception.type")` | exception counts split by type |
| `telemetry.sdk.language`, `service.version`, `deployment.environment`, `k8s.*` | any metric above, split by a `resource(...)` label |
| `gen_ai.*` | nothing here. The shipped rule aborts on these spans; `gen-ai-analyzer` and `virtual-gen-ai.oal` already produce `gen_ai_provider_cpm`, `_input_tokens_sum`, `_total_estimated_cost` and `gen_ai_model_ttft_avg` per provider and per model |

What no rule can produce, because the data is not on the wire. [The source-mapping proposal's gap
analysis](SWIP-otlp-trace-metrics.md#9-gaps-compared-with-agent-traces) states these against the agent path; they
bind TAL identically, since both read the same span:

- **Server-observed relation metrics.** A SERVER span carries `parentSpanId` and no caller identity, so
  `otlp_service_relation_server_*` fills only where the caller is known another way: `User`, a configured
  gateway, or the broker of a consumer. Every other edge carries client-observed numbers only.
- **Endpoint-level dependency.** No parent endpoint exists on the wire, and `MeterSystem` has no endpoint-relation
  scope to write one to; `ScopeType` stops at `SERVICE_INSTANCE_RELATION`.
- **Message transmission latency.** The messaging conventions carry no producer timestamp.
- **Cache command and key.** No key attribute exists, and the operation can only be inferred from
  `db.operation.name`.
- **Trustworthy instance metrics without `service.instance.id`.** Without it every process is the same instance, so
  the block produces a number that is wrong rather than absent, which is why it sits behind
  `if (meta.serviceInstance)`.
- **Resource utilization.** CPU, memory and GC are OTLP metrics, a different signal reaching OAP another way.
- **Anything needing a second span.** A rule sees one span and its own `ExecutionContext`, so spans per trace, error
  propagation and critical-path metrics are out of reach. The root span's own latency is the whole trace's duration,
  and that is the only trace-wide fact a single span holds.

One caveat over every number above: head sampling in the SDK is normal, so a count is of the spans that arrived,
not of the requests that happened. TAL runs ahead of the receiver's own sampling, see [section 2.1](#21-otlp), so it
does not narrow the set further, but it cannot recover what the SDK dropped.

### 8. Worked example

The `otlp-traces` e2e runs the OpenTelemetry Demo's `frontend` (Node.js) and `productcatalogservice` (Go); one
`GET /api/products` produces three spans, in the attribute spelling the demo's instrumentation emits:

```text
resource A: service.name=frontend  service.namespace=opentelemetry-demo  telemetry.sdk.language=nodejs
  span 1  SERVER  "HTTP GET"  root                            110 ms  status UNSET
          http.method=GET  http.target=/api/products  http.status_code=200  net.host.name=frontend  net.host.port=8080
  span 2  CLIENT  "grpc.oteldemo.ProductCatalogService/ListProducts"  parent=span 1   9 ms  status UNSET
          rpc.system=grpc  rpc.service=oteldemo.ProductCatalogService  rpc.method=ListProducts
          rpc.grpc.status_code=0  net.peer.name=productcatalogservice  net.peer.port=3550
resource B: service.name=productcatalogservice  telemetry.sdk.language=go
  span 3  SERVER  "oteldemo.ProductCatalogService/ListProducts"  parent=span 2 (remote)   4 ms  status UNSET
          rpc.system=grpc  rpc.service=oteldemo.ProductCatalogService  rpc.method=ListProducts  rpc.grpc.status_code=0
          server.address=productcatalogservice  server.port=3550   (the :authority; the fixture sets it, the demo's Go server omits it)
```

Neither resource carries `service.layer`, so the `layer: auto` rule runs for all three.

**Span 1**, `frontend` SERVER, root. `meta` arrives as `service=frontend`, `serviceInstance=""`, `endpoint="HTTP GET"`,
`kind=SERVER`, `statusCode=UNSET`, `latency=110`. Neither abort condition holds. `layer "OTLP_SERVICE"` sets
`meta.layer`. The inbound branch runs; there is no `http.route`, so the endpoint stays `HTTP GET`.
`svc` is `MeterEntity{SERVICE, frontend, OTLP_SERVICE}`; its traffic, `ServiceTraffic{frontend, OTLP_SERVICE}`, is
emitted once. Then, at minute bucket of the span's end:

| Block | Metric | Value fed to `MeterSystem` |
|---|---|---|
| `service_cpm` | `otlp_service_cpm` | `1` into `total`; the minute row reads `total / 1` |
| `service_resp_time` | `otlp_service_resp_time` | `110`, averaged |
| `service_sla` | `otlp_service_sla` | `match = true`; the row reads `10000` while every span of the minute matches |
| `service_apdex` | `otlp_service_apdex` | `ApdexArgument{110, frontend, true}`; `frontend` has no entry in `service-apdex-threshold.yml`, so `default`, `500`, applies and the span is satisfied |
| `service_percentile` | `otlp_service_percentile` | `BucketedValues` with `1` in bucket `250`, `0` elsewhere, ranks `50, 75, 90, 95, 99` |
| the four `endpoint_*` blocks | `otlp_endpoint_*` | the same values on `MeterEntity{ENDPOINT, frontend, HTTP GET}`, plus `EndpointTraffic{HTTP GET}` |

`meta.serviceInstance` is empty, so the instance branch is skipped and no `instance_*` values are emitted for this
fleet; `service.instance.id` is the fix, as it is for OTLP logs.

The SERVER-only block runs next. `net.host.name` and `net.host.port` are present, so `alias "frontend:8080"` emits
`NetworkAddressAliasSetup{address=frontend:8080, representServiceId=frontend/normal}` in the minute bucket of the
span's end. The span is root, so `fromUser` is `MeterEntity{SERVICE_RELATION, SERVER, User/UNDEFINED ->
frontend/OTLP_SERVICE, component 49}`; its traffic is `ServiceRelationServerSideMetrics{User -> frontend,
componentIds [49]}` and no `ServiceTraffic` for `User`, and `1` goes into `otlp_service_relation_server_cpm`. Ten
values in all for span 1.

**Span 2**, `frontend` CLIENT. The outbound branch runs. `attr("peer.service")` is empty; `rpc.system` is present, so
`comp = component("grpc")`, which resolves `GRPC`, id `23`; no `db.*`, no `messaging.*`; `net.peer.name` is present
under the fallback spelling, so `peer = "productcatalogservice:3550"` and `peerLayer` stays `OTLP_SERVICE`. `edge`
is `MeterEntity{SERVICE_RELATION, CLIENT, frontend/OTLP_SERVICE -> productcatalogservice:3550/OTLP_SERVICE,
component 23}`. Its traffic is `ServiceRelationClientSideMetrics{frontend -> productcatalogservice:3550,
componentIds [23]}` plus `ServiceTraffic` for both ends. Three values follow: `1` into the `total` of
`otlp_service_relation_client_cpm`, `9` averaged into `_resp_time`, `match = true` into `_sla`. The
topology draws `frontend -> productcatalogservice:3550` as a gRPC line.

**Span 3**, `productcatalogservice` SERVER. As span 1 for `productcatalogservice` with endpoint
`oteldemo.ProductCatalogService/ListProducts` and latency 4. The span has a parent, so no `User` edge, and a server
span cannot name its caller, so no relation. `server.address` and `server.port` are present, so
`alias "productcatalogservice:3550"` emits `NetworkAddressAliasSetup{address=productcatalogservice:3550,
representServiceId=productcatalogservice/normal}`, the row the next request's span 2 resolves.

The `otlp-slow-sql` rule runs on all three spans as well, since they carry no `service.layer` either, and aborts on
its first condition every time: none is a database client span. Nothing is written and no vote is cast.

The result is two `OTLP_SERVICE` services with service and endpoint metrics, the `User -> frontend` edge, the
`frontend -> productcatalogservice:3550` edge, and two alias rows. The address-named node is what the first request
draws: when span 2 is analyzed, `productcatalogservice` has not emitted a span yet, so its name is not in
`KnownServices`, and span 3's alias row is not in `NetworkAddressAliasCache` either, which `CacheUpdateTimer` fills
from `network_address_alias` every 10 seconds. The next `GET /api/products` lands on
`frontend -> productcatalogservice`: `resolveAlias("productcatalogservice:3550")` returns `productcatalogservice`,
by now through the host rule alone, with the cache row behind it for a spelling that is not the name; the same
`serviceRelation` builds `MeterEntity{SERVICE_RELATION, CLIENT, frontend/OTLP_SERVICE ->
productcatalogservice/OTLP_SERVICE, component 23}`, a relation row of its own, and `ServiceTopologyBuilder` folds
the first request's address-named edge into it at query time through the same cache. `peer.service` on the client
remains the SDK-side alternative: had the demo set it, the first edge would have landed on the service directly.

Had the same spans come from a current SDK, the attribute names would read `http.request.method`,
`http.response.status_code`, `server.address` and `server.port`; every `attr()` in the rule lists the current name
first and the demo's spelling second, so the entities and values are identical. One rule, one set of entities,
across SDK generations.

### 9. Runtime-rule hot update

`Catalog` gains `TAL("tal")`. A `TalRuleEngine` for the catalog is modeled on `LalRuleEngine` with a
`TalFileApplier` over `SpanFilterListener.Factory`, and borrows the metric-shape guard from `MalRuleEngine`
because a TAL file, unlike a LAL file, owns storage: `claimedKeys` are the `<layer>:<ruleName>` pairs and the full
metric names the file declares, so two files declaring the same metric is a cross-file collision;
`storageImpactKeys` are the metric names added, removed or reshaped between the prior and the new content, computed
from the declarations, which is simpler than `MalShapeExtractor` because they are literals, plus the rule names
whose `outputType` was added, removed or changed, the same rule LAL applies because those reroute records to a
different storage-backed type; `recordBundledClaims`
loads the shipped file's claims at boot; compile runs in a per-file `RuleClassLoader` from `DSLClassLoaderManager`
so a replaced bundle's classes are dropped together, and `MeterSystem.create` is called with that loader and the
main or peer `StorageManipulationOpt`. `POST /runtime/rule/addOrUpdate?catalog=tal` follows the documented
consistency contract: persist is commit, every node converges on its next scan, a change that adds or reshapes a
metric is a storage change and needs `allowStorageChange`, exactly as for a MAL file. A body-only change, a new
condition or a changed `attr()` fallback, applies without pausing anything.

### 10. Live debugging

`trace-analyzer` gains `TALDebug` static probes and a `TALDebugRecorder` interface, typed to `ExecutionContext`,
`Span`, `MeterEntity` and `AcceptableValue`, with the probe sites LAL and MAL have between them: the input at the
top of `execute`, each block, each statement when `granularity=statement`, and a terminal probe per `metrics {}`
block carrying the metric name, the entity id and the value handed to `MeterSystem`, which is the same boundary
MAL's `captureMeterEmit` marks. The `dsl-debugging` module gains a `TALHolderRegistry` and recorder factory, the
static loader and the runtime-rule engine publish the per-rule `GateHolder` under `RuleKey(TAL, <file>, <rule>)`,
and the input payload is rendered by the input's `TALSourceTypeProvider`, OTLP/JSON for an OTLP span and Zipkin
JSON for a Zipkin one, alongside the derived `meta`; the output payload of a rule with an `outputType` is the
builder's `outputToJson()`, as it is for LAL, and the `sink {}` probe records whether the record was written.
Codegen injection follows `DSLDebugCodegenSwitch` as the other three DSLs do.
`docs/en/setup/backend/admin-api/dsl-debugging-tal.md` is the operator reference, listed in `docs/menu.yml` next
to the LAL page.

A dry run needs no new endpoint: opening a debug session on `otlp-service` and sending one OTLP/JSON payload
through `POST /v1/traces` shows every `attr()` result, every `def`, the branch taken and every metric value
emitted, without waiting for a dashboard to fill.

### 11. Self-observability

On the module's telemetry: `trace_analyzer_spans` labeled by `layer` and `outcome`, one of `analyzed`, `no_rule`,
`aborted`, `unknown_layer`, `duplicate`; `trace_analyzer_latency`, a histogram of the per-span rule evaluation,
which is the number to watch when a rule file grows; `trace_analyzer_metrics_skipped` labeled by `reason`,
`unknown_layer` or `bad_value`, for blocks skipped at runtime; `trace_analyzer_records` labeled by `outputType`,
records a `sink {}` wrote; `trace_analyzer_dropped_by_persist`, spans a `persist {}` vote removed from storage; and
`trace_analyzer_enforced`, spans an `enforcer {}` carried past the receiver's head sampling.
The `layer` label of `trace_analyzer_spans` is joined by an `input` label, `otlp` or `zipkin`.

### 12. Dashboards and menu

The `OTLP_SERVICE` layer gets the standard four templates in the Horizon UI dashboard bundle, root list, service,
instance and endpoint, built on the sixteen metrics of [section 7](#7-shipped-rule), with the topology and trace
tabs the General dashboards have. The service list columns are `otlp_service_cpm`,
`otlp_service_sla`, `otlp_service_resp_time` and `otlp_service_apdex`, the columns of the General
list; because the functions and units are the same, the templates are the General templates with the metric names
replaced, not new widget designs. A menu entry "OpenTelemetry Service" under the service group points at the layer and at the operator doc. The trace
tab queries whichever store the deployment uses, Zipkin or `/otlp`, which is the same UI dependency the storage
proposal records.

### 13. Tests

- Grammar and generator unit tests as `log-analyzer` has them: parser tests over every statement, generator tests
  for typed `span.*` access on both input types, `meta` overwrite, `attr()` fallback order, every entity
  constructor, every function's value type, `buckets` placement and the overflow bucket, `labels` with labeled
  functions, `abort` in every position, `persist` voting and the `enforced` flag, output-field setters and the `sink` lifecycle for a
  builder and for a plain `Source`, input routing by `isInstance`, and every compile-time rejection in
  [section 4](#4-compiler-and-runtime).
- Alias and `User` tests in `TALExecutionTest` and the worked example: the `alias` statement's row carries the
  address, the layer's `normal` flag and the span's minute bucket, and an empty address registers nothing;
  `resolveAlias` substitutes the represented service against a loaded cache and leaves an unknown address alone;
  `alias` skips an address whose host, or first DNS label, names another known service, and `resolveAlias`
  returns that service before the cache, even against a row that maps the address elsewhere; `SpanPairingTest`
  covers both arrival orders, the consumed pair, the remembered answer, a call inside one service, root and
  non-client spans, the guard on a learned row, a two-node transport in which both facts reach the owner and the
  row is written there, and a `PairingFact` wire round trip; the execution test drives the pairing through
  `resolveAlias` in both orders;
  a root span draws `User -> service` under `User`'s non-normal id with no `ServiceTraffic` for `User`, and a span
  with a parent draws nothing; the worked example replays span 2 after a cache reload and asserts the resolved
  edge.
- Metric registration tests: compiling a rule creates each declared metric once with the right function and scope,
  branches sharing a name share a metric, conflicting shapes fail, and the runtime-rule path passes the per-file
  loader and the main or peer storage option through.
- A recorded trace through the shipped rule, `ShippedRuleOverRecordedTraceTest` in the OTLP receiver plugin: the
  eight spans of one `GET /api/cart` from the OpenTelemetry Demo, kept as OTLP/JSON under the plugin's test
  resources, fed with the real `otlp` input type and the metadata the receiver builds, in a cold pass and a warm
  pass. It pins the edges of each pass, the alias rows including the startup race and the pairing's correction of
  it, the metric count per span, and the route-shaped endpoint names read through the real provider.
- Forward tests: `SpanForwardTest` and `OTLPSpanForwardTest` keep an enforced span at `sampleRate: 0`, still drop
  it once `maxSpansPerSecond` is exhausted, and keep the existing decisions for spans that are not enforced.
- Data-driven execution tests through `server-testing`: a `dsl/tal` loader beside `dsl/lal` and `dsl/mal`, rule
  files under `trace-analyzer/src/test/resources/scripts/tal/` with `.input.data` companions holding OTLP/JSON
  spans and the expected emitted metrics as `(name, entity id, value)` triples, `meta` values, abort and
  persistence outcomes, and the records a `sink {}` wrote, asserted against a recording `MeterSystem` and
  `SourceReceiver`. The shipped `otlp-default.yaml` is one of them, over the three spans above plus a PostgreSQL
  client span at 30 ms and one at 900 ms, which must and must not produce a `DatabaseSlowStatement`, a Kafka
  producer and consumer pair, a Valkey client span and an `exception` event. A Zipkin fixture, Brave-shaped JSON
  spans, runs the same rules with `inputType: otlp, zipkin` and must produce the same entities and values as the
  OTLP fixture for the same hop.
- `MeterEntity` and `EntityTrafficGenerator` unit tests: ids built with each side's `normal` flag for two-layer
  relations, `ServiceTraffic` for both ends, single-layer factories and MAL's traffic unchanged. The MAL
  `.data.yaml` suite must pass unchanged after the move.
- `PercentFunction` and `ApdexFunction` unit tests with the cases of the `PercentMetrics` and `ApdexMetrics` tests:
  `combine`, `calculate`, `toHour`, `toDay`, the storage builder round trip, and for apdex the `default` and
  per-service thresholds and the unsuccessful-span rule. A cross-check test feeds the same observations to
  `CPMMetrics` and `sumPerMin`, `PercentMetrics` and `percent`, `ApdexMetrics` and `apdex`, and asserts equal values.
- e2e: `otlp-traces` verifies with `swctl` that `service ly OTLP_SERVICE` lists `frontend` and
  `productcatalogservice`, that `metrics exec` returns `otlp_service_cpm` and `otlp_endpoint_resp_time`
  for them, and that `dependency service` shows the `User -> frontend` edge and the `frontend -> productcatalogservice`
  edge once the alias row has reached the cache, with the first request's `frontend -> productcatalogservice:3550`
  folded into it; the storage e2e's
  `config-dump.yml` gains the new default; `otlp-virtual-genai` proves the GenAI listener and TAL coexist, with the
  GenAI spans aborted by the first condition of the shipped rule.

### 14. Documentation

- `docs/en/concepts-and-designs/tal.md`, the language reference, structured like `lal.md`: rule files and layer
  routing, inputs and `inputType`, `span` and `meta`, functions, filter, extractor, the `metrics` block with its
  constructors and functions, output records and `outputType`, `sink` and `persist`, differences from LAL. Listed
  in `docs/menu.yml` as "Analysis Traces" next to "Analysis Logs".
- `docs/en/guides/tal-extension.md`, the developer guide for custom input and output types, the counterpart of
  `lal-extension.md`: implementing `TALSourceTypeProvider` and `TALOutputBuilder`, registering them over
  `META-INF/services`, and which builders serve both DSLs.
- `docs/en/setup/backend/trace-analyzer.md`, the module configuration, next to `log-analyzer.md`;
  `otlp-trace.md` gains a section pointing at it and describing what appears in the UI.
- `docs/en/concepts-and-designs/runtime-rule-hot-update.md`: the new catalog and that it owns storage like MAL.
- `docs/en/setup/backend/admin-api/dsl-debugging-tal.md`.
- `docs/en/changes/changes.md`.

### 15. What this design gives up, and what it gains

Compared with the source-mapping proposal, which is the reference for parity with agent traces:

| Topic | Source mapping | TAL |
|---|---|---|
| Metric names | `service_cpm`, `service_sla`, the `core.oal` set, in `GENERAL` | `otlp_service_cpm` and whatever the rule declares, in `OTLP_SERVICE`, computed by the same functions in the same units; General dashboards and alarm rules apply after renaming the metric, not as they are |
| Virtual peers | `DatabaseAccess`, `CacheAccess`, `MQAccess` sources feed the existing virtual-service dashboards and slow-statement records | peers exist as topology nodes and in their virtual layer lists through the two-layer relation; their access metrics are the `otlp_service_relation_*` series on the edge, and the `core.oal` panels of the existing virtual-service dashboards stay empty for them, while the slow-statement panel fills from `otlp-slow-sql`. Per-peer metrics are one more `metrics {}` block with `scope service(peer, peerLayer)`, and panels for them are a follow-up |
| Records | `DatabaseSlowStatement`, `CacheSlowAccess` | `DatabaseSlowStatement` through `outputType: SlowSQL`, shipped; `CacheSlowAccess` needs a builder, a follow-up; any other record through a custom `TALOutputBuilder` |
| Other trace protocols | OTLP only | OTLP shipped; Zipkin through `inputType: zipkin` on the same rules |
| Endpoint relations | `EndpointRelation` from `User` | no endpoint-relation scope in `MeterSystem` |
| Adding a convention or a peer kind | a Java change to the rule schema, or a new key in it | an edit to the rule file, hot-applied |
| Changing what is measured | not possible; `core.oal` decides | any function over any value on any entity, declared where the value is computed |
| Operator tooling | none | runtime-rule hot update with a metric shape guard, dsl-debugging sessions, generated-class dumps, data-driven test harness |
| Shared limits | one span at a time, no caller identity on the server side, endpoint names as good as the instrumentation's, sampling at the SDK hides requests, exporter retries double count without a seen-set | the same |

The trade is a fixed, agent-parity metric set against an editable one with the tooling the other DSLs have. The
`otlp_*` naming and the separate layer are the visible cost, and they are also what keeps the two worlds from
silently double counting a service that runs both an agent and an SDK.

## Imported Dependencies libs and their licenses

None. ANTLR4 runtime and Javassist are already dependencies of the analyzer modules.

## Compatibility

- The `trace-analyzer` module is present by default with the shipped rule active, so deployments that send OTLP
  traces gain `OTLP_SERVICE` entities and the sixteen `otlp_*` metrics on upgrade. Setting
  `SW_TRACE_TAL_FILES` empty keeps today's behavior. The metrics are new measures, indices or tables, created at
  boot like any new MAL rule's.
- `OTLP_SERVICE` takes ordinal `52`, the next free built-in ordinal. Every OAP node must run the same build; that
  is already true for every layer.
- `receiver-otel` requires `trace-analyzer`. Selecting `trace-analyzer` out with `-` is not supported; disabling the
  analysis is done through `talFiles`.
- `MeterEntity` gains `destLayer` and two factory overloads; the existing factories and ids are unchanged. Entity
  traffic generation moves from `meter-analyzer` to `server-core`; MAL calls the moved code and behaves as before.
  MAL's grammar and rule files are untouched.
- `MeterSystem` gains two functions, `percent` and `apdex`, registered by the existing annotation scan. No existing
  function changes. `ApdexThresholdConfig` is shared with `ApdexMetrics`, so one `service-apdex-threshold.yml` and
  one dynamic-configuration key serve agent and OTLP services alike. `cpm` in a TAL rule is `sumPerMin`; no new
  class and no change to `SumPerMinFunction`.
- `Catalog` gains one value; the runtime-rule REST surface accepts it; existing catalogs are unaffected.
- `SpanListener`, `OTLPSpanReader`, `SpanListenerResult` and the Zipkin conversion are untouched. Phase-one
  listeners run before TAL and their persistence and tag decisions are merged with TAL's.
- `SpanForward.getSampledTraces` and `OTLPSpanForward.send` gain an enforced flag that skips the trace-id check.
  With no rule voting nothing is enforced, so their decisions are unchanged.
- Spans arriving over the Zipkin protocol are analyzed only by rules that declare `inputType: zipkin`; the shipped
  rules do not, so Zipkin deployments see no change until they opt in. `receiver-zipkin` requires
  `trace-analyzer`. The Zipkin receiver is untouched: no rule sees a Zipkin span in this proposal, and the
  provider that would change that is reserved, not shipped. A Zipkin span has no instance, no resource and no
  scope, so rules that read those would get empty values when it lands.
- `TALOutputBuilder` is a new interface in `server-core`. `DatabaseSlowStatementBuilder` implements it beside
  `LALOutputBuilder`; its LAL behavior is unchanged. A rule with `outputType` writes records through
  `SourceReceiver` exactly as a LAL rule does, and a `Source`-path `outputType` can reach `core.oal`; the shipped
  rules use only `SlowSQL`.
- `log-analyzer` is untouched. TAL compiles with its own expression codegen rather than a module shared with LAL,
  so no TAL change can reach log parsing; the cost is a duplicated engine that has to be kept in step by hand.
- Metrics are exact only if OAP sees every span. SDK head sampling hides requests; exporter retries can double
  count unless `dedupWindow` is set, and then only within one node.
- A GENERAL service and an OTLP_SERVICE service with the same name are one entity in two layers.

## General usage docs

With the defaults nothing needs to be enabled. After an OpenTelemetry SDK exports traces, its services appear under
the OpenTelemetry Service menu with load, latency, success rate and percentile dashboards at service, instance and
endpoint level, the topology shows the calls between them and to their databases, caches and brokers, and alarm
rules can reference the `otlp_*` metrics.

To change what is measured, edit or add a rule file:

```yaml
trace-analyzer:
  selector: default
  default:
    talFiles: otlp-default,my-rules
```

A rule that counts failed HTTP client calls by exception type and status code, keeps every failed span, and
samples the rest:

```yaml
# tal/my-rules.yaml
metricPrefix: otlp
rules:
  - name: failed-http-calls
    layer: auto
    dsl: |
      filter {
        if (meta.kind != "CLIENT" || !hasAttr("http.request.method", "http.method")) {
          abort {}
        }
        extractor {
          layer "OTLP_SERVICE"
          if (meta.statusCode == "ERROR") {
            tag 'exception.type': eventAttr("exception", "exception.type")
            metrics {
              name "http_client_failures"
              scope service(meta.service, meta.layer)
              function sumLabeled
              labels exception: eventAttr("exception", "exception.type"),
                     status: attr("http.response.status_code", "http.status_code")
              value 1
            }
          }
        }
        persist {
          if (meta.statusCode == "ERROR") {
            enforcer {}
          } else {
            sampler {
              rateLimit("${meta.service}:http-client") {
                rpm 600
              }
            }
          }
        }
      }
```

Apply without a restart and watch it run:

```bash
# The rule YAML is the raw body; catalog and name are query parameters, as for every other catalog.
# The file declares a new metric, which is a storage change and needs the explicit opt-in, like a MAL file.
curl -X POST 'http://oap:17128/runtime/rule/addOrUpdate?catalog=tal&name=my-rules&allowStorageChange=true' --data-binary @my-rules.yaml
# Open a statement-level debug session on the new rule and drive one request through the SDK.
curl -X POST 'http://oap:17128/dsl-debugging/session?catalog=tal&name=my-rules&ruleName=failed-http-calls&clientId=me&granularity=statement'
```

The catalog-in-path alias the plugin offers for the other DSLs gets a counterpart,
`/runtime/tal/{addOrUpdate,inactivate,delete}`. For a shipped rule the debug session names the file with its
extension, `name=otlp-default.yaml`; for a runtime-rule entry the entry name, `name=my-rules`; `RuleKey`
canonicalizes both to one key.

To place a fleet in another layer, stamp `service.layer` on its resources and declare the rules under that layer,
passing the layer to the entity constructors. To recognize an SDK that still emits an old attribute name, add the
spelling to the `attr()` call that reads it. To give a new database or broker its own icon, make sure
`component-libraries.yml` has an entry whose name matches the `db.system.name` or `messaging.system` value,
case-insensitively; `component()` does the rest.
