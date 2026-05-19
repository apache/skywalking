# Inspect API

The Inspect API lives on `admin-server` and exposes two browse endpoints that
let operators answer two questions without writing exploratory MQE:

1. *Which metrics has OAP registered, and at what downsampling?*
2. *For metric `X` in time range `T`, which entities currently hold values?*

The output of (2) carries a ready-to-paste `mqeEntity` payload, so the
follow-up MQE call against the public GraphQL `execExpression` mutation is
copy-paste from the inspect response.

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

For a metric + time range + step, returns the entities holding values, each
decoded into a human-readable shape and an MQE-ready `mqeEntity` payload.

Restricted to `REGULAR_VALUE` and `LABELED_VALUE` metrics. The non-MQE
metric types (`HEATMAP` / `SAMPLED_RECORD`) and the out-of-scope scopes
(`Process` / `ProcessRelation`) are rejected with `400`.

Query parameters:

| Name | Required | Description |
|------|----------|-------------|
| `metric` | yes | Metric name. Must resolve in `ValueColumnMetadata`. |
| `start` | yes | Time-range start. Same format as MQE `Duration.start`: `yyyy-MM-dd` (DAY), `yyyy-MM-dd HH` (HOUR), `yyyy-MM-dd HHmm` (MINUTE), `yyyy-MM-dd HHmmss` (SECOND). Note `HHmm` is no-separator — use `1230`, not `12:30`. |
| `end` | yes | Time-range end. Format mirrors `start`. |
| `step` | yes | One of `MINUTE` / `HOUR` / `DAY`. Must be one of the metric's `downsamplings`. |
| `limit` | no | Server-side cap. Default 300, hard-capped at 300. |

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
| 400 | `{"error":"unknown metric: foo"}` | Metric not in `ValueColumnMetadata`. |
| 400 | `{"error":"step DAY not supported by metric foo (MINUTE,HOUR)"}` | Metric not materialised at the requested downsampling. |
| 400 | `{"error":"metric type HEATMAP is not MQE-queryable; /inspect/entities only accepts REGULAR_VALUE and LABELED_VALUE"}` | Metric is `HEATMAP` (`HISTOGRAM` `dataType`). |
| 400 | `{"error":"metric type SAMPLED_RECORD is out of scope for /inspect/entities"}` | Metric is `SAMPLED_RECORD`. |
| 400 | `{"error":"process scope is out of scope"}` | Scope is `Process` / `ProcessRelation`. |
| 400 | `{"error":"limit must be between 1 and 300"}` | `limit` out of range. |

## Limits

* Per-call cap is **300 rows scanned** at the storage layer, not relaxable
  via parameter. The 300 envelope tracks 10 buckets × 30 entities.
* No caching layer in v1. The endpoints are intended for human-driven
  exploration, not high-frequency polling.
* `Process` and `ProcessRelation` scopes are skipped because `ProcessID` is
  a SHA-256 hash with no decoder; readable rendering would require a
  `ProcessTraffic` join, deferred to a future revision.
