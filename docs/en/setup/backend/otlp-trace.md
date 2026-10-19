# OpenTelemetry Trace Format

SkyWalking receives traces in OTLP format through the `otlp-traces` handler of the OpenTelemetry receiver.
The handler stores them in one of two ways, selected by `receiver-otel.default.otlpTraceStorage`:

| `otlpTraceStorage` | What is stored                                                                                                   | Query API                                                                                  |
|--------------------|------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| `otlp` (default)   | The span exactly as it arrived, with its resource and instrumentation scope, in the `otlp_span` record.          | The TraceQL `/otlp` datasource, served to Grafana Tempo as native OTLP.                     |
| `zipkin`           | Spans converted to Zipkin v2 and handed to the Zipkin receiver, the only mode of earlier releases. Follows the [Zipkin Exporter](https://opentelemetry.io/docs/specs/otel/trace/sdk_exporters/zipkin/#summary) conversion rules. | [Zipkin query API](./zipkin-trace.md#zipkin-query), Lens UI, and the TraceQL `/zipkin` datasource. |

Span listeners such as the GenAI analyzer and AI evaluation run in both modes. In `otlp` mode they read the
OTLP span directly instead of its Zipkin conversion.

## Supported Protocols

Both **OTLP/gRPC** and **OTLP/HTTP** are supported for traces, logs, and metrics:

| Signal  | OTLP/gRPC (port 11800)       | OTLP/HTTP (port 12800)  |
|---------|------------------------------|-------------------------|
| Traces  | gRPC `TraceService/Export`    | `POST /v1/traces`       |
| Logs    | gRPC `LogsService/Export`     | `POST /v1/logs`         |
| Metrics | gRPC `MetricsService/Export`  | `POST /v1/metrics`      |

OTLP/HTTP supports both `application/x-protobuf` and `application/json` content types.

## Zipkin conversion mode

1. Make sure to enable the **otlp-traces** handler in the OTLP receiver of `application.yml`.
```yaml
receiver-otel:
  selector: default
  default:
    enabledHandlers: otlp-traces
    otlpTraceStorage: zipkin
```

2. Enable the Zipkin receiver and the Zipkin query in `application.yml`, they hold the storage and the query of the
   converted spans. The OAP refuses to start when `otlp-traces` runs in this mode with `receiver-zipkin` disabled.

3. Read [deploy Lens UI documentation](./zipkin-trace.md#lens-ui) to query the traces, or enable the
   TraceQL `/zipkin` datasource for Grafana, see [TraceQL Service](../../api/traceql-service.md).

The conversion is lossy, because the Zipkin v2 model is smaller than OTLP. What the Zipkin copy cannot keep:

- **Span kind.** Zipkin has `CLIENT`, `SERVER`, `PRODUCER` and `CONSUMER` only. An OTLP `SPAN_KIND_INTERNAL` span is
  stored without a kind and reads back as `SPAN_KIND_UNSPECIFIED`; the receiver adds a `span.kind=internal` tag so the
  information is searchable as an ordinary tag, but the TraceQL `kind` intrinsic cannot match it.
- **Attribute types.** Every attribute becomes a string tag, so an integer such as `http.response.status_code` reads
  back as `"200"`.
- **Events and links.** An event becomes a Zipkin annotation; when it carries attributes they are flattened into the
  annotation value as `name|{attributes}|droppedCount`. A link becomes an `otlp.link.<n>` tag holding the trace and
  span ids in decimal, the trace state and the flattened attributes.
- **Resource and instrumentation scope.** Resource attributes are merged into the span tags, and the scope name and
  version are kept as `otel.library.name` and `otel.library.version` tags.

Choose the [native OTLP mode](#native-otlp-mode) when any of these matters.

## Native OTLP mode

```yaml
receiver-otel:
  selector: default
  default:
    enabledHandlers: otlp-traces
    otlpTraceStorage: otlp
    otlpTraceSearchableTags: resource.deployment.environment.name,resource.deployment.environment,resource.service.namespace,resource.service.version,resource.k8s.namespace.name,resource.k8s.cluster.name,span.http.request.method,span.http.response.status_code,span.url.path,span.rpc.method,span.rpc.grpc.status_code,span.db.system.name,span.db.namespace,span.messaging.system,span.messaging.destination.name,span.peer.service
    otlpTraceSampleRate: 10000
    otlpTraceMaxSpansPerSecond: 0

traceQL:
  selector: default
  default:
    enableDatasourceOTLP: true
    restContextPathOTLP: /otlp
    otlpTracesListResultTags: http.request.method,http.response.status_code
```

The Zipkin receiver and query modules are not needed in this mode.

| Setting                      | Environment variable                 | Meaning                                                                                                                                                                              |
|------------------------------|--------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `otlpTraceStorage`           | `SW_OTEL_TRACE_STORAGE`              | `otlp`, the default, stores the native span. `zipkin` converts to Zipkin v2 as earlier releases did. Any other value fails the boot.                                               |
| `otlpTraceSearchableTags`    | `SW_OTEL_TRACE_SEARCHABLE_TAG_KEYS`  | Attribute keys offered by tag autocomplete (`/api/v2/search/tags`), each written with its TraceQL scope, `resource.<key>` or `span.<key>`, and listed under that scope; an entry without a scope fails the boot. List low-cardinality attributes only: every distinct value is stored once per day. Every resource and span attribute is indexed for equality search regardless of this list.                        |
| `otlpTraceSampleRate`        | `SW_OTEL_TRACE_SAMPLE_RATE`          | Head sampling by trace id, precision 1/10000. `10000` keeps every trace.                                                                                                              |
| `otlpTraceMaxSpansPerSecond` | `SW_OTEL_TRACE_MAX_SPANS_PER_SECOND` | Spans per second accepted before the receiver drops the rest. `0` means no limit. Dropped spans are counted in the `otel_spans_dropped` self-observability metric.                    |

### Service name

The service name is taken from the resource attributes in this order: `service.name`, `faas.name`,
`k8s.deployment.name`, `process.executable.name`. The attributes themselves are not rewritten. A span whose resource
carries none of these keys is rejected and reported in the OTLP `partial_success` response, it is never stored as an
anonymous span.

### Service group

`otlpServiceGroupKeys` (`SW_OTEL_RECEIVER_OTLP_SERVICE_GROUP`) groups OTLP services in OAP's `group::name` convention,
the one `agent::` names follow, from a resource attribute. It lists attribute keys, comma separated, in order of
preference; the first key a resource carries a value for is the group, and the keys after it are not read:

```yaml
receiver-otel:
  default:
    otlpServiceGroupKeys: service.namespace,k8s.namespace.name
```

| Resource attributes | Service in OAP |
|---|---|
| `service.name=cart`, `service.namespace=opentelemetry-demo`, `k8s.namespace.name=otel-demo` | `opentelemetry-demo::cart` |
| `service.name=billing`, `k8s.namespace.name=finance` | `finance::billing` |
| `service.name=legacy` | `legacy` |

The grouped name is the service everywhere OAP names one: the [trace analysis](trace-analyzer.md), its metrics,
instances, endpoints and topology; the stored span, in both storage modes, so a TraceQL search names the service as
the layer does, `{resource.service.name="opentelemetry-demo::cart"}`; and the OTLP logs, which are filed under it.
The resource attributes stay as sent, `service.name` and the grouping attribute among them, so
`{resource.service.namespace="opentelemetry-demo"}` still finds the group's traces. A caller that dials the host
`cart` still reaches `opentelemetry-demo::cart`, since the trace analyzer matches a host against a grouped service's
short name; a short name two groups share, `shop::cart` and `outlet::cart`, matches neither, and those calls are
named through the alias rows and the pairing instead.

Pick an attribute that holds one low-cardinality value per service, such as `service.namespace`,
`deployment.environment.name` or `k8s.namespace.name`; a value that changes per pod or per request splits a service
into many, and a service whose spans carry the attribute only some of the time is split into its grouped and its
plain name. A `service.name` the SDK grouped already, `shop::cart`, is kept, and a value that holds `::` itself groups
nothing. Empty, the default, groups nothing.

### What is stored

Every span is stored as a single-span `ResourceSpans` message, so the query returns the exact resource,
instrumentation scope, attributes, events, links and status the SDK sent. A few columns are extracted
for search: service name and instance (`service.instance.id`), instrumentation scope name, span name,
kind, status code, `peer.service`, start time and duration. The service, span and peer names go through the core
module's naming rules first, the length limits and the endpoint grouping rules SkyWalking applies to its own
services and endpoints, so a TraceQL `name` matches the grouped name while the stored span keeps the original. Every resource attribute is indexed as a
`resource.<key>=<value>` tag and every span attribute as a `span.<key>=<value>` tag, so a scoped TraceQL attribute
matches its own scope only and an unscoped `.<key>` matches either.

The BanyanDB storage keeps the spans in the `otlpTrace` group, see [BanyanDB TTL](../../banyandb/ttl.md)
for its default retention and [BanyanDB storage](./storages/banyandb.md) for the group settings.
The JDBC storages add an `otlp_span_tag` table for the tag index.

### Query

Point a Grafana Tempo datasource at `http://<oap-host>:3200/otlp`, see
[Use Grafana As The UI](./ui-grafana.md#tempo-data-source). The supported TraceQL subset and the HTTP API are
described in [TraceQL Service](../../api/traceql-service.md#otlp-native-trace).

The status module exposes the same queries with the execution trace for debugging under
`/debugging/query/otlp/api/search` and `/debugging/query/otlp/api/v2/trace`, see
[Status APIs](./admin-api/status.md).

## Analysis

Every accepted span also goes through the [Trace Analysis Language](../../concepts-and-designs/tal.md) rules of the
`trace-analyzer` module, in both storage modes and before the head sampling and the rate limit above. The shipped
rule file registers the services under the `OTLP_SERVICE` layer with their metrics, topology and slow statements;
see [OpenTelemetry Trace Analysis](trace-analyzer.md) for the settings and for what it produces.
