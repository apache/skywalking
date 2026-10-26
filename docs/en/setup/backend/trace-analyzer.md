# OpenTelemetry Trace Analysis

The `trace-analyzer` module runs the [Trace Analysis Language (TAL)](../../concepts-and-designs/tal.md) rules over
every span the [OpenTelemetry trace receiver](otlp-trace.md) accepts, in both of its storage modes, and before the
receiver's head sampling and rate limit, so the metrics count what the SDKs sent rather than what the OAP chose to
store. The shipped rules analyze the applications that ask for it, by setting their resource's `service.layer` to
`OTLP_GENERAL`; their services, metrics, topology and slow statements then appear. Every other span, one with no
`service.layer`, a service mesh sidecar's, or any other source's, is stored and can be queried, but is not analyzed.

## Enabling the analysis for an application

Set the layer through the SDK's resource attributes, alongside any others the application already sets:

```shell
OTEL_RESOURCE_ATTRIBUTES=service.layer=OTLP_GENERAL
```

An application without it still has its traces stored and searchable, see [OpenTelemetry Trace
Format](otlp-trace.md), and a call to it from an analyzed application is drawn to the address the caller dialed.
The OAP logs one INFO line per such service, `Spans of service '…' carry no service.layer, so they are stored but not
analyzed`, so empty dashboards point at the cause; a service whose `service.layer` is a registered layer with no
rule, such as `OTLP_MESH`, has opted out and is not logged.

## Configuration

```yaml
trace-analyzer:
  selector: ${SW_TRACE_ANALYZER:default}
  default:
    # TAL rule files under `tal/`, without extension, comma separated. Empty disables span analysis while keeping
    # the module loaded.
    talFiles: ${SW_TRACE_TAL_FILES:otlp-general}
    # Spans seen within this window (seconds) are analyzed once, so an exporter that retries a batch after a
    # transport timeout does not count a request twice. 0, the default, keeps no set at all and treats every
    # arrival as new. Set it longer than the exporter's retry budget; the OTLP SDKs give up after about 30 s. The
    # set is per OAP node: a retry that reaches another node is counted again.
    dedupWindow: ${SW_TRACE_TAL_DEDUP_WINDOW:0}
    # The most (traceId, spanId) pairs the set holds on this node while dedupWindow is above 0; ignored at 0.
    # Only a span some rule runs on takes an entry, not one on a layer no rule is declared for. Size it as such
    # spans per second on this node times dedupWindow, with headroom: once full, the oldest entries drop before
    # they expire and a late retry of one of them is counted. About 150 bytes per entry.
    dedupCapacity: ${SW_TRACE_TAL_DEDUP_CAPACITY:1000000}
    # In a cluster, the percentage of traces whose client and server spans are routed to the trace's owner node to
    # learn a network address alias from the pair; both spans of a call share the trace id, so a pair is never
    # split. An address only has to be learned once, after which every node resolves it from its cache, so a
    # sample is enough: at 25 a new address is learned after about four calls. 100 routes every trace, at one
    # small message per SERVER span called from another process, plus one per CLIENT span whose peer this node
    # cannot name yet. A single node pairs every call and ignores this.
    pairingSamplePercent: ${SW_TRACE_TAL_PAIRING_SAMPLE_PERCENT:25}
```

The rule files live under `config/tal/` of the distribution. To change what is measured, edit a file there or add
one and list it in `talFiles`, then restart the OAP: the files are read at start. A rule file that does not compile,
or that names a layer that is not registered, stops the OAP from starting with a message naming the rule.

## What the shipped rule file produces

`tal/otlp-general.yaml` holds two rules, both declared for the `OTLP_GENERAL` layer. `otlp-general` claims every
span whose resource carries `service.layer=OTLP_GENERAL`, except the GenAI spans the GenAI analyzer already handles,
and `otlp-slow-sql` writes a slow statement record for a database client span above 200 ms. Both read the OpenTelemetry semantic conventions with the current attribute
names first and the deprecated spellings older SDKs still emit as fallbacks.

### Layers

| Layer | Holds | Registered by |
|---|---|---|
| `OTLP_GENERAL` | a service whose spans arrived over OTLP with `service.layer=OTLP_GENERAL` | the `service.name` of a span the rule claims |
| `OTLP_VIRTUAL_DATABASE` | a database a client span names through `db.system.name` or `db.system` | the client's span |
| `OTLP_VIRTUAL_CACHE` | a cache, the same way, for `redis`, `memcached` and the other cache systems | the client's span |
| `OTLP_VIRTUAL_MQ` | a broker a producer or consumer span names through `messaging.system` | the producer's or consumer's span |

The layers are separate from the agent path's `GENERAL` and `VIRTUAL_*` layers because their metrics come from a
different engine under different names. A service id is its name plus the `normal` flag, not the layer, so a
service that reports both agent segments and OTLP spans is one entity listed under both layers, and a database
reached from both paths is one node.

### Service mesh sidecars

A sidecar's spans repeat the calls its application's own spans already describe, and name the workload the mesh's
way, Istio's Envoy as `<service>.<namespace>`. They carry no `OTLP_GENERAL`, so they are stored, and searchable with
TraceQL, but not analyzed: they register no service, no metrics and no topology. A span no rule claims also does not
count as a service when a peer's host is resolved, so the sidecar's name `cart.shop` does not take the calls the
application dials at `cart.shop:7070`, and it sends no pairing fact, see [Layer](../../concepts-and-designs/tal.md#layer).

To label them, set the sidecars' resource `service.layer` to `OTLP_MESH`, a layer no shipped rule is declared for, and
search them with `{resource.service.layer="OTLP_MESH"}`. On Istio, the OpenTelemetry tracing provider's environment
resource detector reads `OTEL_RESOURCE_ATTRIBUTES` from the proxy, which `proxyMetadata` sets:

```yaml
meshConfig:
  enableTracing: true
  defaultConfig:
    proxyMetadata:
      OTEL_RESOURCE_ATTRIBUTES: service.layer=OTLP_MESH
  extensionProviders:
    - name: skywalking
      opentelemetry:
        service: oap.skywalking.svc.cluster.local
        port: 11800
        resourceDetectors:
          environment: {}
```

The provider traces nothing until a `Telemetry` resource selects it, here for the whole mesh from Istio's root
namespace:

```yaml
apiVersion: telemetry.istio.io/v1
kind: Telemetry
metadata:
  name: mesh-tracing
  namespace: istio-system
spec:
  tracing:
    - providers:
        - name: skywalking
      randomSamplingPercentage: 100
```

### Metrics

Every metric carries the file's `otlp_` prefix. On the service and its edges:

| Entity | Metrics |
|---|---|
| service | `otlp_service_cpm`, `otlp_service_resp_time`, `otlp_service_sla`, `otlp_service_apdex`, `otlp_service_percentile` |
| instance, when the resource carries `service.instance.id` | `otlp_service_instance_cpm`, `otlp_service_instance_resp_time`, `otlp_service_instance_sla` |
| endpoint, named `<method> <route>` for HTTP spans and by the span name otherwise | `otlp_endpoint_cpm`, `otlp_endpoint_resp_time`, `otlp_endpoint_sla`, `otlp_endpoint_percentile` |
| the call a CLIENT or PRODUCER span makes | `otlp_service_relation_client_cpm`, `otlp_service_relation_client_resp_time`, `otlp_service_relation_client_call_sla` |
| the call a SERVER or CONSUMER span receives: from the `User` node for a root span, from the broker for a consumer, and from the gateway for a span dialed at an [uninstrumented gateway](uninstrumented-gateways.md) | `otlp_service_relation_server_cpm` |

On the conjectured peers, from the client's span: `otlp_database_access_cpm`, `_resp_time` and `_sla`, and the
`otlp_cache_access_*` and `otlp_mq_access_*` counterparts, with `otlp_mq_destination_*` per queue or topic. The
`core.oal` metrics are not produced for these services; no dashboard template reads the `otlp_*` metrics yet, so
query them through [MQE](../../api/metrics-query-expression.md) and reference them in alarm rules.

#### Porting from the agent metrics

Where an agent metric has a TAL counterpart, the TAL metric is the agent metric's name with the `otlp_` prefix and
holds the same number in the same unit, so an alarm rule or a dashboard widget ports by renaming the metric, with
the two exceptions listed after the table:

| `core.oal` metric | TAL metric |
|---|---|
| `service_cpm`, `service_resp_time`, `service_sla`, `service_apdex`, `service_percentile` | `otlp_service_*` of the same names |
| `service_instance_cpm`, `service_instance_resp_time`, `service_instance_sla` | `otlp_service_instance_*` of the same names |
| `endpoint_cpm`, `endpoint_resp_time`, `endpoint_sla`, `endpoint_percentile` | `otlp_endpoint_*` of the same names |
| `service_relation_client_cpm`, `service_relation_client_resp_time`, `service_relation_client_call_sla` | `otlp_service_relation_client_*` of the same names |
| `service_relation_server_cpm` | `otlp_service_relation_server_cpm`, for the `User` edge, the broker-to-consumer edge and the gateway-to-callee edge |
| `database_access_cpm`, `database_access_resp_time`, `database_access_sla` | `otlp_database_access_*` of the same names |
| `cache_access_cpm`, `cache_access_resp_time`, `cache_access_sla` | `otlp_cache_access_*` of the same names |

Two metrics read differently:

- `*_percentile`: `core.oal` computes `percentile2(10)`, which floors a latency to 10 ms steps, so a 110 ms p99
  reads 110. The TAL `percentile` counts a latency in the bucket whose upper edge is the first bound not below it,
  and a rank reads that edge, so the same p99 reads 250 with the shipped buckets, and a rank above the last bound,
  10 s, reads `9223372036854775807`. An alarm threshold such as `p99 > 200` needs restating against the buckets.
- `*_resp_time`: a minute whose mean is above 0 but below 1 ms reads 1, the meter system's `avg`; `core.oal`'s
  `longAvg` reads 0.

The `otlp_mq_access_*` and `otlp_mq_destination_*` metrics have no `core.oal` counterpart. The agent path's
`mq_service_*` and `mq_endpoint_*` metrics also measure the broker and its topics and queues, but split into produce and
consume, with the transmission latency as the consume latency; the TAL rule emits its MQ metrics only from CLIENT and
PRODUCER spans and measures the span's duration. The agent metrics without a TAL counterpart are not produced: the
relation percentiles, every instance-relation metric (`service_instance_relation_*`, since the shipped rule builds no
instance relation), the server-side relation metrics other than `cpm`, the endpoint relations,
`database_access_percentile` and `cache_access_percentile`, the cache read and write split, and the consume-side MQ
metrics `service_mq_consume_count`, `service_mq_consume_latency` and `endpoint_mq_consume_latency`.

The numbers follow the agent path's definitions:

- A call fails when its span status is `ERROR` or it answered with an HTTP status of 400 or above, which the
  SkyWalking agents mark as an error. OpenTelemetry leaves a server's 4xx `UNSET`; the rule still counts it as failed,
  so `sla` and `apdex` read the same for both paths.
- A call is counted in the minute its span started, and its service, instance, endpoint and edge are registered in
  that minute too.
- Service and endpoint metrics carry their layer, `OTLP_GENERAL` or the peer's virtual layer, as `attr0`, so
  `top_n(otlp_endpoint_cpm, 10, des, attr0='OTLP_GENERAL')` works as it does for the agent metrics.

The component of an edge, the protocol icon in the topology, comes from `rpc.system.name`, `rpc.system`,
`db.system.name`, `db.system` or `messaging.system`, looked up in `component-libraries.yml` ignoring case; `grpc`
finds `GRPC` and `connect_rpc` finds `Connect_RPC`. Where the semantic conventions spell a system differently from
the catalog, the rule maps it first: `apache_dubbo` to `Dubbo`, `mssql` and `microsoft.sql_server` to `SqlServer`,
and `valkey`, which speaks the Redis protocol, to `Redis`. A system the catalog does not list shows as `Unknown`;
`jms` is one, since JMS is an API rather than a broker.

An iOS or iPadOS app's `NSURLSession` CLIENT spans are left to the [iOS analyzer](backend-ios-monitoring.md), which
already turns them into the app's metrics; the rule aborts on them.

A messaging `receive` poll is not a request: the rule drops a CLIENT or CONSUMER span whose `messaging.operation.type` (or the older `messaging.operation`) is `receive`, since the OpenTelemetry Java agent reports a blocking JMS receive as a CONSUMER span lasting the whole wait, which would otherwise count as a ten-second request on every poll. A `process` span, the message actually handled, counts as the consumer's request.

A SERVER span nested under another span of the same process is not a request either: frameworks such as Next.js open their own SERVER span under the one the HTTP instrumentation already opened for the request, and counting both would count the request twice. The rule drops a SERVER span whose OTLP span flags say its parent is local (`meta.parentLocation == "LOCAL"`). A SERVER span whose sender leaves those flags unset, as older SDKs and Envoy do, still counts.

### Topology

A SERVER span with no parent is a call from the `User` node, which appears in the topology without a service list
entry. A CLIENT span's edge lists its own service and a conjectured database, cache or broker on the other end, but
not a peer that is a service: that service lists itself from its own spans, in its own layer, so an address no
service has claimed yet, `cart:7070`, does not appear in the service list, and an agent-instrumented peer stays in
its agent layer. A CLIENT span's peer is named by `service.peer.name`, or its deprecated spelling `peer.service`, when the SDK sets
one, else by `server.address` and
`server.port`, `net.peer.name` and `net.peer.port` on older SDKs, `peer.address` on an Envoy egress span, or the
host and port of `url.full` or `http.url` when the SDK reports nothing else, as the Python `requests` instrumentation
does. An
address-named peer, `cart:7070` or `172.19.0.19:8080`, is folded onto the service it reaches by the three
mechanisms described under [Address-named peers](../../concepts-and-designs/tal.md#address-named-peers): the
server's own `alias`, the known-service registry, and the pairing of a call's two spans by the trace context. The
registry matches a host, or its first DNS label, against the services this node has analyzed spans of, or read from
storage when it started, so `cart.shop.svc.cluster.local:7070` folds onto `cart`. With [OTLP service grouping](otlp-trace.md), a grouped host
resolves in the caller's own group, or in the group its second DNS label names. The
first calls to a freshly seen address are address-named until the alias row has reached every node's cache: up to
one persistence period (`core.default.persistentPeriod`, 25 s by default) for the row to be stored, plus the 10 s
cache reload, so about 35 s. Those calls are drawn to a node named by the address, which no service is registered under, and the topology query folds it onto the service once the row lands.

In a fleet that mixes SkyWalking agents and OpenTelemetry SDKs, both paths write the same alias rows, and as on the
agent path the last write wins. The topology query folds a node that is not a registered service, or a self-call whose alias names another service; other registered nodes keep their own names, so a service named like some alias address keeps its own node: an OTLP service `frontend` is not drawn as `agent::frontend` because an agent registered the host `frontend` for `agent::frontend`. An address listed in the agent analyzer's [uninstrumented
gateways](uninstrumented-gateways.md) is never registered as an alias, and the shipped rule draws it the way the agent
path does, as a `VIRTUAL_GATEWAY` node between the caller and the callee: a CLIENT span that dials it ends its edge on
the gateway, and a SERVER span dialed at it, other than a root span, starts its edge from the gateway. An uninstrumented
proxy that passes its callers' address through to the service behind it belongs in that file; otherwise that service
registers the proxy's address as its own. A proxy that emits its own spans is a service with a node of its own, not a
gateway: when its callers dial it by its service name, as the OpenTelemetry demo's `frontend-proxy:8080` is dialed, the
known-service check keeps that address the proxy's. The callee sees the gateway's address only when the gateway passes
its callers' `Host` through, or sets `X-Forwarded-Host` for an SDK that reads it. A gateway that rewrites the `Host` to
the upstream's, as nginx's `proxy_pass` and Kong do by default, still ends its callers' edges but draws no edge to the
service behind it. With the `agent-analyzer` module not loaded, no address is a gateway.

In a cluster the pairing routes the facts of a trace to the node that owns it, through the same channel the metrics
use, so the two spans of a call meet wherever each arrived. In a Receiver/Aggregator deployment the owners are the
Aggregator nodes, which receive no spans themselves. `pairingSamplePercent` bounds that traffic: an address only has
to be learned once, so a sample of the traces is enough, and a single node ignores the setting. The facts leave
through a buffer of their own, never on the thread that received the spans: when a peer node is slow to take them
and the buffer fills, facts are dropped, which only delays learning an address, rather than holding up ingestion.

Every node loads its own rule files, and a metric is aggregated on whichever nodes its values reach, so every OAP
node of a cluster, Aggregator nodes included, needs the `trace-analyzer` module with the same `talFiles` and the same
files under `tal/`. A node without the module cannot receive the pairing facts routed to it; a node with different
rules declares different metrics, and values for a metric a node never declared are dropped there. While the
cluster membership changes, a pair in flight can split and that address is learned one call later.

The source of a client edge is the span's own `service.name`, never the service of the span it is nested under. An
application that emits its database or cache spans from a tracer named after the backend, as Jaeger's HotROD demo
does with `mysql` and `redis-manual`, therefore gets an edge from that pseudo-service to the peer, `mysql → mysql`,
and a `mysql` entry in `OTLP_GENERAL` with no server metrics, while the service that made the call, `customer`,
shows no edge to the database at all. The analyzer does not attribute a client span to its parent's service: the
child is exported before its parent ends, so the parent is unknown when the child's metrics are written, and
aggregated metrics cannot be moved afterwards. Instrumentations that wrap a driver emit the client span under the
application's own service, and the edge comes out as expected. A database or broker reached without an address is still named: an embedded or in-process database by its
system and namespace, `h2/mem-db`, and a broker whose client library hides the address, as the JMS API does, by
its messaging system alone, `jms`. The queue or topic a message went to becomes an endpoint of that broker
node, carrying `otlp_mq_destination_cpm`, `resp_time` and `sla`, so the virtual layer's list stays one row per
broker while the destinations can still be ranked, and a consumer's handled messages draw the same broker as
their caller. Such a node has no address for an alias to fold, so a broker that other clients reach by address
keeps a second identity under that address. A client span that knows none of these, neither an address, a
`service.peer.name`, a database system nor a messaging system, as the APISIX 2.15 proxy span and HotROD's Redis
spans do, draws no peer node, and its errors reach no metric, since `service_sla` counts server spans.

### Records

`otlp-slow-sql` writes a `DatabaseSlowStatement` for a database client span whose latency is 200 ms or more, with the
statement from `db.query.text` or `db.statement` cut to 2,000 characters as the agent path cuts it, keyed by the
trace and span id. It files the record under the database node the topology names: the rule names the peer with the
same attributes, fallbacks and alias resolution as `otlp-general`, and the two are kept in step. It also keeps the
span behind the record past the receiver's head sampling. The threshold is in the rule file; the agent path keys it by database
type through `slowDBAccessThreshold`, and a deployment edits the rule to match.

## Turning the analysis off

A deployment that wants the OTLP traces stored and queried, and nothing derived from them, empties the rule file
list:

```yaml
trace-analyzer:
  selector: ${SW_TRACE_ANALYZER:default}
  default:
    talFiles: ${SW_TRACE_TAL_FILES:}
```

or sets `SW_TRACE_TAL_FILES` to an empty string. With no rule installed a span costs the analyzer nothing: no
metric, no service or topology entry, no slow statement record, no pairing fact, and in a cluster no pairing
traffic. The receiver's storage path is unchanged, so the traces are still stored in the mode `otlpTraceStorage`
selects and still served to TraceQL.

Removing the module altogether, with `selector: -` or `SW_TRACE_ANALYZER=-`, has the same effect: the OpenTelemetry
trace receiver declares the module as a requirement only when it is loaded, and without it the receiver stores
every span and analyzes none. The difference is what stays resident: with an empty `talFiles` the module and its
services exist and a rule file can be listed again with a restart; with `-` nothing of it is loaded.

To keep part of the analysis, put the rules you want in a file of your own under `config/tal/` and list only that
file. The two shipped rules are independent: `otlp-slow-sql` alone writes the slow statement records and keeps the
spans behind them, without registering any service or metric.

## Deduplication

An OTLP exporter retries a batch after a transport timeout, and the OAP may have accepted the first attempt, so a
retried span would count a request twice. `dedupWindow` keeps a per-node set of the ids of the spans some rule ran on
within the window and analyzes such a span once; it is off by default because it costs memory per span and the OTLP
logs path does not deduplicate either. A span no rule runs on, one without a registered layer or on a layer no rule is
declared for, takes no entry, so size `dedupCapacity` by the analyzed spans alone,
the `OTLP_GENERAL` ones with the shipped rules. The set is per OAP node, so a retry that lands on another node is
counted again.

A retry is not analyzed again, and it is stored as the first copy's rules decided: dropped, enforced, or with the
same tags, as long as those rules are still installed; once they are removed, a retry is stored as the receiver
decides. A retry that arrives while the first copy is still being analyzed takes the receiver's own decision
instead, as if no rule had voted; its metrics are still counted once.
