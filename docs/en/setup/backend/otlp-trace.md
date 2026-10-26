# OpenTelemetry Trace Format

SkyWalking receives traces in OTLP format through the `otlp-traces` handler of the OpenTelemetry receiver.
The handler stores them in one of two ways, selected by `receiver-otel.default.otlpTraceStorage`:

| `otlpTraceStorage` | What is stored                                                                                                   | Query API                                                                                  |
|--------------------|------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| `otlp` (default)   | The span as it arrived, with its resource and instrumentation scope, in the `otlp_span` record; only the resource's `service.layer` is set to the registered layer, `UNDEFINED` otherwise. | The TraceQL `/otlp` datasource, served to Grafana Tempo as native OTLP.                     |
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
The resource attributes are stored as sent, `service.name` and the grouping attribute among them, so
`{resource.service.namespace="opentelemetry-demo"}` still finds the group's traces; only `service.layer` is stored as
the registered layer it names, `UNDEFINED` otherwise, as the [service catalog](../../api/traceql-service.md) files it. A query names each span's
service by the grouped name: a search lists it as the span's `service.name`, and a trace fetched by id carries it as
its resource's `service.name`, the other resource attributes as sent. In the `zipkin` storage mode the stored service
is lower case, since Zipkin lowercases every service name, so a group value with capitals, `service.namespace=Shop`,
gives `Shop::cart` in the analysis and the logs but `shop::cart` in the stored span and the query; Kubernetes
namespace names are always lower case.

A caller that dials the host `cart` still reaches the grouped `cart`: the trace analyzer resolves a host the way
Kubernetes DNS resolves a Service name, against the services the OAP node has seen spans from, in this order:

1. A host qualified with a group names that group's service: `cart.outlet`, `cart.outlet.svc` and
   `cart.outlet.svc.cluster.local` are `outlet::cart`. A qualified host is never resolved in the caller's group by its
   first label, since it says which group it means; only a service of the caller's group named exactly the whole host,
   as in step 2, matches it there.
2. A host names the service of exactly that name in the caller's own group: `cart` dialed by `shop::frontend` is
   `shop::cart`, and dialed by `outlet::frontend` is `outlet::cart`, even when both are deployed. A `service.name`
   that holds a dot is matched whole, `checkout.api` is `shop::checkout.api`, never by its first label.
3. A host, or its first DNS label, that is an ungrouped service's whole name is that service.
4. The network address alias row of the address, written by the callee that was dialed at it.
5. The service an earlier pair of a call's CLIENT and SERVER spans named for the address.
6. The one grouped service, in any group, whose short name is the whole host. This is ranked last because an OAP node
   that has seen only `outlet::cart` would otherwise name it for a `shop` caller; a qualified host such as
   `cart.outlet` is never guessed by its first label.

The first two steps match Kubernetes only when the group is the namespace, so list `k8s.namespace.name` first, or
alone, on Kubernetes; with `service.namespace` first, a qualified host such as `cart.otel-demo` names nothing, while a
bare host still resolves in the caller's group as long as caller and callee carry the same value. Both steps also need
the Kubernetes Service named as the SDK's `service.name`. Calls these steps cannot name land on the address row, which
is keyed by the address alone, as on the agent path, and the last callee to register it wins. So calls to a short name
two groups share, `shop::cart` and `outlet::cart`, may land on either group's service when they come from an ungrouped
caller, such as a browser app without the grouping attribute, or when the Kubernetes Service name differs from
`service.name`. An OAP node also records, when it starts, the OTLP services storage already holds, so a restart does
not forget a callee that emits only now and then, such as a feature flag service; a stored service that sends no span
to the node is forgotten again a day after it was read. A client's peer is expected to name one target. When services
of two groups are reached at the same address, such as a bare `cart:7070` dialed in both namespaces, and the node
cannot name the callee in the caller's own group, the call is named by the address row when there is one, which may
name either group's service, as on the agent path, and otherwise by the pairing or the step 6 guess. A node that never
receives the callee's spans, for example one the collectors are not connected to over their long-lived gRPC
connections, stays in that state once the day the startup read covers has passed. To pin which TAL rules analyze such
spans, set their resource's `service.layer` to a registered layer name and declare TAL rules for that layer. Only
those rules run; the shipped `layer: auto` rules do not. An unknown layer name is logged and its spans are not
analyzed. See [Layer](../../concepts-and-designs/tal.md#layer). The group has no cluster part either: the namespace `shop` of two Kubernetes
clusters groups both clusters' `cart` as one `shop::cart`.

The guard that keeps a proxy's address its own works on short names too: an address whose first DNS label, or whose
whole host, is the short name of any group's service is never registered for a callee of another short name, so a
proxy that passes the `Host` header through keeps its node whichever group it is in. The callee's own short name is
exempt for the first label only: `edge.proxy:8080` stays `infra::edge.proxy`'s even against a callee named `edge`. See
[Address-named peers](../../concepts-and-designs/tal.md#address-named-peers).

Pick an attribute that holds one low-cardinality value per service, such as `service.namespace`,
`deployment.environment.name` or `k8s.namespace.name`; a value that changes per pod or per request splits a service
into many, and a service whose spans carry the attribute only some of the time is split into its grouped and its
plain name. A `service.name` the SDK grouped already, `shop::cart`, is kept, and a value that holds `::` itself groups
nothing. Empty, the default, groups nothing.

### What is stored

Every span is stored as a single-span `ResourceSpans` message, so the query returns the exact resource,
instrumentation scope, attributes, events, links and status the SDK sent, except that each resource's `service.name`
is the service the span was stored under: grouped, and through the naming rules below. A few columns are extracted for
search: service name and instance (`service.instance.id`), instrumentation scope name, span name,
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
rule file analyzes the spans whose resource carries `service.layer=OTLP_GENERAL`, and registers their services under
that layer with their metrics, topology and slow statements; every other span is only stored. See [OpenTelemetry
Trace Analysis](trace-analyzer.md#enabling-the-analysis-for-an-application) for how an application enables it, the
settings, and what it produces.
