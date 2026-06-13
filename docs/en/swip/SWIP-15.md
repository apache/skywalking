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
   attributes, and gates widget visibility through structured, server-evaluated `visibleWhen`
   predicates — data presence and instance-**attribute** equality ship today
   ([horizon-ui #46](https://github.com/apache/skywalking-horizon-ui/pull/46)); a small
   extension (membership / negation operators) completes role/tier-driven dashboards.

The current feature does none of this. It models **each node as its own `Service`**
(`service(['host_name'], Layer.BANYANDB)`), so a cluster appears as a pile of unrelated services; it
never models the cluster, the node role, the tier, or the group; and it still ships stale or misleading
metrics (an operation rate still named after the retired `etcd` registry, a Prometheus `up`-derived
"active instances" that under the FODC proxy would describe the proxy rather than any node, and the
`queue_sub_total_msg_sent_err` family, which BanyanDB removed).

**This SWIP proposes to discard that model and rebuild BanyanDB self-observability around the cluster /
node / group reality**, matching the upstream FODC-proxy metric catalog, and to design the Horizon UI
side — a net-new BanyanDB layer dashboard whose instance view **adapts to the selected container's
role and tier** — including the small Horizon UI entity-gate extension that completes attribute-driven
dashboards.

### Goals

- Model a BanyanDB **cluster** as a single SkyWalking `Service`.
- Model each **container** (`pod_name` + `container_name`) as a `ServiceInstance`, carrying its
  **role** and **tier** as instance attributes, so the UI can show "what this container is".
- Model each **group** as an `Endpoint` of the cluster.
- Mirror the upstream FODC-proxy metric catalog faithfully (the two-dashboard split becomes the
  Instance and Endpoint views).
- Make the **instance dashboard dynamic** — a liaison container shows ingestion/queue/publish panels, a
  data container shows storage/index/subscribe panels, a lifecycle container shows migration panels, and
  the tier refines the data view — via the structured `visibleWhen` gates Horizon UI already evaluates
  (data presence + attribute equality), completed by a proposed membership/negation extension.

### Non-goals

- No change to the OTel receiver or the `Layer.BANYANDB` registration, and no change to OAP core or the
  MAL engine **beyond one addition**: the entity, instance-attribute and per-group/per-node metric
  surface is config-only (MAL rule YAML + Horizon UI template), reusing primitives that already ship and
  are precedented (see [Feasibility](#feasibility-and-precedent)). The **one** MAL-engine addition this
  SWIP required is the `SERVICE_INSTANCE_RELATION` scope — the `ScopeType`, the
  `MeterEntity.newServiceInstanceRelation(...)` factory, the `SampleFamily.serviceInstanceRelation(...)`
  builder and its entity description (`server-core` + `meter-analyzer`), mirroring the
  `SERVICE_RELATION` / `PROCESS_RELATION` scopes that already shipped — without which the intra-cluster
  [deployment topology](#7-intra-cluster-instance-topology-the-deployment-component) could not be
  emitted. That scope landed with this SWIP.
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
 └────────────────┘            │                                        │ Root: cluster list    │
 ┌─ data hot ─────┐  FODC agent ├─► FODC proxy ──► OTel Collector ──►   │ Service: cluster KPIs │
 │  :2121 /metrics │ (sidecar)  │   :17913          (prometheus recv,   │ Instance: container,  │
 └────────────────┘            │   /metrics         adds `cluster`      │   adapts to role/tier │
 ┌─ data warm ────┐  FODC agent │   single target,  label) ──OTLP──►    │ Endpoint: group       │
 │  :2121 /metrics │ (sidecar)  │   identity labels      │              └───────────────────────┘
 └────────────────┘            ┘   node_role/pod_name/   │                        ▲
 ┌─ data cold ────┐                container_name/        ▼                        │ MQE over
 │  :2121 /metrics │                node_type     receiver-otel ──► MAL            │ GraphQL
 └────────────────┘                              otel-rules/banyandb/*  ───────────┘ execExpression
                                                  ├ banyandb-service.yaml           → Service  (cluster)
                                                  ├ banyandb-instance.yaml          → Instance (container + attrs)
                                                  ├ banyandb-endpoint.yaml          → Endpoint (group)
                                                  └ banyandb-instance-relation.yaml → ServiceInstanceRelation (deployment edges)
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

| SkyWalking entity            | BanyanDB concept                          | Identity source (label)                                |
| ---------------------------- | ----------------------------------------- | ------------------------------------------------------ |
| `Service` (Layer `BANYANDB`) | one BanyanDB **cluster**                  | `cluster` (injected by the collector)                  |
| `ServiceInstance`            | one **container** on a node               | `pod_name` + `container_name` (composite)              |
| &nbsp;&nbsp;↳ attribute `container_name` | container **role** (discriminator) | `liaison` / `data` / `lifecycle`                  |
| &nbsp;&nbsp;↳ attribute `node_type` | data-node **tier**                 | `hot` / `warm` / `cold` (data containers only; `n/a` elsewhere) |
| &nbsp;&nbsp;↳ attribute `node_role` | role enum (coarse)                 | `ROLE_LIAISON` / `ROLE_DATA`                           |
| &nbsp;&nbsp;↳ attribute `pod_name`  | host pod (sibling key)             | `demo-banyandb-data-hot-0`                             |
| `Endpoint`                   | one **group** (storage partition)         | `group` (`sw_metricsMinute`, …)                        |

All four labels are attached as instance attributes **verbatim** (not renamed), because the Horizon UI
deployment/topology component groups the intra-cluster instance graph by them: `clusterBy` =
`node_role` + `node_type`, `siblingBy` = `pod_name`, `roleBy` = `container_name`. Emitting the raw
label names keeps the OAP attribute bag and the UI grouping config in lockstep.

**Why the instance is a container, not a `pod_name`.** `pod_name` is **not unique per metrics
emitter**: a data hot/warm pod co-hosts a `lifecycle` migration sidecar that reports under the *same*
`pod_name` (verified on the live cluster — `demo-banyandb-data-hot-0` emits both `container_name=data`
and `container_name=lifecycle`). Keying the instance on `pod_name` alone would silently merge the two
series. The instance identity is therefore `pod_name` + `container_name`, and `container_name` — not
`node_role` — is the role discriminator: `node_role` carries only `ROLE_LIAISON` / `ROLE_DATA` on a
healthy cluster (it stays `ROLE_DATA` on the lifecycle sidecar, and the FODC agent maps unresolved or
meta-only nodes to a transient `ROLE_UNSPECIFIED`), whereas `container_name` cleanly separates
`liaison` / `data` / `lifecycle`. A standalone BanyanDB is the degenerate case: one cluster, one node,
one `container_name=standalone`, no tier.

**Why container/tier are instance attributes, not separate services or endpoints.** A container's role
and tier are *properties of that instance*, which is exactly what `InstanceTraffic.properties` (the UI
"Attributes" panel) is for. Keeping the cluster as the single service means the instance list, the
group list, and cluster-wide KPIs all live under one entity the operator can reason about — and it
makes the instance dashboard able to adapt to the selected container's attributes.

### 2. Scrape source and label scheme (FODC proxy only)

SkyWalking scrapes the **FODC proxy `/metrics`** (default `:17913`) as the single Prometheus target.
The proxy aggregates every container's metrics and stamps four identity labels onto each sample
(verified in the FODC agent's `ParseWithNodeLabels` and against the live cluster):

| Label            | Value                                          | Used for                                              |
| ---------------- | ---------------------------------------------- | ----------------------------------------------------- |
| `pod_name`       | node identity, e.g. `banyandb-data-hot-0`      | instance name (part 1) — **not unique**, see below    |
| `container_name` | `liaison` / `data` / `lifecycle`               | instance name (part 2) + attribute `container_name` (the role discriminator) |
| `node_role`      | raw enum `ROLE_LIAISON` / `ROLE_DATA` (transiently `ROLE_UNSPECIFIED`) | **not** the discriminator — coarser than `container_name`, stays `ROLE_DATA` on the lifecycle sidecar |
| `node_type`      | `hot` / `warm` / `cold` (data containers only) | instance attribute `node_type` (tier)                 |

`pod_name` alone does **not** identify an instance: on the live cluster the four data hot/warm pods
each run two containers (`data` + `lifecycle`) under one `pod_name`, so the instance key is
`pod_name` + `container_name`.

All original BanyanDB labels are preserved on every sample: `group`, `service`, `method`, `operation`,
`remote_node`, `remote_role`, `remote_tier`, `error_type`, `kind`, `path`, `type`, `seg`, `shard`,
`le`, …. Note `service` is BanyanDB's internal **data-model module** (`measure` / `stream` / `trace` /
`property` / `group`) — a workload facet, **never** a SkyWalking service identity. The
Prometheus-synthesized `instance` / `job` / `up` describe the **proxy**, not individual containers —
node liveness is derived from the always-present per-container gauge `banyandb_system_up_time`, never
from `up`.

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

# banyandb-instance.yaml → container (a node may run >1 container), role + tier as attributes
expSuffix: |-
  service(['cluster'], Layer.BANYANDB)
  .instance(['cluster'], '::', ['container_name', 'pod_name'], '@', Layer.BANYANDB,
            { tags -> ['node_role':      tags.node_role,
                       'node_type':      tags.node_type ?: 'n/a',
                       'pod_name':       tags.pod_name,
                       'container_name': tags.container_name] })

# banyandb-endpoint.yaml → group
expSuffix: endpoint(['cluster'], ['group'], Layer.BANYANDB)
```

The instance key is the pair `['container_name', 'pod_name']` joined by `'@'` (signature
`instance(serviceKeys, serviceDelimiter, instanceKeys, instanceDelimiter, layer, propertiesExtractor)`),
so each pod's containers surface as distinct `data@…` / `lifecycle@…` / `liaison@…` instances rather than
colliding, and the role (`container_name`) leads the instance name. The 6-argument overload's properties closure is the standard, precedented mechanism for
attaching labels as instance attributes (the same shape used by `k8s-instance.yaml`). The attributes
ride entirely on the scraped labels — no separate update API. (Two notes: the MAL v2
grammar supports the Elvis operator inside a map-literal value, and `banyandb-instance.yaml` ships exactly
this closure (`'node_type': tags.node_type ?: 'n/a'`), compile-tested at boot by `DSLClassGeneratorTest`.
And `language` is the one reserved property key — the instance query maps it to the language field instead of an
attribute; none of these four labels collides with it.)

### 3. Metric catalog → MAL rules

The redesigned rules mirror the upstream FODC-proxy catalog. The two upstream Grafana boards map onto
two SkyWalking scopes — **Nodes → Instance** (per `pod_name` + `container_name`), **Workload →
Endpoint** (per `group`) — plus a small **Service** summary for cluster KPIs. Source metric names
below are verified against the **live demo cluster** — which runs upstream `main` builds (the
validation pull used the showcase-pinned `main` image of 2026-06-09) — and against BanyanDB
`origin/main` source. The upstream observability PR
[#1159](https://github.com/apache/skywalking-banyandb/pull/1159) (open; docs and Grafana dashboards
only, no metric code) documents the same catalog and defines the two boards this design mirrors.

> **Metric-name prefix (build-critical).** The sketches below drop a common prefix for readability.
> On the wire **every BanyanDB-native family carries the `banyandb_` prefix** (`banyandb_measure_total_written`,
> `banyandb_liaison_grpc_total_started`, `banyandb_system_disk`, …) — the MAL rules must use the full
> prefixed name. The **only** exceptions are the standard Go-runtime and process exporter families
> `go_*` / `process_*`, which are **bare** (no prefix) and are referenced as-is. Every error counter
> this catalog references is lazily registered and emits nothing until the first error fires
> (`banyandb_liaison_grpc_total_err`, `banyandb_liaison_grpc_total_stream_msg_received_err`,
> `banyandb_queue_pub_total_err`, the `*_total_sync_loop_err` family), and the lifecycle last-run
> gauges (`banyandb_lifecycle_last_run_*`, BanyanDB #1167) post-date the build the demo pull validated;
> every other cited family was present in that pull.
>
> **Sketch notation (PromQL-flavored).** Source expressions are written PromQL-style for readability;
> the MAL forms differ mechanically. **(1)** No `or vector(0)` guard exists in MAL — nor is one
> needed: an unfired family resolves to the empty sample family, MAL's `+` treats an empty operand as
> identity, and a rule is skipped only when *all* referenced families are absent — so an error sum
> emits as soon as any one term fires, and a fully healthy cluster shows no series at all (dashboards
> should render absent as 0). **(2)** MAL arithmetic joins samples on exact label equality, so each
> term must be aggregated to the same label set (e.g. `.sum(['cluster'])`) before `+`. **(3)**
> `count(...) by (...)` maps to MAL's multi-label `count([...])`; `histogram_quantile(0.99, …_bucket)`
> maps to `.histogram().histogram_percentile([99])` on the `le`-labeled base family (no `_bucket`
> suffix remains after OTLP conversion); and `time() - <metric>` is computed at **ingest** in the MAL
> rule — MAL ships `time()` (the shipped `envoy-ca.yaml` cert-staleness metric is the precedent),
> while MQE has no current-time function, so it cannot be computed at query time.

#### 3.1 Service scope — cluster summary (`banyandb-service.yaml`)

| Metric (`meter_banyandb_*`) | Meaning                  | Source expression (sketch)                                                                 |
| --------------------------- | ------------------------ | ------------------------------------------------------------------------------------------ |
| `cluster_write_rate`        | cluster writes/s         | `rate(measure_total_written) + rate(stream_tst_total_written) + rate(trace_tst_total_written)` |
| `cluster_query_rate`        | cluster queries/s        | `rate(liaison_grpc_total_started{method='query'})`                                          |
| `cluster_error_rate`        | cluster errors/min       | `liaison_grpc_total_err + liaison_grpc_total_stream_msg_received_err + schema_server_grpc_total_err + queue_pub_total_err + Σ *_total_sync_loop_err` (×60; all lazily registered — see sketch notation above) |
| `reporting_instances`       | live container count by role | `count(system_up_time) by (container_name)`                                              |
| `total_cpu_cores`           | cluster CPU capacity     | `sum(system_cpu_num)`                                                                       |
| `total_memory_used`         | cluster memory used      | `sum(system_memory_state{kind='used'})`                                                     |
| `total_disk_used`           | cluster disk used        | `sum(system_disk{kind='used'})`                                                             |

#### 3.2 Instance scope — per container (`banyandb-instance.yaml`)

The instance scope is **role-separated by a metric-name prefix**: the scope is still a single
`instance()` identity, but each rule's `name` carries a role prefix so the UI layer template can select
the panel set by `container_name` (`liaison` / `data` / `lifecycle`) and a human can read a metric name
and know which role it belongs to. The shared resource/runtime block stays **unprefixed** (it resolves
on whatever container emits the family); front-door rules carry `liaison_*`, storage/index/queue rules
carry `data_*`, and the migration-sidecar health triple carries `lifecycle_*`. The same wire family
read under two prefixes (e.g. `*_total_file_parts` on a liaison buffer vs. a data storage part) is
disambiguated by `container_name` — each role rule reads only its own container's series.

**All roles** (unprefixed; every container emits these — the "Nodes" board):

| Metric (`meter_banyandb_instance_*`) | Source                                                            |
| ------------------------------------ | ---------------------------------------------------------------- |
| `node_uptime`                        | `system_up_time`                                                 |
| `cpu_usage`                          | `rate(process_cpu_seconds_total)`                               |
| `rss_memory`                         | `process_resident_memory_bytes`                                 |
| `system_memory_percent`             | `system_memory_state{kind='used_percent'} * 100` (the gauge is a 0-1 fraction; `*100` → 0-100 %) |
| `disk_usage_percent`                | `avg(system_disk{kind='used_percent'}) * 100` (per node; the data paths share one filesystem, so `avg` collapses them) |
| `disk_used_by_path` / `disk_total_by_path` | `system_disk{kind='used'/'total'} by (path)` (bytes) |
| `disk_used_percent_by_path`         | `system_disk{kind='used_percent'} * 100 by (path)`              |
| `network_recv` / `network_sent`     | `rate(system_net_state{kind='bytes_recv'/'bytes_sent'}) by (name)` |
| `goroutines`                        | `go_goroutines`                                                 |
| `gc_pause_avg`                      | `rate(go_gc_duration_seconds_sum) / rate(go_gc_duration_seconds_count)` |
| `heap_inuse` / `heap_next_gc` / `alloc_rate` | `go_memstats_heap_inuse_bytes` / `go_memstats_next_gc_bytes` / `rate(go_memstats_alloc_bytes_total)` |

**Liaison-only** (`liaison_*` prefix; front door, hidden on data containers — see [dynamic metrics by role and tier](#4-dynamic-metrics-by-role-and-tier)):

| Metric (`meter_banyandb_instance_*`)  | Source                                                                  |
| ------------------------------------- | ----------------------------------------------------------------------- |
| `liaison_query_rate`                  | `rate(liaison_grpc_total_started{method='query'}) by (service)`         |
| `liaison_grpc_error_rate`             | `(rate(liaison_grpc_total_err) + rate(liaison_grpc_total_registry_err) + rate(liaison_grpc_total_stream_msg_received_err)) × 60` (all lazily registered) |
| `liaison_registry_op_rate`            | `rate(liaison_grpc_total_registry_started)` (schema-registry ops on the front door) |
| `liaison_write_rate`                  | `rate({measure,stream_tst,trace_tst}_total_written)`                    |
| `liaison_publish_throughput` / `liaison_publish_bytes` / `liaison_publish_latency_p99` | `rate(queue_pub_total_finished) by (operation)` / `rate(queue_pub_sent_bytes)` / `histogram_quantile(0.99, …queue_pub_total_latency_bucket)` |
| `liaison_publish_batch_throughput` / `liaison_publish_batch_latency_p99` | `rate(queue_pub_total_batch_finished) by (operation)` / `histogram_quantile(0.99, …queue_pub_total_batch_latency_bucket)` (batch granularity, BanyanDB #1169; build-gated) |
| `liaison_wqueue_pending`              | `{measure,stream_tst,trace_tst}_pending_data_count` (front-door write-queue depth) |

**Data-only** (`data_*` prefix; backend, hidden on liaison containers):

| Metric (`meter_banyandb_instance_*`)            | Source                                                              |
| ----------------------------------------------- | ------------------------------------------------------------------ |
| `data_total_data`                               | `{measure,stream_tst,trace_tst}_total_file_elements`               |
| `data_wqueue_file_parts` / `data_wqueue_mem_part` / `data_wqueue_pending` | `{measure,stream_tst,trace_tst}_total_file_parts` / `_total_mem_part` / `_pending_data_count` (on-disk write-queue storage parts) |
| `data_merge_file_rate` / `data_merge_file_partitions` / `data_merge_file_latency` | `rate(*_total_merge_loop_started)` / `rate(*_total_merged_parts{type='file'}) / rate(*_merge_loop_started)` / `*_total_merge_latency{type='file'}` (×1000 ms) |
| `data_series_write_rate` / `data_series_term_search_rate` / `data_total_series` | inverted-index `_total_updates` / `_total_term_searchers_started` / `_total_doc_count`, summed across `measure_inverted_index_*`, `stream_storage_inverted_index_*` **and `trace_storage_inverted_index_*`** (the trace series index, previously dropped, is now included) |
| `data_stream_tst_write_rate` / `data_stream_tst_term_search_rate` / `data_stream_tst_total_docs` | `stream_tst_inverted_index_*` (the stream tst-scope index, distinct from its storage-scope series index) |
| `data_queue_sub_throughput` / `data_queue_sub_latency_p99` (per `operation`) | `rate(queue_sub_total_finished) by (operation)` / `histogram_quantile(0.99, …queue_sub_total_latency_bucket) by (operation)` |
| `data_queue_sub_message_throughput` (per `operation`) | `rate(queue_sub_total_message_finished) by (operation)` (per-message dispatch path, BanyanDB #1169) |
| `data_retention_{measure,stream,trace}_disk_usage_percent` | `storage_retention_{measure,stream,trace}_disk_usage_percent` (kept per scope — a sum of three percentages is meaningless) |

**Lifecycle-only** (`lifecycle_*` prefix; the tier-migration sidecar co-located on `hot`/`warm` data pods; `container_name == 'lifecycle'`):

| Metric (`meter_banyandb_instance_*`) | Source                                                              |
| ------------------------------------ | ------------------------------------------------------------------ |
| `lifecycle_migration_cycles`         | `lifecycle_cycles_total` (cumulative migration cycles)            |
| `lifecycle_last_run`                 | `lifecycle_last_run_timestamp_seconds` — epoch of the last cycle's start; "time since last sync" = `time() - <metric>`, computed at ingest in the MAL rule (MQE has no `time()`) |
| `lifecycle_last_run_success`         | `lifecycle_last_run_success` (`1` = last cycle OK, `0` = failed)  |

> **Lifecycle last-run signals.** The two gauges above were added in BanyanDB
> [#1167](https://github.com/apache/skywalking-banyandb/pull/1167) (merged to `main` on 2026-06-09,
> post-dating the build the demo pull validated) — both are
> stamped on every cycle end (success, error, or panic-recovered), so they drive a "time since last
> sync" staleness panel and a "last sync OK?" status panel directly. They emit only **after the first
> migration runs**, so the staleness panel must guard the never-run case. The same PR also stamps the
> lifecycle's sender identity onto its migration publisher, so a destination data node's `queue_sub`
> `remote_node` / `remote_role` / `remote_tier` now identify the migration source (were empty before).

#### 3.3 Endpoint scope — per group (`banyandb-endpoint.yaml`)

The "Workload" board's by-`group` projections become endpoint metrics (aggregated across the cluster's
nodes per group). The catalog is **type-separated**: a BanyanDB `group` carries exactly one data-model
type, and each type emits a *different* family namespace, so the rule `name` carries a
`measure_*` / `stream_*` / `stream_tst_*` / `trace_*` / `property_*` prefix and each rule reads **only**
the families its type genuinely emits. The earlier design summed `measure + stream + trace` into one
unified rule per concept — but MAL's `+` is an **inner-join on exact label equality**, so two
disjoint groups (one all-`measure`, one all-`stream`) never share a label set and cannot be unioned;
the unified rule (a) rendered an **all-empty** panel for a `property` group (property emits none of
`*_total_written` / `*_tst_*`) and (b) silently dropped the trace inverted index from
`series_write_rate` / `total_series`. The per-type split fixes both — the UI selects the panel set by
the group's data-type, and the `property` data type (below) is now modeled. The queue/publish metrics
stay **type-agnostic** (keyed on `group` + `operation`, not data-model type) and keep their bare names.

**Per data-model type** (`measure_*` / `stream_*` / `stream_tst_*` / `trace_*`; `… by (group)`):

| Metric (`meter_banyandb_endpoint_*`) | Source (`… by (group)`)                                            |
| ------------------------------------ | ------------------------------------------------------------------ |
| `{measure,stream,trace}_write_rate`  | `rate({measure,stream_tst,trace_tst}_total_written)`               |
| `{measure,stream,trace}_query_latency` | `rate(liaison_grpc_total_latency{method='query',service=<type>}) / rate(…_finished{…})` (×1000 ms) |
| `{measure,stream,trace}_total_data`  | `{measure,stream_tst,trace_tst}_total_file_elements`               |
| `{measure,stream,trace}_merge_file_rate` / `_merge_file_latency` / `_merge_file_partitions` | the per-type merge family (`*_total_merge_loop_started` / `_merge_latency{type='file'}` / `_merged_parts{type='file'}`) |
| `{measure,stream,trace}_series_write_rate` | inverted-index `_total_updates` — `measure_inverted_index_*` (no `_storage_`), `stream_storage_inverted_index_*`, `trace_storage_inverted_index_*` |
| `stream_tst_index_write_rate`        | `stream_tst_inverted_index_total_updates` (stream's tst-scope index, distinct from its storage series index) |
| `{measure,stream,trace}_series_term_search_rate` | inverted-index `_total_term_searchers_started` (per-type index read pressure) |
| `{measure,stream,trace}_total_series` | inverted-index `_total_doc_count`                                 |
| `stream_tst_total_series`            | `stream_tst_inverted_index_total_doc_count`                        |

**Property type** (`property_*`; the data type that was previously unmodeled):

| Metric (`meter_banyandb_endpoint_*`) | Source (`… by (group)`)                                            |
| ------------------------------------ | ------------------------------------------------------------------ |
| `property_index_write_rate`          | `rate(property_inverted_index_total_updates)`                      |
| `property_index_merge_rate` / `property_index_merge_latency` | `rate(property_inverted_index_total_merge_started)` (×60) / `rate(_merge_latency)/rate(_merge_started)` (×1000 ms) |
| `property_series_term_search_rate`   | `rate(property_inverted_index_total_term_searchers_started)` — property's **real read-load** signal (property is queried via the registry/term-search path, not the liaison `query` method) |
| `property_total_series`              | `property_inverted_index_total_doc_count`                          |

> **Why property gets its own set.** `property` has no `*_total_written`, no `_tst_` storage table, and
> no `stream_storage` scope: its "writes" are inverted-index updates and it is read via the
> registry/term-search path, so `write_rate` / `query_latency` / `total_data` / tst-merge are genuinely
> N/A for property and are intentionally not modeled. Modeling property as a dedicated
> `property_inverted_index_*` set is what stops the `sw_property` groups from rendering an **all-empty**
> dashboard (every panel in the old unified catalog resolved to no data for a property group).

**Type-agnostic** (keyed on `group` + `operation`; bare names):

| Metric (`meter_banyandb_endpoint_*`) | Source (`… by (group, operation)`)                                 |
| ------------------------------------ | ------------------------------------------------------------------ |
| `queue_throughput`                   | `rate(queue_sub_total_finished)`                                   |
| `queue_latency_p99`                  | `histogram_quantile(0.99, queue_pub_total_latency_bucket)`         |
| `queue_batch_throughput` / `queue_message_throughput` | `rate(queue_sub_total_batch_finished)` / `rate(queue_sub_total_message_finished)` (BanyanDB #1169) |
| `publish_bytes`                      | `rate(queue_pub_sent_bytes) by (group)`                            |

> **Semantic note.** A BanyanDB `group` is a *storage group*, not an HTTP route. Modeling it as an
> Endpoint is mechanically exact and precedented (the same way `elasticsearch-index`, `rocketmq-topic`
> and `apisix` model non-RPC nouns as endpoints), but operators should read "Endpoint" as "storage
> group". Endpoint-only UI affordances that assume RPC semantics (endpoint dependency map, endpoint
> traces, slow-endpoint lists) are not meaningful here and are intentionally not used.

### 4. Dynamic metrics by role and tier

Different roles expose different metrics, so the **instance dashboard must adapt to the selected
container**. Role/type separation is carried **two ways that complement each other**: by the
**metric-name prefix** (`liaison_*` / `data_*` / `lifecycle_*` on the Instance scope,
`measure_*` / `stream_*` / `trace_*` / `property_*` on the Endpoint scope — see
[Instance scope](#32-instance-scope--per-container-banyandb-instanceyaml) and
[Endpoint scope](#33-endpoint-scope--per-group-banyandb-endpointyaml)), which lets the layer template select
a whole panel set by prefix; and by the structured, **server-evaluated** `visibleWhen` gate below,
which hides individual widgets. Horizon UI's widget `visibleWhen` is a structured gate (the BFF
resolves it against data presence or the selected instance's attributes and returns gated-out widgets
as hidden; legacy free-text predicate strings are no longer parsed and degrade to ungated). Two gate
kinds, layered:

**(a) Data-presence gating — available today, no UI code.** The `mqe`-kind gate hides a widget whose
expression returns no data. Each MAL rule only produces samples for containers that emit its source
metric, so liaison-only metrics are simply absent on data instances and vice-versa. This gives correct
adaptive behavior out of the box:

```jsonc
{ "id": "wqueue", "title": "Write Queue (wqueue)", "type": "line",
  "expressions": ["meter_banyandb_instance_wqueue_pending"],
  "visibleWhen": { "kind": "mqe", "expression": "meter_banyandb_instance_wqueue_pending", "op": "exists" } }
```

**(b) Attribute gating — equality ships today; membership is the proposed extension (see
[entity-gate membership operators](#6-horizon-ui-enhancement-entity-gate-membership-operators)).**
Data-presence can't distinguish "wrong role" from "idle but right role", and it still issues the query.
The `entity`-kind gate keys panel visibility directly on the selected instance's `container_name` /
`node_type` attributes (meaningful on the Instance scope only):

```jsonc
{ "id": "wqueue", "visibleWhen": { "kind": "entity", "attribute": "container_name", "op": "eq", "value": "liaison" } }
{ "id": "cold_tier_note", "visibleWhen": { "kind": "entity", "attribute": "node_type", "op": "eq", "value": "cold" } }
```

This is the precise, declarative form, and it is the natural way to express tier-specific panels (a
`hot` data container merges constantly; a `cold` container is mostly static). The landed gate supports
`exists` and case-insensitive `eq`; tier *sets* need the proposed `in` operator — until it lands they
are expressible as duplicated `eq`-gated widget variants.

Role/tier scoping of the catalog:

| Bucket          | Panels                                                                 | Entity gate                        |
| --------------- | --------------------------------------------------------------------- | ---------------------------------- |
| **All roles**   | system resources, disk-by-path, network, Go runtime, node uptime      | (always shown)                     |
| **Liaison**     | gRPC query & errors, non-query ops, write rate, publish throughput & latency, wqueue depth | `container_name eq liaison` |
| **Data**        | storage totals, merge/compaction, inverted index, subscribe queue, retention | `container_name eq data`     |
| **Data + tier** | tier-specific merge/retention hints                                   | `node_type in (hot, warm)` †       |
| **Lifecycle**   | migration cycles, last-run time + status                              | `container_name eq lifecycle`      |

† `in` is the proposed extension of [section 6](#6-horizon-ui-enhancement-entity-gate-membership-operators);
until it lands, two `eq`-gated widget variants.

### 5. Dashboards (Horizon UI BANYANDB layer template)

A net-new layer template `apps/bff/src/bundled_templates/layers/banyandb.json` (config-driven JSON, one
file per layer keyed by its `key` field — `BANYANDB`, filename lowercased — with per-scope widget
arrays and MQE expression strings). One menu touchpoint exists: Horizon UI currently hard-codes the
`BANYANDB` layer out of the sidebar (`HIDDEN_LAYERS`);
[horizon-ui #47](https://github.com/apache/skywalking-horizon-ui/pull/47) replaces that with a
config-driven `layers.excluded` list that un-hides BanyanDB — this SWIP rides on that change (or an
equivalent one-line un-hide). The design mirrors the upstream two boards across the SkyWalking
hierarchy:

```
BANYANDB layer
├─ Root            → cluster list (the layer landing's service-list picker: header columns + sort)
├─ Service (cluster)
│   └─ Overview KPIs + "Cluster Workload Summary" + "Fleet Overview" capacity
│       (cluster_write_rate, cluster_query_rate, cluster_error_rate,
│        reporting_instances by role, total_cpu/memory/disk)
├─ Instance (container)   ← the "Nodes" board, made dynamic; instance = container_name@pod_name
│   ├─ All roles: Resources (CPU/RSS/mem%/disk%), Disk by Path, Network, Go Runtime
│   ├─ Liaison (entity gate container_name eq liaison): Ingestion/Query, Registry, Errors,
│   │     Publish throughput & p99, Write Queue (wqueue) depth
│   ├─ Data (entity gate container_name eq data): Storage totals, Merge, Inverted Index, Retention,
│   │     Subscribe Queue (per operation: query/file-sync/batch-write/control)
│   └─ Lifecycle (entity gate container_name eq lifecycle): migration cycles, last-run time + status
└─ Endpoint (group)  ← the "Workload" board, by group
    └─ Write rate, Query latency, Total data, Merge, Inverted index, Queue, Publish bytes
```

Panel **types/units** follow the upstream Grafana boards for fidelity (stat for KPIs; timeseries for
rates/latencies; `bytes` / `percentunit` / `s` / `reqps` / `wps` units; disk% and memory% turn red at
80%). The upstream per-node "health table" (uptime, CPU cores, RSS, mem%, disk%) maps onto the
all-roles Resources widgets of the Instance view — Horizon UI's instance list deliberately shows only
name + attributes (the role/tier chips), and per-instance metric columns are not assumed by this
design; if embedded health columns prove necessary later, that is an additive Horizon UI enhancement.

This is **design only** — the production `banyandb.json` and its exact widget grid are deliberately left
to the implementation PR in the Horizon UI repository.

### 6. Horizon UI enhancement: entity-gate membership operators

When this SWIP was first drafted, Horizon UI parsed `visibleWhen` as free text and stubbed the
entity-attribute form. That is no longer the upstream state: horizon-ui PR #46 (merged 2026-06-08)
replaced the free-text parser with a structured, **BFF-evaluated** union —

- `{ "kind": "mqe", "expression": "<expr>", "op": "exists" }` — data-presence gating;
- `{ "kind": "entity", "attribute": "<key>", "op": "exists" }` /
  `{ "kind": "entity", "attribute": "<key>", "op": "eq", "value": "<v>" }` — entity-attribute gating
  against the selected instance's attribute feed (`eq` compares case-insensitively; meaningful on the
  Instance scope only, a no-op elsewhere)

— so the attribute feed and the evaluator this section originally proposed **already exist upstream**:
the BFF fetches the selected instance's `attributes [{name,value}]` and returns gated-out widgets as
hidden. Legacy free-text predicates (`"<metric> has value"`, `"#entity.<key>"`) are no longer parsed
and degrade to ungated.

What remains for this design is only **membership and negation**:

| Proposed gate                                                                        | Meaning              |
| ------------------------------------------------------------------------------------ | -------------------- |
| `{ "kind": "entity", "attribute": "<key>", "op": "neq", "value": "<v>" }`             | not-equals a literal |
| `{ "kind": "entity", "attribute": "<key>", "op": "in", "values": ["<v1>", "<v2>"] }`  | membership           |

Scope of the enhancement (design): (1) add the two operator arms to the BFF `visibleWhen` schema and
its entity-gate evaluator; (2) document them in the Horizon UI layer-template authoring docs. Until it
lands, a tier set like `node_type in (hot, warm)` is expressible as two `eq`-gated widget variants —
`in` removes the duplication. It is generic — any layer (K8s node roles, gateway tiers, …) benefits;
BanyanDB is the first consumer. The exact code lands in the Horizon UI repository.

### 7. Intra-cluster instance topology (the "deployment" component)

Beyond the per-instance dashboards, the BanyanDB layer adds a **deployment view**: the
container-to-container call graph *within* the single BanyanDB cluster service — liaison↔data writes,
the hot→warm→cold lifecycle migration chain, and inter-liaison gossip. The legacy booster UI only ever
drew instance topology *between two services*; this is a net-new Horizon UI component for the
**one-service** case (landing via horizon-ui PR #47).

**Data path — no query API change.** The component calls
`getServiceInstanceTopology(clientServiceId, serverServiceId, duration)` with the **same** service id
on both sides. OAP's relation filter is symmetric, so `client == server == svc` collapses to
`source_service_id == dest_service_id == svc`, returning exactly the intra-cluster instance relations
(verified across the BanyanDB / JDBC / ES topology DAOs). Per-node metrics evaluate under
`{ scope: ServiceInstance }`; per-edge metrics under `ServiceInstanceRelation` (server + client
families) — both ordinary MQE. **The relation edges are now emitted by a shipped rule file** —
`banyandb-instance-relation.yaml` (below) — so `getServiceInstanceTopology` renders the live deployment
graph instead of an empty state.

**Grouping contract.** The component lays the graph out from the instance attributes this SWIP emits
([entity model](#1-entity-model)):

| Config key  | Attribute(s)              | Effect                                                          |
| ----------- | ------------------------- | -------------------------------------------------------------- |
| `clusterBy` | `node_role` + `node_type` | one box per role/tier — liaison, data hot/warm/cold            |
| `siblingBy` | `pod_name`                | a pod = main container + sibling containers (data + lifecycle)  |
| `roleBy`    | `container_name`          | per-role node metrics (`liaison` / `data` / `lifecycle`)        |

Per-role node MQE binds to the prefixed `meter_banyandb_instance_*` metrics from the catalog above —
e.g. liaison → `liaison_query_rate` / `liaison_write_rate`, data → `data_total_data` /
`disk_usage_percent`, lifecycle → `lifecycle_migration_cycles` / `lifecycle_last_run_success`. Only
`container_name` ∈ {`liaison`, `data`, `lifecycle`} exists on the wire — there is **no `fodc`
container** (the FODC agent publishes no self-metrics through the proxy), so a `fodc` role is not
modeled.

**The MAL `SERVICE_INSTANCE_RELATION` scope shipped.** This feature is MAL-only: every BanyanDB
entity, metric, and attribute here is produced by the `banyandb/*` MAL rules. MAL builds relations
through `MeterEntity` / `ScopeType`, which already shipped `SERVICE_RELATION` and `PROCESS_RELATION`
(the latter powers the eBPF process topology via `network-profiling.yaml`). This SWIP **added the third
relation scope**: a `SERVICE_INSTANCE_RELATION` `ScopeType`, the `MeterEntity.newServiceInstanceRelation(...)`
factory, the `SampleFamily.serviceInstanceRelation(...)` builder, and the
`ServiceInstanceRelationEntityDescription` — in `server-core` and `meter-analyzer`, mirroring the two
relation scopes that already shipped — plus the `Analyzer` server/client-side relation-traffic emission.
With it, `banyandb-instance-relation.yaml` emits **twelve per-edge metrics**
(`publish_*` from the liaison's CLIENT-side `queue_pub_*`, `queue_sub_*` from each peer's SERVER-side
`queue_sub_*`, and `migration_*` from the lifecycle sidecar's CLIENT-side `lifecycle_migration_*` — each
in `_throughput` / `_latency_p99` / `_error_throughput` / `_bytes_throughput` forms), keyed by the queue
`remote_node` / `remote_role` labels (the lifecycle sender identity now arrives per BanyanDB #1167). The
peer's `remote_node` address is split to its `remote_pod_name` (first segment) so the relation endpoints
resolve to the same `container_name '@' pod_name` instances `banyandb-instance.yaml` emits.
`getServiceInstanceTopology` therefore renders the intra-cluster deployment graph live — the component,
the query path, and the grouping contract above are all in place. (The lifecycle's last-run timestamp /
status stay instance-scope: they are label-less per-instance gauges with no `remote_node`, so they
cannot key a per-edge relation metric until BanyanDB stamps the destination labels on them.)

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
- **Layer.** `Layer.BANYANDB` (ordinal 43) already exists; layer dashboards are auto-discovered from
  the template's own `key` field. The one menu touchpoint: Horizon UI's hard-coded hidden-layers set
  currently drops `BANYANDB` from the sidebar — un-hidden by horizon-ui PR #47's config-driven
  `layers.excluded` (see [Dashboards](#5-dashboards-horizon-ui-banyandb-layer-template)).

## Live validation

The entity scheme and the metric catalog above were validated against a **live 7-node BanyanDB
cluster** — the public SkyWalking demo's FODC proxy `/metrics` (2 liaison + 5 data: `hot×2`, `warm×2`,
`cold×1`), running an upstream `main` build (the showcase-pinned image of 2026-06-09; upstream PR
[#1159](https://github.com/apache/skywalking-banyandb/pull/1159) — open, docs and Grafana dashboards
only — documents the same catalog). The live `/metrics` pull is the authoritative wire reference.
393 metric families. Findings:

- **Instance must be `pod_name` + `container_name`, not `pod_name`.** Every sample carries `pod_name`,
  `node_role` (`ROLE_LIAISON` / `ROLE_DATA` observed; the FODC agent stamps a transient
  `ROLE_UNSPECIFIED` for unresolved or meta-only nodes), `container_name`
  (`liaison` / `data` / **`lifecycle`**), and — on **data containers only** — `node_type`
  (`hot` / `warm` / `cold`). Crucially, the four `data` hot/warm pods each run **two containers under
  one `pod_name`** (`data@…` and `lifecycle@…`), so `pod_name` is not a unique instance key and
  `node_role` is not the discriminator (it reads `ROLE_DATA` on the lifecycle sidecar). This validates
  Service = `cluster`, Instance = `pod_name` + `container_name`, attributes `container_name` / `node_type`.
- **The `lifecycle` migrator surfaces as its own container instance.** It co-locates on the `hot`/`warm`
  data pods and emits `banyandb_lifecycle_cycles_total` plus the shared `system_*` / `go_*` /
  `process_*` runtime families — 50 families under `container_name=lifecycle` in the demo pull. The
  `last_run_timestamp_seconds` / `last_run_success` gauges (BanyanDB #1167) post-date the demo's
  deployed build, so they were absent from that pull but are present on `main` and emit once a migration
  cycle runs (the showcase has since pinned the BanyanDB #1167 merge SHA, so a redeployed demo will
  expose them).
- **The queue model is confirmed verbatim.** `banyandb_queue_sub_*` / `queue_pub_*` carry
  `operation` ∈ {`batch-write`, `control`, `file-sync`, `query`}, plus `group`, `remote_node`,
  `remote_role` (`liaison` / `data`) and `remote_tier` (`hot` / …); `total_latency` is a histogram. The
  `remote_*` labels reconstruct the liaison↔data(tier) call graph end-to-end.
- **system / storage / index families confirmed.** `system_disk{kind,path}` (`kind` ∈ `total` /
  `used` / `used_percent`), `system_net_state{kind,name}`, `system_memory_state{kind}`,
  `liaison_grpc_total_started{group,method,service}`, `*_total_written{group}`,
  `*_inverted_index_*{group,seg,node_type}`. Data-node metrics also carry `node_type`, so the by-group
  endpoint view can be refined by tier.
- **Two registry/schema scopes coexist (corrected).** The live cluster exposes **both**
  `banyandb_liaison_grpc_total_registry_*` (`group`, `service`, `method`; on liaison containers) **and**
  a separate `banyandb_schema_server_grpc_*` scope (`total_started{method}`, `_finished`, `_latency`,
  `_err`; on the data container hosting the metadata/schema server). The `cluster_error_rate` and
  registry panels should pick one deliberately — they are different layers, not aliases. (An earlier
  draft claimed the `liaison_grpc_total_registry_*` series were absent; BanyanDB `main` has emitted
  them since BanyanDB #517.)
- **`storage_retention_*` is a real data-only family** not in earlier drafts:
  `storage_retention_{measure,stream,trace}_disk_usage_percent{service}` and
  `_forced_retention_cooldown_seconds{service}` — the source for the data-container retention panels.
- **Error counters are absent on a healthy cluster, by design.** `liaison_grpc_total_err`,
  `liaison_grpc_total_stream_msg_received_err`, `*_total_sync_loop_err` and `queue_pub_total_err` are
  label-dimensioned counters that emit no series until the first error. The upstream Grafana "Error
  Rate" panel guards each term with PromQL's `or vector(0)`; the MAL rules need no guard — an absent
  family is the identity for MAL's `+` (see the sketch-notation note in the metric catalog, section 3)
  — the summed metric simply has no series until the first error fires. Their non-error siblings
  (`_started` / `_finished` / `_latency` / `_bytes`) are all present.

## Imported Dependencies libs and their licenses

None. The design reuses the existing OpenTelemetry receiver, the MAL engine, the `BANYANDB` layer, and
the Horizon UI template engine. The only new artifacts are configuration/template/doc assets (MAL rule
YAML, a Horizon UI layer JSON, and docs) plus a small, self-contained Horizon UI predicate enhancement.
No new third-party dependency is introduced.

## Compatibility

This is a **breaking change** to the BanyanDB self-observability feature (an internal monitoring
feature, not a public protocol/storage contract):

- **Entity model.** A BanyanDB cluster that previously appeared as *N* services (one per node) now
  appears as *one* service with one instance **per container** (`pod_name` + `container_name`, so a
  data hot/warm pod yields both a `data` and a `lifecycle` instance). Old per-node `Service` entities
  and their `meter_banyandb_*` / `meter_banyandb_instance_*` metric series are superseded; the new
  series use the cluster/container/group identities and a partly new metric set. Historical data under
  the old model is not migrated.
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
- **The Horizon UI entity-gate extension is backward compatible** — `neq` / `in` are additive arms of
  the structured `visibleWhen` union (horizon-ui #46); templates that don't use them are unaffected,
  and legacy free-text predicates already degrade to ungated rather than erroring.

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
- An **instance (container) list** where each entry shows its **container** role
  (`liaison` / `data` / `lifecycle`) and **tier** (`hot` / `warm` / `cold`) as attributes; selecting one
  shows a dashboard **scoped to what that container actually does** — ingestion/queue/publish for
  liaison, storage/index/subscribe/retention for data, migration cycles + last-run time/status for
  lifecycle, refined by tier.
- A **group list** (Endpoints) with per-group throughput, latency, storage, index and queue health.

## Future work

- **A MAL `SERVICE_INSTANCE_RELATION` scope for the deployment component — done.** The third relation
  scope (`ScopeType` + `MeterEntity.newServiceInstanceRelation(...)` factory +
  `SampleFamily.serviceInstanceRelation(...)` builder + `ServiceInstanceRelationEntityDescription`,
  mirroring the shipping `serviceRelation` / `processRelation`) **shipped with this SWIP**, so the
  [intra-cluster instance topology](#7-intra-cluster-instance-topology-the-deployment-component) renders
  live from `banyandb-instance-relation.yaml`, fed by the queue `remote_node` / `remote_role` labels
  (BanyanDB #1167 also populates the lifecycle migration sender identity, so hot→warm→cold tier-migration
  edges are distinguishable). Remaining: model the lifecycle's last-migration timestamp / status as
  per-edge relation metrics once BanyanDB stamps destination labels on those gauges, and surface FODC
  `/cluster/topology` and `/cluster/lifecycle` group settings (shards / segment interval / TTL) on the
  Endpoint view.
- **Alerting.** Ship default alarm rules for the upstream "Key Signals to Watch" (query p99, error rate,
  disk > 85%, memory near the protector limit, sustained wqueue / `queue_pub` backlog).
- **Direct-scrape variant** for standalone / non-FODC deployments, if demand warrants.
