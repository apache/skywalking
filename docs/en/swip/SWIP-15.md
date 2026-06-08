# SWIP-15 Redesign BanyanDB Self-Observability around the Cluster / Node / Group Model

## Motivation

[Apache SkyWalking BanyanDB](https://skywalking.apache.org/docs/skywalking-banyandb/next/readme/) is
the native storage for SkyWalking. SkyWalking already ships a "BanyanDB self-observability" feature
(`Layer: BANYANDB`, `otel-rules/banyandb/*`), but that feature was designed for a **single-node**
BanyanDB and for the layout constraints of the legacy booster UI. It no longer reflects how BanyanDB
is built or operated, and it no longer reflects how SkyWalking renders dashboards.

Three things changed underneath it:

1. **BanyanDB became a clustered, role- and tier-aware database.** A production deployment is one
   *cluster* made of many *nodes*, each with a **role** (`liaison` front door vs. `data` backend) and,
   for data nodes, a storage **tier** (`hot` / `warm` / `cold`). Different roles expose **different
   metrics** — a liaison node has gRPC ingestion, a write queue (wqueue) and the tier-2 publish
   pipeline; a data node has on-disk storage, inverted indexes and the subscribe queue. Data is
   organized into **groups** (`measure-default`, `stream-default`, …).

2. **BanyanDB reorganized its observability around the FODC proxy.** In a cluster, the
   [First Occurrence Data Collection (FODC) proxy](https://skywalking.apache.org/docs/skywalking-banyandb/next/operation/fodc/overview/)
   aggregates every node's Prometheus metrics and re-exposes them on a **single** `/metrics` endpoint,
   stamping each sample with per-node identity labels (`node_role`, `pod_name`, `container_name`,
   `node_type`). The upstream catalog and Grafana dashboards were rebuilt around this scheme as two
   complementary boards: a **Nodes** board (per-node health, aggregated by `pod_name`) and a
   **Workload** board (throughput/latency, aggregated by `group`).

3. **SkyWalking replaced the bundled booster UI with the Horizon UI.** The OAP backend no longer ships
   dashboard JSON (dropped in #13877); BanyanDB has not yet been ported to Horizon UI at all. Horizon
   UI is config-driven, has a real **Service → Instance → Endpoint** hierarchy, surfaces per-instance
   attributes, and can hide panels that have no data — and, with a small enhancement, can drive panel
   visibility from instance **attributes** (role / tier).

The current feature does none of this. It models **each node as its own `Service`**
(`service(['host_name'], Layer.BANYANDB)`), so a cluster appears as a pile of unrelated services; it
never models the cluster, the node role, the tier, or the group; and it still references metrics that
BanyanDB removed (an `etcd`-era operation rate, a Prometheus `up`-derived "active instances", and the
pre-refactor `queue_sub_total_msg_sent_err` family).

**This SWIP proposes to discard that model and rebuild BanyanDB self-observability around the cluster /
node / group reality**, matching the upstream FODC-proxy metric catalog, and to design the Horizon UI
side — a net-new BanyanDB layer dashboard whose node view **adapts to the selected node's role and
tier** — including the one Horizon UI enhancement that makes attribute-driven dashboards possible.

### Goals

- Model a BanyanDB **cluster** as a single SkyWalking `Service`.
- Model each **node** as a `ServiceInstance`, carrying its **role** and **tier** as instance
  attributes, so the UI can show "what this node is".
- Model each **group** as an `Endpoint` of the cluster.
- Mirror the upstream FODC-proxy metric catalog faithfully (the two-dashboard split becomes the
  Instance and Endpoint views).
- Make the **node dashboard dynamic** — a liaison node shows ingestion/queue/publish panels, a data
  node shows storage/index/subscribe panels, and the tier refines the data view — first via the
  data-presence mechanism that already exists, then via a proposed attribute predicate.

### Non-goals

- No change to OAP core, the MAL engine, the OTel receiver, or the `Layer.BANYANDB` registration —
  every primitive this design needs already ships and is precedented (see
  [Feasibility](#feasibility-and-precedent)).
- No FODC on-demand profiling / heap-dump / topology integration. This SWIP is about the **metrics**
  surface (FODC `/metrics`). The FODC `/cluster/topology`, `/cluster/lifecycle` and `/diagnostics`
  APIs are noted as [future work](#future-work).
- Only the **FODC-proxy** scrape path is in scope. Direct per-pod scraping of `:2121` is intentionally
  out of scope (see [Compatibility](#compatibility)).

## Architecture Graph

```
 BanyanDB cluster                         SkyWalking OAP                         Horizon UI
 ────────────────                         ─────────────                         ──────────
 ┌─ liaison node ─┐  FODC agent ┐                                       BANYANDB layer
 │  :2121 /metrics │ (sidecar)  │                                       ┌───────────────────────┐
 └────────────────┘            │                                       │ Root: cluster list    │
 ┌─ data hot ─────┐  FODC agent ├─► FODC proxy ──► OTel Collector ──►  │ Service: cluster KPIs │
 │  :2121 /metrics │ (sidecar)  │   :17913          (prometheus recv,   │ Instance: node, panels│
 └────────────────┘            │   /metrics         adds `cluster`      │   adapt to role/tier  │
 ┌─ data warm ────┐  FODC agent │   single target,  label) ──OTLP──►    │ Endpoint: group       │
 │  :2121 /metrics │ (sidecar)  │   per-node labels      │              └───────────────────────┘
 └────────────────┘            ┘   node_role/pod_name/   │                        ▲
 ┌─ data cold ────┐                container_name/        ▼                        │ MQE over
 │  :2121 /metrics │                node_type     receiver-otel ──► MAL            │ GraphQL
 └────────────────┘                              otel-rules/banyandb/*  ───────────┘ execExpression
                                                  ├ banyandb-service.yaml   → Service  (cluster)
                                                  ├ banyandb-instance.yaml  → Instance (node + attrs)
                                                  └ banyandb-endpoint.yaml  → Endpoint (group)
                                                          │
                                                          ▼
                                                  metrics storage (Layer: BANYANDB)
```

The only new label the pipeline needs is **`cluster`**; every other label this design consumes
(`node_role`, `pod_name`, `container_name`, `node_type`, `group`, `service`, `method`, `operation`, …)
is already stamped onto each sample by the FODC proxy. `cluster` is injected once, as a static label on
the collector scrape job — exactly how every other SkyWalking OTel-monitored component sets its service
identity.

## Proposed Changes

### 1. Entity model

| SkyWalking entity            | BanyanDB concept                              | Identity source (label)                 |
| ---------------------------- | --------------------------------------------- | --------------------------------------- |
| `Service` (Layer `BANYANDB`) | one BanyanDB **cluster**                      | `cluster` (injected by the collector)   |
| `ServiceInstance`            | one **node**                                  | `pod_name` (e.g. `banyandb-data-hot-0`) |
| &nbsp;&nbsp;↳ attribute `node_role` | node **role**                          | `container_name` (`liaison` / `data`)   |
| &nbsp;&nbsp;↳ attribute `node_type` | data-node **tier**                     | `node_type` (`hot` / `warm` / `cold`)   |
| `Endpoint`                   | one **group** (storage partition)             | `group` (`measure-default`, …)          |

A standalone BanyanDB is the degenerate case: one cluster, one node whose role is `standalone` (all
roles co-resident) and no tier.

**Why role/tier are instance attributes, not separate services or endpoints.** A node's identity is
its `pod_name`; its role and tier are *properties of that node*, which is exactly what
`InstanceTraffic.properties` (the UI "Attributes" panel) is for. Keeping the cluster as the single
service means the node list, the group list, and cluster-wide KPIs all live under one entity the
operator can reason about — and it makes the node dashboard able to adapt to the selected node's
attributes.

### 2. Scrape source and label scheme (FODC proxy only)

SkyWalking scrapes the **FODC proxy `/metrics`** (default `:17913`) as the single Prometheus target.
The proxy aggregates every node's metrics and stamps four identity labels onto each sample (verified in
the FODC agent's `ParseWithNodeLabels`):

| Label            | Value                                        | Used for                          |
| ---------------- | -------------------------------------------- | --------------------------------- |
| `pod_name`       | full node identity, e.g. `banyandb-data-hot-0` | instance name                   |
| `container_name` | `liaison` / `data` (the role discriminator)  | instance attribute `node_role`    |
| `node_role`      | raw enum `ROLE_LIAISON` / `ROLE_DATA`        | (available; `container_name` preferred for clean values) |
| `node_type`      | `hot` / `warm` / `cold` (data nodes only)    | instance attribute `node_type` (tier) |

All original BanyanDB labels are preserved on every sample: `group`, `service`, `method`, `operation`,
`remote_node`, `remote_role`, `remote_tier`, `error_type`, `kind`, `path`, `type`, `name`, `le`, …. The
Prometheus-synthesized `instance` / `job` / `up` describe the **proxy**, not individual nodes — node
liveness is derived from the always-present per-node gauge `banyandb_system_up_time`, never from `up`.

**Collector scrape job (illustrative — operator configuration, not a shipped file):**

```yaml
receivers:
  prometheus:
    config:
      scrape_configs:
        - job_name: "banyandb-monitoring"
          scrape_interval: 15s
          # The FODC proxy is the single target; it carries per-node identity labels.
          static_configs:                    # or kubernetes_sd_configs keeping app.kubernetes.io/component=fodc-proxy
            - targets: ["banyandb-fodc-proxy:17913"]
              labels:
                cluster: my-banyandb          # ← the only label SkyWalking must inject
exporters:
  otlp:
    endpoint: oap:11800
    tls: { insecure: true }
service:
  pipelines:
    metrics: { receivers: [prometheus], processors: [batch], exporters: [otlp] }
```

**MAL entry (illustrative — the redesigned `expSuffix` for each rule file):**

```text
# filter shared by all three files
filter: "{ tags -> tags.job_name == 'banyandb-monitoring' }"

# banyandb-service.yaml  → cluster
expSuffix: service(['cluster'], Layer.BANYANDB)

# banyandb-instance.yaml → node, with role + tier as attributes
expSuffix: |-
  service(['cluster'], Layer.BANYANDB)
  .instance(['cluster'], '::', ['pod_name'], '', Layer.BANYANDB,
            { tags -> ['node_role': tags.container_name, 'node_type': tags.node_type ?: 'n/a'] })

# banyandb-endpoint.yaml → group
expSuffix: endpoint(['cluster'], ['group'], Layer.BANYANDB)
```

The 6-argument `.instance(...)` overload's properties closure is the standard, precedented mechanism for
attaching labels as instance attributes (the same shape used by `k8s-instance.yaml`). The attributes
ride entirely on the scraped labels — no separate update API.

### 3. Metric catalog → MAL rules

The redesigned rules mirror the upstream FODC-proxy catalog. The two upstream Grafana boards map onto
two SkyWalking scopes — **Nodes → Instance** (per `pod_name`), **Workload → Endpoint** (per `group`) —
plus a small **Service** summary for cluster KPIs. Source metric names below are verified against
BanyanDB `origin/main` (the base of the upstream observability PR).

#### 3.1 Service scope — cluster summary (`banyandb-service.yaml`)

| Metric (`meter_banyandb_*`) | Meaning                  | Source expression (sketch)                                                                 |
| --------------------------- | ------------------------ | ------------------------------------------------------------------------------------------ |
| `cluster_write_rate`        | cluster writes/s         | `rate(measure_total_written) + rate(stream_tst_total_written) + rate(trace_tst_total_written)` |
| `cluster_query_rate`        | cluster queries/s        | `rate(liaison_grpc_total_started{method='query'})`                                          |
| `cluster_error_rate`        | cluster errors/min       | `liaison_grpc_total_err + _stream_msg_received_err + schema_server_grpc_total_err + queue_pub_total_err + Σ *_total_sync_loop_err` (×60, each `or vector(0)`) |
| `reporting_nodes`           | live node count by role  | `count(system_up_time) by (container_name)`                                                 |
| `total_cpu_cores`           | cluster CPU capacity     | `sum(system_cpu_num)`                                                                       |
| `total_memory_used`         | cluster memory used      | `sum(system_memory_state{kind='used'})`                                                     |
| `total_disk_used`           | cluster disk used        | `sum(system_disk{kind='used'})`                                                             |

#### 3.2 Instance scope — per node (`banyandb-instance.yaml`)

**All roles** (every node emits these — the "Nodes" board):

| Metric (`meter_banyandb_instance_*`) | Source                                                            |
| ------------------------------------ | ---------------------------------------------------------------- |
| `node_uptime`                        | `system_up_time`                                                 |
| `cpu_usage`                          | `rate(process_cpu_seconds_total)`                               |
| `rss_memory`                         | `process_resident_memory_bytes`                                 |
| `system_memory_percent`             | `system_memory_state{kind='used_percent'}`                      |
| `disk_usage_percent`                | `system_disk{kind='used'} / system_disk{kind='total'}`          |
| `disk_used_by_path` / `disk_total_by_path` / `disk_used_percent_by_path` | `system_disk{...} by (path)`               |
| `network_recv` / `network_sent`     | `rate(system_net_state{kind='bytes_recv'/'bytes_sent'}) by (name)` |
| `goroutines`                        | `go_goroutines`                                                 |
| `gc_pause_avg`                      | `rate(go_gc_duration_seconds_sum) / rate(go_gc_duration_seconds_count)` |
| `heap_inuse` / `heap_next_gc` / `alloc_rate` | `go_memstats_heap_inuse_bytes` / `go_memstats_next_gc_bytes` / `rate(go_memstats_alloc_bytes_total)` |

**Liaison-only** (front door; hidden on data nodes — see [§4](#4-dynamic-metrics-by-role-and-tier)):

| Metric (`meter_banyandb_instance_*`)  | Source                                                                  |
| ------------------------------------- | ----------------------------------------------------------------------- |
| `query_rate_by_service`               | `rate(liaison_grpc_total_started{method='query'}) by (service)`         |
| `grpc_error_rate`                     | `rate(liaison_grpc_total_err) by (service, method)` (+ `_stream_msg_received_err`; both lazily registered) |
| `non_query_op_rate`                   | `rate(liaison_grpc_total_started{method!='query'}) by (method)` |
| `write_rate`                          | `rate({measure,stream_tst,trace_tst}_total_written)`                    |
| `publish_throughput` / `publish_latency_p99` | `rate(queue_pub_total_finished) by (operation)` / `histogram_quantile(0.99, …queue_pub_total_latency_bucket)` |
| `wqueue_file_parts` / `wqueue_mem_part` / `wqueue_pending` | `{measure,stream_tst,trace_tst}_total_file_parts` / `_total_mem_part` / `_pending_data_count` |

**Data-only** (backend; hidden on liaison nodes):

| Metric (`meter_banyandb_instance_*`)            | Source                                                              |
| ----------------------------------------------- | ------------------------------------------------------------------ |
| `total_data`                                    | `{measure,stream_tst,trace_tst}_total_file_elements`               |
| `merge_file_rate` / `merge_file_latency` / `merge_file_partitions` | `rate(*_total_merge_loop_started)` / `…_merge_latency{type='file'}` / `…_merged_parts{type='file'}` |
| `series_write_rate` / `series_term_search_rate` / `total_series` | `measure_inverted_index_total_updates` / `_term_searchers_started` / `_doc_count`; `stream_storage_inverted_index_*` |
| `stream_tst_write_rate` / `stream_tst_term_search_rate` / `stream_tst_total_docs` | `stream_tst_inverted_index_*` |
| `queue_sub_throughput` / `queue_sub_latency_p99` (per `operation`) | `rate(queue_sub_total_started/finished) by (operation)` / `histogram_quantile(0.99, …queue_sub_total_latency_bucket) by (operation)` |

#### 3.3 Endpoint scope — per group (`banyandb-endpoint.yaml`)

The "Workload" board's by-`group` projections become endpoint metrics (aggregated across the cluster's
nodes per group):

| Metric (`meter_banyandb_endpoint_*`) | Source (`… by (group)`)                                            |
| ------------------------------------ | ------------------------------------------------------------------ |
| `write_rate`                         | `rate({measure,stream_tst,trace_tst}_total_written) by (group)`    |
| `query_latency`                      | `rate(liaison_grpc_total_latency{method='query'}) / rate(…_started{method='query'}) by (group)` |
| `total_data`                         | `{measure,stream_tst,trace_tst}_total_file_elements by (group)`    |
| `merge_file_rate` / `merge_file_latency` / `merge_file_partitions` | the merge family `by (group)`                       |
| `series_write_rate` / `total_series` | inverted-index `_total_updates` / `_doc_count` `by (group)`        |
| `queue_throughput` / `queue_latency_p99` | `queue_sub` / `queue_pub` `by (operation, group)`             |
| `publish_bytes`                      | `rate(queue_pub_sent_bytes) by (group)`                            |

> **Semantic note.** A BanyanDB `group` is a *storage group*, not an HTTP route. Modeling it as an
> Endpoint is mechanically exact and precedented (the same way `elasticsearch-index`, `rocketmq-topic`
> and `apisix` model non-RPC nouns as endpoints), but operators should read "Endpoint" as "storage
> group". Endpoint-only UI affordances that assume RPC semantics (endpoint dependency map, endpoint
> traces, slow-endpoint lists) are not meaningful here and are intentionally not used.

### 4. Dynamic metrics by role and tier

Different roles expose different metrics, so the **node (Instance) dashboard must adapt to the selected
node**. Two mechanisms, layered:

**(a) Data-presence gating — available today, no UI code.** Horizon UI already supports
`visibleWhen: "<metric> has value"` on a widget; a panel whose metric returns all-null self-hides. Each
MAL rule only produces samples for nodes that emit its source metric, so liaison-only metrics are simply
absent on data instances and vice-versa. This gives correct adaptive behavior out of the box:

```jsonc
{ "id": "wqueue", "title": "Write Queue (wqueue)", "type": "line",
  "expressions": ["meter_banyandb_instance_wqueue_pending"],
  "visibleWhen": "meter_banyandb_instance_wqueue_pending has value" }
```

**(b) Attribute predicate — proposed enhancement (see [§6](#6-horizon-ui-enhancement-entity-attribute-predicate)).**
Data-presence can't distinguish "wrong role" from "idle but right role", and it still issues the query.
An attribute predicate keys panel visibility directly on the node's `node_role` / `node_type`
attributes:

```jsonc
{ "id": "wqueue", "visibleWhen": "#entity.node_role == 'liaison'" }
{ "id": "cold_tier_note", "visibleWhen": "#entity.node_type == 'cold'" }
```

This is the precise, declarative form, and it is the natural way to express tier-specific panels (a
`hot` data node merges constantly; a `cold` node is mostly static).

Role/tier scoping of the catalog:

| Bucket          | Panels                                                                 | Predicate                         |
| --------------- | --------------------------------------------------------------------- | --------------------------------- |
| **All roles**   | system resources, disk-by-path, network, Go runtime, node uptime      | (always shown)                    |
| **Liaison**     | gRPC query & errors, non-query ops, write rate, publish throughput & latency, wqueue depth | `#entity.node_role == 'liaison'` |
| **Data**        | storage totals, merge/compaction, inverted index, subscribe queue     | `#entity.node_role == 'data'`     |
| **Data + tier** | tier-specific merge/retention hints                                   | `#entity.node_type in (hot,warm)` |

### 5. Dashboards (Horizon UI BANYANDB layer template)

A net-new layer template `apps/bff/src/bundled_templates/layers/banyandb.json` (config-driven JSON, one
file per layer, per-scope widget arrays, MQE expression strings). The design mirrors the upstream two
boards across the SkyWalking hierarchy:

```
BANYANDB layer
├─ Root            → cluster list (ServiceList), showGroup=false
├─ Service (cluster)
│   └─ Overview KPIs + "Cluster Workload Summary" + "Fleet Overview" capacity
│       (cluster_write_rate, cluster_query_rate, cluster_error_rate,
│        reporting_nodes by role, total_cpu/memory/disk)
├─ Instance (node)   ← the "Nodes" board, made dynamic
│   ├─ All roles: Resources (CPU/RSS/mem%/disk%), Disk by Path, Network, Go Runtime
│   ├─ Liaison (visibleWhen role==liaison): Ingestion/Query, Registry, Errors,
│   │     Publish throughput & p99, Write Queue (wqueue) depth
│   └─ Data (visibleWhen role==data): Storage totals, Merge, Inverted Index,
│         Subscribe Queue (per operation: query/file-sync/batch-write/control)
└─ Endpoint (group)  ← the "Workload" board, by group
    └─ Write rate, Query latency, Total data, Merge, Inverted index, Queue, Publish bytes
```

Panel **types/units** follow the upstream Grafana boards for fidelity (stat for KPIs; timeseries for
rates/latencies; table for the per-node health row; `bytes` / `percentunit` / `s` / `reqps` / `wps`
units; disk% and memory% turn red at 80%). The per-node "health table" (uptime, CPU cores, RSS, mem%,
disk%) becomes the Instance-list columns on the Service view.

This is **design only** — the production `banyandb.json` and its exact widget grid are deliberately left
to the implementation PR in the Horizon UI repository.

### 6. Horizon UI enhancement: `#entity` attribute predicate

Horizon UI's widget `visibleWhen` already parses two predicate forms but only one is implemented:

- `"<metric> has value"` — implemented (client-side data-presence gating).
- `"#entity.<key>"` — **parsed but stubbed**: the renderer's `isVisible` currently returns `true`
  unconditionally for any `#entity.*` predicate, with the comment *"Entity-attribute predicates need an
  attributes feed we don't surface yet. Render the widget unconditionally for now."*

The data is already on the wire: the instance list the UI fetches carries each instance's
`attributes [{name,value}]`. The enhancement is to **wire those attributes into the predicate
evaluator** and give the predicate a small comparison grammar:

| Predicate form                          | Meaning                                            |
| --------------------------------------- | -------------------------------------------------- |
| `#entity.<key>`                         | attribute present and truthy                       |
| `#entity.<key> == '<v>'` / `!= '<v>'`   | equals / not-equals a literal                      |
| `#entity.<key> in (<v1>,<v2>)`          | membership                                         |

Scope of the enhancement (design): (1) pass the selected instance's `attributes` into the
`LayerDashboardsView` predicate context; (2) implement the `#entity.*` branch of `isVisible` to read
that context; (3) extend the predicate parser with `==` / `!=` / `in`; (4) document it in the Horizon UI
layer-template authoring docs. It is generic — any layer (K8s node roles, gateway tiers, …) benefits;
BanyanDB is the first consumer. The exact code lands in the Horizon UI repository.

## Feasibility and precedent

Verified against the OAP and Horizon UI source — **no OAP core / MAL / receiver change is required**:

- **Scopes.** `ScopeType` already has `SERVICE`, `SERVICE_INSTANCE`, `ENDPOINT`. `SampleFamily` exposes
  `.service(...)`, `.instance(...)` (incl. the 6-arg properties-closure overload), and `.endpoint(...)`.
- **Endpoint from a label** is shipping practice: `nginx-endpoint.yaml`, `kong-endpoint.yaml`,
  `apisix.yaml`, `elasticsearch-index.yaml`, `rocketmq-topic.yaml`, `aws-dynamodb/dynamodb-endpoint.yaml`.
- **Instance attributes from labels** via the properties closure is shipping practice: `k8s-instance.yaml`.
- **Pure-metrics endpoints work end-to-end** (no trace required): `Analyzer.generateTraffic` emits an
  `EndpointTraffic` whenever the endpoint name is non-empty; `EndpointTraffic` is `supportUpdate=true`
  and is listed by GraphQL `findEndpoint` (empty keyword ⇒ list all), which the BanyanDB metadata DAO
  serves from the traffic table without touching any trace data.
- **Layer.** `Layer.BANYANDB` (ordinal 43) already exists; layer dashboards are auto-discovered by the
  UI from the template's own `layer` field — no menu code change.

## Live validation

The entity scheme and the metric catalog above were validated against a **live 7-node BanyanDB
cluster** — the public SkyWalking demo's FODC proxy `/metrics` (2 liaison + 5 data: `hot×2`, `warm×2`,
`cold×1`). Findings:

- **All four identity labels are present and exactly as designed.** Every sample carries `pod_name`
  (e.g. `demo-banyandb-data-hot-0`), `node_role` (`ROLE_LIAISON` / `ROLE_DATA`), `container_name`
  (`liaison` / `data`), and — on **data nodes only** — `node_type` (`hot` / `warm` / `cold`). Liaison
  nodes carry no `node_type`, so the instance closure defaults the tier attribute (`tags.node_type ?:
  'n/a'`). This validates Service = `cluster`, Instance = `pod_name`, attributes `node_role` /
  `node_type`.
- **The queue model is confirmed verbatim.** `banyandb_queue_sub_*` / `queue_pub_*` carry
  `operation` ∈ {`batch-write`, `control`, `file-sync`, `query`}, plus `group`, `remote_node`,
  `remote_role` (`liaison` / `data`) and `remote_tier` (`hot` / …); `total_latency` is a histogram. The
  `remote_*` labels reconstruct the liaison↔data(tier) call graph end-to-end.
- **system / storage / index families confirmed.** `system_disk{kind,path}` (`kind` ∈ `total` /
  `used` / `used_percent`), `system_net_state{kind,name}`, `system_memory_state{kind}`,
  `liaison_grpc_total_started{group,method,service}`, `*_total_written{group}`,
  `*_inverted_index_*{group,seg,node_type}`. Data-node metrics also carry `node_type`, so the by-group
  endpoint view can be refined by tier.
- **One reconciliation vs. the upstream doc.** Schema/registry operations are **not** exposed as
  `banyandb_liaison_grpc_total_registry_*` (those series do not exist on the live cluster) — they are a
  **separate `banyandb_schema_server_grpc_*` scope** (`total_started{method}`, `_finished`, `_latency`,
  `_err`), running on the nodes hosting the metadata/schema server. The tables above use the
  `schema_server_grpc_*` names accordingly.
- **Error counters are absent on a healthy cluster, by design.** `liaison_grpc_total_err`,
  `*_total_sync_loop_err` and `queue_pub_total_err` are label-dimensioned counters that emit no series
  until the first error — so the rules must guard each error term with `or vector(0)`, exactly as the
  upstream "Error Rate" panel does. Their non-error siblings (`_started` / `_finished` / `_latency` /
  `_bytes`) are all present.

## Imported Dependencies libs and their licenses

None. The design reuses the existing OpenTelemetry receiver, the MAL engine, the `BANYANDB` layer, and
the Horizon UI template engine. The only new artifacts are configuration/template/doc assets (MAL rule
YAML, a Horizon UI layer JSON, and docs) plus a small, self-contained Horizon UI predicate enhancement.
No new third-party dependency is introduced.

## Compatibility

This is a **breaking change** to the BanyanDB self-observability feature (an internal monitoring
feature, not a public protocol/storage contract):

- **Entity model.** A BanyanDB cluster that previously appeared as *N* services (one per node) now
  appears as *one* service with *N* instances. Old per-node `Service` entities and their
  `meter_banyandb_*` / `meter_banyandb_instance_*` metric series are superseded; the new series use the
  cluster/node/group identities and a partly new metric set. Historical data under the old model is not
  migrated.
- **Scrape target.** Cluster deployments must scrape the **FODC proxy `:17913`** (single target) and
  inject a `cluster` label. The legacy per-pod `:2121` collector config is replaced. Direct per-pod
  scraping is **out of scope** for this redesign (a standalone node still reports through its FODC
  agent/proxy); if direct-scrape support is wanted later it would be an additive, separate rule variant.
- **Removed metrics.** The stale `etcd_operation_rate`, `up`-derived `active_instance`, and the
  pre-refactor queue error names are dropped; the new error/queue metrics follow the current BanyanDB
  exposition.
- **Dashboards.** The old booster-UI templates are gone already; the new dashboards ship from the
  Horizon UI bundle.
- **OAP rule loading** is unchanged: `enabledOtelMetricsRules` already globs `banyandb/*`, so the new
  `banyandb-endpoint.yaml` is picked up without an `application.yml` change.
- **Horizon UI predicate enhancement is backward compatible** — `#entity.*` only ever returned `true`
  before, so implementing it can only *add* hiding behavior to templates that opt in; existing templates
  are unaffected.

## General usage docs

This is a preliminary usage sketch to help reviewers; the final operator docs (replacing
`docs/en/banyandb/dashboards-banyandb.md`) land with the implementation.

**Setup**

1. Run a BanyanDB **cluster** (liaison + data nodes; data nodes may be tiered hot/warm/cold) with the
   **FODC proxy** enabled and the Prometheus metrics provider on (default).
2. Run an **OpenTelemetry Collector** whose `prometheus` receiver scrapes the FODC proxy `/metrics`
   (`:17913`) as the single target and adds a static `cluster: <name>` label, exporting OTLP to OAP.
3. Enable SkyWalking's **OpenTelemetry receiver** (the `banyandb/*` MAL rules are enabled by default).
4. Open the **Horizon UI** → `BanyanDB` layer.

**What the operator sees**

- A **cluster** as a single service, with cluster-wide write/query/error rates and capacity.
- A **node list** where each node shows its **role** (`liaison` / `data`) and **tier**
  (`hot` / `warm` / `cold`) as attributes; selecting a node shows a dashboard **scoped to what that node
  actually does** — ingestion/queue/publish for liaison, storage/index/subscribe for data, refined by
  tier.
- A **group list** (Endpoints) with per-group throughput, latency, storage, index and queue health.

## Future work

- **Topology / lifecycle.** Fuse FODC `/cluster/topology` (node inventory + roles + tiers) and the queue
  `remote_node` / `remote_role` / `remote_tier` labels into a node-to-node call graph, and surface FODC
  `/cluster/lifecycle` group settings (shards / segment interval / TTL) on the Endpoint view.
- **Alerting.** Ship default alarm rules for the upstream "Key Signals to Watch" (query p99, error rate,
  disk > 85%, memory near the protector limit, sustained wqueue / `queue_pub` backlog).
- **Direct-scrape variant** for standalone / non-FODC deployments, if demand warrants.
