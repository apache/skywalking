# SWIP-17 Move the Native Trace Analysis from Java Listeners to TAL

## Motivation

SkyWalking analyzed traces with two engines. Before this change, spans that arrived over OTLP were analyzed by
[TAL](../concepts-and-designs/tal.md) rules, which an operator can read and edit, and segments that SkyWalking's own
agents send were analyzed by Java listeners in the `agent-analyzer` module. Those listeners turned each span into OAL
sources (`Service`, `ServiceInstance`, `Endpoint`, the relations, `DatabaseAccess` and others) for `core.oal` and the
other `.oal` files, wrote the trace itself, and handed GenAI spans to their analyzers. Changing how a native span
became a service, an endpoint or a relation, such as reading one more tag, was a Java change. The knowledge of what a
span means lived in two places, written in two styles.

This change moved native analysis into TAL rules hosted by `trace-analyzer` and removed the production
`AnalysisListenerFactory` SPI: every analysis of a native span is a rule file that the configuration activates. It was
a port and nothing else:

- **No output change.** For every segment, the rules send the same sources, with the same field values, in the same
  order, as the listeners did. They make the same stateful calls and side-effect calls in the same order, apart from
  the three exceptions below, and write the same log lines, debug included, under the script's logger rather than the
  listener's class name. OAL, storage, query, alarm and the UI were untouched, and so was every metric name.
- **No logic change.** Each rule reproduces one listener branch by branch, including the cases where the Java failed a
  segment on malformed input. A difference found while porting was a defect of the port, not a fix to make on the
  way. A behaviour worth changing is changed afterwards, in its own change, with its own changelog line.
- **One exception, the evaluation sample.** The listener sampled a GenAI span for evaluation during the span loop. The
  rule sends the sample as a `GenAIEvaluationSample` source in `build {}`, where a native rule makes all of its
  outputs, so the sample is taken after the segment's last span. A segment that analyzes completely makes the same
  `sample` calls, with the same contexts, in the same order, only later. A segment that fails no longer takes the
  samples of the GenAI spans before the failure, see
  [Failure behaviour the port keeps](#failure-behaviour-the-port-keeps).
- **A second exception, adopted as a feature: the bare-host gateway match.** OTLP and native rules share one
  `isGateway`, which also tries a bare host at port 80, the port `gateways.yml` gives an instance that names none. An
  HTTP client that omits the default port records its peer as the bare host, so a gateway listed once, at port 80, now
  matches it for native traces as it already did for OTLP ones; the listeners matched the exact address only. An
  address with a port is unchanged, and the debug lines follow the lookups made, see
  [Failure behaviour the port keeps](#failure-behaviour-the-port-keeps).
- **A third exception, a repeated tag key.** An agent is not expected to send a tag key twice on a span. When it does, a
  native rule reads the first value everywhere, by name and in the one tag map. The listeners differed by place: the
  sources' `originalTags` kept the last value, the GenAI analyses the first, and the cache analysis failed the segment;
  see [Failure behaviour the port keeps](#failure-behaviour-the-port-keeps).

The change added the native input, lifecycle constructs, outputs and functions needed by the port, summarized in
[What is new in TAL](#what-is-new-in-tal):

- **Native input:** the `sw-native` input exposed the agent's protobuf `SpanObject` as `span`, its `SegmentObject`
  through `segment()`, and the runner's segment-wide results through functions. It is provided through
  `TALSourceTypeProvider`, the trace analyzer's internal bridge to an input.
- **Settings:** a rule reads a setting through a function, as it reads everything else an input or a module serves:
  `agent-analyzer` answers through a new typed service, and `ai-evaluation` contributes its two functions.
- **Outputs in `build {}`:** a native rule makes its outputs in one deferred block. `source {}` hands an existing
  source to the source receiver, which dispatches it by scope to the OAL engine or to the dispatcher a module declares,
  documented as the adoption path for the existing source system; `metrics {}` declares a new metric, as it does in
  every TAL rule. `source {}` belongs to every input: an OTLP rule writes its records with it too, and TAL has no other
  record output.
- **Lifecycle constructs, plain assignment, typed values and `log`:** what a per-span rule needs to reproduce a
  listener exactly, and to read like one.
- **Bridge functions:** functions that call the caches, naming rules and lookups the listeners called. Other modules
  contribute their own functions through a new `TALFunctionProvider` extension point. Logic stays in the rules: a
  function reads a cache, a settings file or a lookup the rules cannot reach, or parses what TAL has no syntax for.
- **OTLP AI evaluation as a rule:** the evaluation sampling of natively stored OTLP spans, which
  `AIEvaluationSpanListener` made in Java, moved to the shipped `otlp-ai-evaluation` rule, which sends the same
  `GenAIEvaluationSample` source as the native rule. As every OTLP rule, it sees the spans of the services that set
  `service.layer=OTLP_GENERAL`. Zipkin has no TAL input, so the listener kept the Zipkin path, OTLP spans stored as
  Zipkin spans included, unchanged; see [Contributed by other modules](#contributed-by-other-modules).

The ported rules shipped under `oap-server/server-starter/src/main/resources/tal/`; this SWIP links those files, listed
under [The shifted rule files](#the-shifted-rule-files). A test module ran the removed listeners and native rules
against the same constructed segments and compared their ordered outputs, stateful calls, log lines and failures at
TAL's boundary, with the three documented differences handled explicitly, following the precedent of the MAL and LAL
move from Groovy to the v2 compilers.

## Scope

The following table mapped the former listeners and processors to the shipped native rule files.

| Before the port, in `agent-analyzer` and the modules plugged into it | Shipped native rule files under `tal/` |
|---|---|
| `RPCAnalysisListener` | `native-rpc.yaml`: rules `native-rpc-entry`, `native-rpc-exit`, `native-rpc-logic-endpoint` |
| `EndpointDepFromCrossThreadAnalysisListener` | `native-endpoint-cross-thread.yaml` |
| `NetworkAddressAliasMappingListener` | `native-network-alias.yaml` |
| `SegmentAnalysisListener` | `native-segment.yaml`; its `parseSegment` work is performed by the runner before the spans and exposed through the segment functions |
| `VirtualServiceAnalysisListener`: `VirtualCacheProcessor`, `VirtualDatabaseProcessor`, `VirtualMQProcessor` | `native-virtual-service.yaml`: rules `native-virtual-cache`, `native-virtual-database`, `native-virtual-mq` |
| `VirtualServiceAnalysisListener`: `VirtualGenAIProcessor` (only when `gen-ai-analyzer` is loaded) | `native-virtual-genai.yaml`, loaded only when `gen-ai-analyzer` is loaded |
| `GenAIEvaluationAnalysisListener`, plugged in by `ai-evaluation` through the `AnalysisListenerFactory` SPI | `native-ai-evaluation.yaml`, loaded only when `ai-evaluation` is loaded |
| `TraceAnalyzer`, `AnalysisListener`, `AnalysisListenerFactory`, the listener interfaces, `SegmentParserServiceImpl` and the listener manager | removed from the OAP, kept in the test-only `trace-analysis-listener-v1` module; `trace-analyzer` runs the rules |
| `ISegmentParserService`, the segment entry point, in `agent-analyzer` | moved to `trace-analyzer`, whose runner implements it |

Six listeners and four processors were ported. RPC was split into entry, exit and logic-endpoint rules to preserve
build order: the listener's `build()` sent all entry sources, then all exit sources, then all logic-endpoint sources;
three rules, flushed in file order, send in that order. See
[How trace-analyzer runs the native rules](#how-trace-analyzer-runs-the-native-rules).

On the OTLP side, the evaluation sampling of natively stored spans, `AIEvaluationSpanListener.onNativeOTLPSpan`,
moved to `otlp-ai-evaluation.yaml`, loaded only when `ai-evaluation` is loaded and listed in the default `talFiles`;
the listener kept its Zipkin path. See [Contributed by other modules](#contributed-by-other-modules).

## Architecture Graph

```text
Before the port
  agent --segment--> trace receiver --> ISegmentParserService (agent-analyzer)
                                          '--> TraceAnalyzer, one per segment
                                                 |- RPCAnalysisListener
                                                 |- EndpointDepFromCrossThreadAnalysisListener
                                                 |- NetworkAddressAliasMappingListener   --> SourceReceiver --> OAL, records
                                                 |- SegmentAnalysisListener
                                                 |- VirtualServiceAnalysisListener (cache, database, MQ, GenAI)
                                                 '- AnalysisListenerFactory SPI (ai-evaluation)

Implemented architecture
  agent --segment--> trace receiver --> ISegmentParserService (trace-analyzer)
                                          '--> native rule runner, one run per segment
                                                 segment-wide state derived before the spans, then
                                                 rules from nativeTalFiles, in list order:
                                                 native-rpc, native-endpoint-cross-thread,
                                                 native-network-alias, native-segment,      --> SourceReceiver --> OAL, records
                                                 native-virtual-service, native-virtual-genai,
                                                 native-ai-evaluation                       --> source GenAIEvaluationSample
                                                                                                --> GenAIEvaluationSampleDispatcher
                                                                                                --> IAIEvaluationService queue
                                                 rules call: the native input's functions, which its provider
                                                   declares: tag, findNetworkAlias, tagList, clusterNameOrAddress,
                                                   slowDBAccessThreshold, segment, segmentSampled, ...; and the
                                                   shared formatServiceName, matchGenAIModel, isGateway,
                                                   parseStatusCode, isEmpty, ...
                                                 rules read settings through functions: agent-analyzer's
                                                   NativeTraceAnalysisConfig service, ai-evaluation's provider
  OTLP SDK --span--> OTLP trace receiver --> trace-analyzer, the rules of talFiles for the span's service.layer
                                          '--> otlp-ai-evaluation, for a natively stored span  --> source GenAIEvaluationSample
                                                                                                --> GenAIEvaluationSampleDispatcher
  Zipkin span, or OTLP span stored as one --> AIEvaluationSpanListener                         --> IAIEvaluationService queue

  test/script-cases/trace-analysis-listener-v1     the removed Java, test scope only
  test/script-cases/trace-analysis-v1-v2-checker   runs both on the same segments, compares ordered event logs
```

The runner derived segment-wide state before the spans; rules read it through the segment functions.

`trace-analyzer` already depended on `agent-analyzer` (`oap-server/analyzer/trace-analyzer/pom.xml`), so the runner
lives there with no new Maven dependency between the two modules. The native trace receiver plugin and the Kafka
fetcher plugin gained a Maven dependency on `trace-analyzer`, where the moved service lives.

`ISegmentParserService` is internal: its only callers are the native trace receiver's gRPC and REST handlers, the Kafka
fetcher's segment handler and the data-generator tool, all in this repository. `ISegmentParserService` moved to
`trace-analyzer`, whose provider registered the native runner. Its callers required and looked up the service there,
see [Activation and startup](#activation-and-startup).

## Proposed Changes

### The `sw-native` input type

`SwNativeTALSourceTypeProvider` implements `TALSourceTypeProvider`, the bridge between TAL and an input and the point
an input is extended at, under the alias `sw-native`, beside `OtlpTALSourceTypeProvider` (`otlp`). It exposed the
protobuf `SpanObject` directly, the wire object the listeners read, as an OTLP rule's `span` is the OTLP `Span`. A rule
reads the input from three places, and each value is in exactly one of them:

- `span` is the `SpanObject` the agent sent;
- `segment()` returns the span's `SegmentObject`, as the agent sent it;
- the segment functions return what the runner derived from the whole segment.

A field is read by its name, `span.operationName`, which compiles to the protobuf getter, `getOperationName()`.

| `span.` | Value |
|---|---|
| `operationName`, `peer`, `spanId`, `parentSpanId`, `startTime`, `endTime`, `componentId`, `isError`, `skipAnalysis` | the `SpanObject` fields |
| `spanType` | the `SpanType` enum: `Entry`, `Exit`, `Local`, or `UNRECOGNIZED` for a value this OAP does not know; compared with a literal, `span.spanType == "Entry"` |
| `spanLayer` | the `SpanLayer` enum, such as `Database` or `FAAS`, or `UNRECOGNIZED`; compared with a literal, `span.spanLayer == "Database"` |
| `tagsList` | the tags in wire order, each with `key` and `value`; a key can repeat |
| `logsList` | the logs in wire order, each with `time` and `dataList`, its key and value pairs in wire order |
| `refsList` | the references in wire order, each a `SegmentReference` with its protobuf fields: `refType` (the `RefType` enum, `CrossProcess` or `CrossThread`), `traceId`, `parentTraceSegmentId`, `parentSpanId`, `parentService`, `parentServiceInstance`, `parentEndpoint` and `networkAddressUsedAtPeer` |

| `segment().` | Value |
|---|---|
| `service`, `serviceInstance`, `traceId`, `traceSegmentId`, `isSizeLimited` | the `SegmentObject` fields, as the agent sent them |
| `spansList` | every `SpanObject` of the segment, in wire order |

| Function | Value |
|---|---|
| `segmentStartTime()`, `segmentLatency()`, `segmentIsError()`, `segmentTags()`, `segmentSampled()` | the work of `SegmentAnalysisListener.parseSegment`, which the runner does once per segment before any rule runs, see below |
| `segmentBytes()` | the `SegmentObject` serialized again by `toByteArray()`, what the `Segment` record stores; serialized at each call |

No view classes or first-reference helpers were introduced. Where `RPCAnalysisListener.parseEntry` built once per
reference or once without one, `native-rpc-entry` iterated `span.refsList` when the list was non-empty
(`!isEmpty(span.refsList)`) and wrote a separate no-reference branch, including its own build body. The MQ rule
selected the first reference with a guarded loop.

The runner does `parseSegment`'s work before the first rule's first run, in its order, calling the same collaborators
and writing the same log lines under the runner's own logger:

1. for each span in wire order: the start (restarting whenever the current minimum is `0`) and end fold;
   `isError = isError || SegmentStatusAnalyzer.isError(span)` under `agent-analyzer`'s
   `segmentStatusAnalysisStrategy`; and the span's tags that `core.searchableTracesTags` lists, within
   `Tag.TAG_LENGTH`, each equal key and value pair once, logging at debug a tag dropped for its length;
2. the duration, capped at `Integer.MAX_VALUE`;
3. `TraceSegmentSampler.shouldSample`, or an error segment under `agent-analyzer`'s `forceSampleErrorSegment`.

The runner exposed the derived start, duration, error state, searchable tags and sampling decision through
`segmentStartTime()`, `segmentLatency()`, `segmentIsError()`, `segmentTags()` and `segmentSampled()`; `segmentTags()`
is the read-only `List<Tag>` the `Segment` record stores. It performed that work for every non-empty segment,
regardless of `traceAnalysis`, as `SegmentAnalysisListener` had been registered whatever it said; the end-time fold
remained internal.

Each segment run held its own internal derived state, shared by the runs of every span of that segment, as
`TraceAnalyzer` handed every listener the same `SegmentObject`; concurrent segments never share it. The rules read the
immutable protobuf span and segment objects, so a rule cannot change a span or the segment. The shipped `native-segment`
rule, as `SegmentAnalysisListener` did, stored the unmodified `SegmentObject` reserialized by `segmentBytes()`
(`toByteArray()`) when a span `0` had initialized the record, and selected the record's index fields; without a span
`0` it kept the listener's default record fields, a null `dataBinary` among them. The language did not restrict a custom
`source Segment {}` to those fields; such a rule supplied the contents it stored.

What the listeners derived for one span alone, the rules write out where they need it, as the logic it is:

- **The service's layer,** `identifyServiceLayer`: `FAAS` for a span whose `spanLayer` is `FAAS`, `GENERAL` for any
  other. It is per span, not per segment: a FaaS function's exit span over HTTP is `GENERAL`. From
  `native-rpc-entry`:

  ```text
  def calleeLayer = "GENERAL" as Layer
  if (span.spanLayer == "FAAS") {
    calleeLayer = "FAAS" as Layer
  }
  ```

- **The span's latency,** `(span.endTime - span.startTime) as Integer`. The `long` conversion in
  `TalValueConversions` performed Java's `(int)` cast, including overflow, and preserved the listeners' conversion
  without the OTLP path's clamping.
- **Once per segment,** as `SegmentAnalysisListener.build()` ran once: a `segmentDef` guard on the segment's first
  span in wire order, whatever its type, as `native-segment` writes it:

  ```text
  if (buildQueued == null) {
    segmentDef buildQueued = true
    extractor {
      build {
        ...
      }
    }
  }
  ```

The `otlp` input's `meta` root, and the statements that overwrite it (`service`, `instance`, `endpoint`, `layer`), are
not part of the `sw-native` input. The runner keeps the protocol-neutral `SpanMetadata` the engine needs internally,
which no rule reads.

A native rule reads the native span in its own words. `tag(key)` answers the value of the first tag with that key,
empty when there is none, and `hasTag(key)` whether there is one, where an OTLP rule reads attributes with `attr` and
`hasAttr`. Where a listener iterated tags, the rule iterated `span.tagsList`; where it built a map of them, the rule
calls `tagMap()`. `tag()` and `tagMap()` kept the first value of a repeated key, a
[documented difference](#failure-behaviour-the-port-keeps). Native logs were exposed as `span.logsList`, with `time`
and `dataList` on each log, what an OTLP span's events are. A rule reads them as the list; no native listener read
them.

Native spans carry no `service.layer`. The runner runs the `sw-native` rules on every span of every segment. The
shipped native rules declared `layer: GENERAL`; compilation validated it, but native execution did not select rules by
layer: the runner ran every loaded native rule on every span. A rule still sends `FAAS` where the listeners did,
computing it as `identifyServiceLayer` did.

### The settings a rule reads

A rule reads a setting through a function, as it reads everything else an input or a module serves; TAL has no
settings root. A dynamic setting is read at the call, so a value the module changes later reads its new value.

- **`agent-analyzer` gained a typed service.** It listed `NativeTraceAnalysisConfig` in `AnalyzerModule.services()`
  and registered it in `prepare()`, over its `AnalyzerModuleConfig`, as it already served
  `UninstrumentedGatewaysConfig` to `trace-analyzer`. The provider itself is never exposed, only what the service is
  written to answer, and the module pattern makes it mandatory: the module manager's `requiredCheck` fails the boot
  when the provider does not register it.

  ```java
  public interface NativeTraceAnalysisConfig extends Service {
      boolean isTraceAnalysis();                         // agent-analyzer.traceAnalysis
      int getMaxSlowSQLLength();                         // agent-analyzer.maxSlowSQLLength
      boolean isError(SpanObject span);                  // SegmentStatusAnalyzer under segmentStatusAnalysisStrategy
      boolean shouldSample(SegmentObject segment, int duration, boolean isError);
                                                         // TraceSegmentSampler, then forceSampleErrorSegment
      int slowDBAccessThreshold(String dbType);          // DBLatencyThresholdsAndWatcher.getThreshold
      int slowCacheReadThreshold(String type);           // CacheReadLatencyThresholdsAndWatcher.getThreshold
      int slowCacheWriteThreshold(String type);          // CacheWriteLatencyThresholdsAndWatcher.getThreshold
      boolean isNoUpstreamRealAddressAgent(int componentId); // AnalyzerModuleConfig.shouldIgnorePeerIPDue2Virtual
  }
  ```

  The native input's functions `traceAnalysis()`, `maxSlowSQLLength()`, `slowDBAccessThreshold(type)`,
  `slowCacheReadThreshold(type)` and `slowCacheWriteThreshold(type)` read it, see
  [Functions for the `sw-native` input](#functions-for-the-sw-native-input); so does the shared
  `isNoUpstreamRealAddressAgent(componentId)`, see [Functions for every input](#functions-for-every-input); and so
  does the runner's `parseSegment` work, through `isError` and `shouldSample`.
- **`ai-evaluation` contributes functions.** Through a `TALFunctionProvider` that serves every input, the native
  `native-ai-evaluation` rule and the OTLP `otlp-ai-evaluation` rule among its callers, it contributes
  `evaluationActive()`, the decision its `prepare()` made, `false` when the judge configuration in
  `ai-evaluation.yml` is incomplete and it registered `InactiveAIEvaluationService`, and `evaluationSampleRate()`, from
  its `AIEvaluationConfig`. The provider reads both from a typed service, `AIEvaluationSamplingConfig` (`active`,
  `sampleRate`), which `AIEvaluationProvider.prepare()` registers and `AIEvaluationModule.services()` lists, as
  `agent-analyzer` serves its settings; the module provider itself is never exposed. It contributes nothing of the
  judge settings, the API key among them.
- **The core's setting** the runner's `parseSegment` work reads, `core.searchableTracesTags`
  (`SW_SEARCHABLE_TAG_KEYS`): the runner read `core.searchableTracesTags` through the core's `ConfigService`, as
  `SegmentAnalysisListener` read it, and exposed the resulting searchable-tag list through `segmentTags()`.
- **Files with their own matching rules,** `gateways.yml` and `server-clusters.yml`, stay behind the functions
  `isGateway` and `clusterNameOrAddress`.

### How trace-analyzer runs the native rules

The runner reproduces `TraceAnalyzer`'s lifecycle callback for callback. TAL stays per span. A listener's per-segment
behaviour maps to two blocks and one kind of variable, each the counterpart of one Java mechanism:

| `TraceAnalyzer` before the port | The runner |
|---|---|
| A segment without spans returns at once | Same |
| `SegmentListener.parseSegment`, before the spans | The segment-function results, computed before the first rule ran |
| `FirstAnalysisListener.parseFirst`, on every span whose id is `0`, before any other callback on that span, whatever its type | [`first {}`](#first) of every rule, in file order, on every span whose `spanId` is `0`, before any rule's regular run on that span, whatever its type |
| The entry, exit or local callbacks of every listener, in listener order, on every span in wire order | Each rule runs, in file order, on every span in wire order; the rules select entry, exit and local spans through `span.spanType`. An unknown type matches none of those values, as no callback ran for it, and produces the runner's error log, the line `TraceAnalyzer` wrote |
| A listener's fields, alive for one segment | [`segmentDef`](#segmentdef), a variable alive for one segment |
| `build()` of every listener, in listener order, after the last span | [`build {}`](#build) blocks. A block is queued when a span's run reaches it. After the last span the runner runs the queued blocks rule by rule in file order, and within a rule in the order they were queued |
| `NetworkAddressAliasMappingListener`'s alias row, sent during the span loop | [`writeNetworkAlias`](#writenetworkalias), sent at its statement |
| `GenAIEvaluationAnalysisListener`'s sample, taken during the span loop | a `GenAIEvaluationSample` [`source {}`](#new-tal-section-source) in `build {}`, taken in the build phase: the one place the port moves a send, see [Failure behaviour the port keeps](#failure-behaviour-the-port-keeps) |
| A listener's log lines | [`log`](#log) statements, under the script's logger `org.apache.skywalking.trace.analysis.script.<script name>`, and the functions that call the logging Java. The runner calls a compiled rule directly, not through `TalDSL.evaluate`, so the `[TAL] rule=...` debug line the OTLP path writes before each rule is not written for a native rule |

Ordering follows from this. A Java builder is one queued `build {}` block: the parse callback filled it, `build()`
formatted its names and sent its sources. A rule that formats names inside `build {}` therefore makes the
`NamingControl` calls, whose results depend on `EndpointNameGrouping`'s per-service state and whose truncations log, at
the same point and in the same number as the Java did. A rule that sends from `build {}` sends in the Java order.

Failures follow the Java too. The runner catches nothing, neither around a rule nor around the dispatch of a source it
sends; the OTLP path catches around a rule. A rule that throws stops the segment at the same point a throwing
listener stopped it, and the exception reaches `ISegmentParserService.send` and the receiver unchanged:

- the gRPC handler counts and logs it;
- the REST handler counts it and rethrows;
- the Kafka fetcher's handler catches only `InvalidProtocolBufferException`, so the exception leaves the handler.

A throw before the build phase means no queued block runs, and so no evaluation sample of the segment is taken.
Everything already done stays done: alias rows sent, log lines written, and changes to the endpoint grouping's state. A
throw inside a `build {}` block, including one raised while a source is dispatched, stops the remaining blocks, and
what was already sent stays sent.

The `sw-native` input has no retry dedup (`dedupWindow` does not apply), no network-alias pairing, and no `persist {}`
or `tag`: the segment's storage is the `native-segment` rule's `Segment` record. `prepareNetworkAlias`,
`buildNetworkAlias`, `persist {}` and `tag` are compile errors in a `sw-native` rule, and so are `metrics {}` and
`source {}` outside `build {}`: a native rule makes its outputs there.

### Activation and startup

```yaml
trace-analyzer:
  default:
    talFiles: ${SW_TRACE_TAL_FILES:otlp-general,otlp-ai-evaluation}
    # TAL rule files under `tal/` for spans that SkyWalking agents send, without extension, comma separated, in the
    # order their rules run. The default reproduces the agent path.
    nativeTalFiles: ${SW_TRACE_NATIVE_TAL_FILES:native-rpc,native-endpoint-cross-thread,native-network-alias,native-segment,native-virtual-service,native-virtual-genai,native-ai-evaluation}
```

- **Rule files.** The files sit in `tal/` beside `otlp-general.yaml`, and every rule declares `inputType: sw-native`.
  A listed file that does not exist, a rule of `nativeTalFiles` that declares another input, or two native rules of
  one name fail the boot. A separate list keeps native analysis running for an operator who set `SW_TRACE_TAL_FILES`
  to their own OTLP files.
- **`requiredModules`.** A rule file may declare `requiredModules` at the top level. Its rules are compiled and run only
  when every module listed is loaded: `native-virtual-genai` lists `gen-ai-analyzer`, the check
  `VirtualServiceAnalysisListener.Factory` made, and `native-ai-evaluation` lists `ai-evaluation`, the
  `requiredModules()` of `GenAIEvaluationAnalysisListener.Factory`. The OTLP `otlp-ai-evaluation` file, added to the
  default `talFiles`, lists `ai-evaluation` too.
- **`traceAnalysis`.** The setting kept its meaning. Before the port it registered the RPC, cross-thread and alias
  listeners. The RPC, cross-thread and alias rules preserved `traceAnalysis` by aborting when its function returned
  false, and the other rules ignore it.
- **Startup.**
  - `trace-analyzer` declares among its required modules, when each is loaded, every module the rules reach, as it
    already declared `agent-analyzer` through `getManager().has`: `agent-analyzer`, the module each discovered
    `TALFunctionProvider` names, and every module a file of `nativeTalFiles` or `talFiles` lists under
    `requiredModules`, since `requiredModules` serves every input. Its `prepare()` reads those keys, and the module
    manager asks for the required modules only after every module has prepared. The shipped files give
    `agent-analyzer`, `gen-ai-analyzer` and `ai-evaluation`. So every module whose functions or settings a rule uses,
    or whose dispatcher takes a source a rule sends, has started before `trace-analyzer` starts, a third party's
    included. A rule file that sends a source another module's dispatcher takes lists that module under
    `requiredModules`.
  - In its `prepare()` it registered the runner as the `ISegmentParserService` implementation. During startup, in its
    `start()`, the registry bound each input provider, its helpers and the providers of loaded modules before rule
    compilation, and the native rules were compiled when `agent-analyzer` was loaded. A provider names the module it
    belongs to; a provider of a module that is not loaded is not bound, and the rule files that would call it are not
    compiled, by their `requiredModules`.
  - `ModuleManager.find` is not allowed during `prepare()`, which is why binding waits for `start()`.
  - The native trace receiver, the Kafka fetcher and the data-generator tool require `trace-analyzer` beside
    `agent-analyzer`, so they start after the rules are compiled. The Kafka fetcher, which starts consuming in its own
    `start()`, therefore never hands over a segment before the runner is ready; a segment handed to the runner before
    its rules are loaded is refused with an exception rather than analyzed without rules.
- **Without `trace-analyzer`.** With `SW_TRACE_ANALYZER=-` and a caller loaded, the native trace receiver (on by
  default) or the Kafka fetcher, the callers' required module is missing, and the module manager fails the boot,
  naming it, rather than receiving segments and analyzing none. With no caller loaded, nothing requires the module,
  and removing it still works for OTLP alone.
- **Without `agent-analyzer`.** The callers still require `agent-analyzer`, so the native trace receiver cannot load
  without it, as before. `trace-analyzer` alone, for OTLP, stays allowed and compiles no native file.
- **Packagings with their own configuration.** Required modules are checked at boot, not loaded on demand. The
  data-generator tool (`server-tools/data-generator`) has its own `application.yml`, which enables `agent-analyzer`,
  and calls `ISegmentParserService` from `SegmentGeneratorHandler`. Data-generator gained a `trace-analyzer` section
  with empty `talFiles`, the default `nativeTalFiles`, and access to the shipped rules through its `server-starter`
  dependency; its provider requires `agent-analyzer` and `trace-analyzer`. Apart from `server-starter`'s standard
  configuration, which this change updated, no other packaging under `oap-server` configures these modules on its own.

The order of `nativeTalFiles` is part of the contract. No rule reads another rule's output: the one cross-file write, an
alias row, reaches `native-rpc-exit` only through the alias cache, where it is not visible until the cache reloads; a
reload can land while a segment is still being analyzed, as it could before. Order still decides what reaches an
order-sensitive merge first, what is already sent when a segment fails, and the order of the stateful naming calls:

| Order | Why |
|---|---|
| `native-rpc` before `native-endpoint-cross-thread` | Both send `EndpointRelation` and `Endpoint` for the same endpoints. `EndpointRelationServerSideMetrics.combine` keeps the first object, so the first relation's component id wins for the minute, and `EndpointTraffic.combine` keeps the first object apart from `lastPingTimestamp`. |
| `native-rpc` and `native-endpoint-cross-thread` before `native-segment`, before the virtual-service files | Build blocks flush rule by rule, and a failure while one source is dispatched stops every later block, so this order decides which sources a failing segment has already sent. |
| `native-network-alias` at its place | It sends during the span loop, so a rule earlier in the order that throws on a span decides whether that span's alias row is sent. |
| `native-ai-evaluation` last | Its blocks run after every other file's, so a failure on any span or in any earlier block means the segment takes no sample. |
| `native-segment` before `native-virtual-service` | Both name endpoints during the span loop, the segment's own service and the broker's, through the stateful endpoint grouping. |
| Inside `native-rpc`: entry, exit, logic endpoint. Inside `native-virtual-service`: cache, database, MQ, with GenAI after | The listener's build grouping and the processor order. |

Adding an operator's own file at the end of the list keeps the shipped rules in their relative order. Its rules still
run on every span, so one of them can stop a segment before the build phase, or change the endpoint grouping's state
that later spans and the shipped build blocks read. Moving a shipped file changes the order itself.

### New TAL section: Source

The port added `source {}` as the adoption path for existing sources, and as TAL's one record output, for every
input. It hands one source of the existing system to the source receiver, which dispatches it by its scope: to the OAL
engine, the same object the SkyWalking agent path built for `core.oal` and the other `.oal` files, or to the
dispatcher a module declares for it, as `ai-evaluation` declares one for `GenAIEvaluationSample`. It exists so that an
analysis written in Java could move to TAL without changing what the existing system computes from it: every metric
named in `.oal` keeps its name, its function and its number, and a record such as `DatabaseSlowStatement` is stored as
before. A new metric is declared with [`metrics {}`](../concepts-and-designs/tal.md#metrics) instead. The operator
syntax was documented in the language page's
[Source (existing system adoption)](../concepts-and-designs/tal.md#source-existing-system-adoption), and the OTLP
page's [Records](../concepts-and-designs/tal-otlp.md#records) describe the record the shipped OTLP rule writes.

```text
source <Name> {
  <field> <expr> [as <Type>]
  ...
}
```

It compiled source names and setters against the registered source classes, required a time bucket and sent each
source at its statement:

- **`<Name>`** is the name OAL's `from()` gives the source, the `name` of its `@ScopeDeclaration`: `Service`,
  `ServiceInstance`, `Endpoint`, `ServiceRelation`, `ServiceInstanceRelation`, `EndpointRelation`, `ServiceMeta`,
  `EndpointMeta`, `DatabaseAccess`, `DatabaseSlowStatement`, `ServiceDatabaseSlowStatement`, `CacheAccess`,
  `VirtualCacheSlowAccess`, `MQAccess`, `MQEndpointAccess`, `Segment`, `TagAutocomplete`, `GenAIProviderAccess`,
  `GenAIModelAccess`, `GenAIEvaluationSample`, and any source a module declares. These are the classes the source
  receiver dispatches by scope name, records such as `Segment` and `DatabaseSlowStatement` among them, and
  `GenAIEvaluationSample`, which no OAL script reads, see
  [Contributed by other modules](#contributed-by-other-modules). Only a concrete source class with a public
  no-argument constructor qualifies; an unknown name is a compile error that lists the known ones.
- **Each field statement** calls the source's setter of that name, checked when the rule compiles. Keywords such as
  `name`, `status`, `layer` and `endpoint` work as field names here, and set the source's field. The value is
  converted to the setter's parameter type:
  - a number, a boolean, a string or a byte array as it is;
  - a `Layer` or an enum, such as `RequestType` or `DetectPoint`, as it is when the value already has that type
    (see [Typed Layer and enum values](#typed-layer-and-enum-values)), and from its name when it is a string. A
    literal name is checked when the rule compiles; any other string is converted when the rule runs, and a name that
    matches nothing fails the rule;
  - a `List` or `Map` field takes the list or map object as it is, such as
    [`tagList()`](#functions-for-the-sw-native-input), without copying it.
  - among a setter's overloads, the one the value's type fits best, as a function call chooses; a value that fits
    none, or several equally, is a compile error until an `as` settles it.

  A field the block does not set keeps the source's constructor default: `null` for most objects, an empty list for
  `Segment`'s tags, a `SideCar` for `Service`'s.
- **`timeBucket` is required**, `minuteTimeBucket(...)` for a metrics source and `recordTimeBucket(...)` for a record
  such as `DatabaseSlowStatement`, as the agent path set them. A `source {}` without it is a compile error.
- **Nothing is formatted implicitly.** A name is stored as the expression gives it. A rule ported from the agent path
  calls `formatServiceName`, `formatInstanceName` and `formatEndpointName` where the Java called `NamingControl`.
- **The block holds field statements only.** A value that depends on a condition is computed into a `def` first.
- **Where it is written.** On `sw-native`, inside [`build {}`](#build): it is built and sent at its statement in the
  segment's build phase, so a later failure in the same block leaves it sent. On `otlp`, in the extractor, sent at its
  statement as `metrics {}` is, so an `abort {}` or a failure later in the rule leaves it sent. OTLP has no `sink {}`
  and no `outputType` key: the output-builder lifecycle the OTLP input had in 11.1.0 was removed with them, and
  `TALConfig` has no `outputType` field, so an 11.1.0 rule file that sets it fails to load with the YAML binder's
  unknown-property error until its record moves to a `source {}`. `persist {}` keeps the vote statements, `sampler`
  with `rateLimit`, `enforcer {}`, `dropper {}` and `if`, for the span's own fate.

The shipped `otlp-slow-sql` rule writes its record this way, in its extractor after every `abort {}`, reproducing the
`SlowSQL` builder field by field; `DatabaseSlowStatementBuilder` stays a LAL output builder only:

```text
source DatabaseSlowStatement {
  id meta.traceId + "-" + meta.spanId
  traceId meta.traceId
  databaseServiceId buildServiceId(formatServiceName(peer), false)
  statement cut(attr("db.query.text", "db.statement"), 2000)
  latency meta.latency
  timeBucket recordTimeBucket(meta.timestamp)
  timestamp meta.timestamp
}
```

From `native-virtual-database`:

```text
source ServiceMeta {
  name db
  layer "VIRTUAL_DATABASE"
  timeBucket bucket
}
```

### Other syntax additions

#### Assignment

```text
<name> = <expr> [as <Type>]
```

Every input. TAL gained plain assignment to distinguish assigning a declared variable from declaring it: `def`
declares a variable and `=` assigns it, so a reader tells the two apart. Before, TAL had only `def`, whose first use
declared and whose later uses assigned, so a reassignment read like a second declaration.

```text
def ok = true
if (span.isError) {
  ok = false
}
```

- **What it assigns.** A variable declared by a `def` earlier in the rule's text and in scope where the assignment
  stands, or by a `segmentDef` anywhere in the rule. A name nothing declares is a compile error, and so is a value that
  does not fit the variable's type, the type of the value its declaration gave it.
- **Declaring before the branches.** A variable the branches of an `if` assign is declared before them. A `String`, a
  `Layer` or an enum is declared with a typed `null`: `def owner = null as String`, `def callerLayer = null as Layer`.
  `as String` already kept `null`, and `as Layer` and `as <Enum>` keep it too: they convert nothing on the literal
  `null` and give the variable its type. Such a variable is `null` on the paths that do not assign it, the value it had
  when a branch's `def` declared it. A number or a boolean is declared with a literal, `def logicLatency = 0`:
  `as Integer`, `as Long` and `as Boolean` turn `null` into `0` and `false`, as they did before, and the port did not
  change them.
- **Scopes.** A `def` in a [`foreach`](#foreach) body, in a [`build {}`](#build) block or in a [`first {}`](#first) run
  declares a variable of that iteration, block or run; `=` assigns it there, or assigns a variable of an enclosing
  scope.
- **Compatibility.** A later `def` of an existing name retained its previous assignment behaviour, so existing rules
  remained compatible; `otlp-general` kept its form. The native rules used `def` for declarations and `=` for
  assignments.

#### `foreach`

```text
foreach (<name> in <list>) {
  <the statements of the block foreach sits in>
}
```

`foreach` iterated a `java.util.List`, including `span.refsList`, `span.tagsList` and `segmentTags()`, with the element
type resolved from the getter or function. The loop variable is typed to the list's element type when the rule
compiles, from the getter's or function's generic return type, so `ref.parentService` and `spanTag.key` are
compile-checked chains. A `def` inside the body declares a variable of one iteration. It is declared again at each
iteration, and it is not visible after the loop: that is how the Java built a fresh builder per reference. A variable
declared before the loop and assigned inside it with `=` keeps its value across iterations. `foreach` is allowed
wherever `if` is. Native rules used iteration to select a reference; list indexing such as `span.refsList[0]` and
dedicated first-reference helpers were not introduced.

#### `build`

```text
build {
  <def, assignment, if, foreach, abort, log, metrics, source>
}
```

`sw-native` only, inside `extractor {}`. The block is queued when the run reaches it and runs once after the segment's
last span, see [How trace-analyzer runs the native rules](#how-trace-analyzer-runs-the-native-rules).

- **What it sees.** The run's input, its `span` and its function context, as they were for that run, not the last
  span's. The `def` values the run had when it reached the block, including the loop variables of the `foreach` it sits
  in, captured by value. The `segmentDef` values as they are when the block runs.
- **Its own variables.** A captured value is the block's own copy. `caller = formatServiceName(caller)` reads the copy
  and assigns it, as the Java formatted a builder's field in place; the run and the other blocks do not see the
  change. A `def` inside the block declares a variable of that block.
- **Outputs.** A `sw-native` rule makes its outputs here and nowhere else, each at its statement:
  - `metrics {}`, a new metric, as in every TAL rule. Its `timestamp` defaults to `span.startTime`, there being no
    `meta`, and a failure inside it skips that block, as it does everywhere: the first failure of a metric logs a
    warning, later ones log at debug. No shipped native rule declares one;
  - [`source {}`](#new-tal-section-source), one source of the existing system, an OAL source, a record such as
    `DatabaseSlowStatement`, or `GenAIEvaluationSample`, which `ai-evaluation`'s dispatcher takes.
- **Deciding.** A rule decides whether an output is made with `if` and `abort {}` before its statement, as the
  listeners decided in Java. The evaluation's own sampling is the evaluation module's policy, written in the rule at
  filter level from `ai-evaluation`'s settings through `evaluationActive()` and `evaluationSampleRate()`, where the
  listener asked the service.
- **Abort.** An `abort {}` later in the same run withdraws the blocks that run queued. An `abort {}` inside a block
  stops that block only.

#### `first`

```text
first {
  <the statements of a filter>
}
```

`sw-native` only, at filter level. It runs on every span whose `spanId` is `0`, before any rule's regular run on that
span, whatever the span's type, as `FirstAnalysisListener.parseFirst` did. It is a run of its own: it has its own
`def` values, an `abort {}` in it ends that run only, and it shares only `segmentDef` values with the rule's regular
run.

#### `segmentDef`

```text
segmentDef <name> = <expr>
```

`sw-native` only. A variable of the rule that lives for one segment, as a listener's field did. It starts as `null` for
each segment. The `segmentDef` statement declares it and assigns it each time it runs; `<name> = <expr>` assigns it
too, anywhere in the rule, on any span of the segment; and the name reads it anywhere in the rule, before its first
assignment included. Its storage belongs to one run of the runner, never to the compiled rule, so concurrent segments
do not share it, and it is cleared when the segment ends, also when it fails.

#### `writeNetworkAlias`

```text
writeNetworkAlias <address>
```

`sw-native` only. It sends, at its statement, a `NetworkAddressAliasSetup` with:

- `address` the given address;
- `representService` `formatServiceName(segment().service)`;
- `representServiceNormal` true;
- `representServiceInstance` `formatInstanceName(segment().serviceInstance)`;
- the minute of the span's start as its time bucket.

Formatting the service before the instance preserved the listeners' naming-call order. This is the row
`NetworkAddressAliasMappingListener` sent from an entry span's cross-process reference. It sends the source and lets the
dispatcher and the cache reload do the rest, without touching the cache. It does not use the OTLP path's alias
registration, which refuses an empty address or service and writes no instance.

The SkyWalking agent propagates the address the caller dialed in the `sw8` header, so the callee alone has every fact
of the row, and nothing waits for a caller's half as the OTLP pairing does. The source has no `@ScopeDeclaration`
name, so `source {}` cannot name it.

#### `log`

```text
log <trace | debug | info | warn | error> "<message>" [, <expr>, ...]
```

Writes the message through SLF4J under the logger `org.apache.skywalking.trace.analysis.script.<script name>`, the
script being the rule file without its extension, such as `org.apache.skywalking.trace.analysis.script.native-rpc`; the
name is not configurable. Each `{}` is replaced by the next argument as SLF4J formats it. The arguments are evaluated
only when the level is enabled, as the Java's `isDebugEnabled()` guards did. It is allowed wherever a statement is.
`log`, `parseStatusCode`, `parseLongOrZero` and `parseIntOrZero` log under the calling rule's script logger; other
helper diagnostics, such as those of `toJson`, `toJsonArray` and `component`, and the Java a function delegates to,
such as the naming functions, log under their own class names.

#### Typed Layer and enum values

`as` converts a string to a `Layer` or to an enum a source takes, such as `RequestType`,
`DetectPoint`, `MQOperation`, `VirtualCacheOperation`, `TagType` and `GenAITraceRefType`:

```text
def calleeLayer = "VIRTUAL_CACHE" as Layer
def type = "HTTP" as RequestType
```

`Layer` is a registry, not a Java enum, and an unknown name fails. The compiler checked typed literals when the rule
compiled: a `Layer` literal against the layers registered by then, those a rule file's `layerDefinitions` register
included, and an enum literal against the enum's constants. Enum literals and built-in Layer literal casts compiled to
their constants; other Layer conversions resolved the name at the statement. A `def` given a typed value has that
type, so a misspelt name is a compile error where it is written, not a failure when its branch runs.

A `Layer` or an enum value compared with a string literal compares by name, and the literal is checked when the rule
compiles: `calleeLayer != "FAAS"`, `operation == "Write"`. Enum comparisons with literals used enum constants, while
Layer comparisons retained name semantics. String-literal equality compiled to `equals`.

#### Where each construct applies

OTLP and the native input read different spans and build differently: an OTLP rule reads attributes and events,
overwrites the span's metadata and votes on the stored span; a native rule reads tags and logs and makes its outputs in
the segment's build phase. So the language keeps what every input has, and
`TALSourceTypeProvider` is the bridge between TAL and an input, and the point an input is extended at: TAL asks every
input the same neutral questions, and each implementation answers in its own concepts. The input's structure, its span,
is primary and lives in the provider; functions are its helpers and extensions.

- **Its alias and its span,** `alias()` and `inputType()`. `inputType()` supplied the Java type behind `span`, whose
  getters are the `span.*` fields; functions exposed additional input data, including the native segment and the OTLP
  resource and scope.
- **Its constructs,** `constructs()`.
- **Its functions,** the provider's own public methods that take the run first, named in the input's own words:
  `OtlpTALSourceTypeProvider`'s `attr`, `event`, `resource` and the rest, `SwNativeTALSourceTypeProvider`'s `tag`,
  `segment` and the rest. Input providers gained `bind(ModuleManager)`. The native provider retained service
  references bound once at startup, while per-run span and segment state remained in the execution context. Two inputs
  may each declare a function of one name, since no input has both.
- **Its helper functions,** `helperFunctions()`: the helper function implementations its rules call beside its own
  functions, handed to the TAL kernel. Each is a `TALFunctionProvider` that answers through `supportsInputType` which
  inputs it serves, every input or a specific one, and must serve the input that hands it over; the kernel binds each
  once. The shipped inputs both hand over the logic functions and the configuration functions, which are the
  [functions for every input](#functions-for-every-input).

The OTLP span's remaining public reader, `spanAttributes(Object)`, is a method of `OtlpTALSourceTypeProvider`, not of
the bridge; an input that builds through span metadata as OTLP does extends that provider. The compiler loads every
input, so a construct or a function a rule's input does not have is reported as another input's:
`the pairing root belongs to the inputs [otlp], not to this rule's inputType sw-native`, or
`tag() is a function of the inputs [sw-native], not of this rule's inputType otlp`. The rules compile at boot, so the
misuse fails the boot, naming the file and the rule, as a rule on an unregistered layer does. A file that
`requiredModules` leaves out is not compiled, and so not checked.

| | `otlp` | `sw-native` |
|---|---|---|
| span | the OTLP `Span` | the protobuf `SpanObject`, see [The `sw-native` input type](#the-sw-native-input-type) |
| key and value data | `attr(key, ...)`, `hasAttr(key, ...)`: the span's attributes | `tag(key)`, `hasTag(key)`: the span's tags by name, the first of a repeated key; `tagMap()`: the same as a map; `span.tagsList`: the tags in wire order |
| events | `event(name)`, `eventAttr(name, key, ...)`: the span's events | `span.logsList`: the span's logs, each with `time` and `dataList` |
| resource and scope | `resource(key, ...)`, `scopeAttr(key, ...)` | none: the agent reports its identity as the segment's `service` and `serviceInstance` |
| constructs | the `meta` root and the statements that overwrite it (`service`, `instance`, `endpoint`, `layer`), `tag`, `persist {}`, `prepareNetworkAlias`, `buildNetworkAlias` and the `pairing` root | `build {}`, `first {}`, `segmentDef`, `writeNetworkAlias` |
| functions | the [functions of the OTLP input](#functions-of-the-otlp-input) | the [functions of the `sw-native` input](#functions-for-the-sw-native-input) |

Every input has assignment (`<name> = <expr>`), `foreach`, `log`, typed `Layer` and enum values,
`metrics {}` and `source {}` (inside `build {}` in a `sw-native` rule), `requiredModules`, `def`, `if`, `abort {}`,
`urlHost`, `toJson`, `toJsonArray`, `component`, and the [functions for every input](#functions-for-every-input).
`TALConstruct` lists the constructs an input adds: `SPAN_METADATA`, OTLP's, and `NATIVE_SEGMENT`, `build {}`,
`first {}`, `segmentDef` and `writeNetworkAlias`.

#### New keywords

`source`, `foreach`, `in`, `build`, `first`, `segmentDef`, `writeNetworkAlias` and `log`. They still work as field
names and label keys, but no longer as `def` names. `sink` stopped being a TAL keyword, with `sink {}`. The shipped
`otlp-general` rule uses none of them as a `def` name. The existing keywords stay what they are: the native rules name
their locals so that none is a keyword, such as `spanTag` rather than `tag` and `logicJson` rather than `value`.

### New TAL functions

Every function below calls the Java the listeners called, or reproduces a private method of it where the table says
so, so a value, a failure and a log line are the same as theirs.

#### Functions for every input

These are the helper functions the shipped inputs hand over, see
[Where each construct applies](#where-each-construct-applies): the logic functions below, and the configuration
functions, `isGateway`, `isNoUpstreamRealAddressAgent`, `component`, `matchGenAIModel` and the naming functions
`formatServiceName`, `formatInstanceName` and `formatEndpointName`. With them, `urlHost`, `component`, `toJson` and
`toJsonArray`, which OTLP rules already called, are functions of every input, callable wherever a value goes. The
shared helpers also added `isEmpty(list)`, available to every input. The naming functions serve the native rules,
which make the listeners' exact `NamingControl` calls through them, and an OTLP rule's `source {}`, which names its
entities itself: the shipped slow-statement rule formats the database's name with `formatServiceName` before it builds
the record's service id, and `otlp-ai-evaluation` formats the span's service.
`matchGenAIModel` serves the native GenAI rules and the OTLP `otlp-ai-evaluation` rule, which resolves a span's provider
from it as `GenAIContextResolver.resolve` did; its result type, `GenAIModelMatch`, moved with it to the shared runtime
package. `ai-evaluation`'s two functions are listed here too: its provider serves every input.

| Function | Returns | The Java it calls |
|---|---|---|
| `buildServiceId(name, normal)` | the service id | `IDManager.ServiceID.buildId` |
| `buildInstanceId(serviceId, name)` | the instance id | `IDManager.ServiceInstanceID.buildId` |
| `buildEndpointId(serviceId, name)` | the endpoint id | `IDManager.EndpointID.buildId` |
| `minuteTimeBucket(millis)`, `recordTimeBucket(millis)` | the minute and the record (second) time bucket, in the JVM's time zone | `TimeBucket.getMinuteTimeBucket`, `TimeBucket.getRecordTimeBucket` |
| `round(double)` | the `long` nearest the value | `Math.round(double)` |
| `cut(text, length)` | the text cut to at most `length` characters | `StringUtil.cut` |
| `isBlank(text)` | whether the text is `null`, or empty once trimmed | `StringUtil.isBlank` |
| `emptyList()`, `emptyMap()` | a new, empty, mutable `ArrayList` or `HashMap` | the containers an `EndpointSourceBuilder` started with |
| `isEmpty(list)` | true for a `null` or empty list; it let the native RPC rule distinguish references from the no-reference case, where `RPCAnalysisListener.parseEntry` tested the reference count | `CollectionUtils.isEmpty` |
| `parseJsonObject(text)` | the text parsed as a JSON object, `null` for blank text; malformed or non-object text fails the rule. The getters that follow are Gson's, so their coercions and failures are Gson's. `toJson()` answers an empty object instead | `Gson.fromJson(text, JsonObject.class)` |
| `parseLongOrZero(text)`, `parseIntOrZero(text)` | `0` for `null` or empty text; the number; or `0` after the warning `Failed to parse value to long: {}` (`to int` for the second) under the script's logger, for text that does not parse | reproduces `GenAIMeterAnalyzer`'s private `parseSafeLong` and `parseSafeInt`: `Long.parseLong`, `Integer.parseInt` |
| `murmur3Hash(text)` | the first 64 bits of the murmur3-128 hash of the text's UTF-8 bytes, as a `long` | Guava's `Hashing.murmur3_128().hashString(text, UTF_8).asLong()`, as `DefaultAIEvaluationSamplingPolicy` hashes a trace id |
| `floorMod(x, y)` | the floor modulus of two `long` values, never negative for a positive `y` | `Math.floorMod(long, long)` |
| `parseStatusCode(text)` | the text as an `Integer`, or `null` after logging, under the script's logger, the warning `span {} has illegal status code {}` with the span as its input renders it, the wire span for a native span, and the text | `Integer.parseInt` in `RPCAnalysisListener.setPublicAttrs`, its `catch (NumberFormatException)` and `log.warn` |
| `isGateway(address)` | whether `gateways.yml` lists the address; a bare host is also tried at port 80, the port the file gives an instance that names none, a feature native traces gained over the listeners' exact match, see [Failure behaviour the port keeps](#failure-behaviour-the-port-keeps); `false` when `agent-analyzer` is not loaded | `UninstrumentedGatewaysConfig`, as the OTLP path matched it |
| `isNoUpstreamRealAddressAgent(componentId)` | whether `agent-analyzer.noUpstreamRealAddressAgents` lists the component, the list parsed on first read with that class's warning for an illegal value | `AnalyzerModuleConfig.shouldIgnorePeerIPDue2Virtual` |
| `formatServiceName(name)` | the service name after the length limit; `null` stays `null`; a truncation logs | `NamingControl.formatServiceName` |
| `formatInstanceName(name)` | the instance name after the length limit; a truncation logs | `NamingControl.formatInstanceName` |
| `formatEndpointName(service, endpoint)` | the endpoint unchanged when either argument is empty; otherwise the name after the length limit and the endpoint grouping, which keeps per-service state | `NamingControl.formatEndpointName` |
| `matchGenAIModel(model)` | the match for the model's longest prefix in `gen-ai-config.yml`: `provider`, `unknown` when none or when the model is empty, and `pricing`, the matched model's `inputEstimatedCostPerM` and `outputEstimatedCostPerM` (`double`), `null` when no model matches. The first call loads `gen-ai-config.yml` | `GenAIModelMatcher.getInstance().match(model)` (`library-util`), which `GenAIContextResolver.resolve` asks for the provider and `GenAIProviderPrefixMatcher.match` for the prices |
| `evaluationActive()`, `evaluationSampleRate()` | contributed by `ai-evaluation`: whether the module evaluates, and its `sampleRate`, the traces sampled out of 1,000,000 | `AIEvaluationSamplingConfig.isActive`, `getSampleRate` |

#### Functions for the `sw-native` input

`SwNativeTALSourceTypeProvider` declares them as its own methods. The native provider read the bound analysis
configuration, alias cache and server-cluster service, with run-based lookup retained for unbound test contexts, and
reads the run's span where it needs it. It added `segment()`, `segmentStartTime()`, `segmentLatency()`,
`segmentIsError()`, `segmentTags()`, `segmentSampled()` and `segmentBytes()`; the last serialized the received segment
on each call. The naming functions the native rules call are [functions for every input](#functions-for-every-input).

| Function | Returns | The Java it calls |
|---|---|---|
| **Tags** | | |
| `tag(key)`, `hasTag(key)` | the value of the span's first tag with that key, empty when there is none; whether there is one | the listeners' reads of `SpanObject.getTagsList()` |
| `tagList()` | the span's tags as `key.trim() + ":" + value.trim()` strings, in wire order, in a new list | `EndpointSourceBuilder.setTag`, the `tags` list |
| `tagMap()` | the span's raw tags as a new map; a key is not expected to repeat, and when it does the first value is kept, a [documented difference](#failure-behaviour-the-port-keeps) | the maps `EndpointSourceBuilder.setTag` (`originalTags`), `GenAIMeterAnalyzer`, `GenAIEvaluationAnalysisListener` and `VirtualCacheProcessor` built |
| **The segment** | | |
| `segment()` | the span's `SegmentObject`, as the agent sent it | the `SegmentObject` `TraceAnalyzer` handed every listener |
| `segmentStartTime()`, `segmentLatency()`, `segmentIsError()`, `segmentTags()`, `segmentSampled()` | what the runner derived from the segment before the spans, see [The `sw-native` input type](#the-sw-native-input-type) | `SegmentAnalysisListener.parseSegment` |
| `segmentBytes()` | the segment serialized again, what the `Segment` record stores; serialized at each call | `SegmentObject.toByteArray()`, as `SegmentAnalysisListener` stored it |
| **References and peers** | | |
| `findNetworkAlias(address)` | the alias row stored for the address, or `null`. Its `service` and `instance` are decoded from its ids, the service first and both always, so a malformed id fails even when the instance is not used. `instance` is `_blank` when the row names none | `NetworkAddressAliasCache.get`, and the `IDManager` decoding in `RPCAnalysisListener.parseExit` |
| `clusterNameOrAddress(address)` | the cluster `server-clusters.yml` lists the address under, or the address; nothing caught. The OTLP input's `findServerClusterName` answers empty instead, and catches its exceptions | `ServerClusterConfig.clusterNameOrAddress` |
| **Settings of `agent-analyzer`**, through its `NativeTraceAnalysisConfig` service | | |
| `traceAnalysis()` | `agent-analyzer.traceAnalysis` | `NativeTraceAnalysisConfig.isTraceAnalysis` |
| `maxSlowSQLLength()` | `agent-analyzer.maxSlowSQLLength` | `NativeTraceAnalysisConfig.getMaxSlowSQLLength` |
| **Slow statements, the records** | | |
| `slowDBAccessThreshold(dbType)` | `agent-analyzer.slowDBAccessThreshold` for the type: the type lower-cased, then `default`, then `Integer.MAX_VALUE`, read at the call, so a dynamic change applies | `DBLatencyThresholdsAndWatcher.getThreshold` |
| `slowCacheReadThreshold(type)`, `slowCacheWriteThreshold(type)` | the two `agent-analyzer` cache settings, looked up the same way | `CacheReadLatencyThresholdsAndWatcher.getThreshold`, `CacheWriteLatencyThresholdsAndWatcher.getThreshold` |

#### Functions of the OTLP input

`OtlpTALSourceTypeProvider` declares them as its own methods: the functions that read what an OTLP span
carries, or the rows its pairing writes. They were built into the language before and kept their behaviour; an OTLP
rule calls them as it did before, and the attribute functions keep taking any number of keys: `attr`, `hasAttr`,
`event`, `eventAttr`, `resource`, `scopeAttr`, `callerScopedAddress`, `resolveNetworkAlias` and
`findServerClusterName`. `isGateway`, which both inputs call, is one of the
[functions for every input](#functions-for-every-input).

#### Contributed by other modules

The GenAI extraction and the evaluation's sampling policy are logic, so the rules write them out. The shipped
[native-virtual-genai.yaml](../../../oap-server/server-starter/src/main/resources/tal/native-virtual-genai.yaml) and
[native-ai-evaluation.yaml](../../../oap-server/server-starter/src/main/resources/tal/native-ai-evaluation.yaml)
contained the extraction and sampling logic: the tokens, the time to first token, the cost from the model's prices,
and the decision from `ai-evaluation`'s `evaluationActive()` and `evaluationSampleRate()`, the two functions it
contributes, see [The settings a rule reads](#the-settings-a-rule-reads), and a murmur3 hash of the trace id. Beyond
those two settings, neither module contributes a function. The OTLP GenAI metrics and the Zipkin evaluation keep
calling the same Java (`GenAIMeterAnalyzer`, `IAIEvaluationService.shouldSample`), which reads the same settings, so
the rules and the Java decide alike.

`ai-evaluation` takes `GenAIEvaluationSample`, a source that `native-ai-evaluation` sends with
[`source {}`](#new-tal-section-source) in `build {}`, and `otlp-ai-evaluation` at its statement, through a dispatcher of
its own:

- **The source:** `org.apache.skywalking.oap.server.core.source.GenAIEvaluationSample` in `server-core`, beside the
  other sources, declared with `@ScopeDeclaration` under the name `GenAIEvaluationSample` and the new scope id
  `DefaultScopeDefine.GEN_AI_EVALUATION_SAMPLE`, 106. Its fields are the fields of `AIEvaluationContext`: `traceId`,
  `traceRefType` (a `GenAITraceRefType`), `segmentId`, `spanIndex`, a nullable `Integer` the native rule sets, `spanId`,
  which the OTLP rule sets instead, `serviceName`, `serviceInstanceName`, `operationName`, `providerName`, `modelName`,
  `startTimeMillis`, `endTimeMillis`, `error` and `tags` (the map itself), with the `timeBucket` every source carries,
  a record time bucket, which nothing reads. No OAL script reads the scope.
- **The dispatcher:** `org.apache.skywalking.oap.server.ai.evaluation.listener.GenAIEvaluationSampleDispatcher`, beside
  the Zipkin `AIEvaluationSpanListener`, a `SourceDispatcher` of the source that the source receiver finds by
  scanning the classpath, as it finds the core's dispatchers. It builds the context from the fields as the rule set
  them, without normalizing a name or deriving another index, and calls `IAIEvaluationService.sample`, on the rule's
  thread, with nothing caught. The module's `AnalysisListenerFactory` services file moved, with the SPI, to the
  test-only listener module.
- **Reaching the service:** the source receiver creates dispatchers by class scanning, with no constructor arguments,
  so the dispatcher reaches the service statically, as the core's dispatchers reach their stream processors.
  `AIEvaluationProvider.prepare()` binds the service it registers, active or inactive, through the dispatcher's static
  `bind`. While `ai-evaluation` is not loaded nothing is bound, and no rule sends the source: both rule files list the
  module under `requiredModules`, which `trace-analyzer` declares as a required module whenever it is loaded.
- **A hand-off, not a stored record.** Most sources the rules send are dispatched to OAL or to storage. This one hands
  its fields to a module's service instead: `sample` picks the evaluation strategy, skips a task already pending, and
  queues the context for the LLM judge, which writes its own result records later. Nothing is stored at this point.
  The listener called `sample` itself, and the Zipkin `AIEvaluationSpanListener` still does.

The OTLP side moved too. The shipped
[otlp-ai-evaluation.yaml](../../../oap-server/server-starter/src/main/resources/tal/otlp-ai-evaluation.yaml), rule
`otlp-ai-evaluation`, declared for `OTLP_GENERAL` with `inputType: otlp` and listed in the default `talFiles`, replaced
`AIEvaluationSpanListener.onNativeOTLPSpan`:

- **Which spans.** It aborts a span the receiver stores as a Zipkin span, `meta.traceStorage == "zipkin"`, which the
  Zipkin path samples as a Zipkin trace; a span whose `gen_ai.response.model` is blank; every span while
  `evaluationActive()` is false; and a span whose trace id is empty or whose
  `floorMod(murmur3Hash(meta.traceId), 1000000L)` is not below `evaluationSampleRate()`, the
  `DefaultAIEvaluationSamplingPolicy` decision. As every OTLP rule, it sees only the spans of the services whose
  resource sets `service.layer=OTLP_GENERAL`; the listener saw every natively stored GenAI span. A GenAI application
  that sets no layer is not evaluated, a trade-off the move accepted.
- **What it sends.** The tags are the seven attributes the listener copied, `gen_ai.response.model`,
  `gen_ai.provider.name`, `gen_ai.system`, `gen_ai.operation.name`, `gen_ai.input.messages`,
  `gen_ai.output.messages` and `error`, each when it has a value, with `error` set to `true` for an `ERROR` status.
  The provider is the declared `gen_ai.provider.name`, else `gen_ai.system`, else `matchGenAIModel(model).provider`,
  as `GenAIContextResolver.resolve` chose. `source GenAIEvaluationSample {}` then carries `traceRefType "OTLP"`, the
  trace and span ids, `formatServiceName(meta.service)`, the span's name, the model, the span's own start and end,
  `span.startTimeUnixNano / 1000000` and `span.endTimeUnixNano / 1000000`, the error and the tags.
- **The storage mode.** `SpanMetadata` gained `traceStorage`, `otlp` or `zipkin`, empty for a span no OTLP receiver
  stores. `OpenTelemetryTraceHandler.spanMetadataOf` sets it from `otlpTraceStorage`, and
  `TraceAnalyzerServiceImpl` copies it into each rule's `meta`.
- **The listener** kept only its Zipkin path, Zipkin spans and OTLP spans stored as Zipkin spans, unchanged; it lost
  `onNativeOTLPSpan`, the `NamingControl` only that path used, and its `CoreModule` requirement.

#### The function extension points

Rules called functions declared by their input provider, shared helper providers and loaded modules. An input's own
functions are its `TALSourceTypeProvider`'s public methods that take the run first; its helper functions are the
`TALFunctionProvider` implementations its provider hands over, `helperFunctions()`. A module adds functions to an input
through `TALFunctionProvider` too, which sits in `server-core` beside the sources, so a module can contribute
functions without depending on `trace-analyzer`: `ai-evaluation` contributed `evaluationActive()` and
`evaluationSampleRate()` that way. A module such as `gen-ai-analyzer`, which depends only on `server-core` while
`agent-analyzer` depends on it, could not depend on `trace-analyzer` without closing a cycle.

- **Declaring.** A provider names its module, states the inputs it serves with `supportsInputType(inputType)`, which
  has no default, so every provider declares it, reaches its services in `bind(ModuleManager)`, and is a class whose
  public methods TAL calls by name. The interface also holds the alias of the native input, `SW_NATIVE_INPUT_TYPE`,
  `sw-native`, which a provider of a module compares with, since the module cannot depend on `trace-analyzer`. A
  method whose first parameter is a `TALFunctionContext` receives the run there; an input's own functions take
  `trace-analyzer`'s helper that implements it.
- **Resolving.** The compiler resolves a call by name and the rule's input, then by argument count. The compiler
  selected fixed-arity candidates before varargs and ranked argument fits as exact type, assignable supertype, numeric
  widening and scalar conversion. All numeric widenings had equal rank. Equal best scores caused a compile error until
  an explicit cast selected an overload. The result is typed from the chosen method's return type, a nullable object
  or a generic `List` included.
- **Discovery and binding.** A module's providers are found through `ServiceLoader`; an input's helpers come from its
  provider. Startup bound input providers, their helpers and
  providers of loaded modules once, in `trace-analyzer`'s `start()`, see
  [Activation and startup](#activation-and-startup).
- **Threading and names.** A function name that two function classes serve for one input fails the boot, among an
  input's own functions, its helpers and the modules' providers, and so does one that takes an entity constructor's
  name, such as `service`; a function that only another input has fails the rule that calls it, naming that input.
  Compilation installed the resolved function objects in fields of the generated rule instance. Runs called those
  fields directly; the objects held no segment-specific state and were shared safely across receiver threads.
- **The span as an argument.** Native functions and contributed functions received the protobuf wire objects directly,
  through the run's input or typed arguments. No native view interfaces were added to `server-core`; getter chains
  were compiled against the protobuf types.

### Failure behaviour the port keeps

These Java paths failed a segment on input the agent should not send. The port reproduces each, through the functions
above and the runner's no-catch rule:

| Java | Input that fails | The rule |
|---|---|---|
| `VirtualMQProcessor` | a non-blank `transmission.latency` tag that `Long.parseLong` rejects, even when a later one is valid | `spanTag.value as Long` in an assignment, which calls `Long.parseLong`, after the same blank check |
| `RPCAnalysisListener` logic endpoint | an `x-le` tag that is blank (Gson answers `null`, then `has` throws), not a JSON object, malformed, or holds a value its getter cannot convert | `parseJsonObject` and the same Gson getters, in the same order; a tag without the keys is skipped, as before |
| OAL dispatch during `build()` | a source the OAL engine fails on, such as a blank `transmission.latency` tag converted by `core.oal`'s `(str->long)` cast | the same source sent at the same point of the same `build {}` order |

Making any of them tolerant is a behaviour change, and so it was out of scope.

One read changed, by choice: a repeated tag key. An agent is not expected to send a tag key twice on a span, and a
native rule reads the first value wherever it reads a tag by name, through `tag(key)` and the one `tagMap()`. The
listeners differed by place: `EndpointSourceBuilder.setTag` kept the last value in the sources' `originalTags`;
`GenAIMeterAnalyzer` and `GenAIEvaluationAnalysisListener` kept the first; `VirtualCacheProcessor`'s
`Collectors.toMap` threw, failing the segment before its `cache.type` guard. After the port a span with a repeated
key keeps the first value in `originalTags`, and a cache span with one is analyzed rather than failing the segment.
The GenAI analyses are unchanged. The checker's fixtures cover these cases as differences of their own.

One match gained, as a feature: the bare-host gateway match. `RPCAnalysisListener`, `EndpointDepFromCrossThreadAnalysisListener`
and `NetworkAddressAliasMappingListener` asked `UninstrumentedGatewaysConfig.isAddressConfiguredAsGateway` for the
exact address. The native rules call the [`isGateway`](#functions-for-every-input) every input shares, which also tries
a bare host at port 80, the port `gateways.yml` gives an instance that names none. A peer or a reference's
`networkAddressUsedAtPeer` written as a bare host, whose gateway the file lists without a port or at port 80, is
therefore a gateway after the port and was not before: its relation goes to the gateway, and no alias row is written
for it. An address with a port takes the one exact lookup the listeners made, with the same result and the same debug
line. Two log differences ride along: a bare host writes `UninstrumentedGatewaysConfig`'s debug line twice, for the
address and for it at port 80, and an empty address, which the listeners looked up and logged, is answered `false`
without the lookup or its line. The checker's fixtures cover these cases as differences of their own.

One failure path changed, by choice: when the evaluation sample is taken. `GenAIEvaluationAnalysisListener` sampled a
GenAI span during the span loop. `native-ai-evaluation` sends the sample as a `GenAIEvaluationSample`
[`source {}`](#new-tal-section-source) in `build {}`, where a `sw-native` rule makes its outputs, so it is taken in the
build phase, after every earlier file's blocks. The sampling decision and the tag reads stay in the span run, in their
place.

- **A segment that analyzes completely** makes the same `sample` calls, with the same contexts, in the same order. The
  sampling decision is a hash of the trace id, and the evaluation queue shares no state with the OAL engine, so the
  analysis itself does not change. What the evaluation service does with a call depends on its consumer threads,
  before the port as after it: whether a task of the same id is still pending, whether the bounded queue has room, and
  in which minute its counters move, which the OAP's self-observability rules read as per-minute increases. A call made
  later can meet a different state, so a duplicate task, such as one from a re-sent segment, or a call that meets a
  full queue, can be accepted or skipped differently, and a counter can move in the next minute.
- **A segment that fails,** on any span or in any earlier block, takes no sample. Before the port, the GenAI spans
  before the failure had theirs taken.
- **`sample` itself** checks the context and counts a full queue rather than throwing, so taking the samples last
  stops nothing that would have run.

### Verifying that the rules behave as the listeners did

#### The precedent

When MAL, LAL and the hierarchy rules moved from Groovy to the ANTLR4 and Javassist compilers (#13723, March 2026),
the Groovy runtime stayed in the build as test-only `*-v1-with-groovy` modules under
`test/script-cases/script-runtime-with-groovy/`, part of the default `backend` profile. `mal-lal-v1-v2-checker` and
`hierarchy-v1-v2-checker` compiled the rules of their script directories with both engines and compared the outputs:
MAL samples and entities, with a `0.001` numeric tolerance; LAL's abort, save and standard fields, except for cases
marked v2-only, which ran on v2 alone; the hierarchy matches. MAL's `.data.yaml` expectations were generated from the
Groovy output, and benchmarks compared the engines. After v10.4.0 shipped the new engines, #13782 (April 2026) removed
the Groovy modules and the checkers, and kept v2-only execution tests asserting those expectations. Neither module
existed when this work started.

This SWIP repeated that pattern with a stricter comparison: exact, ordered, and covering stateful calls, side effects
and log lines at TAL's output boundary, because the port's claim is identical behaviour, not equivalent output. The
evaluation sample's `sample` calls were compared as a list of their own, and the repeated tag key and the bare-host
gateway match as differences of their own.

#### Modules

- **`test/script-cases/trace-analysis-listener-v1`** holds the removed listener logic, moved with `git mv` so its
  history follows it, with the parser service's import adjusted for the moved interface:
  - `TraceAnalyzer`, `SegmentParserServiceImpl`, the listener managers, `SpanTags` and the listener interfaces;
  - the six listeners and the four processors, `GenAIEvaluationAnalysisListener` with `ai-evaluation`'s
    `AnalysisListenerFactory` services file among them;
  - the builders and helpers only they use: `RPCTrafficSourceBuilder`, `EndpointSourceBuilder`,
    `EndpointDependencyBuilder`, `CommonAnalysisListener`;
  - `ListenerManagerAssembly`, the listener assembly of `AnalyzerModuleProvider.listenerManager()`.
    `ListenerManagerAssembly` recorded the baseline `287b24fedaf91a75d33c9fcfce67569da5e05f35`.

  It is part of the default `backend` profile, through `test/script-cases`, and is never packaged. The existing tests
  of these classes moved with them unchanged: `VirtualDatabaseProcessorTest`, `VirtualCacheProcessorTest` and
  `VirtualServiceClusterNameTest` from `agent-analyzer`, and `RPCAnalysisListenerTest`,
  `EndpointDepFromCrossThreadAnalysisListenerTest` and `ServerClusterNameAnalysisTest`, with their `MockReceiver`, from
  the trace receiver plugin. The configuration and cache classes the functions and the runner read, such as
  `TraceSegmentSampler`, the `SegmentStatusAnalyzer` strategies and the latency-threshold watchers, stay in
  `agent-analyzer`.
- **`test/script-cases/trace-analysis-v1-v2-checker`** contained `NativeTraceComparisonTest`, its fixtures and
  recording collaborators, the JaCoCo branch gate and `NativeTraceBenchmark`.

#### What is compared

Each side ran every fixture with its own instance of every stateful collaborator: `NamingControl` with a real
`EndpointNameGrouping`, `NetworkAddressAliasCache`, `AnalyzerModuleConfig` (whose component list parses, and warns, on
first read), the latency-threshold watchers, the sampling policy, the searchable-tags watcher, the GenAI analyzer (v1
side) and a recording evaluation service, each kept for a whole multi-segment run as the OAP keeps them. Each side used
its own analysis collaborators; source scopes were registered once, and the checker did not run stream processors.
Logging was captured at TRACE except in the quiet-logs profile. The recording evaluation service records each call,
answers `shouldSample` as the service it stands for answers, the active one from its sampling policy or the inactive
one with `false`, and forwards no `sample` call. Only the v1 side asks it `shouldSample`; the new side decides in the
rule from the same `ai-evaluation` settings, served by the module's `evaluationActive()` and
`evaluationSampleRate()`, which both sides are given. `GenAIModelMatcher` is loaded before either side runs, so its
one-time load lines belong to neither. The comparison therefore covers each segment's analysis up to and including its
`sample` calls. What the evaluation service does with a call, inside `sample` (the pending-task dedup, the enqueue,
the capacity counter) and after it on the consumer threads (the judge calls, result records and other counters), is
the same code on both sides and outside this comparison. The v1 side is the old `TraceAnalyzer` with the old listener
assembly. The new side is the runner with the default `nativeTalFiles`.

Each side produces one ordered event log per segment. Every event carries its phase (the `parseSegment` work, a first
run, a regular run, the build phase), the wire index of the span for the phases that have one, and a canonical group:
the Java listener or processor it belongs to, which on the new side is the rule that ports it (`RPCAnalysisListener`
for the three `native-rpc` rules, and so on). The events are:

1. every source sent, rendered at `SourceReceiver.receive`: its class, then every non-static field of the class and
   its superclasses, read by reflection and rendered recursively. Lists render in order, maps sorted by key, byte
   arrays by their content, enums and `Layer` values by name, and `null` stays distinct from empty. No method of the
   source is called, so `NetworkAddressAliasSetup`, whose `getEntityId` throws, renders like the others. A
   `receive(null)`, which the Java made for an optional relation it did not build and the dispatcher ignores, is not an
   event;
2. every `NamingControl` call, with its arguments and its result or exception, through a recording `NamingControl`;
3. every call to the sampler, the searchable-tags watcher and the segment status analyzer, and every alias-cache read
   (`findNetworkAlias`, which calls `NetworkAddressAliasCache.get`), with its arguments and result. The alias cache is
   bounded (`maxSizeOfNetworkAddressAlias`) and a read updates its eviction order, so an extra or reordered read could
   change a later segment without changing this one's sources;
4. nothing for the evaluation's sampling decision and the GenAI extraction, which the v1 side asks of Java and the new
   side computes in the rules: their effects are the events that follow, the GenAI sources and naming calls, the log
   lines and the `sample` calls, which are compared apart, see below. `transferToSources` builds the four GenAI
   sources; its naming calls are events through item 2;
5. every log line: its level, message pattern and arguments, and its logger. A listener's class logger and the logger
   of the script that ports it count as one, and so do `TraceAnalyzer`'s and `SegmentAnalysisListener`'s `parseSegment`
   lines and the runner's own logger, and so do `GenAIMeterAnalyzer`'s and `native-virtual-genai`'s; a delegated Java
   class logs under its own name on both sides;
6. the exception at the point it was thrown. Its class always matches. Its message matches for exceptions the
   listener code and its libraries raise by name, such as `NumberFormatException`, Gson's exceptions and
   `IllegalStateException`; a `NullPointerException`'s helpful message names a variable and differs between the two
   sides, so only its class is compared.

Ordinary fixtures required equal ordered event logs and endpoint-grouping state, the state compared after each
segment, failed ones included. Gateway and repeated-tag differences were checked explicitly, including the permitted
log differences.

Evaluation samples were captured separately in call order, each context snapshotted at the call. Successful analysis
compared the contexts against the live v1 reference, adjusted for documented differences. A failed native analysis was
required to produce no samples; the v1 calls remained in the review dump.

The checker ended at TAL's output boundary: emitted sources and evaluation records, stateful collaborator calls, log
lines, exceptions and endpoint-grouping state. It did not execute OAL dispatch, stream processors, TopN buffering or
persistence. No shipped native rule emitted `metrics {}`; a separate native-output unit test exercised that language
path.

#### Input

- **Fixtures.** The checker used named `SegmentObject` fixtures constructed in Java. They covered the native analysis
  branches and malformed-input cases directly; it did not depend on recorded e2e segments, protobuf files, JSON
  companions or agent-version metadata. Named after what they exercise, the fixtures covered:
  - every span type and layer, unknown enum values, an empty segment, a segment without span `0`, a late span `0`,
    repeated span `0`s, a span `0` that is an exit, a local or an unknown span, and a reversed wire order;
  - `skipAnalysis` on an entry, an exit and a local span, including a local span with logic endpoints, a
    virtual-service span and a GenAI span, which the GenAI processor still analyzes and the evaluation skips;
  - no, one and several references, mixed reference types, and an MQ entry whose first reference has a blank address
    and a later one does not;
  - empty against whitespace peers, endpoints, instances and endpoint owners;
  - HTTP status tags valid, invalid, then valid, across both spellings, and repeated RPC status tags;
  - database fixtures covered reordered tags at the threshold, a low-threshold type before a high-threshold type, a
    blank final statement and locale-sensitive type matching;
  - cache fixtures covered a repeated `cache.key`, missing or blank type, absent command or key, and unknown or
    mixed-case operations;
  - MQ `transmission.latency` values that are blank, signed, padded, overflowing or invalid, including an invalid one
    before a valid one;
  - every `x-le` case under [Failure behaviour the port keeps](#failure-behaviour-the-port-keeps): blank text, a JSON
    `null`, a scalar, an array, a malformed object, missing keys, a number where a string is read and the reverse, an
    incompatible getter, a local span's `logic-span`, an entry span carrying `logic-span`, and repeated `x-le` tags;
  - segment fixtures exercised the derived-function results under the status and sampling profiles, zero, negative and
    extreme timestamps, durations beyond the `int` range, searchable-tag length and duplicate handling, empty trace ids
    and errors on later spans. Short-name profiles exercised truncation;
  - GenAI spans with repeated tags, a missing or blank model, provider names in current and legacy spellings, token
    and time-to-first-token values that do not parse or overflow, long provider and model names that the naming rules
    truncate, cost rounding, and a duration beyond the `int` range;
  - evaluation fixtures covered a span id different from its wire index, missing or blank models, entry, exit, local
    and skipped GenAI spans, and a GenAI span before a later failure;
  - two relations of one entity and minute with different component ids, in both send orders;
  - endpoint grouping at its per-service capacity;
  - alias rows written and dialed in one segment, rows without an instance, with a malformed id and naming a virtual
    service.

  The checker exercised malformed-input failures during span analysis and compared the events preceding them.
  Separate TAL unit tests covered guarded getters, typed values, loop scope, deferred captures, segment variables,
  abort behaviour and output validation. JMH supplied the concurrent-segment workload; the checker did not add a
  concurrent equivalence test or a dispatcher-induced build-failure test.
- **Configuration profiles.** Each fixture runs under several profiles, since configuration decides branches. The
  profiles used the constructed fixtures' addresses and tags and included configuration changes, bounded caches,
  Turkish locale and WARN-level logging:
  - the defaults, read from the shipped `application.yml`;
  - `gateways.yml` listing fixture addresses, a bare host, and an address beyond the service name length limit;
  - `server-clusters.yml` listing the database, cache and broker addresses;
  - `noUpstreamRealAddressAgents` at its default, which lists the Nginx component the nginx-lua agent reports,
    emptied, and holding an illegal value;
  - thresholds low enough that slow statements and slow accesses fire, and a short `maxSlowSQLLength`;
  - short service, instance and endpoint name limits, endpoint grouping rules, and a low
    `maxHttpUrisNumberPerService`;
  - each `segmentStatusAnalysisStrategy`, a sampling policy with a low rate and a per-service rule, and
    `forceSampleErrorSegment` off;
  - `searchableTracesTags` without the fixtures' keys;
  - `traceAnalysis: false`;
  - active and inactive evaluation services, evaluation without the GenAI analyzer, and absent optional modules, using
    the configured evaluation sample rate;
  - a default locale whose lower-casing differs from English, Turkish;
  - a sampling policy and searchable-tags update arriving between two segments;
  - an alias cache of two rows, so the rows a run reads decide which ones a later segment still finds;
  - logging at WARN, the quiet-logs profile.
- **Alias cache.** Exit spans read the alias cache. Ordinary fixtures ran in cold and warm multi-segment passes with
  synchronized reloads and updates, as the OTLP recorded-trace test does:
  - a cold pass with empty caches;
  - a warm pass whose caches hold the rows the cold pass's alias setups produce once the cache reload has run, plus
    rows without an instance, with a malformed id, and naming a virtual service, and a row loaded halfway through.

  Each side has its own cache with identical contents, reloaded at the same points. A segment whose entry writes an
  alias that a later exit of the same segment dials checks that the row is not visible before the reload.
  Intentional-difference fixtures ran separately on fresh sides so their changed state could not affect the ordinary
  comparison.

#### Guards against a vacuous pass

- The default and evaluation-active profiles required designated positive fixtures for each compiled rule, and every
  profile required a non-empty event log. Negative fixtures may rightly produce none.
- Mutation tests checked the comparator itself. Each mutates one side's log in a way a defect would show up, a
  changed field, a dropped event, a duplicated event, two events swapped, `null` turned into empty, or a missing log
  line, and asserts the comparison fails. A further self-test asserts that each difference check rejects a new side
  that behaves as the v1 side.
- The checker runs JaCoCo over every moved class. After the complete profile run, JaCoCo required every missed branch
  in the moved classes to match the allowlist exactly; an unexpected miss, a newly covered allowance or a changed count
  failed the gate. The allowlist contained three entries covering five unreachable branches, each with its reason,
  such as the `UnexpectedException` default of `identifyRemoteServiceLayer`. A passing gate showed the fixtures reached
  every branch except the five allowlisted ones; the event-log comparison is what showed the behaviour was the same on
  the paths they exercised.

#### Expected data

The checker used the live v1 execution as its oracle and committed no expected event data. Each run wrote the v1
events and evaluation samples to `target/checker-events-<profile>.txt` for review. Those files were run outputs, not
test inputs.

#### The moved OTLP outputs

The two OTLP outputs that moved, the slow statement and the evaluation sample, were each compared once, before and
after, in two worktrees, the pre-change code against the changed code, with no committed harness:

- **The slow statement.** The shipped `otlp-slow-sql` rule with the `SlowSQL` builder, written as the OTLP runner wrote
  it, against the rule with `source DatabaseSlowStatement {}`, over 32 constructed OTLP spans: every peer-naming
  fallback, the server-cluster lookup, the cache and kind aborts, latency and statement edges, cutting, unicode, an
  over-long peer name and timestamp edges. Every `DatabaseSlowStatement` (25 written, 7 refused) and every persist
  vote matched.
- **The evaluation sample.** `AIEvaluationSpanListener.onNativeOTLPSpan` against `otlp-ai-evaluation` with the real
  dispatcher, over 79 constructed OTLP spans under three settings: a sample rate of 1,000,000, a rate of 500,000 with
  60 seeded trace ids, and an inactive module. All 237 outcomes matched, 76, 43 and 0 sampled, with every
  `AIEvaluationContext` field equal, a span with no start time and attributes of every value kind included.

`OtlpAIEvaluationRuleTest` keeps the rule's cases in the unit tests, over the shipped file, with a test
`gen-ai-config.yml` the trace-analyzer tests gained for `matchGenAIModel`.

#### Performance

`NativeTraceBenchmark` (JMH, as `MalBenchmark` and `LalBenchmark` were) compiled rules and created collaborators
outside measurement at trial setup. It compared throughput and GC-profiled allocation for the constructed fixture mix,
wide segments and four-thread workloads, with cold or preloaded alias caches, logging disabled and emitted sources
consumed by a blackhole.

Native performance work stored function objects in compiled-rule fields, bound native services once, compiled literal
operations directly, and reused one execution context per segment. `bindNativeRun` reset only state a native rule
could change; OTLP retained its runtime lifecycle. No retained-heap measurement or automatic regression threshold was
added.

#### Boot and end to end

Startup wiring was implemented through module dependencies and rule compilation before callers started. Unit tests
covered file ordering, missing files, duplicate native rule names, input validation and refusal to analyze before rules
were loaded. The checker remained in process and added no OAP/data-generator boot matrix, Kafka startup-race test or
e2e recording run. The existing e2e cases were not changed, apart from the storage case's expected configuration dump,
which gained `nativeTalFiles` and the new `talFiles` default, and the two GenAI cases with natively stored OTLP
spans, `otlp-virtual-genai/native` and `mixed-virtual-genai`, whose OTLP application sets `service.layer=OTLP_GENERAL`
through `OTEL_RESOURCE_ATTRIBUTES` so that `otlp-ai-evaluation` sees its spans.

#### Retirement

The implementation retained the listener oracle and comparison module in the backend test build. This change did not
retire either module.

### Steps

The implementation added the language constructs, typed conversions and extension points; ported the listeners to
shipped rules over protobuf inputs; moved the native service and startup wiring; retained the listener oracle with an
output-boundary checker, branch gate and JMH benchmark; and reorganized the operator documentation. It also applied the
native runtime optimizations described above. No retirement step was included.

1. **TAL.** Plain assignment and typed `null` declarations, `source {}` for every input, `foreach` with its scoping and
   element typing, `build {}` with `metrics {}` inside it, `first {}`, `segmentDef`, `writeNetworkAlias`, `log`, typed
   `Layer` and enum values, file-level `requiredModules`, `TALFunctionProvider`, `agent-analyzer`'s
   `NativeTraceAnalysisConfig` service and `ai-evaluation`'s two functions, and the functions for every input, each
   with unit tests. The OTLP `outputType` key, `sink {}` and the `TALOutputBuilder` interface were removed, and the
   shipped `otlp-slow-sql` rule writes its record with `source DatabaseSlowStatement {}`; `TraceAnalyzerServiceImpl`
   writes no record and no longer takes a `ModuleManager`. The OTLP evaluation sampling moved from
   `AIEvaluationSpanListener` to the shipped `otlp-ai-evaluation` rule, with `SpanMetadata.traceStorage` set by the OTLP
   receiver and `matchGenAIModel` and `ai-evaluation`'s two functions served to every input. The compiler work this
   needed beyond the
   grammar:
   - getters evaluated where they stand in the control flow and in their phase. A typed chain's getter had been
     hoisted to the start of the method, which would dereference a `null` before the guard that protects it, or run a
     build-phase getter during the span run;
   - `is` getters for booleans, byte arrays, and generic element types carried through chains;
   - setters for objects, `null`, enums, `Layer`, byte arrays, lists and maps, without converting them to strings,
     with the best-fitting overload chosen as for a function call;
   - deferred `build {}` code with its captured input and values, and `first {}` as a run of its own;
   - `segmentDef` storage per segment and rule, outside the compiled rule;
   - input-aware compile checks that fail the boot, per [Where each construct applies](#where-each-construct-applies):
     each input's `TALSourceTypeProvider`, the bridge of alias, input type, `constructs()`, helper functions, startup
     binding and the provider's own functions, with the OTLP functions moved onto `OtlpTALSourceTypeProvider` and the
     native ones on `SwNativeTALSourceTypeProvider`, and `supportsInputType` on `TALFunctionProvider`;
   - rules loaded in configured file order and in-file order;
   - no catch on the `sw-native` path, around rules and around the dispatch of the sources they send, and no
     `TalDSL.evaluate` per-rule debug line on it;
   - the native runtime optimizations described under [Performance](#performance).
2. **The port.**
   - The `sw-native` input type over the protobuf `SpanObject` and `SegmentObject`, the runner with its `parseSegment`
     work, `ISegmentParserService` moved to `trace-analyzer` and its callers' required modules, the data-generator
     tool's configuration, the startup dependencies, `nativeTalFiles`, the shipped rule files, the functions of
     `trace-analyzer`, the `GenAIEvaluationSample` source in `server-core` and `ai-evaluation`'s dispatcher of it.
   - The listener framework, the listeners and the `AnalysisListenerFactory` SPI moved to
     `trace-analysis-listener-v1`, and the checker module was added.
3. **Documentation,** per [Documentation layout](#documentation-layout): the language page, the two input pages, the
   native setup and bundled-analysis pages, the menu, the moved anchors and their inbound links, the
   `TALFunctionProvider` Javadoc and `changes.md`.

## Imported Dependencies libs and their licenses.

None in the distribution. The checker module uses JaCoCo (the Maven plugin and `org.jacoco.core`, EPL-2.0) at test
time only; it is not packaged.

## Compatibility

- **Outputs.** The checker compared ordered outputs, collaborator events and logs, with explicit checks for
  repeated-tag handling, gateway matching and deferred evaluation samples: the same sources with the same fields in
  the same order, the same `NamingControl` calls, sampler calls, alias rows, log lines and failures. The evaluation
  `sample` calls are the same, with the same contexts and in the same order, for a segment that completes, though made
  after its last span, so what the evaluation service accepts can differ with its consumers' timing; a segment that
  fails makes none, see [Failure behaviour the port keeps](#failure-behaviour-the-port-keeps).
- **Configuration.**
  - One new setting, `trace-analyzer.default.nativeTalFiles`.
  - `talFiles`, `traceAnalysis`, the analyzer's thresholds and every other existing setting kept their meaning.
    The default `talFiles` gained `otlp-ai-evaluation`; a deployment that sets `talFiles` (`SW_TRACE_TAL_FILES`) adds
    it to keep the OTLP evaluation sampling.
  - `SW_TRACE_ANALYZER=-` now stops the boot while the native trace receiver, which is on by default, or the Kafka
    fetcher is loaded, since `trace-analyzer` hosts the native analysis. An operator who removed the module to turn
    off OTLP analysis sets `talFiles` to empty instead, which the module already supported. With neither caller
    loaded, removing the module still works.
- **`TALSourceTypeProvider`,** now the point an input is extended at, was reshaped. `TALSourceTypeProvider` gained
  constructs, helper functions and startup binding, with input functions declared as provider methods: `alias()`,
  `inputType()`, `constructs()`, `helperFunctions()` and `bind(ModuleManager)`. OTLP-style custom inputs retained the
  constructs by extending `OtlpTALSourceTypeProvider`. `spanAttributes(Object)` remained overridable; scope attributes
  came from the execution's supplied scope map, and custom event behaviour required overriding the public function
  methods rather than the former reader SPI.
- **Extension point.** The `AnalysisListenerFactory` SPI was removed. A third-party listener moves to a rule file, plus
  a `TALFunctionProvider` for what TAL cannot read or call, and a source with its dispatcher for what it hands over
  that is not an OAL source, as `ai-evaluation` takes `GenAIEvaluationSample`. The `TALOutputBuilder` interface of
  11.1.0 was removed, so a third-party builder written for it moves to a source and its dispatcher too. The module
  system did not change: `agent-analyzer` declares the `NativeTraceAnalysisConfig` service, and its provider registers
  it, as it registers `UninstrumentedGatewaysConfig`; `ai-evaluation` does the same with `AIEvaluationSamplingConfig`.
- **TAL.** Additive, except the eight new keywords, which a user's TAL rule can no longer use as `def` names, and the
  OTLP record output: the `outputType` key and `sink {}` were removed, so a custom OTLP rule file written for 11.1.0
  that uses them fails to load until its record moves to a `source {}`, as the shipped `otlp-slow-sql` rule's did.
  Plain assignment is additive: a later `def` of a declared name keeps assigning it.
- **Getters.** On every input, a getter now runs where it stands rather than hoisted to the start of the rule. A chain
  that a hoisted getter dereferenced before its guard threw there, which on the OTLP path skips the rule for that span,
  with a warning the first time and at debug after; it now reaches the guard. The `otlp` input's chains read protobuf
  getters, which never return `null` and have no side effects, so the shipped `otlp-general` rules behave the same. A
  custom input whose getters can return `null` may now pass a guard where it used to fail before it.
- **Log lines.** Ordinary log levels, messages and arguments were preserved under the new logger names; the documented
  gateway lookups also changed their debug lines. The native analysis's lines moved from the listeners' class loggers
  to `org.apache.skywalking.trace.analysis.script.<script name>` and the runner's own logger, so a logging
  configuration that sets a level for a listener class sets it for the script's logger instead.
  `GenAIMeterAnalyzer`'s lines for native spans, its missing-model debug line and its parse warnings, moved to the
  `native-virtual-genai` script's logger; it keeps logging under its own name for OTLP and Zipkin spans.
- **OTLP AI evaluation.** A natively stored OTLP GenAI span is evaluated only when its service sets
  `service.layer=OTLP_GENERAL`, as `otlp-ai-evaluation` sees the spans of that layer; the listener evaluated every such
  span. A GenAI application sets the attribute on its resource, for example
  `OTEL_RESOURCE_ATTRIBUTES=service.layer=OTLP_GENERAL`. Zipkin spans, and OTLP spans stored as Zipkin spans, are
  evaluated as before. The rule runs with the other TAL rules, before the receiver checks the span listeners' veto and
  the rules' `persist {}` votes, where the listener ran inside the native store, after both: a span that a custom rule
  or listener keeps out of storage is evaluated now, though no shipped rule or listener vetoes a GenAI span. With
  `dedupWindow` above 0, a span an exporter sends again to the same node, while that node's dedup set still holds it,
  is not analyzed again and so not evaluated again, even after the earlier evaluation finished; the listener sampled
  it again once the evaluation service no longer held the earlier task as pending.
- **Failures while a source is dispatched.** On the OTLP input a source is dispatched at its statement, so a failure
  there fails the rule, which is caught and skipped: the slow-statement rule's `persist {}` vote is then not counted,
  where the runner used to write the record after the rule and log a failed write, keeping the vote. A failure while
  an OTLP evaluation sample is dispatched no longer drops the span, as the native store's catch did when the listener
  threw.
- **Storage, protocols, query, UI.** No change.
- **Editing the rules.** An operator who edits a native rule file, or reorders `nativeTalFiles`, changes the native
  analysis, as with any rule file. The shipped files and the default order are what the checker compares.
- **Distributions.** The native TAL rules are compiled when the module starts. This change added no distribution
  precompilation and no boot-verification suite.

## General usage docs

### What is new in TAL

The port added the native constructs and functions summarized here. The sections above define each item.

**Objects**

Its objects were the protobuf `SpanObject`, its `tagsList`, `logsList` and `refsList`, and the `SegmentObject`
returned by `segment()`. Logs exposed `dataList`; span and reference kinds remained enums. Segment-wide derived values
were exposed through functions.

| Object | What it holds |
|---|---|
| `span`, the `SpanObject` | `operationName`, `peer`, `spanId`, `parentSpanId`, `startTime`, `endTime`, `componentId`, `isError`, `skipAnalysis`, `spanType` (enum), `spanLayer` (enum), `tagsList`, `logsList`, `refsList` |
| a log, in `span.logsList` | `time`, `dataList` (key and value pairs) |
| a reference, in `span.refsList` | `refType` (enum), `traceId`, `parentTraceSegmentId`, `parentSpanId`, `parentService`, `parentServiceInstance`, `parentEndpoint`, `networkAddressUsedAtPeer` |
| a tag, in `span.tagsList` | `key`, `value` |
| `segment()`, the `SegmentObject` | `service`, `serviceInstance`, `traceId`, `traceSegmentId`, `isSizeLimited`, `spansList` |
| the segment functions | derived before the spans: `segmentStartTime()`, `segmentLatency()`, `segmentIsError()`, `segmentTags()`, `segmentSampled()`; serialized again at each call: `segmentBytes()` |

**API**

| Kind | Name | What it does |
|---|---|---|
| block | `source <Name> { <field> <expr> }` | every input, inside `build {}` on `sw-native`, in the extractor on `otlp`: builds one source by its scope name and sends it, at its statement, to the source receiver, which dispatches it to OAL or to a module's dispatcher; TAL's one record output |
| block | `build { }` | `sw-native`: queued during the span loop with the run's values, run after the last span; where a native rule makes its outputs, `metrics {}` and `source {}` |
| block | `first { }` | `sw-native`: runs on every span whose id is `0`, before every rule's regular run on it |
| statement | `<name> = <expr>` | assigns a declared variable; `def` declares, and `def x = null as String`, `as Layer` or `as <Enum>` declares a typed variable before the branches that assign it |
| statement | `foreach (<name> in <list>) { }` | loops over a list, with per-iteration `def` scope |
| statement | `segmentDef <name> = <expr>` | `sw-native`: a variable that lives for one segment |
| statement | `writeNetworkAlias <address>` | `sw-native`: sends the alias row the sw8 reference names, at once |
| statement | `log <level> "<message>", <args>` | writes a log line under `org.apache.skywalking.trace.analysis.script.<script name>` |
| conversion | `<expr> as Layer`, `<expr> as <Enum>` | a typed `Layer` or enum value; a literal is checked when the rule compiles |
| comparison | a `Layer` or enum value `==` / `!=` a string literal | compares by name; the literal is checked when the rule compiles |
| rule file key | `requiredModules` | compiles and runs the file's rules only when the modules are loaded |
| input type | `sw-native` | `SwNativeTALSourceTypeProvider`, on `TALSourceTypeProvider`, the bridge to an input and the point it is extended at |
| setting | `trace-analyzer.default.nativeTalFiles` | the native rule files, in run order |
| `meta` field | `traceStorage` | `otlp` or `zipkin`, how the OTLP receiver stores the span, its `otlpTraceStorage`; empty for a span no OTLP receiver stores |
| functions, every input | `buildServiceId`, `buildInstanceId`, `buildEndpointId`, `minuteTimeBucket`, `recordTimeBucket`, `round`, `cut`, `isBlank`, `emptyList`, `emptyMap`, `isEmpty`, `parseJsonObject`, `parseLongOrZero`, `parseIntOrZero`, `murmur3Hash`, `floorMod`, `parseStatusCode`, `urlHost`, `toJson`, `toJsonArray` | the logic helper functions both inputs hand over; call the Java the listeners called, or reproduce its private parsing |
| functions, every input (configuration) | `isGateway`, `isNoUpstreamRealAddressAgent`, `component`, `formatServiceName`, `formatInstanceName`, `formatEndpointName`, `matchGenAIModel` | the configuration helper functions both inputs hand over: the checks against `gateways.yml`, `noUpstreamRealAddressAgents` and `component-libraries.yml`, and the core's naming rules |
| functions, `sw-native` | `tag`, `hasTag`, `tagList`, `tagMap`, `segment`, `segmentStartTime`, `segmentLatency`, `segmentIsError`, `segmentTags`, `segmentSampled`, `segmentBytes`, `traceAnalysis`, `maxSlowSQLLength`, `findNetworkAlias`, `clusterNameOrAddress`, `slowDBAccessThreshold`, `slowCacheReadThreshold`, `slowCacheWriteThreshold` | declared by the native input, for what only native spans need |
| functions, `otlp` | `attr`, `hasAttr`, `event`, `eventAttr`, `resource`, `scopeAttr`, `callerScopedAddress`, `resolveNetworkAlias`, `findServerClusterName` | built into the language before; now declared by the OTLP input, unchanged in behaviour |
| source | `GenAIEvaluationSample` (`server-core`, scope id 106) | sent by `native-ai-evaluation` and `otlp-ai-evaluation` with `source {}`; `ai-evaluation`'s `GenAIEvaluationSampleDispatcher` hands the evaluation context to `IAIEvaluationService.sample`, which queues it for the LLM judge, and stores nothing |
| extension point | `TALSourceTypeProvider` (`trace-analyzer`) | an input for TAL: `alias()`, `inputType()`, `constructs()`, `helperFunctions()`, `bind(ModuleManager)`, and the provider's own methods that take the run first as the input's functions; an OTLP-style input extends `OtlpTALSourceTypeProvider` |
| extension point | `TALFunctionProvider` (`server-core`) | the functions a module adds to an input, scoped with `supportsInputType(inputType)`, which every provider implements, and bound once through `bind(ModuleManager)`; it holds the native input's alias, `SW_NATIVE_INPUT_TYPE` |
| module service | `NativeTraceAnalysisConfig` (`agent-analyzer`) | what the native analysis reads of `agent-analyzer`'s configuration: `traceAnalysis`, `maxSlowSQLLength`, the segment status and sampling decisions, the slow-statement thresholds and `noUpstreamRealAddressAgents` |
| functions, `ai-evaluation` | `evaluationActive`, `evaluationSampleRate` | contributed through `TALFunctionProvider` for every input: the module's active decision and its sample rate |
| extension point | a source and its dispatcher | an output a module takes: the source class in `server-core`, with a `@ScopeDeclaration` whose id `DefaultScopeDefine` holds, and a `SourceDispatcher` of it in an `org.apache.skywalking` package, which the source receiver finds by scanning the classpath |

### The shifted rule files

The following table linked the shipped native rule files under `oap-server/server-starter/src/main/resources/tal/`,
and the OTLP rule file that took the OTLP side of the evaluation sampling over.
Their comments identified the listener methods they ported. `native-segment` used the segment functions for the
runner's `parseSegment` results. No duplicate rule files were included with the SWIP.

| File | Rules | Ported from |
|---|---|---|
| [native-rpc.yaml](../../../oap-server/server-starter/src/main/resources/tal/native-rpc.yaml) | `native-rpc-entry`, `native-rpc-exit`, `native-rpc-logic-endpoint` | `RPCAnalysisListener`, `RPCTrafficSourceBuilder`, `EndpointSourceBuilder` |
| [native-endpoint-cross-thread.yaml](../../../oap-server/server-starter/src/main/resources/tal/native-endpoint-cross-thread.yaml) | `native-endpoint-cross-thread` | `EndpointDepFromCrossThreadAnalysisListener`, `EndpointDependencyBuilder` |
| [native-network-alias.yaml](../../../oap-server/server-starter/src/main/resources/tal/native-network-alias.yaml) | `native-network-alias` | `NetworkAddressAliasMappingListener` |
| [native-segment.yaml](../../../oap-server/server-starter/src/main/resources/tal/native-segment.yaml) | `native-segment` | `SegmentAnalysisListener` (with the runner's `parseSegment` results read through the segment functions) |
| [native-virtual-service.yaml](../../../oap-server/server-starter/src/main/resources/tal/native-virtual-service.yaml) | `native-virtual-cache`, `native-virtual-database`, `native-virtual-mq` | `VirtualCacheProcessor`, `VirtualDatabaseProcessor`, `VirtualMQProcessor` |
| [native-virtual-genai.yaml](../../../oap-server/server-starter/src/main/resources/tal/native-virtual-genai.yaml) | `native-virtual-genai` | `VirtualGenAIProcessor`, `GenAIMeterAnalyzer.extractMetricsFromSWSpan` and `transferToSources` |
| [native-ai-evaluation.yaml](../../../oap-server/server-starter/src/main/resources/tal/native-ai-evaluation.yaml) | `native-ai-evaluation` | `GenAIEvaluationAnalysisListener`, with `AIEvaluationService.shouldSample` and `DefaultAIEvaluationSamplingPolicy` |
| [otlp-ai-evaluation.yaml](../../../oap-server/server-starter/src/main/resources/tal/otlp-ai-evaluation.yaml) | `otlp-ai-evaluation` | `AIEvaluationSpanListener.onNativeOTLPSpan`, with `AIEvaluationService.shouldSample`, `DefaultAIEvaluationSamplingPolicy` and `GenAIContextResolver.resolve` |

### Documentation layout

The tracing documentation is organized by where a trace comes from. TAL comes first, as the language the native and
OpenTelemetry analyses are written in. Then each source has the same parts: its setup, its bundled analysis (what the
shipped rules produce and how to configure them), its TAL input reference, and best practices where there are some.

The documentation was reorganized by trace source. Before, TAL was one page written for OpenTelemetry spans, listed
under "Customization" as "Analysis OpenTelemetry Traces", and the native analysis was spread over four feature pages
under "Tracing" and the virtual-service pages under "Marketplace". The shared TAL page became the language reference,
separate native and OTLP pages documented their inputs, and native setup and bundled-analysis pages were added.
Existing feature pages retained their paths; OTLP-specific TAL sections and Zipkin sampling text moved to their
source-specific pages.

```text
Setup > Tracing
├── Overview                          setup/backend/tracing-overview.md            new
├── Trace Analysis Language           concepts-and-designs/tal.md                  the language, reorganized
├── SkyWalking Native Traces
│   ├── Setup                         setup/backend/native-trace.md                new
│   ├── Bundled Analysis                                                           new catalog
│   │   ├── Overview                  setup/backend/native-trace-analysis.md       new
│   │   ├── Trace Sampling            setup/backend/trace-sampling.md              existing, its Zipkin part moved out
│   │   ├── Slow Database Statement   setup/backend/slow-db-statement.md           existing
│   │   ├── Slow Cache Command        setup/backend/slow-cache-command.md          existing
│   │   └── Message Queue Performance setup/backend/mq.md                          existing
│   └── TAL for Native Segments       concepts-and-designs/tal-sw-native.md        new
├── OpenTelemetry Traces
│   ├── Setup                         setup/backend/otlp-trace.md                  existing
│   ├── Bundled Analysis              setup/backend/trace-analyzer.md              existing
│   ├── TAL for OpenTelemetry Spans   concepts-and-designs/tal-otlp.md             new
│   └── Best Practices                setup/backend/otlp-trace-practices/*.md      existing, links updated
├── Uninstrumented Gateways           setup/backend/uninstrumented-gateways.md     existing
├── Server Clusters                   setup/backend/server-clusters.md             existing
└── Zipkin Traces                     setup/backend/zipkin-trace.md                existing, gained the Zipkin sampling part
```

Native Bundled Analysis became a catalog containing Overview, Trace Sampling, Slow Database Statement, Slow Cache
Command and Message Queue Performance. The native branch ended with TAL for Native Segments. Existing OTLP Best
Practices remained in the menu, with links updated to the moved TAL sections.

| Page | What it holds |
|---|---|
| Overview (new) | The three trace sources and how each is received, analyzed and stored: SkyWalking agents' segments, analyzed by the `sw-native` TAL rules; OpenTelemetry spans, analyzed by the `otlp` TAL rules; Zipkin spans, stored and queried as Zipkin traces and read by the GenAI and AI evaluation span listeners, not by TAL. The span listeners, the iOS and GenAI ones, also read OpenTelemetry spans beside TAL; AI evaluation samples natively stored OpenTelemetry spans through the `otlp-ai-evaluation` rule, and those stored as Zipkin spans through its listener. Which modules each needs, and where each source's pages are |
| Trace Analysis Language | The language, the same for every input: the inputs and the comparison table below; the rule file and its keys (`inputType`, `requiredModules`, `layerDefinitions`, `metricPrefix`); `filter` and `extractor`; `def`, assignment, `if`, `foreach`, `abort {}` and `log`; expressions, comparisons and typed values; the functions for every input; `metrics {}`; `source {}`, in its Source (existing system adoption) section, see [New TAL section: Source](#new-tal-section-source); [Where each construct applies](#where-each-construct-applies), the one table that says which construct belongs to which input; the extension points (`TALSourceTypeProvider`, `TALFunctionProvider` and `supportsInputType`, a source and its dispatcher); differences from LAL |
| Native: Setup (new) | How agents send segments, over gRPC and HTTP to the trace receiver or through Kafka to the [Kafka fetcher](../setup/backend/kafka-fetcher.md); the modules the native source needs, `receiver-trace`, `agent-analyzer` and `trace-analyzer`; what is stored; failures |
| Native: Bundled Analysis (new) | `nativeTalFiles`, its default order and why the order matters; each shipped rule file and what it produces, linking the feature pages below it and the virtual-service pages that stay in "Marketplace" (Virtual Database, Cache, MQ and GenAI, and AI Evaluation); `requiredModules` for `gen-ai-analyzer` and `ai-evaluation`; `traceAnalysis`; the script loggers, and how a logging configuration that names a listener class moves to them; startup and `SW_TRACE_ANALYZER=-`; adding an operator's own file |
| TAL for Native Segments (new) | The native reference documented raw `SpanObject` and `SegmentObject` inputs, segment functions, lifecycle and outputs: `inputType: sw-native`; how the rules run, with `first {}`, `segmentDef` and the build phase; the outputs in `build {}`, `metrics {}` and `source {}`, which the language page's Source section describes; `writeNetworkAlias`; the functions only this input has; failures, which are not caught. It distinguished the shipped rule's stored segment, the received `SegmentObject` reserialized unmodified, from the contents supplied by a custom `source Segment` |
| OpenTelemetry: Bundled Analysis | Stays the page of the OTLP analysis and of the `trace-analyzer` module: its Configuration section lists `nativeTalFiles` with the module's other keys and points to the native pages. The analysis documentation distinguished an empty OTLP file list from removing a module required by native callers: its "Turning the analysis off" section says that an empty `talFiles` turns the OTLP analysis off, and that removing the module stops the boot whenever a module that requires it is loaded, the native trace receiver (`receiver-trace`, loaded by default) or the Kafka fetcher; with neither, removing it still works for OTLP alone. Its shipped-files section also describes `otlp-ai-evaluation.yaml`, which loads only with `ai-evaluation` and samples natively stored GenAI spans of `OTLP_GENERAL` services |
| TAL for OpenTelemetry Spans (new) | `inputType: otlp`: `span` as the OTLP `Span` and the `meta` root, `meta.traceStorage` among its fields; rule selection by `service.layer`; the functions only this input has (`attr`, `hasAttr`, `event`, `eventAttr`, `resource`, `scopeAttr`, `callerScopedAddress`, `resolveNetworkAlias`, `findServerClusterName`); Records, the `source DatabaseSlowStatement {}` the shipped rule writes; `persist {}`, with the vote statements, and `tag`; address-named peers and pairing; retry deduplication; a failing rule caught and skipped |
| Uninstrumented Gateways, Server Clusters | Listed after the two TAL sources, which both use them: `gateways.yml` answers `isGateway`, which both inputs call; `server-clusters.yml` renames peers for both |
| Zipkin Traces | Gained the "Zipkin receiver sampling" part of `trace-sampling.md`, with its pointer to BanyanDB tail sampling, so each sampling text covers one source |

The comparison table on the language page:

| | `otlp` | `sw-native` |
|---|---|---|
| Sent by | OpenTelemetry SDKs and collectors | SkyWalking agents, as trace segments over gRPC, HTTP or Kafka |
| Rule files | `talFiles` (`SW_TRACE_TAL_FILES`) | `nativeTalFiles` (`SW_TRACE_NATIVE_TAL_FILES`) |
| Which rules see a span | the rules declared for the span's `service.layer` | every rule of every listed file whose `requiredModules` are loaded, in list order, on every span of the segment, in wire order |
| What a rule reads | `span`, the OTLP `Span`, and `meta`, what the receiver derived from it | `span`, the native `SpanObject`, `segment()`, its `SegmentObject`, and the values the runner derives from the segment |
| Runs | one per span and rule | `first {}` on a span `0`, the span's run, and `build {}` after the segment's last span; `segmentDef` lives for the segment |
| Outputs | `metrics {}` and `source {}`, each sent at its statement; `persist {}` and `tag` for the stored span | inside `build {}`: `metrics {}` and `source {}` |
| The trace itself | stored by the receiver when no span listener vetoes it and no rule's `persist {}` votes to drop it, then past the receiver's rate limit and head sampling. An `enforcer {}` skips head sampling only; with `otlpTraceStorage: zipkin` it marks the span `debug`, which the Zipkin path keeps past both its rate limit and its sample rate. Neither overrides a veto | stored as the `native-segment` rule's `Segment` source when the segment is sampled: the received segment reserialized unmodified, or a null `dataBinary` when no span `0` initialized the record |
| Network alias | learned from caller and callee pairing, `prepareNetworkAlias` and `buildNetworkAlias` | written by the callee alone, `writeNetworkAlias`, from the address the `sw8` header carries |
| Entity names | in `metrics {}`, formatted by the entity constructor, `service(...)`, `endpoint(...)` and the others, where it runs, inline in `scope` or in a `def`; for a `source {}`, by the rule, through `formatServiceName`, `formatInstanceName` and `formatEndpointName` | formatted by the rule, through `formatServiceName`, `formatInstanceName` and `formatEndpointName`, before `source {}` |
| Retries | with `dedupWindow` above `0` (off by default), a span seen again on the same node within the window, while its id is still held, is analyzed once | none |
| A rule that throws | skipped for that span, logged, and the span's other rules still run | stops the segment there; nothing is caught |
| Its page | [TAL for OpenTelemetry Spans](../concepts-and-designs/tal-otlp.md) | [TAL for Native Segments](../concepts-and-designs/tal-sw-native.md) |

Native rules read `span` and `segment()` and used the derived-value functions. The shipped `native-segment` rule
stored the received segment, reserialized unmodified, when it was sampled and a span `0` had initialized the record.
OTLP entity constructors formatted names whether used inline or assigned to a `def`; native rules called naming
functions at the listeners' original points, and the OTLP slow-statement rule calls `formatServiceName` for its
record.

The menu was updated to the source-based tree. Under "Setup", "Tracing" became the tree above. Under "Customization",
"Analysis OpenTelemetry Traces" became "Trace Analysis Language (TAL)" and points to the language page, beside OAL, MAL
and LAL; the two input pages are reached from the language page and from their sources.

What moved, and the links that followed it:

- From `tal.md` to `tal-otlp.md`: Span metadata, Layer, the OTLP functions of Global functions, the OTLP-only parts of
  Filter and Extractor, Persist, which took the vote statements of the removed Sink section, Records, in place of the
  removed Output Type section, and Address-named peers. `tal.md` kept the rest and gained the language additions of
  this SWIP: assignment, `foreach`, `log`, typed values, the functions for every input, `source {}`, the construct
  table and the extension points.
- Links to moved OTLP sections were updated to `tal-otlp.md`, including `#layer`, `#address-named-peers` and
  `#functions-of-the-otlp-input`: the links among the moved sections stay within
  `tal-otlp.md`, `#layer` and `#address-named-peers` are linked from `setup/backend/trace-analyzer.md`, `otlp-trace.md`,
  the `otlp-trace-practices` pages and `uninstrumented-gateways.md`, and `#functions-of-the-otlp-input`, for
  `findServerClusterName`, from `server-clusters.md`. Shared language links, including `#metrics`, remained on
  `tal.md`. The SWIP used those resulting targets as well. The anchor-free links in `trace-analyzer.md` and
  `otlp-trace.md` name both the language page and the OTLP input page, and the `talFiles` entry of
  `configuration-vocabulary.md` links the OTLP input page. The trace sampling page links the Zipkin part at its new
  place, `zipkin-trace.md#zipkin-receiver-sampling`; the pages that link to `trace-sampling.md` keep their links, since
  the page stays.
- The language page's Source section documents `source {}` for every input, with `GenAIEvaluationSample`, which a
  module's dispatcher takes, among the sources; its extension-point section documents input support and a source with
  its dispatcher. The OTLP page's Records section documents the `DatabaseSlowStatement` the shipped rule writes.
- The `TALFunctionProvider` Javadoc documents `supportsInputType`, which every provider declares, and the native
  input's alias.
- [Configuration Vocabulary](../setup/backend/configuration-vocabulary.md) lists `nativeTalFiles`, and its
  `talFiles` entry says it names the OTLP rule files, `otlp-general,otlp-ai-evaluation` by default.
- [AI Evaluation](../setup/backend/ai-evaluation.md) says which path samples which spans: `native-ai-evaluation` for
  agents' segments, `otlp-ai-evaluation` for natively stored OTLP spans of services that set
  `service.layer=OTLP_GENERAL`, and the module's span listener for Zipkin spans and OTLP spans stored as Zipkin
  spans.
- The changelog recorded the native TAL port, repeated-tag and gateway differences, failed-segment evaluation
  behaviour, `nativeTalFiles`, native callers' dependency on `trace-analyzer`, script logger names, language additions
  and reserved keywords, records written with `source {}` in place of the OTLP `outputType` and `sink {}`, the OTLP
  evaluation sampling moved to `otlp-ai-evaluation` with its `service.layer` requirement and `talFiles` note, and the
  tracing-documentation reorganization.
