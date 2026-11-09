# Native Trace Analysis

The `trace-analyzer` module analyzes the segments SkyWalking agents send with [TAL rules for native
segments](../../concepts-and-designs/tal-sw-native.md). The shipped rules build the services, instances, endpoints,
topology, virtual database, cache, MQ and GenAI nodes, slow statements, network address aliases and stored traces of
the agent path, the sources `core.oal` and the other `.oal` files compute every agent metric from. See [SkyWalking
Native Traces](native-trace.md) for how the segments arrive and the modules they need.

## Configuration

```yaml
trace-analyzer:
  selector: ${SW_TRACE_ANALYZER:default}
  default:
    # TAL rule files under `tal/` for spans that SkyWalking agents send, without extension, comma separated, in the
    # order their rules run. The default reproduces the agent path.
    nativeTalFiles: ${SW_TRACE_NATIVE_TAL_FILES:native-rpc,native-endpoint-cross-thread,native-network-alias,native-segment,native-virtual-service,native-virtual-genai,native-ai-evaluation}
```

The rule files live under `config/tal/` of the distribution, beside the OpenTelemetry ones, and are read when the OAP
starts. A listed file that does not exist, a rule that does not compile, or two native rules of one name stop the OAP
from starting, with a message that names the missing file, the rule that failed to compile, or the repeated rule
name. `nativeTalFiles` is separate from `talFiles`, so an
operator who lists their own OpenTelemetry files keeps the native analysis.

The other settings the rules read belong to `agent-analyzer`: [trace sampling](trace-sampling.md), the [slow
database statement](slow-db-statement.md) and [slow cache command](slow-cache-command.md) thresholds,
`maxSlowSQLLength`, `segmentStatusAnalysisStrategy`, `forceSampleErrorSegment`, `noUpstreamRealAddressAgents` and
`traceAnalysis`; the core's `searchableTracesTags`, [endpoint grouping](endpoint-grouping-rules.md) and name length
limits; and the [uninstrumented gateways](uninstrumented-gateways.md) and [server clusters](server-clusters.md) files.

## The shipped rule files

| File | Rules | What it sends |
|---|---|---|
| `native-rpc` | `native-rpc-entry`, `native-rpc-exit`, `native-rpc-logic-endpoint` | the services, instances and endpoints an entry span names, and the calls into them from its references or from `User`; the calls an exit span makes to its peer, a gateway, an alias or an address; the logic endpoints a local span's `x-le` tag declares |
| `native-endpoint-cross-thread` | `native-endpoint-cross-thread` | the endpoint dependency from each reference's endpoint to an exit or local span with references, such as the first span of a task another thread started, and the span's endpoint |
| `native-network-alias` | `native-network-alias` | the network address alias an entry span's cross-process reference names: the address the caller dialed, for this service and instance |
| `native-segment` | `native-segment` | the `Segment` record, the trace itself, for a sampled segment, and the tags it stores for trace search |
| `native-virtual-service` | `native-virtual-cache`, `native-virtual-database`, `native-virtual-mq` | the [virtual cache](../service-agent/virtual-cache.md), [database](../service-agent/virtual-database.md) and [MQ](../service-agent/virtual-mq.md) nodes exit spans reach, their accesses, the slow statements and commands above the thresholds, and the [message queue](mq.md) endpoints and consuming latency |
| `native-virtual-genai` | `native-virtual-genai` | the [virtual GenAI](../service-agent/virtual-genai.md) provider and model nodes, with tokens, time to first token and estimated cost |
| `native-ai-evaluation` | `native-ai-evaluation` | an [AI evaluation](ai-evaluation.md) sample for a sampled GenAI span |

`native-virtual-genai` lists `gen-ai-analyzer` under `requiredModules`, and `native-ai-evaluation` lists
`ai-evaluation`: a file whose module is not loaded is not compiled, and its rules do not run. `ai-evaluation` is off by
default, and when it is loaded without a complete judge configuration it samples nothing.

`traceAnalysis: false` in `agent-analyzer` turns off `native-rpc`, `native-endpoint-cross-thread` and
`native-network-alias`, whose rules abort on every span; the other files ignore it, so the traces are still stored and
the virtual nodes still built.

## The order of the files

The order of `nativeTalFiles` is part of the analysis. No rule reads another rule's output: the one cross-file write,
an alias row, reaches `native-rpc-exit` only through the alias cache, once the cache reloads. The order still decides
what reaches an order-sensitive merge first, what a failing segment has already sent, and the order of the stateful
naming calls:

| Order | Why |
|---|---|
| `native-rpc` before `native-endpoint-cross-thread` | Both send `EndpointRelation` and `Endpoint` for the same endpoints, and the first relation's component id wins for the minute. |
| `native-rpc` and `native-endpoint-cross-thread` before `native-segment`, before the virtual-service files | The build blocks run rule by rule, and a failure while one source is dispatched stops every later block, so this order decides which sources a failing segment has already sent. |
| `native-network-alias` at its place | It sends during the span loop, so a rule earlier in the order that throws on a span decides whether that span's alias row is sent. |
| `native-ai-evaluation` last | Its blocks run after every other file's, so a failure on any span or in any earlier block means the segment takes no sample. |
| `native-segment` before `native-virtual-service` | Both name endpoints during the span loop, the segment's own service and the broker's, through the stateful endpoint grouping. |

Within `native-rpc` the rules run entry, exit, logic endpoint; within `native-virtual-service` cache, database, MQ.

## Adding a rule file

Put a file of your own under `config/tal/`, declare `inputType: sw-native` on each rule, and append its name to
`nativeTalFiles`. Appended at the end, it keeps the shipped rules in their relative order. Its rules still run on every
span of every segment, so one of them that throws stops the segment before the build phase, and one that formats
endpoint names changes the endpoint grouping's state that later spans and the shipped build blocks read. Moving or
removing a shipped file changes what the agent path produces: a metric whose source no rule sends any more stays
empty.

## Logging

A rule's `log` statements and the functions that log for it write under the script's logger,
`org.apache.skywalking.trace.analysis.script.<file>`; the segment-level lines, the unknown span type error and the
dropped long tags, under the runner's,
`org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace.SwNativeRuleRunner`. The naming, the endpoint grouping,
the gateway lookup and the other Java the rules call still log under their own classes. A logging configuration that
named one of the Java listeners that analyzed segments before, in
`org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener`, or `TraceAnalyzer` one package up, now names
the logger that writes its lines:

| A logger that named | Now names |
|---|---|
| `RPCAnalysisListener` | `org.apache.skywalking.trace.analysis.script.native-rpc` |
| `EndpointDepFromCrossThreadAnalysisListener` | `org.apache.skywalking.trace.analysis.script.native-endpoint-cross-thread` |
| `NetworkAddressAliasMappingListener` | `org.apache.skywalking.trace.analysis.script.native-network-alias` |
| `SegmentAnalysisListener` | `org.apache.skywalking.trace.analysis.script.native-segment`, and the runner for the dropped long tags |
| `TraceAnalyzer` | the runner |
| `VirtualServiceAnalysisListener`, `vservice.VirtualCacheProcessor`, `vservice.VirtualDatabaseProcessor`, `vservice.VirtualMQProcessor` | `org.apache.skywalking.trace.analysis.script.native-virtual-service` |
| `vservice.VirtualGenAIProcessor`, and `org.apache.skywalking.oap.analyzer.genai.service.GenAIMeterAnalyzer` for native spans | `org.apache.skywalking.trace.analysis.script.native-virtual-genai` |
| `GenAIEvaluationAnalysisListener` | `org.apache.skywalking.trace.analysis.script.native-ai-evaluation` |

`GenAIMeterAnalyzer` keeps logging under its own name for OpenTelemetry and Zipkin spans.

## Startup

`trace-analyzer` requires `agent-analyzer` and every module a listed file names under `requiredModules`, when they are
loaded, so they start before it. It compiles the native rules in its `start()`, and the trace receiver and the Kafka
fetcher, which require it, start after. Removing the module with `SW_TRACE_ANALYZER=-` while the trace receiver
(loaded by default) or the Kafka fetcher is loaded fails the boot, naming the missing module; with neither loaded,
removing it still works for OpenTelemetry alone, see [Turning the analysis off](trace-analyzer.md#turning-the-analysis-off).
