# Tracing Overview

The OAP receives traces from three sources. Each is received, analyzed and stored its own way, and each has its own
pages under this section.

| | SkyWalking native traces | OpenTelemetry traces | Zipkin traces |
|---|---|---|---|
| Sent by | SkyWalking language agents, as trace segments | OpenTelemetry SDKs and collectors, over OTLP | Zipkin instrumentation and the service meshes that emit Zipkin spans |
| Received by | the trace receiver, `receiver-trace`, over gRPC and HTTP, or the Kafka fetcher | the OpenTelemetry receiver, `receiver-otel` | the Zipkin receiver, `receiver-zipkin` |
| Analyzed by | the `sw-native` [TAL](../../concepts-and-designs/tal.md) rules of `trace-analyzer`, listed in `nativeTalFiles` | the `otlp` TAL rules of `trace-analyzer`, listed in `talFiles`, for the services that set `service.layer` | not by TAL; the GenAI and AI evaluation span listeners read the spans |
| Modules | `receiver-trace`, `agent-analyzer` and `trace-analyzer` | `receiver-otel`; `trace-analyzer` for the analysis, `agent-analyzer` for the gateway list | `receiver-zipkin`, `query-zipkin` |
| Stored as | SkyWalking traces, one `Segment` record per sampled segment | SkyWalking traces or Zipkin traces, as `otlpTraceStorage` selects | Zipkin traces |
| Pages | [Setup](native-trace.md), [Bundled analysis](native-trace-analysis.md), [TAL for native segments](../../concepts-and-designs/tal-sw-native.md) | [Setup](otlp-trace.md), [Bundled analysis](trace-analyzer.md), [TAL for OpenTelemetry spans](../../concepts-and-designs/tal-otlp.md), best practices | [Zipkin traces](zipkin-trace.md) |

Beside TAL, the span listeners read OpenTelemetry spans too: the iOS ones, for [iOS monitoring](backend-ios-monitoring.md),
and the GenAI ones. [AI evaluation](ai-evaluation.md) samples natively stored OpenTelemetry spans through the
`otlp-ai-evaluation` TAL rule, and those stored as Zipkin spans, like Zipkin's own, through its span listener.

Two configuration files serve more than one source:

- [Uninstrumented gateways](uninstrumented-gateways.md), `gateways.yml`, lists the gateways and proxies that do not
  trace; both TAL inputs ask it through `isGateway`, so a call through a listed gateway is drawn to the gateway.
- [Server clusters](server-clusters.md), `server-clusters.yml`, renames the addresses of an uninstrumented server, a
  broker, a database or a cache to the cluster they belong to, for both TAL inputs.

Sampling and storage limits are per source: [trace sampling](trace-sampling.md) for native traces, the receiver's
sample rate and rate limit for [OpenTelemetry](otlp-trace.md) and for [Zipkin](zipkin-trace.md#zipkin-receiver-sampling),
and BanyanDB's [trace tail sampling](../../banyandb/tail-sampling.md) after storage.
