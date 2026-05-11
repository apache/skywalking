# SWIP-14 Inspect API for Admin Server

## Motivation

When operators author OAL / MAL / LAL rules and turn them on (statically or via
runtime-rule hot-update from SWIP-13), the natural follow-up question is:

- "Which metrics did this rule actually produce?"
- "For metric `service_xyz`, which entities are emitting values right now?"
- "Once I have an entity, can I query it via MQE without guessing how to
  reconstruct the `Entity` shape?"

Today the only way to answer these is to dig through dashboards, write
exploratory MQE expressions against guessed entity names, or read storage
directly. There is no first-class "browse" interface.

The public GraphQL surface deliberately does not expose
"enumerate entities with values for metric X in range R", because that scan is
unbounded and would compete with user-facing query traffic for storage
resources. Operators still need it — for debugging rules, validating new
monitoring features, and onboarding new agents.

This SWIP proposes an **Inspect API** on the existing admin-server
(SWIP-13's admin-host bundle, default port 17128) that exposes:

1. The metric catalog with type + scope + supported downsamplings.
2. For a given metric + time range, the set of entities (capped) that hold
   values, decoded into MQE-ready form.

The output of (2) is shaped so the operator can paste it directly into a
follow-up MQE call against the existing `MetricsExpressionQuery` GraphQL
mutation. This SWIP **does not** introduce a new MQE entry point — the public
GraphQL path remains the single MQE surface.

## Scope

In scope:

- `COMMON_VALUE` and `LABELED_VALUE` metric types — the two `dataType`s
  that `MQEVisitor.visitMetric` actually dispatches today.
- `Service`, `ServiceInstance`, `Endpoint`, `ServiceRelation`,
  `ServiceInstanceRelation`, `EndpointRelation` scopes.
- All three storage backends: BanyanDB, Elasticsearch, JDBC.
- `/inspect/metrics` lists every registered metric regardless of type
  (including `HEATMAP` and `SAMPLED_RECORD`) so operators can see the full
  catalog. Only `/inspect/entities` is restricted to MQE-dispatchable types.
- **Companion: relocate + dual-bind the Status API.** The existing
  `status-query-plugin` (cluster nodes / alarm runtime status / TTL config
  / debug query trace) is replaced by a new `status` feature module under
  `server-admin/`. The module registers its handlers on **both** the
  public REST `HTTPHandlerRegister` (preserving today's binding —
  skywalking-ui consumes these endpoints on `core.restPort`) **and**, if
  admin-server is enabled, the admin-server `HTTPHandlerRegister`. The
  default state remains "status on the public REST port" so the UI keeps
  working unchanged. No new endpoint is added — clients that need to
  discover the OAP REST address parse the existing
  `GET /debugging/config/dump`, which already publishes the effective
  configuration including REST host and port.

Out of scope (deferred):

- `HISTOGRAM` / `HEATMAP` metrics from `/inspect/entities`. MQE has no
  dispatch path for histogram dataType — `MQEVisitor.visitMetric` only
  handles `COMMON_VALUE`, `LABELED_VALUE`, and `SAMPLED_RECORD`; histograms
  flow through the legacy `MetricsQuery.readHeatMap` GraphQL resolver,
  which is being phased out. The `mqeEntity` block this API produces would
  not be usable for them. They remain visible in `/inspect/metrics` (with
  `type: HEATMAP`) so operators can see they exist.
- `SAMPLED_RECORD` metrics from `/inspect/entities`. These live in Stream
  storage (logs, trace segments, browser errors) and are addressed via
  `IRecordsQueryDAO` / trace-relative queries, not the metrics DAO. A
  future Inspect-Records endpoint can mirror this design once the scoping
  question is settled. They remain visible in `/inspect/metrics`.
- `Process` and `ProcessRelation` scopes. `ProcessID` is a SHA-256 hash with
  no decoder; readable rendering would require a `ProcessTraffic` join, which
  we punt on for v1.
- Firing MQE from admin-server. Operators take the inspect response and call
  the existing public GraphQL `execExpression` mutation. Keeping MQE on one
  surface preserves auth/quotas/observability there.

## Architecture

```
   ┌── operator / swctl / curl ──┐         ┌── skywalking-ui ──┐
   │  /inspect/* (admin-only)    │         │  /status/* (UI)   │
   │  /status/*  (admin mirror)  │         │                   │
   └─────────────┬───────────────┘         └─────────┬─────────┘
                 │ admin port 17128                  │ public REST port 12800
                 ▼                to kee                   ▼
   ┌─────────────────────────────────┐   ┌──────────────────────────────┐
   │      admin-server (Armeria)     │   │  public REST (sharing/core)  │
   │  ┌───────────────────────────┐  │   │   GraphQL / MQE / receivers  │
   │  │  inspect feature module   │  │   │                              │
   │  │   ├── /inspect/metrics    │  │   │   ┌────────────────────────┐ │
   │  │   └── /inspect/entities   │  │   │   │   status feature module │ │
   │  └───────────────────────────┘  │   │   │   ├── /status/cluster/.. │ │
   │                                 │   │   │   ├── /status/alarm/..  │ │
   │  ┌───────────────────────────┐  │   │   │   ├── /status/config/.. │ │
   │  │  status feature module    │◄─┼───┼───┤   └── /debugging/...    │ │
   │  │  (same handler instance,  │  │   │   │                          │ │
   │  │   dual-bound when admin   │  │   │   └────────────────────────┘ │
   │  │   is on; this side is     │  │   │                              │
   │  │   silent if admin off)    │  │   └──────────────────────────────┘
   │  └───────────────────────────┘  │
   └─────────────────────────────────┘
                     │                             │
                     ▼                             ▼
       ┌──────────────────────────┐  ┌──────────────────────────────┐
       │ ValueColumnMetadata      │  │  IMetricsQueryDAO            │
       │ StorageModels            │  │   .listEntityIdsInRange(...) │  ◄── new method
       │ MetadataQueryService     │  │  (BanyanDB / ES / JDBC impl) │
       │ DefaultScopeDefine       │  └──────────────────────────────┘
       └──────────────────────────┘
                     │
                     ▼
              decode entity_id via IDManager.*ID.analysisId(),
              enrich with layer(s) from MetadataQueryService cache,
              shape into MQE-ready Entity

  inspect-client flow (admin operator):
    1. (one-time) GET admin:17128/debugging/config/dump → parse REST host/port
       OR rely on a known deployment-time URL.
    2. GET admin:17128/inspect/metrics            → pick a metric
    3. GET admin:17128/inspect/entities?metric=…  → get entityId + mqeEntity
    4. POST <REST URL>/graphql execExpression(    → fire the actual query on
         entity = mqeEntity, expression = …)        the public GraphQL surface

  ui flow (skywalking-ui, unchanged):
    1. GET oap:12800/status/cluster, /status/alarm, /status/ttl, …
       (admin-server may be off — these still work via the public REST binding)
```

The inspect feature module is a sibling of `dsl-debugging` and
`runtime-rule` under the existing admin-host. It registers its handlers in
`start()` against the shared `HTTPHandlerRegister`. No new port, no new auth
surface.

## API

### `GET /inspect/metrics`

Lists all registered metrics with type, scope catalog, and the downsampling
levels at which each metric is materialized.

Query parameters:

| Name        | Required | Description                                        |
|-------------|----------|----------------------------------------------------|
| `regex`     | no       | Java regex over metric name. Default `.*`.         |
| `type`      | no       | Filter by `REGULAR_VALUE` / `LABELED_VALUE` / `HEATMAP` / `SAMPLED_RECORD`. Repeatable. |
| `mqeQueryable` | no    | If `true`, return only metrics that `/inspect/entities` accepts (`REGULAR_VALUE`, `LABELED_VALUE`). Default `false`. |
| `catalog`   | no       | Filter by catalog name (`SERVICE`, `SERVICE_INSTANCE`, `ENDPOINT`, `SERVICE_RELATION`, `SERVICE_INSTANCE_RELATION`, `ENDPOINT_RELATION`). Repeatable. |

Response (`200 OK`, `application/json`):

```json
{
  "metrics": [
    {
      "name": "service_cpm",
      "type": "REGULAR_VALUE",
      "catalog": "SERVICE",
      "scopeId": 1,
      "scope": "Service",
      "valueColumnName": "value",
      "downsamplings": ["MINUTE", "HOUR", "DAY"]
    },
    {
      "name": "service_percentile",
      "type": "LABELED_VALUE",
      "catalog": "SERVICE",
      "scopeId": 1,
      "scope": "Service",
      "valueColumnName": "value",
      "downsamplings": ["MINUTE", "HOUR", "DAY"]
    },
    {
      "name": "endpoint_response_time",
      "type": "HEATMAP",
      "catalog": "ENDPOINT",
      "scopeId": 3,
      "scope": "Endpoint",
      "valueColumnName": "dataset",
      "downsamplings": ["MINUTE", "HOUR", "DAY"]
    }
  ]
}
```

Implementation:

- Source of metric list is `ValueColumnMetadata.INSTANCE.getAllMetadata()`,
  the same map already exposed via `MetricsMetadataQueryService.listMetrics`.
- Entries with `Column.ValueDataType.NOT_VALUE` are **filtered out** —
  these are persisted-but-not-queryable internal columns (topology lines,
  service entries) per `Column.java`'s contract. They never appear in
  `/inspect/metrics`.
- Entries whose scope resolves to `Scope.All` are **filtered out** as well
  (`Scope.All` is deprecated and not routable through the inspect path).
- `type` is mapped from `Column.ValueDataType` exactly as
  `MetricsMetadataQueryService.typeOfMetrics` does today.
- `scope` is `Scope.Finder.valueOf(scopeId).name()`.
- `downsamplings` is derived once at startup by scanning
  `StorageModels.allModels()`, grouping by metric name, collecting
  `model.getDownsampling()` values into a `Set`. Pure metadata, no storage I/O.
- This endpoint is safe to poll; results change only when models are
  registered or removed (boot, runtime-rule add/delete).

### `GET /inspect/entities`

Lists entities that hold values for a metric within a time range, decoded
into MQE-ready form.

Query parameters:

| Name      | Required | Description                                                              |
|-----------|----------|--------------------------------------------------------------------------|
| `metric`  | yes      | Metric name (must resolve in `ValueColumnMetadata`).                     |
| `start`   | yes      | Time-range start. Same format as MQE `Duration.start`: `yyyy-MM-dd` (DAY), `yyyy-MM-dd HH` (HOUR), `yyyy-MM-dd HHmm` (MINUTE), `yyyy-MM-dd HHmmss` (SECOND). |
| `end`     | yes      | Time-range end. Format mirrors `start`.                                  |
| `step`    | yes      | One of `MINUTE`, `HOUR`, `DAY`. Must be one of the metric's `downsamplings`. |
| `limit`   | no       | Server-side cap. Default 300, hard-capped at 300.                        |

`coldStage` is intentionally not exposed. The inspect API targets the
operationally-active hot path; cold-stage scans bypass the same TTL window
the operator would be debugging within.

The `limit` is applied as `LIMIT N` at the storage layer — it bounds the
total rows scanned (N = 300 ≅ 10 buckets × 30 entities), not 300 entities.
Backends dedup on `entity_id` before returning.

`step` is parsed via `DownSampling.valueOf(step)` after upper-casing and
must appear in the metric's `supportedDownsamplings` (else 400). The
`(start, end)` pair is converted to time buckets through the same
`Duration.getStartTimeBucket()` / `getEndTimeBucket()` path MQE uses, so
range semantics match exactly.

Response (`200 OK`, `application/json`) — example: `service_cpm` over the
last 10 minutes, where service `payment` is registered under both `MESH` and
`GENERAL` layers:

```json
{
  "metric": "service_cpm",
  "scope": "Service",
  "step": "MINUTE",
  "start": "2026-05-10 1230",
  "end":   "2026-05-10 1240",
  "rows": [
    {
      "entityId": "cGF5bWVudA==.1",
      "decoded": { "serviceName": "payment", "isReal": true },
      "layer": "MESH",
      "mqeEntity": {
        "scope": "Service",
        "serviceName": "payment",
        "normal": true
      }
    },
    {
      "entityId": "cGF5bWVudA==.1",
      "decoded": { "serviceName": "payment", "isReal": true },
      "layer": "GENERAL",
      "mqeEntity": {
        "scope": "Service",
        "serviceName": "payment",
        "normal": true
      }
    },
    {
      "entityId": "Y2hlY2tvdXQ=.1",
      "decoded": { "serviceName": "checkout", "isReal": true },
      "layer": "GENERAL",
      "mqeEntity": {
        "scope": "Service",
        "serviceName": "checkout",
        "normal": true
      }
    },
    {
      "entityId": "bXlzcWw=.0",
      "decoded": { "serviceName": "mysql", "isReal": false },
      "layer": "VIRTUAL_DATABASE",
      "mqeEntity": {
        "scope": "Service",
        "serviceName": "mysql",
        "normal": false
      }
    }
  ]
}
```

An empty result is not an error — `{ "metric": …, "rows": [] }` simply
means no entity wrote a value in the requested window.

Multi-layer rule: one logical entity emits **one row per layer** it is
registered under. `payment` appears twice above because
`ServiceTraffic` has two rows (`payment`/`MESH` and `payment`/`GENERAL`),
and the inspect API does not collapse them — the operator may want to
investigate one layer at a time, and `Layer` is meaningful for downstream
calls such as `listInstances(serviceId)`. The `mqeEntity` block, however, is
identical across layer rows because MQE itself does not take a layer
parameter — including it lets the operator copy-paste without thinking.

ServiceInstance example (`service_instance_cpm`):

```json
{
  "rows": [
    {
      "entityId": "cGF5bWVudA==.1_cG9kLTAx",
      "decoded": {
        "serviceName": "payment",
        "isReal": true,
        "serviceInstanceName": "pod-01"
      },
      "layer": "GENERAL",
      "mqeEntity": {
        "scope": "ServiceInstance",
        "serviceName": "payment",
        "serviceInstanceName": "pod-01",
        "normal": true
      }
    }
  ]
}
```

ServiceRelation example (`service_relation_client_cpm`):

```json
{
  "rows": [
    {
      "entityId": "Y2hlY2tvdXQ=.1-cGF5bWVudA==.1",
      "decoded": {
        "source":      { "serviceName": "checkout", "isReal": true },
        "destination": { "serviceName": "payment",  "isReal": true }
      },
      "layer": "GENERAL",
      "mqeEntity": {
        "scope": "ServiceRelation",
        "serviceName": "checkout",
        "normal": true,
        "destServiceName": "payment",
        "destNormal": true
      }
    }
  ]
}
```

ServiceInstanceRelation example
(`service_instance_relation_client_cpm`, e.g., a `consumer` instance
calling a `provider` instance in the java-agent provider/consumer
showcase):

```json
{
  "rows": [
    {
      "entityId": "Y29uc3VtZXI=.1_cG9kLWE=-cHJvdmlkZXI=.1_cG9kLWI=",
      "decoded": {
        "source": {
          "serviceName": "consumer",
          "isReal": true,
          "serviceInstanceName": "pod-a"
        },
        "destination": {
          "serviceName": "provider",
          "isReal": true,
          "serviceInstanceName": "pod-b"
        }
      },
      "layer": "GENERAL",
      "mqeEntity": {
        "scope": "ServiceInstanceRelation",
        "serviceName": "consumer",
        "normal": true,
        "serviceInstanceName": "pod-a",
        "destServiceName": "provider",
        "destNormal": true,
        "destServiceInstanceName": "pod-b"
      }
    }
  ]
}
```

EndpointRelation example (`endpoint_relation_cpm`, where consumer's
`/order` calls provider's `/charge`):

```json
{
  "rows": [
    {
      "entityId": "Y29uc3VtZXI=.1-L29yZGVy-cHJvdmlkZXI=.1-L2NoYXJnZQ==",
      "decoded": {
        "source": {
          "serviceName": "consumer",
          "isReal": true,
          "endpointName": "/order"
        },
        "destination": {
          "serviceName": "provider",
          "isReal": true,
          "endpointName": "/charge"
        }
      },
      "layer": "GENERAL",
      "mqeEntity": {
        "scope": "EndpointRelation",
        "serviceName": "consumer",
        "normal": true,
        "endpointName": "/order",
        "destServiceName": "provider",
        "destNormal": true,
        "destEndpointName": "/charge"
      }
    }
  ]
}
```

Error responses:

| Status | Body                                                              | Cause                                                |
|--------|-------------------------------------------------------------------|------------------------------------------------------|
| 400    | `{"error":"unknown metric: foo"}`                                 | metric not in `ValueColumnMetadata`.                 |
| 400    | `{"error":"step DAY not supported by metric foo (MINUTE,HOUR)"}`  | metric not materialized at requested downsampling.   |
| 400    | `{"error":"metric type HEATMAP is not MQE-queryable; /inspect/entities only accepts REGULAR_VALUE and LABELED_VALUE"}` | metric type is `HEATMAP` (HISTOGRAM dataType). |
| 400    | `{"error":"metric type SAMPLED_RECORD is out of scope for /inspect/entities"}` | metric type is `SAMPLED_RECORD`.                     |
| 400    | `{"error":"process scope is out of scope"}`                       | scope is `Process` / `ProcessRelation`.              |
| 400    | `{"error":"limit must be between 1 and 300"}`                     | `limit` out of range.                                |

### Status API relocation

The `status-query-plugin` module currently registers its HTTP handlers
(`AlarmStatusQueryHandler`, `ClusterStatusQueryHandler`,
`TTLConfigQueryHandler`, `DebuggingHTTPHandler` for query trace) against
the public `HTTPHandlerRegister` exposed by the core / sharing-server
module — the same HTTP surface as GraphQL, on the public REST port
(`core.restPort`, default `12800`, or the sharing-server port if
configured). **skywalking-ui calls these endpoints on the public REST
port today**, so the binding cannot move; it can only be supplemented.

This SWIP replaces `status-query-plugin` with a new feature module
`status` under `server-admin/` that **dual-binds**:

1. **Always**: registers every handler on the public REST
   `HTTPHandlerRegister` resolved through `CoreModule` — the same surface
   `status-query-plugin` uses today, with the same URIs and payloads. UI
   compatibility is preserved.
2. **Conditionally**: if `AdminServerModule` is loaded
   (`SW_ADMIN_SERVER=default`), also registers every handler on the
   admin-server `HTTPHandlerRegister`. Operators that gateway-protect the
   admin port can drive status from the same private surface as
   `/inspect/*`, `/dsl-debugging/*`, `/runtime/rule/*`.

The module is gated by `SW_STATUS=default` and is **enabled by default**
(matching today's `status-query-plugin` posture so existing UI deployments
keep working without an opt-in step). It does **not** require
admin-server: when admin-server is off, status still binds on the public
REST port — that is the entire point of the dual-bind design.

**Routes hosted by the `status` feature module** — every existing route
preserved verbatim from `status-query-plugin`. No new endpoint is added
by this SWIP; clients that need to discover the OAP REST URL parse the
existing `GET /debugging/config/dump` response, which already publishes
the effective configuration including REST host and port.

| URI (all `GET`) | Purpose |
|---|---|
| `/status/cluster/nodes` | OAP cluster peer list. |
| `/status/alarm/rules` | Registered alarm rules. |
| `/status/alarm/{ruleId}` | Per-rule runtime status. |
| `/status/alarm/{ruleId}/{entityName}` | Per-rule + per-entity runtime status. |
| `/status/config/ttl` | Effective TTL configuration. |
| `/debugging/config/dump` | Effective configuration dump (also serves as the REST-URL discovery primitive for inspect clients). |
| `/debugging/query/mqe` | Run an MQE expression with debug tracing. |
| `/debugging/query/trace/queryBasicTraces` | Trace query brief, debug-traced. |
| `/debugging/query/trace/queryTrace` | Trace query detail, debug-traced. |
| `/debugging/query/zipkin/api/v2/traces` | Zipkin compat brief, debug-traced. |
| `/debugging/query/zipkin/api/v2/trace` | Zipkin compat detail, debug-traced. |
| `/debugging/query/topology/getGlobalTopology` | |
| `/debugging/query/topology/getServicesTopology` | |
| `/debugging/query/topology/getServiceInstanceTopology` | |
| `/debugging/query/topology/getEndpointDependencies` | |
| `/debugging/query/topology/getProcessTopology` | |
| `/debugging/query/log/queryLogs` | |

Every route is published on both binding surfaces (public REST + admin
when admin-server is enabled). URI paths, query parameters, and response
payloads do not change — only the host:port the routes bind on.

## Storage layer

A new abstract method on `IMetricsQueryDAO`:

```java
List<String> listEntityIdsInRange(String metricName,
                                  String valueColumnName,
                                  Duration duration,
                                  int limit) throws IOException;
```

Abstract on purpose so any 3rd party storage backend that implements
`IMetricsQueryDAO` MUST provide this override — a default body would
let a missing override slip through compilation and surface as silent
"no entities" or 500 the first time the inspect API hit that backend.

Returns distinct `entity_id`s, ordered by most recent timestamp
descending, capped at `limit`. Each backend must enforce that ordering
explicitly — without an explicit sort directive, scoring / index-internal
order can drop a hot entity that ingested late before the cap is reached.
Implementations:

- **BanyanDB** (`BanyanDBMetricsQueryDAO`):
  `MeasureQuery` against the resolved measure (downsampling chosen via
  `MetadataRegistry.formatName(metricName, downSampling)`), timestamp range
  filter, tag projection limited to `ENTITY_ID`, value-column field
  projection, **`setOrderBy(new AbstractQuery.OrderBy(Sort.DESC))`** for
  timestamp-DESC ordering, `.limit(limit)`. Client-side `LinkedHashSet`
  dedup preserves the server-side ordering.
- **Elasticsearch** (`MetricsQueryEsDAO`):
  range filter on `time_bucket`, **`search.sort(Metrics.TIME_BUCKET, Sort.Order.DESC)`**
  for timestamp-DESC ordering, `Search.size(limit)`. The downsampling-suffixed
  index name is resolved through the existing `IndexController` /
  `MetricsModelInstaller` path (the same name the existing
  `readMetricsValues` uses for the requested step). Client-side
  `LinkedHashSet` dedup on the returned hits.
- **JDBC** (`JDBCMetricsQueryDAO`):
  `getTablesForRead(metricName, startTimeBucket, endTimeBucket)` resolves
  the day-partitioned tables. Per table:
  `SELECT entity_id, MAX(time_bucket) AS latest_time_bucket FROM <table>
  WHERE table_column = ? AND time_bucket BETWEEN ? AND ? GROUP BY entity_id
  ORDER BY latest_time_bucket DESC LIMIT ?`. (PostgreSQL rejects
  `ORDER BY time_bucket` when it isn't in a `SELECT DISTINCT` list, so the
  GROUP BY shape is the portable one across H2 / MySQL / PostgreSQL.) Per-table
  results are merged into a `Map<entityId, maxTimeBucket>` with `Math::max`,
  then sorted by the value DESC and capped at the global `limit` — so a
  multi-day range cannot fill the result with stale entities from older
  partitions before newer partitions are queried.

This shape mirrors the existing
`readLabeledMetricsValuesWithoutEntity(metricName, valueColumnName, labels,
duration)` (limit hard-coded to 10, projects value as well) — we reuse the
range-scan and downsampling-resolution scaffolding from those
implementations.

## Decode & enrich

Per row, the handler:

1. Calls `Scope.Finder.valueOf(scopeId)` to resolve the scope from the
   metric's metadata.
2. Switches on the scope to call the correct
   `IDManager.{ServiceID,ServiceInstanceID,EndpointID}.analysisId(...)` /
   `analysisRelationId(...)`.
3. For Service-bearing rows, reads `MetadataQueryService.getService(serviceId)`
   from the in-memory cache and emits one row per layer in
   `service.getLayers()`.
4. Builds the `mqeEntity` block by inverting the decode — same fields
   `Entity.buildId()` would consume to reconstruct the same `entity_id`.

If a service is missing from the metadata cache (e.g., it was just deleted
but a metric row still exists in the storage TTL window), the row is still
emitted with `layer: null` and the decoded names; the operator can still
call MQE.

## Module layout

Two new feature modules under the existing admin-server bundle:

```
oap-server/server-admin/inspect/
├── pom.xml
├── src/main/java/org/apache/skywalking/oap/server/admin/inspect/
│   ├── InspectModule.java               # ModuleDefine
│   ├── InspectModuleProvider.java       # ModuleProvider
│   ├── InspectModuleConfig.java
│   ├── handler/InspectRestHandler.java  # Armeria handler
│   ├── decoder/EntityDecoder.java       # scope → IDManager dispatch
│   └── response/{MetricRow, EntityRow, MqeEntity}.java
└── src/main/resources/META-INF/services/
    └── org.apache.skywalking.oap.server.library.module.ModuleDefine

oap-server/server-admin/status/        ◄── relocated from
                                           server-query-plugin/status-query-plugin
├── pom.xml
├── src/main/java/org/apache/skywalking/oap/server/admin/status/
│   ├── StatusModule.java
│   ├── StatusModuleProvider.java
│   ├── StatusModuleConfig.java
│   ├── handler/
│   │   ├── ClusterStatusHandler.java    # moved
│   │   ├── AlarmStatusHandler.java      # moved
│   │   ├── TTLConfigHandler.java        # moved
│   │   └── DebuggingHTTPHandler.java    # moved (query trace)
│   └── service/{AlarmStatusQueryService, …}.java   # moved
└── src/main/resources/META-INF/services/
    └── org.apache.skywalking.oap.server.library.module.ModuleDefine
```

`requiredModules()`:

- `inspect`: `CoreModule.NAME`, `StorageModule.NAME`, `AdminServerModule.NAME`.
  Inspect is admin-only; it has no public-REST mirror and therefore
  **does** require admin-server.
- `status`: `CoreModule.NAME` only. **`AdminServerModule.NAME` is
  consulted reflectively** via `moduleManager.has(...)` for the
  conditional second handler registration. Declaring it as required
  would force every status-using deployment to also enable admin-server,
  which contradicts the "UI keeps working with admin off" goal.

Handler registration in `status`'s `start()`:

```java
// Always — public REST surface, where the UI calls today.
HTTPHandlerRegister publicRegister = manager.find(CoreModule.NAME)
    .provider().getService(HTTPHandlerRegister.class);
registerAll(publicRegister);

// Additionally, when admin-server is enabled.
if (manager.has(AdminServerModule.NAME)) {
    HTTPHandlerRegister adminRegister = manager.find(AdminServerModule.NAME)
        .provider().getService(HTTPHandlerRegister.class);
    registerAll(adminRegister);
}
```

Inspect's `start()` registers only on the admin-server register
(mirroring `dsl-debugging` and `runtime-rule`). The old
`status-query-plugin` module is removed.

### Maven module / pom changes

One module dropped, two added, three existing poms edited. Net: same number of distribution jars, just relocated under `server-admin`.

| Pom | Edit |
|-----|------|
| `oap-server/server-admin/pom.xml` | `<modules>` adds `inspect` and `status` (already lists `admin-server`, `runtime-rule`, `dsl-debugging`). |
| `oap-server/server-query-plugin/pom.xml` | `<modules>` drops `status-query-plugin`. |
| `oap-server/server-starter/pom.xml` | Removes `<dependency>status-query-plugin</dependency>`; adds `<dependency>status</dependency>` and `<dependency>inspect</dependency>` next to the existing `admin-server` / `runtime-rule` / `dsl-debugging` block. |
| `oap-server/server-admin/inspect/pom.xml` | **New.** `parent=server-admin`, `packaging=jar`, depends on `server-core`, `admin-server`, `library-server`. |
| `oap-server/server-admin/status/pom.xml` | **New.** Same parent / packaging; carries the deps the relocated handlers already used (notably `library-server` for the Armeria handler glue). |
| `oap-server/server-query-plugin/status-query-plugin/` | **Directory deleted** along with the pom. Sources move under `oap-server/server-admin/status/src/main/java/...` (package rename `org.apache.skywalking.oap.query.debug` → `org.apache.skywalking.oap.server.admin.status`). |

## Cost & safety

- `/inspect/metrics` is in-process metadata only — O(N) over registered
  metrics, no I/O.
- `/inspect/entities` issues exactly one storage call per request bounded by
  `LIMIT 300`. The 300 ceiling is enforced server-side and cannot be raised
  via parameter. This is the same shape as today's
  `readLabeledMetricsValuesWithoutEntity` (limit 10) just with a higher cap;
  no new query class is introduced.
- The endpoint is bound only on the admin port (default 17128), which is
  not the agent or query port; existing operator-network controls apply.
- No caching layer in v1. If poll-storms become a problem we can add a
  short-TTL in-process cache keyed on `(metric,start,end,step,limit)`, but
  given operators are humans and the cap is 300 rows, premature.

## Compatibility

The dual-bind design keeps the Status API backwards compatible for the
UI and for any scripted consumer of `http://oap:12800/status/...`.

| Change | Impact |
|--------|--------|
| `status-query-plugin` module is removed; replaced by the new `status` feature module under `server-admin/`. The selector renames from the QUERY-plugin form (`SW_QUERY=…,status-query-plugin`) to a top-level `SW_STATUS=default` (on by default). | **Mild.** Custom `application.yml` overrides that referenced `status-query-plugin` need to be repointed; default deployments do not need to change anything. URIs, payloads, and the public REST binding are preserved. |
| Status API docs move from `docs/en/status/` → `docs/en/setup/backend/admin-api/status.md` (new consolidated page). The `docs/en/status/` directory is removed and `docs/menu.yml` updated; `docs/en/status/status_apis.md` becomes a redirect stub for one minor cycle. | **Doc move only.** No URI or payload churn. |
| Additive: second handler binding on the admin port (when admin-server is enabled), `/inspect/metrics`, `/inspect/entities`, new `IMetricsQueryDAO.listEntityIdsInRange` method, new modules `inspect` and `status`. | **No impact** on existing deployments. UI continues to call status on the public REST port; admin-port consumers are opt-in via `SW_ADMIN_SERVER=default`. |

The admin-port binding for status is intentionally **additional**, not a
replacement, so that admin-server may stay off (the default) without
breaking the UI. A future SWIP can drop the public-REST binding once
skywalking-ui has migrated to the admin-port path; until then, both
surfaces co-exist.

## Test plan

The inspect surface is e2e-validated against the existing **java-agent
e2e** (`test/e2e-v2/cases/simple/...`) — that suite already runs across
every supported storage backend (BanyanDB, Elasticsearch, JDBC), so
piggy-backing keeps the matrix free.

The agent showcase boots a `provider` and a `consumer` Java service; this
gives us live metrics across every scope inspect supports. The flow uses
`curl` + `jq` directly (no swctl wiring) so the test is portable across
the existing e2e runners:

1. Wait for the showcase to ingest at least one minute window.
2. (one-time) `curl http://oap:17128/debugging/config/dump` and parse the
   response to learn the OAP REST host:port the harness will fire MQE
   against. Existing endpoint; no new discovery primitive needed.
3. `curl http://oap:17128/inspect/metrics?regex=service_cpm` →
   assert `service_cpm` appears with `scope=Service`, `type=REGULAR_VALUE`,
   `downsamplings` includes `MINUTE`. Repeat for `instance_jvm_memory_*`
   (ServiceInstance scope) and `instance_jvm_class_loaded_count`
   (ServiceInstance, REGULAR_VALUE).
4. `curl http://oap:17128/inspect/entities?metric=service_cpm&start=…&end=…&step=MINUTE`
   → expect rows for both `provider` and `consumer`. `jq` out one row's
   `mqeEntity` block.
5. `POST` MQE expression `service_cpm` against `<discovered MQE URL>`
   with the extracted `mqeEntity` → assert non-empty time-series result.
6. Repeat (4)–(5) with `instance_jvm_memory_heap_used` (ServiceInstance
   scope) — confirm both provider and consumer instances surface, and
   MQE for the picked instance returns data.
7. Relation coverage: `/inspect/entities?metric=service_relation_client_cpm`
   → assert a `consumer→provider` row, then MQE
   `service_relation_client_cpm` against its `mqeEntity` returns data.
   Same pattern for `service_instance_relation_client_cpm` and
   `endpoint_relation_cpm` (when endpoint relations are present in the
   showcase).
8. Negative cases: HEATMAP metric (`endpoint_response_time`) → expect
   400 from `/inspect/entities`; `step=DAY` against a metric registered
   only at MINUTE → expect 400 with the supported-downsamplings list.

Module-level unit tests live alongside each new source: `EntityDecoder`
for every supported scope (covering `isReal=true` and `isReal=false`
service IDs, multi-layer service merge, missing-from-cache fallback),
the `/inspect/metrics` filter (NOT_VALUE / Scope.All exclusion),
and the new DAO method per backend mocked at the storage interface.

## Deliverables

| Item | Location |
|------|----------|
| Module sources | `oap-server/server-admin/inspect/`, `oap-server/server-admin/status/` |
| Storage DAO method | `IMetricsQueryDAO.listEntityIdsInRange` + 3 implementations |
| Operator doc | `docs/en/setup/backend/admin-api/inspect.md` (new), `admin-api/status.md` (new, consolidates the relocated `docs/en/status/*` pages) |
| Doc index updates | `docs/en/setup/backend/admin-api/readme.md` (add inspect + status sections), `docs/menu.yml` (drop `docs/en/status/`, add `admin-api/{inspect,status}.md`) |
| Redirect stub | `docs/en/status/status_apis.md` → one-cycle redirect pointing to `admin-api/status.md` |
| Changelog | `docs/en/changes/changes-X.X.X.md` — entries for: new `/inspect/*`, status-plugin → status-feature-module relocation + selector rename, new `IMetricsQueryDAO.listEntityIdsInRange` |
| E2E updates | `test/e2e-v2/cases/simple/...` — add an inspect verification step to the existing java-agent showcase case; runs unchanged across BanyanDB / ES / JDBC matrix |
| Unit tests | per-module under each new source root |

## Implementation order

The status-relocation work and the inspect-API work share zero code paths
and can be staged independently:

1. **Phase 1 — status relocation.** Create `server-admin/status` module,
   move sources, set up dual-bind, delete `status-query-plugin`. UI and
   existing operator scripts continue to work; URI paths and payloads
   are unchanged.
2. **Phase 2 — inspect.** Add `IMetricsQueryDAO.listEntityIdsInRange`
   across the three backends, then `server-admin/inspect` with handlers
   and decoder. Independent of Phase 1 — inspect clients that need to
   discover the OAP REST URL parse `/debugging/config/dump`, an
   already-existing endpoint preserved by Phase 1.

Each phase is a self-contained PR.

## Future work

- Inspect-Records (`/inspect/records`) for `SAMPLED_RECORD` metrics, backed
  by `IRecordsQueryDAO`.
- Inspect-Process for `Process` / `ProcessRelation`, contingent on a
  decoder for `ProcessID` (likely a `ProcessTraffic` join).
- Optional `?attribute=k=v` filter pushdown to leverage the same attribute
  conditions `TopNCondition` accepts.
