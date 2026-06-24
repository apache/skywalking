# Inspect API

The Inspect API lives on `admin-server` and exposes three endpoints that
let operators answer three questions without writing exploratory MQE:

1. *Which metrics has OAP registered, and at what downsampling?* — `GET /inspect/metrics`
2. *For metric `X` in time range `T`, which entities currently hold values?* — `GET /inspect/entities`
3. *For metric `X` + entity `E`, what are the values?* — `POST /inspect/values`

For a locally-defined metric, the output of (2) carries a ready-to-paste
`mqeEntity` payload, so the follow-up MQE call against the public GraphQL
`execExpression` mutation is copy-paste from the inspect response. A metric
persisted by **another OAP** that this node does not define can also be
inspected with caller-supplied metadata — both its entities (2) and its values
(3) — see [Foreign metrics](#foreign-metrics-not-defined-on-this-oap).

## Enabling

Enabled by default. Both `SW_ADMIN_SERVER=default` and `SW_INSPECT=default`
are on out of the box; no opt-in required. To disable:

```bash
export SW_INSPECT=                          # disable inspect only
export SW_ADMIN_SERVER=                     # close the admin host entirely
```

If `SW_INSPECT=default` is explicitly kept while `SW_ADMIN_SERVER=` is set
empty, OAP fails fast at startup with a `ModuleNotFoundException: admin-server`
— same gate that protects every other admin feature module.

## Security

Inspect routes bind on the **admin port** only (default `17128`). They do
**not** mirror onto the public REST port — the entity-enumeration scan in
`/inspect/entities` competes with user-facing query traffic for storage and
is intentionally gated behind the private admin surface.

The general admin-port security guidance applies: gateway-protect with an
IP allow-list and an authenticating reverse proxy, bind only to a private
interface, never expose to the public internet. See the
[admin-server security notice](readme.md#security-notice).

## Endpoints

### `GET /inspect/metrics`

Lists registered metrics with type, scope, catalog, value-column name, and
the downsamplings each metric is materialised at.

Internal-only entries are filtered:

* `Column.ValueDataType.NOT_VALUE` columns (topology lines, service
  entries — persisted but not queryable) are skipped silently.
* `Scope.All`-scoped entries (deprecated, not routable through MQE) are
  skipped silently.

Query parameters:

| Name | Required | Description |
|------|----------|-------------|
| `regex` | no | Java regex over metric name. Default `.*`. |
| `type` | no | Filter by `REGULAR_VALUE` / `LABELED_VALUE` / `HEATMAP` / `SAMPLED_RECORD`. Repeatable. |
| `catalog` | no | Filter by catalog (`SERVICE`, `SERVICE_INSTANCE`, `ENDPOINT`, `SERVICE_RELATION`, `SERVICE_INSTANCE_RELATION`, `ENDPOINT_RELATION`). Repeatable. |
| `mqeQueryable` | no | If `true`, return only metrics that `/inspect/entities` accepts (`REGULAR_VALUE`, `LABELED_VALUE`). Default `false`. |

Example:

```bash
curl 'http://oap-admin:17128/inspect/metrics?regex=service_cpm'
```

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
    }
  ]
}
```

### `GET /inspect/entities`

For a metric + time range + step, returns the entities holding values. For a
metric this OAP defines locally, each row is decoded into a human-readable
shape and an MQE-ready `mqeEntity` payload. A metric persisted by **another
OAP** that this node does not define can also be inspected by additionally
supplying `valueColumn` + `valueType` — see
[Foreign metrics](#foreign-metrics-not-defined-on-this-oap).

Restricted to `REGULAR_VALUE` and `LABELED_VALUE` metrics. The non-MQE
metric types (`HEATMAP` / `SAMPLED_RECORD`) and the out-of-scope scopes
(`Process` / `ProcessRelation`) are rejected with `400`.

Query parameters:

| Name | Required | Description |
|------|----------|-------------|
| `metric` | yes | Metric name. If unknown to this OAP's local registry, also supply `valueColumn` + `valueType` (see [Foreign metrics](#foreign-metrics-not-defined-on-this-oap)). |
| `start` | yes | Time-range start. Same format as MQE `Duration.start`: `yyyy-MM-dd` (DAY), `yyyy-MM-dd HH` (HOUR), `yyyy-MM-dd HHmm` (MINUTE), `yyyy-MM-dd HHmmss` (SECOND). Note `HHmm` is no-separator — use `1230`, not `12:30`. |
| `end` | yes | Time-range end. Format mirrors `start`. |
| `step` | yes | One of `MINUTE` / `HOUR` / `DAY`. For a locally-defined metric, must be one of the metric's `downsamplings`; for a foreign metric the requested step is trusted as-is. |
| `limit` | no | Server-side cap. Default 300, hard-capped at 300. |
| `valueColumn` | conditional | **Required when `metric` is not defined on this OAP.** The metric's value column (post-override physical name, e.g. `value`, `value_`, `double_value`, `datatable_value`, `dataset`). Ignored for a locally-defined metric. |
| `valueType` | conditional | **Required when `metric` is not defined on this OAP.** One of `LONG` / `INT` / `DOUBLE` / `LABELED`. Ignored for a locally-defined metric. |

The `limit` is applied as `LIMIT N` at the storage layer — it bounds the
total rows scanned (300 ≈ 10 buckets × 30 entities), not 300 distinct
entities. Backends dedup on `entity_id` before returning.

Multi-layer services emit **one row per layer**. The `mqeEntity` block is
identical across layer rows (MQE itself does not take a layer parameter);
the duplication exists so the operator can pick any single layer as the
investigation entry point. An empty result is `{ "rows": [] }` — not an
error.

Example — `service_cpm` over the last 10 minutes, with `payment` registered
under both `MESH` and `GENERAL`:

```bash
curl 'http://oap-admin:17128/inspect/entities?metric=service_cpm&start=2026-05-10%201230&end=2026-05-10%201240&step=MINUTE'
```

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
      "layer": "GENERAL",
      "mqeEntity": {
        "scope": "Service",
        "serviceName": "payment",
        "normal": true
      }
    },
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

### Foreign metrics (not defined on this OAP)

A metric persisted by **another OAP** — a different OAL/MAL/runtime-rule set —
is absent from this node's local registry, so its value column, type, and scope
cannot be recovered from the metric name alone (there is no OAL/MAL text here to
read). Supply `valueColumn` + `valueType` on the request and the backend resolves
the physical index/table/group from its own running configuration (the
deterministic metric → storage mapping that merging has used for years), with
**no storage schema / table-metadata read**:

* **ES** — the merged `metrics-all` index, filtered by the `metric_table`
  discriminator. Not supported under `logicSharding=true`, where the physical
  index is derived from the metric's stream class (returns `500`).
* **JDBC** — probes the node's aggregation-function metric tables
  (`metrics_<fn>` / `meter_<fn>`) by the `table_name` discriminator.
* **BanyanDB** — synthesizes a read-only measure schema from the deterministic
  measure/group mapping.

Because the scope is unknown, the response degrades gracefully:

* `scope` is `null` (the structural kind is per-row in `decoded`).
* `entity_id` is decoded **structurally**: a single entity yields `serviceName`
  (plus a generic `name` leaf for a 2nd-level instance/endpoint — the two are
  byte-identical and not distinguishable without the scope); a relation yields a
  `source` / `destination` pair.
* **No `mqeEntity`** is produced — MQE needs the exact scope, and a foreign
  metric is not MQE-queryable on this node anyway.

Existence is decided by the data probe itself, so an **empty result means "no
rows in range", not "metric absent"**. Nothing is validated against metadata up
front: a wrong `valueColumn` / `valueType` surfaces as a storage error (`500`)
or an empty result.

Tip: query the writing OAP's own `/inspect/metrics?regex=<metric>` to read the
exact `valueColumnName`, then pass that as `valueColumn`.

Example — `meter_custom_x`, defined on another OAP, inspected here:

```bash
curl 'http://oap-admin:17128/inspect/entities?metric=meter_custom_x&valueColumn=value&valueType=LONG&start=2026-05-10%201230&end=2026-05-10%201240&step=MINUTE'
```

```json
{
  "metric": "meter_custom_x",
  "scope": null,
  "step": "MINUTE",
  "start": "2026-05-10 1230",
  "end":   "2026-05-10 1240",
  "rows": [
    {
      "entityId": "cGF5bWVudA==.1",
      "decoded": { "serviceName": "payment", "isReal": true },
      "layer": "GENERAL"
    }
  ]
}
```

### `POST /inspect/values`

Reads the **values** of a metric this OAP does not define locally, by running the
real MQE engine over caller-supplied metadata. Where `GET /inspect/entities`
answers *which entities hold values*, this answers *what those values are* — the
native MQE `ExpressionResult` (the same shape the UI renders for a catalog metric),
for a metric that is otherwise foreign to this node.

Because it trusts caller-supplied metadata and forces a read of a metric this OAP
cannot validate, it is **admin-only** (it never mirrors onto the public REST /
GraphQL surface) and takes a request **body**: an MQE expression plus one metadata
entry per foreign metric the expression references.

Request body (`application/json`):

| Field | Required | Description |
|-------|----------|-------------|
| `expression` | yes | The MQE expression to evaluate — a single foreign metric name, or an expression combining foreign and/or catalog metrics. |
| `entity` | yes | The MQE query entity; its `scope` binds every foreign metric. e.g. `{ "scope": "Service", "serviceName": "X", "normal": true }` (use `serviceInstanceName` / `endpointName` for the deeper scopes). |
| `start` / `end` | yes | Time range, same format as [`/inspect/entities`](#get-inspectentities). |
| `step` | yes | One of `MINUTE` / `HOUR` / `DAY`. |
| `foreignMetrics` | yes | One entry per metric in `expression` that this OAP does not define: `{ "name": "...", "valueColumn": "value", "valueType": "LONG" }`. `valueColumn` is the post-override physical column; `valueType` is one of `LONG` / `INT` / `DOUBLE` / `LABELED`. A locally-defined metric must **not** be listed here (query it via the public GraphQL `execExpression`). |

The metadata is overlaid **provide-if-absent** (the local catalog always wins) onto
the same registries the engine already consults, so a foreign metric looks registered
for the duration of the request: `ValueColumnMetadata` resolves its value column / type
/ scope, and the storage location registries resolve its index / table / measure exactly
as described in [Foreign metrics](#foreign-metrics-not-defined-on-this-oap). The overlay
is request-scoped to the calling thread and removed when the read completes; the public
query path never sets it.

Only scalar (`LONG` / `INT` / `DOUBLE`) and labeled (best-effort) value series are
supported. An expression that resolves to `top_n` / records / heatmaps needs a local
model and surfaces as an error. Under ES `logicSharding=true` a foreign value read is
unsupported (the physical index derives from the metric's stream class), returning `500`.

Example — read the value series of `meter_custom_x`, defined on another OAP:

```bash
curl -X POST 'http://oap-admin:17128/inspect/values' \
  -H 'Content-Type: application/json' \
  -d '{
    "expression": "meter_custom_x",
    "entity": { "scope": "Service", "serviceName": "payment", "normal": true },
    "start": "2026-05-10 1230", "end": "2026-05-10 1240", "step": "MINUTE",
    "foreignMetrics": [
      { "name": "meter_custom_x", "valueColumn": "value", "valueType": "LONG" }
    ]
  }'
```

```json
{
  "type": "TIME_SERIES_VALUES",
  "results": [
    {
      "metric": { "labels": [] },
      "values": [
        { "id": "1778416200000", "value": "42" },
        { "id": "1778416260000", "value": "42" }
      ]
    }
  ],
  "error": null
}
```

## Discovering the OAP REST URL for the MQE follow-up

To keep the surface minimal, the inspect API does not introduce a separate
discovery endpoint. Clients that need to learn which host:port serves the
public GraphQL / MQE surface parse the response of the existing
[`/debugging/config/dump`](status.md#debuggingconfigdump) — also hosted by
the relocated status feature module — for `core.restHost` / `core.restPort`
(or the sharing-server overrides if configured). One curl + `jq` once at
session start is enough.

## Errors

| Status | Body | Cause |
|--------|------|-------|
| 400 | `{"error":"metric unknown locally: foo — provide valueColumn and valueType to inspect a metric persisted by another OAP"}` | Metric not defined on this OAP, and the `valueColumn` / `valueType` pair was not supplied. See [Foreign metrics](#foreign-metrics-not-defined-on-this-oap). |
| 400 | `{"error":"valueType must be one of LONG / INT / DOUBLE / LABELED (got X)"}` | Invalid `valueType` on the foreign-metric path. |
| 400 | `{"error":"step DAY not supported by metric foo (MINUTE,HOUR)"}` | Metric not materialised at the requested downsampling (locally-defined metric only). |
| 400 | `{"error":"metric type HEATMAP is not MQE-queryable; /inspect/entities only accepts REGULAR_VALUE and LABELED_VALUE"}` | Metric is `HEATMAP` (`HISTOGRAM` `dataType`). |
| 400 | `{"error":"metric type SAMPLED_RECORD is out of scope for /inspect/entities"}` | Metric is `SAMPLED_RECORD`. |
| 400 | `{"error":"process scope is out of scope"}` | Scope is `Process` / `ProcessRelation`. |
| 400 | `{"error":"limit must be between 1 and 300"}` | `limit` out of range. |
| 400 | `{"error":"foreignMetrics is required; a locally-defined metric should be queried via the public GraphQL execExpression"}` | `POST /inspect/values` body had no `foreignMetrics`. |
| 400 | `{"error":"metric foo is defined locally; query it via the GraphQL execExpression and drop it from foreignMetrics"}` | A `foreignMetrics` entry names a metric this OAP already defines. |
| 400 | `{"error":"valueColumn is invalid: …"}` | A `foreignMetrics` `valueColumn` is not a bare identifier. |
| 400 | `{"error":"<MQE error>"}` | `POST /inspect/values` expression resolved to an unsupported shape (e.g. `top_n` / record / heatmap) for a foreign metric. |
| 500 | `{"error":"<storage error>"}` | A wrong `valueColumn` / `valueType`, or ES `logicSharding=true`, surfaced at the storage layer during a value read. |

## Limits

* Per-call cap is **300 rows scanned** at the storage layer, not relaxable
  via parameter. The 300 envelope tracks 10 buckets × 30 entities.
* No caching layer in v1. The endpoints are intended for human-driven
  exploration, not high-frequency polling.
* `Process` and `ProcessRelation` scopes are skipped because `ProcessID` is
  a SHA-256 hash with no decoder; readable rendering would require a
  `ProcessTraffic` join, deferred to a future revision.
