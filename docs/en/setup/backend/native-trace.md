# SkyWalking Native Traces

SkyWalking's language agents report traces as segments: a segment is the part of a trace one process ran, its spans
in the order the agent recorded them, with references to the segments that called it. The OAP receives the segments,
analyzes them with the [native TAL rules](native-trace-analysis.md), and stores the sampled ones as traces.

## Receiving segments

Agents send segments to the OAP over one of three transports, all in the [trace data protocol
v3](../../api/trace-data-protocol-v3.md):

- **gRPC**, `TraceSegmentReportService`, `collect` (a stream) or `collectInSync`, on the gRPC port of the core or of
  the [sharing server](backend-expose.md), `11800` by default;
- **HTTP**, `POST /v3/segment` (one segment) or `POST /v3/segments` (a list), on the HTTP port, `12800` by default;
- **Kafka**, the `skywalking-segments` topic, read by the [Kafka fetcher](kafka-fetcher.md), which is off by default.

The gRPC and HTTP handlers belong to the trace receiver, `receiver-trace`, which is loaded by default.

## The modules it needs

```yaml
receiver-trace:
  selector: ${SW_RECEIVER_TRACE:default}
  default:

agent-analyzer:
  selector: ${SW_AGENT_ANALYZER:default}
  default:
    # sampling, slow statement thresholds and the other settings the native analysis reads

trace-analyzer:
  selector: ${SW_TRACE_ANALYZER:default}
  default:
    nativeTalFiles: ${SW_TRACE_NATIVE_TAL_FILES:native-rpc,native-endpoint-cross-thread,native-network-alias,native-segment,native-virtual-service,native-virtual-genai,native-ai-evaluation}
```

- `receiver-trace` and the Kafka fetcher require `agent-analyzer` and `trace-analyzer`. Removing either one, with
  `SW_AGENT_ANALYZER=-` or `SW_TRACE_ANALYZER=-`, while the trace receiver or the Kafka fetcher is loaded fails the
  boot, naming the missing module, rather than receiving segments and analyzing none.
- `agent-analyzer` holds the settings the analysis reads: [trace sampling](trace-sampling.md), the [slow database
  statement](slow-db-statement.md) and [slow cache command](slow-cache-command.md) thresholds, the [uninstrumented
  gateways](uninstrumented-gateways.md), `segmentStatusAnalysisStrategy`, `forceSampleErrorSegment`,
  `noUpstreamRealAddressAgents` and `traceAnalysis`.
- `trace-analyzer` runs the rules: `nativeTalFiles` lists them, in the order they run, see [Native Trace
  Analysis](native-trace-analysis.md).
- `gen-ai-analyzer` and `ai-evaluation` add the GenAI analysis and the AI evaluation samples when they are loaded.

The trace receiver starts after `trace-analyzer` has compiled the rules, so no segment arrives before the analysis is
ready, and neither does one the Kafka fetcher reads.

## What is stored

For each segment, the shipped rules send:

- the services, instances and endpoints the segment names, their traffic, and the calls between them, from which the
  `.oal` files compute the service, instance and endpoint metrics and the topology;
- the network address aliases its entry spans' references name, so a caller's address-named peer folds onto this
  service;
- the database, cache, message queue and GenAI nodes its exit spans reach, with their slow statements and commands;
- the `Segment` record, the trace itself, when the segment is sampled, and the tags `core.searchableTracesTags` lists
  for trace search;
- an AI evaluation sample for a GenAI span, when `ai-evaluation` is loaded and active.

The `Segment` record stores the segment as received. Sampled segments are queried as traces in the
UI and through the [query APIs](../../api/query-protocol.md), and the [trace tail
sampling](../../banyandb/tail-sampling.md) of BanyanDB can drop traces after storage.

## Failures

A segment the rules fail on stops at the failing point, and what was sent before stays sent; see
[Failures](../../concepts-and-designs/tal-sw-native.md#failures). The gRPC handler counts and logs it, the HTTP
handler counts it and rethrows it, and the Kafka fetcher's handler lets it leave the handler.
