# OpenTelemetry Trace Analysis

The `trace-analyzer` module runs the [Trace Analysis Language (TAL)](../../concepts-and-designs/tal.md) rules over
every span the [OpenTelemetry trace receiver](otlp-trace.md) accepts, in both of its storage modes, and before the
receiver's head sampling and rate limit, so the metrics count what the SDKs sent rather than what the OAP chose to
store. With the defaults nothing needs to be enabled: once an OpenTelemetry SDK exports traces, its services, their
metrics, their topology and their slow statements appear.

## Configuration

```yaml
trace-analyzer:
  selector: ${SW_TRACE_ANALYZER:default}
  default:
    # TAL rule files under `tal/`, without extension, comma separated. Empty disables span analysis while keeping
    # the module, which the OTLP trace receiver requires.
    talFiles: ${SW_TRACE_TAL_FILES:otlp-default}
    # Spans seen within this window (seconds) are analyzed once, so an exporter that retries a batch after a
    # transport timeout does not count a request twice. 0, the default, keeps no set at all and treats every
    # arrival as new. Set it longer than the exporter's retry budget; the OTLP SDKs give up after about 30 s. The
    # set is per OAP node: a retry that reaches another node is counted again.
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

The rule files live under `config/tal/` of the distribution. To change what is measured, edit a file there or add
one and list it in `talFiles`, then restart the OAP: the files are read at start. A rule file that does not compile,
or that names a layer that is not registered, stops the OAP from starting with a message naming the rule.

## What the shipped rule file produces

`tal/otlp-default.yaml` holds two rules. `otlp-service` claims every span whose resource carries no `service.layer`,
except the GenAI spans the GenAI analyzer already handles, and `otlp-slow-sql` writes a slow statement record for a
database client span above 200 ms. Both read the OpenTelemetry semantic conventions with the current attribute
names first and the deprecated spellings older SDKs still emit as fallbacks.

### Layers

| Layer | Holds | Registered by |
|---|---|---|
| `OTLP_SERVICE` | a service whose spans arrived over OTLP | every span's `service.name` |
| `OTLP_VIRTUAL_DATABASE` | a database a client span names through `db.system.name` or `db.system` | the client's span |
| `OTLP_VIRTUAL_CACHE` | a cache, the same way, for `redis`, `memcached` and the other cache systems | the client's span |
| `OTLP_VIRTUAL_MQ` | a broker a producer or consumer span names through `messaging.system` | the producer's or consumer's span |

The layers are separate from the agent path's `GENERAL` and `VIRTUAL_*` layers because their metrics come from a
different engine under different names. A service id is its name plus the `normal` flag, not the layer, so a
service that reports both agent segments and OTLP spans is one entity listed under both layers, and a database
reached from both paths is one node.

### Metrics

Every metric carries the file's `otlp_` prefix. On the service and its edges:

| Entity | Metrics |
|---|---|
| service | `otlp_service_cpm`, `otlp_service_resp_time`, `otlp_service_sla`, `otlp_service_apdex`, `otlp_service_percentile` |
| instance, when the resource carries `service.instance.id` | `otlp_instance_cpm`, `otlp_instance_resp_time`, `otlp_instance_sla` |
| endpoint, named `<method> <route>` for HTTP spans and by the span name otherwise | `otlp_endpoint_cpm`, `otlp_endpoint_resp_time`, `otlp_endpoint_sla`, `otlp_endpoint_percentile` |
| the call a CLIENT or PRODUCER span makes | `otlp_service_relation_client_cpm`, `otlp_service_relation_client_resp_time`, `otlp_service_relation_client_sla` |
| the call a root SERVER span receives, from the `User` node | `otlp_service_relation_server_cpm` |

On the conjectured peers, from the client's span: `otlp_database_access_cpm`, `_resp_time` and `_sla`, and the
`otlp_cache_access_*` and `otlp_mq_access_*` counterparts. The names follow the agent metrics of the same shape, so
an alarm rule or a dashboard widget written for `service_sla` or `database_access_cpm` ports by renaming the
metric. The `core.oal` metrics are not produced for these services; no dashboard template reads the `otlp_*`
metrics yet, so query them through [MQE](../../api/metrics-query-expression.md) and reference them in alarm rules.

The component of an edge, the protocol icon in the topology, comes from `rpc.system.name`, `rpc.system`,
`db.system.name`, `db.system` or `messaging.system`, looked up in `component-libraries.yml` ignoring case; `grpc`
finds `GRPC` and `connect_rpc` finds `Connect_RPC`. A system the catalog does not list shows as `Unknown`.

A messaging `receive` poll is not a request: the rule drops a CLIENT or CONSUMER span whose `messaging.operation.type` (or the older `messaging.operation`) is `receive`, since the OpenTelemetry Java agent reports a blocking JMS receive as a CONSUMER span lasting the whole wait, which would otherwise count as a ten-second request on every poll. A `process` span, the message actually handled, counts as the consumer's request.

### Topology

A SERVER span with no parent is a call from the `User` node, which appears in the topology without a service list
entry. A CLIENT span's peer is named by `service.peer.name`, or its deprecated spelling `peer.service`, when the SDK sets
one, else by `server.address` and
`server.port`, `net.peer.name` and `net.peer.port` on older SDKs, `peer.address` on an Envoy egress span, or the
host and port of `url.full` or `http.url` when the SDK reports nothing else, as the Python `requests` instrumentation
does. An
address-named peer, `cart:7070` or `172.19.0.19:8080`, is folded onto the service it reaches by the three
mechanisms described under [Address-named peers](../../concepts-and-designs/tal.md#address-named-peers): the
server's own `alias`, the known-service registry, and the pairing of a call's two spans by the trace context. The
first calls to a freshly seen address are address-named until the alias row has reached every node's cache, within
about 10 seconds, and the topology query folds those.

In a cluster the pairing routes the facts of a trace to the node that owns it, through the same channel the metrics
use, so the two spans of a call meet wherever each arrived. `pairingSamplePercent` bounds that traffic: an address
only has to be learned once, so a sample of the traces is enough, and a single node ignores the setting. While the
cluster membership changes, a pair in flight can split and that address is learned one call later.

The source of a client edge is the span's own `service.name`, never the service of the span it is nested under. An
application that emits its database or cache spans from a tracer named after the backend, as Jaeger's HotROD demo
does with `mysql` and `redis-manual`, therefore gets an edge from that pseudo-service to the peer, `mysql → mysql`,
and a `mysql` entry in `OTLP_SERVICE` with no server metrics, while the service that made the call, `customer`,
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
statement from `db.query.text` or `db.statement`, under the database node the topology names, and keeps the span
behind it past the receiver's head sampling. The threshold is in the rule file; the agent path keys it by database
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
retried span would count a request twice. `dedupWindow` keeps a per-node set of the span ids seen within the
window and analyzes a span once; it is off by default because it costs memory per span and the OTLP logs path does
not deduplicate either. The set is per OAP node, so a retry that lands on another node is counted again.
