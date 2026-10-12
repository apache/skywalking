# Metrics and Topology from OTLP Traces

## Motivation

Traces reported by SkyWalking agents drive the whole analysis platform. `TraceAnalyzer` walks every span of a
segment, `RPCAnalysisListener` turns entry and exit spans into `Service`, `ServiceInstance`, `Endpoint`,
`ServiceRelation`, `ServiceInstanceRelation` and `EndpointRelation` sources, the virtual-service processors turn
database, cache, message-queue and GenAI calls into virtual services with their access metrics and slow-statement
records, and OAL aggregates all of it into `service_cpm`, `service_resp_time`, `service_sla`, the percentiles, the
endpoint metrics, the topology and everything alarms and dashboards are built on.

Traces reported over OTLP drive none of it. Whether they are converted to Zipkin, as today, or stored natively, as
[the native OTLP trace storage proposal](SWIP-otlp-native-trace.md) describes, the only analysis an OTLP span gets is
the name catalogs of its trace store and the two special-purpose span listeners that exist for GenAI and iOS
attributes. An OpenTelemetry SDK user sees their services nowhere in the SkyWalking service list, gets no topology,
no RED metrics, no virtual database or broker nodes, and cannot alarm on any of it. The OpenTelemetry Collector's
`spanmetrics` and `servicegraph` connectors can produce metrics that a MAL rule could ingest, but that path needs a
collector in front of OAP, yields dimensions defined by collector configuration rather than SkyWalking entities, and
never produces the per-span sources the General-layer dashboards, the virtual services and the alarm rules were
built around.

The gap is not the aggregation layer, which is protocol-agnostic. It is the step that reads a span and emits the
sources. This proposal adds that step for OTLP spans, as a `SpanListener` that maps each span onto the same sources
the agent path emits, driven by a rule file that carries the OpenTelemetry semantic-convention knowledge.

OTLP spans differ from agent segments in three ways the design has to absorb:

| Agent segment carries | OTLP span carries | Consequence |
|---|---|---|
| `SegmentReference` with the caller's service, instance, endpoint and the address the caller used | `parentSpanId` only | A server-side span cannot name its caller. Topology edges come from the client side of each hop |
| `SpanLayer` and `componentId` | Semantic-convention attributes: `http.*`, `rpc.*`, `db.*`, `messaging.*`, `gen_ai.*`, `faas.*` | Request type, layer and component are derived from attribute families, by configuration |
| One `peer` address | `peer.service`, `server.address`, `server.port`, or for Envoy an `upstream_cluster` | Peer resolution is a rule, and differs per layer |

A fourth difference is the layer of the reporting service itself. Agents only ever produce GENERAL or FAAS. OTLP
resources carry attributes that identify a service mesh sidecar, a function runtime or an explicit
`service.layer`, so the layer becomes a per-resource decision.

## Architecture Graph

```text
OTLP/gRPC, OTLP/HTTP
        │
        v
OpenTelemetryTraceHandler.processExport
        │  for every span, before head sampling and before storage of either mode:
        v
SpanListenerManager.notifyOTLPPhase ───────────────────────────────────────────────┐
        │                                                                            │
        ├─> IOSHTTPSpanListener, IOSMetricKitSpanListener, GenAISpanListener (existing) │
        │                                                                            │
        └─> OTLPTraceAnalysisListener   (new module oap-server/analyzer/otlp-trace-analyzer)
                 │  otlp-trace-analysis.yml
                 │    aliases ─ service identity & layer ─ request type ─ status ─ peers ─ callers
                 │
                 ├─ resource  ──> layer (GENERAL | FAAS | MESH | explicit service.layer)
                 ├─ SERVER / CONSUMER span ──> callingIn:  Service, ServiceInstance, Endpoint,
                 │                              ServiceRelation from User / gateway / broker
                 ├─ CLIENT / PRODUCER span ──> callingOut: ServiceRelation, ServiceInstanceRelation,
                 │                              ServiceMeta + DatabaseAccess | CacheAccess | MQAccess ...
                 ├─ SERVER span ──> NetworkAddressAliasSetup (server.address:port -> this service)
                 └─ INTERNAL span ──> nothing, or a logic Endpoint when opted in
                                   │
                                   v
                          SourceReceiver ──> OAL (core.oal, unchanged) ──> metrics, topology, alarms
```

The listener sits in the existing phase-one hook, so it runs on every span regardless of the trace store, of
`otlpTraceStorage`, and of head sampling, which `OTLPSpanForward` and `SpanForward` apply afterwards. That is the
same ordering the agent path has: only `SegmentAnalysisListener` consults the sampler, and it decides storage, not
metrics.

## Proposed Changes

### 1. Extension point and module

A new analyzer module, `oap-server/analyzer/otlp-trace-analyzer`, alongside `gen-ai-analyzer` and `ios-analyzer`,
containing `OTLPTraceAnalysisListener implements SpanListener`, registered through
`META-INF/services/org.apache.skywalking.oap.server.core.trace.SpanListener`. The agent-analyzer listeners
`CommonAnalysisListener`, `RPCAnalysisListener` and `SegmentAnalysisListener` are not touched.

`requiredModules()` declares `CoreModule.NAME`, for `SourceReceiver`, `NamingControl`, `NetworkAddressAliasCache`
and `IComponentLibraryCatalogService`, and `AnalyzerModule.NAME`, for the shared configuration service in
[section 4](#4-shared-configuration-from-the-analyzer-module). The listener is inert unless enabled:

```yaml
receiver-otel:
  selector: ${SW_OTEL_RECEIVER:default}
  default:
    # Derive SkyWalking sources (services, endpoints, topology, virtual databases, brokers, caches) from
    # OTLP spans, the way the agent trace analyzer does for segments. Independent of otlpTraceStorage.
    otlpTraceAnalysis: ${SW_OTEL_TRACE_ANALYSIS:false}
    otlpTraceAnalysisRules: ${SW_OTEL_TRACE_ANALYSIS_RULES:otlp-trace-analysis.yml}
    # Spans seen within this window (seconds) are analyzed once, even if a retrying exporter delivers them again.
    # 0 disables the check. Bounded by otlpTraceAnalysisDedupCapacity entries per OAP node.
    otlpTraceAnalysisDedupWindow: ${SW_OTEL_TRACE_ANALYSIS_DEDUP_WINDOW:300}
    otlpTraceAnalysisDedupCapacity: ${SW_OTEL_TRACE_ANALYSIS_DEDUP_CAPACITY:1000000}
```

The listener reads its switch and rule path from `OtelMetricReceiverConfig` through the module manager at `init`,
so the receiver owns the configuration and the module owns the behavior. `SpanListenerResult.CONTINUE` is always
returned: this listener neither vetoes persistence nor injects attributes.

### 2. `OTLPSpanReader` additions

The reader exposes `traceId`, `spanId`, `spanName`, `spanKind`, the two timestamps and `getAttribute(key)` today.
The native storage proposal adds `Map<String, String> attributes()`. This proposal adds, all as default methods so
existing implementations compile:

| Method | Returns | Used for |
|---|---|---|
| `parentSpanId()` | lowercase hex, empty for a root span | caller resolution, section 6 |
| `statusCode()` | the OTLP enum name, `STATUS_CODE_UNSET`, `STATUS_CODE_OK`, `STATUS_CODE_ERROR` | status |
| `statusMessage()` | the status message | GenAI and error records |
| `attributes()` | every span attribute, values rendered as strings | rule matching, tags |

`spanKind()` already returns the proto enum name, `SPAN_KIND_CLIENT` rather than `CLIENT`; the listener matches on
those names.

### 3. Shared source builders

`EndpointSourceBuilder` and `RPCTrafficSourceBuilder` in `agent-analyzer` are the only code that knows how to fill
the six traffic sources consistently, including `NamingControl` formatting in `prepare()`, the null rules for
instance and endpoint relations, and the `key:value` tag rendering. They are package-private and depend only on
`server-core` types and `KeyStringValuePair`, which `server-core` already imports. They move to
`org.apache.skywalking.oap.server.core.source.builder` in `server-core` and become public. `RPCAnalysisListener`
and the logic-endpoint code update their imports; no behavior changes. The OTLP listener then produces exactly the
objects the agent path produces, with the same formatting.

`NetworkAddressAliasSetup` is reused as is for address registration, see section 6.

### 4. Shared configuration from the analyzer module

Three settings the agent analyzer owns are needed unchanged: the uninstrumented gateways from `gateways.yml`
(`UninstrumentedGatewaysConfig`), and the slow thresholds `slowDBAccessThreshold`, `slowCacheReadThreshold` and
`slowCacheWriteThreshold` with their dynamic-configuration watchers. Duplicating their parsing would give operators
two places to configure one thing. `AnalyzerModule` gains a service, `TraceAnalysisConfigService`, exposing
`isAddressConfiguredAsGateway(address)`, `dbLatencyThreshold(type)`, `cacheReadLatencyThreshold(type)` and
`cacheWriteLatencyThreshold(type)`. `AnalyzerModuleProvider` is its only provider and registers it; the
`server-tools` data generator references the module but does not provide it.

### 5. The rule file

`otlp-trace-analysis.yml` ships in `server-starter/src/main/resources`. Every semantic-convention name lives here
and not in Java, because the messaging, FaaS and GenAI conventions are still at Development stability and the
database conventions were renamed when they stabilized in 1.30. The `aliases` section is what lets a mixed fleet of
SDK versions land on one set of entities.

```yaml
# Matching vocabulary, used everywhere below:
#   resource: / span:   which attribute set to look at
#   key                 attribute key, resolved through `aliases` (current name first, then old spellings)
#   exists | equals | in | matches   one predicate per condition; `matches` is a Java regex, full match
#   anyOf / allOf       combine conditions; a bare condition is allOf of one

# 1. Attribute aliases: current name first, then the deprecated spellings older SDKs still emit.
aliases:
  db.system.name:                 [db.system]
  db.query.text:                  [db.statement]
  db.operation.name:              [db.operation]
  db.namespace:                   [db.name]
  db.collection.name:             [db.sql.table]
  messaging.operation.type:       [messaging.operation]
  messaging.consumer.group.name:  [messaging.kafka.consumer.group]
  messaging.kafka.offset:         [messaging.kafka.message.offset]
  gen_ai.provider.name:           [gen_ai.system]
  http.request.method:            [http.method]
  http.response.status_code:      [http.status_code]
  server.address:                 [net.peer.name, net.host.name]
  server.port:                    [net.peer.port, net.host.port]
  client.address:                 [http.client_ip]
  network.peer.address:           [net.sock.peer.addr, net.peer.ip]

# 2. The reporting service: identity and layer, decided once per resource.
service:
  name:
    from: [service.name, faas.name, k8s.deployment.name, process.executable.name]
    # Optional SkyWalking group prefix, `<group>::<name>`, taken from service.namespace. Off by default:
    # peer.service values and dialed addresses do not carry the namespace, so enabling it needs both sides
    # of every hop to be named the same way.
    group: { from: service.namespace, enabled: false }
  instance:
    from: [service.instance.id, service.instance, k8s.pod.name, host.name]
    default: unknown
    # Instance properties, emitted once per minute per instance as ServiceInstanceUpdate; `language` drives
    # the icon in the service and instance lists and must be a Language enum name.
    properties:
      language:    { from: telemetry.sdk.language, map: { java: JAVA, dotnet: DOTNET, nodejs: NODEJS, python: PYTHON, ruby: RUBY, go: GO, php: PHP }, default: UNKNOWN }
      hostname:    { from: host.name }
      ipv4:        { from: host.ip }
      namespace:   { from: k8s.namespace.name }
      Process No.: { from: process.pid }
      OS Name:     { from: os.type }
      copyRemaining: true          # every other resource attribute is copied under its own key
  layer:
    # An explicit resource attribute wins over the rules. An unknown value logs a warning and
    # falls through to the rules instead of dropping the span, unlike the OTLP logs path.
    attribute: service.layer
    rules:
      - layer: MESH
        # ALS is the source of truth for MESH metrics and Istio samples tracing at 1% by default,
        # so mesh spans are stored and catalogued but produce no sources unless this is flipped.
        metrics: false
        when:
          anyOf:
            - resource: { key: telemetry.sdk.name, equals: envoy }
            - span:     { key: istio.canonical_service, exists: true }
        # Envoy's resource has no service.instance.id; the pod is the third field of node_id.
        instance:
          from: node_id
          matches: '[^~]*~[^~]*~(?<pod>[^.~]+)\.[^~]*~.*'
          format: '${pod}'
        # Sidecar client spans carry no peer.service; the destination comes from Envoy's cluster
        # name, rendered in the ALS naming shape `<canonical-name>.<namespace>`.
        peer:
          from: upstream_cluster
          matches: 'outbound\|\d+\|[^|]*\|(?<name>[^.]+)\.(?<namespace>[^.]+)\.svc\..*'
          format: '${name}.${namespace}'
      - layer: FAAS
        when:
          resource: { key: faas.name, exists: true }
        endpoints: false          # mirrors RPCAnalysisListener.build(): no Endpoint source for FAAS
      - layer: GENERAL
        default: true

# 3. Request type. A span may carry several families; the first match in `precedence` wins.
requestType:
  precedence: [GenAI, MQ, DATABASE, RPC, HTTP]
  GenAI:    { span: { key: gen_ai.provider.name,  exists: true } }
  MQ:       { span: { key: messaging.system,      exists: true } }
  DATABASE: { span: { key: db.system.name,        exists: true } }
  RPC:      { span: { key: rpc.system,            exists: true } }
  HTTP:     { span: { key: http.request.method,   exists: true } }

# 4. Status, latency and endpoint naming.
status:
  error:
    anyOf:
      - status.code: ERROR
      # - span: { key: http.response.status_code, matches: '5\d\d' }   # opt in: a bare 5xx is an error
  httpResponseStatusCode: http.response.status_code
  rpcStatusCode:          rpc.grpc.status_code
latency:
  from: end_time_unix_nano - start_time_unix_nano
  unit: ms                    # rounded, clamped to [0, Integer.MAX_VALUE]
endpoint:
  name: span.name             # already `{method} {route}` per the HTTP conventions; NamingControl applies

# 5. Peers: what a CLIENT / PRODUCER span calls, and what a CONSUMER span consumed from.
peers:
  address:
    from: [peer.service, server.address]     # peer.service wins; neither present: span skipped
    portFrom: server.port
    aliasLookup: true                        # resolve through NetworkAddressAliasCache
  registerServerAddress: true                # SERVER spans register server.address:port for their service
  gateways: gateways.yml                     # listed addresses become VIRTUAL_GATEWAY on both sides
  virtual:
    - layer: VIRTUAL_CACHE
      when: { span: { key: db.system.name, in: [redis, valkey, memcached] } }
      component:
        from: db.system.name
        map: { redis: Redis, valkey: Redis, memcached: Memcached }
      nameFallback: '${db.system.name}-local'
      operation:
        from: db.operation.name
        read:  [GET, MGET, HGET, HGETALL, HMGET, LRANGE, LINDEX, SMEMBERS, SISMEMBER, ZRANGE, ZSCORE, EXISTS, TTL, SCAN, KEYS, GETS]
        write: [SET, SETEX, SETNX, MSET, HSET, HMSET, HDEL, DEL, INCR, INCRBY, DECR, LPUSH, RPUSH, LPOP, RPOP, SADD, SREM, ZADD, ZREM, EXPIRE, CAS, ADD, REPLACE, APPEND]
      command: db.operation.name
      # Thresholds: slowCacheReadThreshold / slowCacheWriteThreshold keyed by the system value.
    - layer: VIRTUAL_DATABASE
      when: { span: { key: db.system.name, exists: true } }
      component:
        from: db.system.name
        map:
          postgresql: PostgreSQL
          mysql: Mysql
          mariadb: Mariadb
          oracle.db: ORACLE
          microsoft.sql_server: SqlServer
          mongodb: MongoDB
          cassandra: Cassandra
          elasticsearch: Elasticsearch
          clickhouse: ClickHouse
          h2database: H2
          sqlite: SQLite
          influxdb: InfluxDB
          hbase: HBase
          neo4j: Neo4j
        default: Unknown
      nameFallback: '${db.system.name}-local'
      statement: db.query.text
      # Thresholds: slowDBAccessThreshold keyed by the system value (`default:200,mongodb:100`).
    - layer: VIRTUAL_MQ
      when: { span: { key: messaging.system, exists: true } }
      component:
        from: messaging.system
        map: { kafka: Kafka, rabbitmq: RabbitMQ, rocketmq: RocketMQ, pulsar: Pulsar, activemq: ActiveMQ, nats: NATS }
        default: Unknown
      endpoint: messaging.destination.name
      operation:
        produce: { kind: PRODUCER }
        consume: { kind: CONSUMER }
        ignore:  { span: { key: messaging.operation.type, in: [receive, settle, create] } }
      # transmission latency has no source attribute in the messaging conventions; left unset.
      # A batch `process` span (messaging.batch.message_count > 1) counts as one access, as one agent span does.
    - layer: VIRTUAL_GENAI
      when: { span: { key: gen_ai.provider.name, exists: true } }
      name: gen_ai.provider.name             # the VIRTUAL_GENAI service GenAIMeterAnalyzer creates, so the edge lands on it
      handler: gen-ai-analyzer               # access metrics come from GenAISpanListener
    - layer: FAAS
      when: { span: { key: faas.invoked_name, exists: true } }
      name: faas.invoked_name
    - layer: GENERAL
      when:
        anyOf:
          - span: { key: rpc.system,          exists: true }
          - span: { key: http.request.method, exists: true }
      component:
        from: rpc.system
        map: { grpc: GRPC, apache_dubbo: Dubbo }
        default: http                        # plain HTTP calls; the topology line type reads `http`
    - layer: UNDEFINED
      default: true

# 6. Callers of SERVER / CONSUMER spans. No SegmentReference exists, so the caller is known
#    only for a trace root or a configured gateway.
callers:
  rootSpan: User                     # no parentSpanId: the end user, as a ref-less entry today
  remoteParent: none                 # Service/Instance/Endpoint sources only, no server-side relation
  gatewayFromClientAddress: true     # client.address listed in gateways.yml: caller is that gateway
  # Opt in when callers stamp their identity into the request, e.g. W3C Baggage copied onto the server span
  # by an SDK extension or a collector processor. Names the caller and restores the server-side relations.
  fromAttributes:
    enabled: false
    service:  sw.caller.service
    instance: sw.caller.instance
    endpoint: sw.caller.endpoint

# 7. INTERNAL spans produce nothing by default. Opt in to logic endpoints, the counterpart of the
#    agents' `x-le` tag, by marking the spans that should count.
internal:
  logicEndpoints:
    enabled: false
    when: { span: { key: skywalking.logic_endpoint, equals: 'true' } }
```

The file carries no thresholds and no gateway list of its own. Slow database and cache access keep using the core
`slowDBAccessThreshold`, `slowCacheReadThreshold` and `slowCacheWriteThreshold` settings, keyed by the
`db.system.name` value, and `gateways.yml` stays the single source for uninstrumented gateways. Every `component`
name in the file resolves through `IComponentLibraryCatalogService.getComponentId`, so the topology icons and the
`database_access_*`, `cache_*` and `mq_*` OAL metrics need no change. The file is loaded at startup; hot reload can
follow the sampling policy's `ConfigChangeWatcher` pattern later.

The file is validated when it is loaded, and OAP refuses to start on a bad one rather than analyzing with a partial
rule set: a YAML parse error, a layer name `Layer.nameOf` does not know, a component name the catalog does not
resolve, a `matches` regex that does not compile, a `format` referencing a group the regex does not define, or a
`requestType.precedence` entry with no matching definition. The error names the section and the offending value.
Regexes are compiled once at load; per span, the listener builds the attribute map once and evaluates the rules in
order, so the cost is linear in the number of attributes plus the number of rules and independent of sampling.

### 6. The analysis, span by span

The listener resolves the reporting service once per resource: name, instance and layer from
[section 5 of the rule file](#5-the-rule-file), all through `NamingControl`. A resource with no resolvable name is
skipped, as the OTLP handler already skips it. It then dispatches on the span kind.

| Kind | Builder | Sources emitted | Caller or peer |
|---|---|---|---|
| SERVER, CONSUMER | `callingIn`, `DetectPoint.SERVER` | `Service`, `ServiceInstance`, `Endpoint` unless the layer says `endpoints: false`; `ServiceRelation`, `ServiceInstanceRelation` and `EndpointRelation` only when a caller is known | root span: `User` in UNDEFINED, as a reference-less entry span today. `client.address` in `gateways.yml`: that VIRTUAL_GATEWAY. CONSUMER: the broker as a VIRTUAL_MQ caller, as the MQ branch of `parseEntry` does. Otherwise no caller and no relation |
| SERVER | alias registration | `NetworkAddressAliasSetup` mapping `server.address:server.port` to this service and instance | replaces what `networkAddressUsedAtPeer` provides for agents |
| CLIENT, PRODUCER | `callingOut`, `DetectPoint.CLIENT` | `ServiceRelation`; `ServiceInstanceRelation` when the peer resolved to a real instance; for a virtual peer also `ServiceMeta` and the access source of its kind, `DatabaseAccess` and `DatabaseSlowStatement`, `CacheAccess` and `CacheSlowAccess`, `MQAccess`, `MQEndpointAccess` and `EndpointMeta` | `peer.service`, else `server.address:server.port`, resolved through `NetworkAddressAliasCache`; unresolved addresses become the virtual peer the `peers.virtual` list selects, or UNDEFINED |
| CLIENT or CONSUMER with `messaging.operation.type` `receive` | none | none | a poll is transport bookkeeping, whichever kind the SDK gives the wait |
| INTERNAL | none, or `EndpointSourceBuilder` | a `RequestType.LOGIC` `Endpoint` when `internal.logicEndpoints` opts the span in | none |

Two sources are emitted per resource on every span, independent of the kind, and deduplicated per minute by OAL:
`ServiceMeta{name, layer}` and `ServiceInstanceUpdate{service, instance, properties}`. Without them a service that
only ever sends CLIENT or PRODUCER spans, a batch job publishing to Kafka for instance, would exist as a relation
source but never in the service list, because `ServiceTraffic` and `InstanceTraffic` are created by these sources
and by the calling-in path only. The properties come from the `service.instance.properties` rules:
`telemetry.sdk.language` mapped onto the `Language` enum drives the language icon, and the remaining resource
attributes appear on the instance page the way agent-reported properties do.

`DatabaseSlowStatement.traceId` and the GenAI records carry the OTLP trace id in hex. The UI links from them to the
trace resolve only where the service's trace tab does, see the Compatibility section.

The common attributes follow `setPublicAttrs` in the agent path: latency is the nanosecond difference rounded to
milliseconds and clamped to `[0, Integer.MAX_VALUE]`, status is false when `status.code` is `STATUS_CODE_ERROR`,
`httpResponseStatusCode` and `rpcStatusCode` come from the configured attributes, `type` from the request-type
precedence, and every span attribute is added as a `key:value` tag plus the `originalTags` map. The time bucket is
the minute of the span's start.

One step has no counterpart in the agent path. OTLP exporters retry a batch on transient failure, in SDKs and in
the collector, and a retry after a transport timeout re-delivers spans OAP has already processed. The trace store
deduplicates them by trace id and span id; the sources would not be, and every retried request would be counted
twice in `service_cpm`, `service_sla` and the relation metrics. The listener therefore keeps a seen-set of
`(traceId, spanId)` for `otlpTraceAnalysisDedupWindow` seconds, capped at `otlpTraceAnalysisDedupCapacity`
entries, and analyzes a span only on first sight. The window defaults to the collector exporter's default maximum
retry time. The set is per OAP node: a retry that lands on another node behind a load balancer is not caught, which
is why the export response must never invite a retry of accepted spans, see the `partial_success` handling in the
native storage proposal.

Two decisions differ from the agent path and are deliberate:

- A CLIENT span to a cache gets `RequestType.DATABASE`, because `db.system.name` is what identifies it. The agent
  path types a `SpanLayer.Cache` span as RPC, since `RequestType` has no cache value. Dashboards that split by type
  should know this.
- A SERVER span with a remote parent emits no server-side relation rather than a false `User` edge. Every hop's edge
  comes from its client side, so the topology still forms. The `service_relation_server_*` metrics populate only for
  `User` and gateway callers.

### 7. Layer resolution

The reporting service's layer is decided per resource in this order: the `service.layer` resource attribute if
present and known, then the first matching rule, then GENERAL. This is the same convention the OTLP logs handler
uses, with one difference: an unknown `service.layer` value is logged and falls through instead of dropping the
data.

MESH is the case that matters most. Envoy's OpenTelemetry tracer stamps its resource with `telemetry.sdk.name`
`envoy`, and Istio adds `istio.canonical_service` and its siblings as span tags, so sidecar spans are recognized
without configuration. Their `service.name` is `<canonical-name>.<namespace>`, the shape `k8sServiceNameRule`
gives ALS services, so mesh spans attach to the MESH services ALS already created. Because `ServiceTraffic`
identifies a service by name plus layer, an application that also runs an SDK becomes a separate GENERAL entity, and
the hierarchy joins the two through `K8S_SERVICE`. MESH metrics default to off: ALS is the full-fidelity source, and
Istio samples tracing at one percent by default, so span-derived numbers would contradict it. With `metrics: true`,
the `peer` extractor turns `outbound|9080|v2|reviews.default.svc.cluster.local` into `reviews.default`, and the
`instance` extractor takes the pod from `node_id`.

The virtual layers are decided per span, not per resource, from the `peers.virtual` list. Redis, Valkey and
Memcached are listed under `db.system.name` by the conventions but belong to VIRTUAL_CACHE, so the cache rule is
evaluated before the database rule. Every peer layer the agent path can produce has a rule: VIRTUAL_DATABASE,
VIRTUAL_CACHE, VIRTUAL_MQ, VIRTUAL_GENAI, VIRTUAL_GATEWAY, FAAS, GENERAL and UNDEFINED.

### 8. Worked example

The [native storage proposal's worked example](SWIP-otlp-native-trace.md#worked-example) is one HTTP hop. The
example for this proposal is the OpenTelemetry Demo's checkout flow, nine spans over two traces: `frontend` calls
`checkout` over gRPC, `checkout` calls `cart`, `cart` reads Valkey, `checkout` publishes to Kafka, and `accounting`
polls, processes and inserts into PostgreSQL. The consumer side is its own trace linked to the producer span, the
messaging conventions' default. The sources, with the ids `IDManager` derives:

| Span | Sources |
|---|---|
| every span of every resource | `ServiceMeta{<service>, GENERAL}` and `ServiceInstanceUpdate{<service>, <instance>, {language: NODEJS or GO or DOTNET, hostname, namespace: otel-demo, Process No., ...}}`, deduplicated per minute |
| `frontend` SERVER `POST /api/checkout`, root | `Service frontend` (GENERAL, `ZnJvbnRlbmQ=.1`), `ServiceInstance frontend-6c8d7f9b4-x2k9p`, `Endpoint POST /api/checkout`, `ServiceRelation User -> frontend` (SERVER, HTTP, 249 ms, status true, HTTP 200, component 49), `ServiceInstanceRelation`, `EndpointRelation User -> POST /api/checkout`; `NetworkAddressAliasSetup frontend:8080 -> frontend` |
| `frontend` CLIENT `oteldemo.CheckoutService/PlaceOrder` | `ServiceRelation frontend -> checkout` (CLIENT, RPC, 239 ms, `rpcStatusCode "0"`, component 23, `ZnJvbnRlbmQ=.1-Y2hlY2tvdXQ=.1`), `ServiceInstanceRelation`; `checkout:8080` resolved through the alias the checkout SERVER span registered |
| `checkout` SERVER `PlaceOrder`, remote parent | `Service checkout`, `ServiceInstance`, `Endpoint`, 237 ms; no relation |
| `checkout` CLIENT `GetCart`, `cart` SERVER `GetCart` | as the two rows above, `checkout -> cart`, 4 ms and 2 ms |
| `cart` CLIENT `HGET` | `ServiceMeta valkey-cart:6379` (VIRTUAL_CACHE), `CacheAccess` (Redis, Read, 1 ms), `ServiceRelation cart -> valkey-cart:6379` (DATABASE, component 7); no `CacheSlowAccess`, under the 10 ms Redis read threshold |
| `checkout` PRODUCER `send orders` | `ServiceMeta kafka:9092` (VIRTUAL_MQ), `EndpointMeta orders`, `MQAccess` (Kafka, Produce), `MQEndpointAccess orders` (Produce), `ServiceRelation checkout -> kafka:9092` (MQ, 5 ms, component 27) |
| `accounting` CLIENT `poll orders` | nothing: `receive` is ignored |
| `accounting` CONSUMER `process orders` | `Service accounting`, `ServiceInstance`, `Endpoint process orders` (MQ, 9 ms), `ServiceRelation kafka:9092 -> accounting` (SERVER, VIRTUAL_MQ to GENERAL), `ServiceInstanceRelation`, `MQAccess` and `MQEndpointAccess` (Consume) |
| `accounting` CLIENT `INSERT otel.orders` | `ServiceMeta postgresql:5432` (VIRTUAL_DATABASE), `DatabaseAccess` (PostgreSQL, 5 ms), `ServiceRelation accounting -> postgresql:5432` (DATABASE, component 22); no `DatabaseSlowStatement`, under the 200 ms default |

The HTTP relation from `User` carries component 49, `http`, and the gRPC relations carry 23, `GRPC`, from the
GENERAL peer rule's `component` map, so the topology line types read as the protocols rather than Unknown. Systems
the map does not list fall back to `http`; adding one is a rule-file change, not a code change.

#### 8.1 The rules step by step

Every span goes through the same eight steps. The first happens once per resource, the rest once per span.

1. **Resolve the reporting service** from the resource with `service.name`, `service.instance` and `service.layer`.
   The layer is the explicit `service.layer` attribute if present and known, else the first matching `layer.rules`
   entry, else GENERAL. A rule with `metrics: false` stops the analysis for every span of the resource.
2. **Resolve attribute keys through `aliases`.** Every lookup tries the current name first, then its listed old
   spellings. The rest of the file never sees the difference.
3. **Dispatch on span kind.** SERVER and CONSUMER take the calling-in path, CLIENT and PRODUCER the calling-out
   path, INTERNAL the `internal` section.
4. **Pick the request type** by the first attribute family present in `requestType.precedence`.
5. **Compute status, latency and endpoint name** from the `status`, `latency` and `endpoint` sections.
6. **Find the other side.** Calling in: apply `callers`. Calling out: apply `peers.address`, then the alias cache,
   then the `peers.virtual` list, first match wins.
7. **Build the sources** with the shared builders, every name through `NamingControl`.
8. **Hand them to `SourceReceiver`**, where the unchanged `core.oal` aggregates them.

Three spans of the checkout trace, followed through those steps.

**Span A, `cart` calls Valkey, `HGET`**

```text
resource: service.name=cart  service.instance.id=cart-5f7b8c9d6-h7v2n  telemetry.sdk.name=opentelemetry
span:     kind=CLIENT  name=HGET  1.2 ms  status={}
          db.system.name=redis  db.namespace=0  db.operation.name=HGET  db.query.text="HGET ? ?"
          server.address=valkey-cart  server.port=6379  network.peer.address=10.244.3.12
```

| Step | Rule consulted | Result |
|---|---|---|
| 1 | `service.name.from`, `service.instance.from`, `service.layer` | `cart`, `cart-5f7b8c9d6-h7v2n`; no `service.layer`, `telemetry.sdk.name` is not `envoy`, no `faas.name`, so the `default: true` rule gives GENERAL |
| 2 | `aliases` | `db.system.name` is present under its current name, no fallback needed |
| 3 | kind | CLIENT, calling out |
| 4 | `requestType.precedence` | no `gen_ai.*`, no `messaging.system`, `db.system.name` exists, so DATABASE |
| 5 | `status`, `latency` | `status.code` UNSET, status true; 1.2 ms rounds to 1 |
| 6 | `peers.address.from` | no `peer.service`, so `server.address` plus `server.port`, `valkey-cart:6379`; `aliasLookup` finds no SERVER span ever registered that address |
| 6 | `peers.virtual`, first match | the VIRTUAL_CACHE rule is listed first and `redis` is in its `in` list, so the VIRTUAL_DATABASE rule below it is never reached |
| 6 | `component.map`, `operation` of that rule | `redis` maps to `Redis`, component id 7; `HGET` is in the `read` list, so Read; `command` is `HGET` |
| 7 | builders | `ServiceMeta{valkey-cart:6379, VIRTUAL_CACHE}`, `CacheAccess{valkey-cart:6379, type 7, latency 1, status true, Read}`, `ServiceRelation{cart -> valkey-cart:6379, CLIENT, DATABASE, component 7, latency 1}` |
| 7 | thresholds from `TraceAnalysisConfigService` | `slowCacheReadThreshold` for `redis` is 10 ms; 1 ms is under it, no `CacheSlowAccess` |

Had the same span come from an SDK two years older it would read `db.system=redis`, `db.statement="HGET ? ?"`,
`net.peer.name=valkey-cart` and `net.peer.port=6379`. Step 2 resolves `db.system.name` through `[db.system]`,
`server.address` through `[net.peer.name, net.host.name]` and `server.port` through `[net.peer.port, net.host.port]`,
and steps 3 to 7 produce identical sources. That is what `aliases` is for: one virtual cache, not two.

**Span B, `frontend` calls `checkout` over gRPC**

```text
resource: service.name=frontend  service.instance.id=frontend-6c8d7f9b4-x2k9p
span:     kind=CLIENT  name=oteldemo.CheckoutService/PlaceOrder  238.8 ms  status={}
          rpc.system=grpc  rpc.service=oteldemo.CheckoutService  rpc.method=PlaceOrder  rpc.grpc.status_code=0
          server.address=checkout  server.port=8080  network.peer.address=10.244.2.31
```

| Step | Rule consulted | Result |
|---|---|---|
| 1 | layer rules | GENERAL, as for span A |
| 3 | kind | CLIENT, calling out |
| 4 | precedence | no GenAI, MQ or DB family; `rpc.system` exists, so RPC |
| 5 | `status.rpcStatusCode` | `rpc.grpc.status_code` gives `"0"`; `httpResponseStatusCode` stays NONE; 239 ms |
| 6 | `peers.address` | no `peer.service`, so `checkout:8080` |
| 6 | `aliasLookup` | hit: the `checkout` SERVER span of this call carries `server.address=checkout`, `server.port=8080`, and `registerServerAddress: true` made it emit `NetworkAddressAliasSetup{checkout:8080 -> checkout / checkout-7d9c6b5f8-qm4tz}`; the peer resolves to the real service and instance |
| 6 | `peers.virtual` | the peer is a real service, so the list only supplies the layer: the GENERAL rule matches on `rpc.system` |
| 6 | GENERAL rule `component` | `rpc.system` `grpc` maps to `GRPC`, component 23 |
| 7 | builders | `ServiceRelation{frontend -> checkout, CLIENT, RPC, latency 239, rpcStatusCode "0", component 23}`, `ServiceInstanceRelation{frontend-6c8d7f9b4-x2k9p -> checkout-7d9c6b5f8-qm4tz}` |

**Span C, `accounting` consumes from Kafka, `process orders`**

```text
resource: service.name=accounting  service.instance.id=accounting-8b6d4c7f5-z9q3w
span:     kind=CONSUMER  name="process orders"  parentSpanId=<the poll span>  9.1 ms  status={}
          messaging.system=kafka  messaging.operation.type=process  messaging.destination.name=orders
          messaging.consumer.group.name=accounting  server.address=kafka  server.port=9092
```

| Step | Rule consulted | Result |
|---|---|---|
| 3 | kind | CONSUMER, calling in |
| 4 | precedence | `messaging.system` exists and no `gen_ai.*`, so MQ |
| 5 | `endpoint.name`, `latency` | endpoint `process orders`; 9 ms; status true |
| 6 | `callers` | the span has a parent, so `rootSpan: User` does not apply and `remoteParent: none` would mean no caller; the VIRTUAL_MQ rule overrides this for CONSUMER spans, as the MQ branch of `parseEntry` does for agents: the caller is the broker |
| 6 | `peers.virtual` VIRTUAL_MQ | `server.address:port` gives `kafka:9092`; `kafka` maps to `Kafka`, component 27; `consume: { kind: CONSUMER }` selects Consume; `process` is not in the `ignore` list |
| 7 | builders | `Service{accounting, endpoint process orders, MQ, 9}`, `ServiceInstance`, `Endpoint{process orders}`, `ServiceRelation{kafka:9092 -> accounting, SERVER, VIRTUAL_MQ to GENERAL}`, `ServiceInstanceRelation`, `MQAccess{kafka:9092, Kafka, Consume}`, `MQEndpointAccess{kafka:9092, orders, Consume}`, `ServiceMeta`, `EndpointMeta` |

The span before it, `poll orders`, is kind CLIENT with `messaging.operation.type` `receive`. It reaches step 6, the
VIRTUAL_MQ rule matches on `messaging.system`, and the `ignore` list stops it: no sources. A poll is how the client
fetched bytes, not a request anyone can be measured on.

#### 8.2 What `checkout:8080` means in step 6

The address in span B is not a service name. It is the key the analysis uses to find out which service answered
there, and its meaning changes across three tiers.

- **`peer.service`, the callee's declared name.** The OpenTelemetry attribute is defined as the remote side's
  `service.name`. When present it is taken verbatim as the destination, in GENERAL, and no address is involved. No
  instrumentation library sets it on its own; it appears only through operator configuration such as the Java
  agent's peer-service mapping or a collector `attributes` processor, so it is absent from most spans.
- **`server.address` plus `server.port`, the address as the agent path knows it.** Joined into `checkout:8080`, it
  is the exact analog of the `peer` field of an agent exit span, the dialed host and port. The pod IP in
  `network.peer.address` is deliberately not used: IPs churn and many IPs serve one service. The port is kept
  because one host can serve several services.
- **The alias cache, from address to service.** `checkout:8080` is looked up in `NetworkAddressAliasCache`. Entries
  come from the other side of the hop: the `checkout` SERVER span carries `server.address=checkout` and
  `server.port=8080`, the authority it was reached by, and `registerServerAddress: true` turns that into
  `NetworkAddressAliasSetup{checkout:8080 -> checkout / checkout-7d9c6b5f8-qm4tz}`. For agents this registration
  comes from `SegmentReference.networkAddressUsedAtPeer`; OTLP has no reference, so the server span's own address
  plays the role. Both sides usually derive the string from the same Host or `:authority` header, so they match
  unless a gateway rewrites it.

When the lookup misses, the address itself becomes the destination service name in the layer the `peers.virtual`
list picks, GENERAL for an HTTP or gRPC call, and the topology shows a node under that name, as it does for agents
calling an uninstrumented or external peer such as `api.stripe.com:443`. A miss caused by timing heals itself:
`ServiceTopologyBuilder` runs destinations through the alias cache again at query time, so edges recorded against
`checkout:8080` before the server span arrived are drawn to `checkout` once the alias exists. A miss caused by
different spellings, `checkout.default.svc.cluster.local:8080` registered against `checkout:8080` dialed, leaves an
address-named node beside the real one; `peer.service` on the client is the cure, which is why it is the first
tier. A CLIENT span with neither `peer.service` nor `server.address` is skipped, as an agent exit span without
`peer` is skipped today.

The alias cache is per OAP node. `NetworkAddressAliasSetup` is persisted as a `NetworkAddressAlias` record, and
every node's `CacheUpdateTimer` reloads the recently updated records every 10 seconds, so a client span handled on
one node sees an alias registered on another after at most that delay plus persistence. This is the same behavior
agents get, and the query-time re-resolution above covers the gap.

#### 8.3 What OAL does with the sources

| Source | Metrics in `core.oal` that receive a point |
|---|---|
| `Service` for `accounting` | `service_cpm`, `service_resp_time` with 9, `service_sla` with true |
| `Endpoint` `process orders` | `endpoint_cpm`, `endpoint_resp_time` |
| `ServiceRelation` `frontend -> checkout`, CLIENT | `service_relation_client_cpm`, `service_relation_client_resp_time`; the server-side series get nothing, since no server span could name its caller |
| `ServiceRelation` `kafka:9092 -> accounting`, SERVER | `service_relation_server_cpm`, the one server-side series that populates, because the broker is a known caller |
| `CacheAccess` Read for `valkey-cart:6379` | `cache_read_cpm`, `cache_read_resp_time`, `cache_read_sla`, `cache_read_percentile`, and the `cache_access_*` totals |
| `MQAccess` Consume for `kafka:9092` | `mq_service_consume_cpm`, `mq_service_consume_sla`; `mq_service_consume_latency` receives 0, since transmission latency has no source attribute |
| `DatabaseAccess` for `postgresql:5432`, from the insert span | `database_access_cpm`, `database_access_resp_time`, `database_access_sla`, `database_access_percentile` |

Two ordering rules decide most outcomes. Within `peers.virtual`, position breaks ties: Redis is a `db.system.name`
value, so if the VIRTUAL_DATABASE rule came first every cache would be a database, and `requestType.precedence` is
the same idea for spans carrying several attribute families. Within `aliases`, the current name comes first, so a
span carrying both `db.system.name` and a stale `db.system` from a bridging SDK reads the current one, and upgrading
an SDK never flips an entity's identity.

### 9. Gaps compared with agent traces

The agent path is the reference. Everything below is a place where OTLP spans give less, or different, and the
design either compensates, documents, or offers an opt-in.

Gaps that follow from data the wire does not carry, and no rule can close:

| Gap | Agent path | OTLP path | Consequence |
|---|---|---|---|
| Caller identity on the server side | `SegmentReference` names the parent service, instance, endpoint and the dialed address | `parentSpanId` only | `service_relation_server_*` populate only for `User`, gateways and brokers; edges carry client-observed numbers only |
| Endpoint-level dependency | `EndpointRelation` from the parent endpoint; `EndpointDepFromCrossThreadAnalysisListener` relates endpoints across async hops | no parent endpoint; INTERNAL spans emit nothing | `endpoint_relation_*` exist only from `User`; the endpoint dependency view is empty |
| Gateway detection on the server side | the address the client dialed, matched against `gateways.yml` | `client.address`, the source IP | matches only when the gateway's egress IPs are configured |
| Message transmission latency | `transmission.latency`, the producer timestamp propagated in headers | nothing in the messaging conventions | `mq_service_consume_latency` reads 0 |
| Cache operation and key | `cache.op`, `cache.cmd`, `cache.key` | Read or Write classified from `db.operation.name` by a command table; no key attribute exists | `CacheSlowAccess.key` is empty; commands outside the table are `Others` |

Gaps whose size depends on how each SDK was instrumented:

| Gap | Agent path | OTLP path | Consequence |
|---|---|---|---|
| Alias matching | the server registers the exact address the client dialed | the server registers its own `server.address`, defined as the Host or `:authority` header, but some SDKs fill it with the listening hostname | a mismatch leaves an address-named node beside the real one; `peer.service` is the cure |
| Endpoint names | plugins normalize operation names from the framework's route | `{method} {route}` when the framework reports `http.route`, bare `GET` when it does not, arbitrary names from manual instrumentation | one endpoint absorbing every request, or a cardinality explosion; `endpoint-name-grouping.yml` repairs, it does not source |
| Error semantics | each plugin decides `isError` from exceptions and status codes | `STATUS_CODE_ERROR` for server 5xx and client 4xx and 5xx by convention, often UNSET | `service_sla` of an agent service and an SDK service are not comparable |
| Component identity | Tomcat, Spring MVC, Dubbo, the JDBC driver, Jedis | `grpc`, `http`, `postgresql`, `kafka` | coarser topology icons; no component-based server-name inference |
| Instance identity | the agent's instance name, unique per process | `service.instance.id` is optional | without it and without a pod or host name, every process is the instance `unknown`; instance metrics are wrong rather than missing |
| Sampling | every segment reported by default, OAP samples for storage | head sampling in the SDK is common | dropped spans are requests the metrics never see; MESH defaults to no metrics for this reason |

One correctness risk the agent path does not have: duplicate delivery from exporter retries, handled by the seen-set
in [section 6](#6-the-analysis-span-by-span) within one node, and not across nodes.

Where OTLP is better: the reporting service's layer is a per-resource decision, where agents only ever produce
GENERAL or FAAS; resource attributes carry instance properties agents report only partially; and the semantic
conventions give typed, standardized attributes for status codes, database systems and messaging systems rather
than per-plugin tag names.

Mitigations, in and out of scope:

- The seen-set for retried spans, in scope.
- Caller identity through request attributes, opt-in through `callers.fromAttributes`. An SDK extension or a
  collector processor stamps the caller's service, instance and endpoint into W3C Baggage and copies them onto the
  server span; the rule then names the caller and the server-side and endpoint relations return for that fleet. It
  is not a standard convention, so it ships disabled.
- `peer.service` for address mismatches and `http.route` support for useful endpoint names, documented as the two
  instrumentation prerequisites in the operator doc.
- A dry run on the admin server, `POST /debugging/otlp-trace-analysis`, which takes an OTLP/JSON payload and returns
  the sources the current rules would emit without emitting them, so an operator can see these gaps against real
  data before trusting the metrics.

### 10. Self-observability

Two counters and a histogram on the receiver's telemetry, next to `otel_spans_latency` and `otel_spans_dropped`:
`otlp_trace_analysis_spans` labeled by kind and resolved layer, `otlp_trace_analysis_skipped` labeled by reason, one
of `no_service_name`, `no_peer`, `unknown_kind`, `layer_metrics_disabled`, `ignored_operation`, and
`otlp_trace_analysis_latency` for the per-span rule evaluation, which is the number to watch when a rule file grows.
A fourth counter, `otlp_trace_analysis_duplicates`, counts spans the seen-set rejected; a non-zero rate is the sign of
an exporter retrying against a slow OAP.

### 11. Tests

- Unit tests for the rules engine: alias fallback, request-type precedence, layer resolution including an unknown
  `service.layer`, the MESH `peer` and `instance` regexes over subset and non-subset cluster names, the virtual-peer
  order for Redis, the caller rules for root, remote-parent and gateway spans, `callers.fromAttributes` with and
  without the attributes present, and the seen-set: the same span twice within the window emits sources once, and
  again after the window.
- Listener tests over two fixture payloads checked in as JSON: the demo checkout trace above and an Istio Bookinfo
  hop with two sidecar resources and an application resource. The expected `Source` objects are asserted field by
  field, including entity ids and time buckets.
- The `otlp-traces` e2e runs with `SW_OTEL_TRACE_ANALYSIS=true` on BanyanDB and verifies with `swctl` that the demo
  services appear under `service ly GENERAL`, that `metrics exec` returns `service_cpm` and `service_resp_time` for
  them, that `dependency service` shows the `frontend -> checkout -> cart` edges and the virtual nodes, that
  `service ly VIRTUAL_DATABASE`, `VIRTUAL_CACHE` and `VIRTUAL_MQ` list the peers, and that `database_access_resp_time`
  is present. `test/e2e-v2/cases/storage/expected/config-dump.yml` gains the two new defaults.
- `otlp-virtual-genai` runs with the flag on to prove the GenAI listener and this listener coexist on the same spans.

### 12. Documentation

`docs/en/setup/backend/otlp-trace.md` gains a section on metrics from OTLP traces: the two settings, how the layer
is decided, the rule file with its sections explained, the list of what appears in the UI and what does not,
the sampling caveat, and the semantic-convention versions the shipped aliases cover. `docs/en/changes/changes.md`
records the feature. No new menu entry is needed, since the entities land in the existing General and virtual
layers.

## Imported Dependencies libs and their licenses

None.

## Compatibility

- `otlpTraceAnalysis` defaults to `false`. Existing deployments see no new entities, metrics or sources.
- `OTLPSpanReader` gains default methods only. `AnalyzerModule.services()` gains one service with one provider.
  Moving the two source builders to `server-core` changes packages and visibility, not behavior.
- It works with both `otlpTraceStorage` modes and does not depend on the native storage proposal landing first.
  The two proposals share the `OTLPSpanReader.attributes()` addition and the phase-one placement.
- `service.namespace` is not part of the service name unless `service.name.group` is enabled, so two services
  with the same `service.name` in different namespaces are one entity by default, as they would be for agents
  without the `group::name` convention. Enabling the group changes every name to `<namespace>::<name>` and must be
  matched by `peer.service` values; it is a deployment-wide decision.
- An OTLP service and an agent service with the same name in the same layer are one entity, because the id is the
  name plus the layer. That is intended: a service migrating from an agent to an SDK keeps its history. A process
  running both an agent and an SDK at once would count its requests twice; this is documented, not prevented.
- Server-side relation metrics populate only for `User`, gateway and broker callers, and endpoint relations only
  from `User`, unless `callers.fromAttributes` is enabled fleet-wide. The full comparison with agent traces is in
  [section 9](#9-gaps-compared-with-agent-traces).
- Retried exporter batches are deduplicated per OAP node within `otlpTraceAnalysisDedupWindow`; a retry that lands
  on another node still counts twice.
- Metrics are exact only if OAP sees every span. SkyWalking agents report every segment and let OAP sample;
  OpenTelemetry SDKs often sample at the head. For exact numbers the SDK must export at 100 percent and
  `otlpTraceSampleRate` in `receiver-otel` should carry the storage sampling. MESH metrics stay off by default for
  this reason, since Istio samples at one percent.
- A GENERAL service created from OTLP spans shows metrics and topology in the existing dashboards. Its trace tab
  queries native segments and is empty until the Horizon UI learns to fetch that service's traces from the Zipkin
  or `/otlp` TraceQL datasource, whichever store the deployment uses. This is a UI change outside this proposal.
- Spans that arrive over the Zipkin protocol have no resource attributes and are not analyzed by this listener.
  A phase-two counterpart could be added later; it is out of scope here.

## General usage docs

Enable the analysis and, if needed, adjust the rules:

```yaml
receiver-otel:
  selector: default
  default:
    enabledHandlers: otlp-traces,otlp-metrics,otlp-logs
    otlpTraceAnalysis: true
    otlpTraceAnalysisRules: otlp-trace-analysis.yml
```

After an OpenTelemetry SDK exports traces, its services appear under the General layer with the standard service,
instance and endpoint dashboards, the topology shows the client-side edges between them, and databases, caches and
brokers they call appear as virtual services with their access metrics. Alarm rules written against `service_*`,
`endpoint_*`, `service_relation_*` and the virtual-service metrics fire for them without change, since the metrics
are the same. To place a group of services in another layer, set `service.layer` on their resource, for example
through the collector:

```yaml
processors:
  resource/layer:
    attributes:
      - key: service.layer
        value: FAAS
        action: upsert
```

To recognize an SDK that still emits an old attribute name, add it to `aliases`. To make a new database or broker
show its own icon, add its `db.system.name` or `messaging.system` value to the matching `component.map` with a name
from `component-libraries.yml`. To let mesh spans produce metrics where ALS is not deployed, set `metrics: true` on
the MESH rule.
