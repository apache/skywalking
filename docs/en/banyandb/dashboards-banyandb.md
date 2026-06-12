# BanyanDB self-observability dashboard

[Apache SkyWalking BanyanDB](https://skywalking.apache.org/docs/skywalking-banyandb/next/readme/) is the
native storage for SkyWalking. A production deployment is one **cluster** made of many **nodes**, each
running one or more **containers** with a role (`liaison` front door, `data` backend, and the `lifecycle`
tier-migration sidecar), and data is organized into **groups**. SkyWalking models that reality directly
and renders it on the `Layer: BANYANDB` dashboards in the Horizon UI:

| SkyWalking entity | BanyanDB concept | Identity |
| ----------------- | ---------------- | -------- |
| `Service` | one BanyanDB **cluster** | the `cluster` label |
| `ServiceInstance` | one **container** on a node | `container_name` + `pod_name` (joined by `@`, e.g. `data@…-data-hot-0`) |
| &nbsp;&nbsp;↳ attributes | role / tier | `container_name` (`liaison`/`data`/`lifecycle`), `node_type` (`hot`/`warm`/`cold`), `node_role`, `pod_name` |
| `Endpoint` | one **group** (storage partition) | the `group` label (e.g. `sw_metricsMinute`) |

> **Requires BanyanDB 0.11+.** This feature reads the FODC-proxy cluster-observability metric families
> and the queue / lifecycle metric families that BanyanDB introduced after 0.10. Run a 0.11+ cluster
> with the FODC proxy and the Prometheus metrics provider enabled.

## Data flow

1. Each BanyanDB container exposes its metrics; in a cluster the
   [FODC proxy](https://skywalking.apache.org/docs/skywalking-banyandb/next/operation/fodc/overview/)
   aggregates every container's Prometheus metrics onto a single `/metrics` endpoint (default `:17913`)
   and stamps each sample with per-container identity labels (`pod_name`, `container_name`, `node_role`,
   and `node_type` on data containers).
2. An OpenTelemetry Collector scrapes the FODC proxy `/metrics` as the single Prometheus target, adds a
   static `cluster: <name>` label (the only label SkyWalking must inject), and pushes via the
   OpenTelemetry gRPC exporter to the SkyWalking OAP Server.
3. The OAP Server parses the [MAL](../concepts-and-designs/mal.md) rules under `otel-rules/banyandb/` to
   filter / calculate / aggregate and store the cluster, instance and group metrics.

## Set up

1. Run a BanyanDB **0.11+** cluster (liaison + data nodes; data nodes may be tiered hot/warm/cold) with
   the **FODC proxy** enabled and the Prometheus metrics provider on (default). Standalone mode is the
   degenerate case — one cluster, one node, one `container_name=standalone`.
2. Run an **OpenTelemetry Collector** whose `prometheus` receiver scrapes the FODC proxy `/metrics`
   (`:17913`) as the single target and adds a static `cluster: <name>` label, exporting OTLP to OAP.
   The FODC proxy already stamps the per-node identity labels (`pod_name` / `container_name` /
   `node_role` / `node_type`), so `cluster` is the only label the collector must inject.

   > **The scrape `job_name` MUST be `banyandb-monitoring`.** Every rule file filters on
   > `{ tags -> tags.job_name == 'banyandb-monitoring' }` (the OTel receiver maps the Prometheus `job`
   > to the `job_name` tag), so a differently-named job produces no metrics.

   ```yaml
   receivers:
     prometheus:
       config:
         scrape_configs:
           - job_name: "banyandb-monitoring"          # REQUIRED — the rules filter on it
             scrape_interval: 15s
             static_configs:
               - targets: ["<fodc-proxy-host>:17913"] # the FODC proxy aggregates every node's metrics
                 labels:
                   cluster: <your-cluster-name>        # the only label SkyWalking must inject
   exporters:
     otlp:
       endpoint: <oap-host>:11800
       tls:
         insecure: true
   service:
     pipelines:
       metrics:
         receivers: [prometheus]
         processors: [batch]
         exporters: [otlp]
   ```

   Scrape the **FODC proxy**, not the individual nodes. The proxy resolves each node's identity from
   the cluster and stamps `pod_name` / `container_name` / `node_role` and the data-node tier
   (`node_type`) onto every sample — context the raw per-node `:2121` endpoints do not carry. Direct
   per-node scraping is not recommended: it would have to hand-inject all of those identity labels for
   every node. (The e2e does exactly that only because it runs no FODC proxy — see the
   [test collector config](../../../test/e2e-v2/cases/banyandb/otel-collector-config.yaml) — and is not
   a production pattern.)
3. Enable SkyWalking's
   [OpenTelemetry receiver](https://skywalking.apache.org/docs/main/next/en/setup/backend/opentelemetry-receiver/).
   The `banyandb/*` rules are enabled by default in `enabledOtelMetricsRules`.
4. Open the **Horizon UI** → `BanyanDB` layer.

## Metrics

The metric source expressions mirror the upstream BanyanDB Grafana boards, so the SkyWalking dashboards
stay in lockstep with the BanyanDB catalog. The rule files are
`otel-rules/banyandb/banyandb-service.yaml`, `banyandb-instance.yaml`, `banyandb-endpoint.yaml` and
`banyandb-instance-relation.yaml`.

The instance and endpoint catalogs are **category-separated**: the rule name carries a role prefix
(instance scope) or a data-type prefix (endpoint scope) so that a human can read a metric name and know
which role / data type it belongs to, and so the UI layer template can select the right panel set. The
storage prefix is on the rule **name** only — every metric still carries the
`meter_banyandb_{instance,endpoint,instance_relation}_` family prefix, and the scope / entity keys are
unchanged.

### Service scope — cluster summary (`meter_banyandb_*`)

| Unit | Metric | Description |
| ---- | ------ | ----------- |
| w/s | `meter_banyandb_cluster_write_rate` | Cluster write rate across measure/stream/trace |
| r/s | `meter_banyandb_cluster_query_rate` | Cluster query rate |
| c/m | `meter_banyandb_cluster_error_rate` | Cluster error rate (counts/min) |
| Count | `meter_banyandb_reporting_instances` | Live container count by role |
| Count | `meter_banyandb_total_cpu_cores` | Cluster CPU capacity |
| Bytes | `meter_banyandb_total_memory_used` | Cluster memory used |
| Bytes | `meter_banyandb_total_disk_used` | Cluster disk used |

### Instance scope — per container (`meter_banyandb_instance_*`)

Instance rules are **role-separated by name prefix**. The shared resource / runtime block stays
**unprefixed** (the family is inherently per-instance and resolves on whatever container emits it);
front-door families carry a `liaison_*` prefix, storage / index / queue families a `data_*` prefix, and
the migration-sidecar health triple a `lifecycle_*` prefix. The prefix lets the UI select the panel set
per role (`container_name` `liaison` / `data` / `lifecycle`) and disambiguates the same wire family read
under two roles (e.g. the `pending_data_count` family is `liaison_wqueue_pending` on the front door and
`data_wqueue_pending` on the backend — each role rule reads only its own container's series).

**Shared — resources / disk-by-path / Go runtime** (unprefixed; every container emits these, except
`node_uptime` which is absent on `lifecycle` — that container runs the metric service without the system
collector):

| Unit | Metric | Description |
| ---- | ------ | ----------- |
| s | `node_uptime` | Node uptime |
| Cores | `cpu_usage` | CPU usage |
| Bytes | `rss_memory` | Resident memory |
| percent | `system_memory_percent` | System memory used % |
| percent | `disk_usage_percent` | Disk used % (BanyanDB `used_percent`, averaged across the node's data paths, which share one filesystem) |
| Bytes | `disk_used_by_path` / `disk_total_by_path` | Disk used / total bytes by mount path |
| percent | `disk_used_percent_by_path` | Disk used % by mount path |
| Bytes/s | `network_recv` / `network_sent` | Network throughput by interface |
| Count | `goroutines` | Go goroutines |
| s | `gc_pause_avg` | Average GC pause |
| Bytes | `heap_inuse` / `heap_next_gc` | Go heap in-use / next-GC threshold |
| Bytes/s | `alloc_rate` | Go allocation rate |

**Liaison** (`liaison_*`; front door — the dashboard gates these on `container_name == 'liaison'`):

| Unit | Metric | Description |
| ---- | ------ | ----------- |
| r/s | `liaison_query_rate` | Query rate by data-model service (`measure`/`stream`/`trace`/`property`) |
| c/m | `liaison_grpc_error_rate` | gRPC error rate (total + registry + stream-msg-received errors) |
| r/s | `liaison_registry_op_rate` | Schema-registry / non-query operation rate |
| w/s | `liaison_write_rate` | Write rate seen at the front door |
| ops | `liaison_publish_throughput` | Tier-2 publish throughput by operation (liaison → data) |
| Bytes/s | `liaison_publish_bytes` | Publish bytes |
| s | `liaison_publish_latency_p99` | Publish send latency p99 |
| ops | `liaison_publish_batch_throughput` | Tier-2 publish batch throughput by operation (build-gated, BanyanDB #1169) |
| s | `liaison_publish_batch_latency_p99` | Publish batch send latency p99 (build-gated, BanyanDB #1169) |
| Count | `liaison_wqueue_pending` | Front-door write-queue pending records |

**Data** (`data_*`; backend — the dashboard gates these on `container_name == 'data'`):

| Unit | Metric | Description |
| ---- | ------ | ----------- |
| Count | `data_total_data` | Total stored data elements |
| Count | `data_wqueue_file_parts` | Write-queue on-disk file parts |
| Count | `data_wqueue_mem_part` | Write-queue in-memory parts |
| Count | `data_wqueue_pending` | Write-queue pending records |
| o/s | `data_merge_file_rate` | Merge-loop rate |
| Count | `data_merge_file_partitions` | Avg parts merged per loop (file path) |
| s | `data_merge_file_latency` | Avg file-merge latency |
| o/s | `data_series_write_rate` | Inverted-index write rate (measure + stream + trace storage indexes) |
| o/s | `data_series_term_search_rate` | Inverted-index term-search rate |
| Count | `data_total_series` | Inverted-index documents (measure + stream + trace storage indexes) |
| o/s | `data_stream_tst_write_rate` | Stream tst index write rate |
| o/s | `data_stream_tst_term_search_rate` | Stream tst index term-search rate |
| Count | `data_stream_tst_total_docs` | Stream tst index documents |
| ops | `data_queue_sub_throughput` | Subscribe-queue throughput by operation |
| s | `data_queue_sub_latency_p99` | Subscribe-queue latency p99 |
| ops | `data_queue_sub_message_throughput` | Subscribe-queue per-message throughput by operation (BanyanDB #1169) |
| percent | `data_retention_measure_disk_usage_percent` | Retention disk-usage % (measure scope) |
| percent | `data_retention_stream_disk_usage_percent` | Retention disk-usage % (stream scope) |
| percent | `data_retention_trace_disk_usage_percent` | Retention disk-usage % (trace scope) |

> The trace storage inverted index is now folded into `data_series_write_rate` /
> `data_series_term_search_rate` / `data_total_series` (it was silently dropped in the previous,
> measure+stream-only design).

**Lifecycle** (`lifecycle_*`; the tier-migration sidecar on hot/warm data pods —
`container_name == 'lifecycle'`):

| Unit | Metric | Description |
| ---- | ------ | ----------- |
| Count | `lifecycle_migration_cycles` | Cumulative migration cycles |
| s | `lifecycle_last_run` | Seconds since the last migration cycle started (build-gated, BanyanDB #1167+) |
| Status | `lifecycle_last_run_success` | Last cycle status (1 = OK, 0 = failed; build-gated, BanyanDB #1167+) |

### Endpoint scope — per group (`meter_banyandb_endpoint_*`)

A group carries exactly one data-model type, and each type emits a different family namespace, so the
endpoint rules are **type-separated by name prefix** (`measure_*` / `stream_*` / `stream_tst_*` /
`trace_*` / `property_*`). The previous design summed measure + stream + trace into one unified rule per
concept, which (a) rendered all-empty panels for a `property` group and (b) silently dropped the trace
inverted index from `series_*` / `total_series`. The per-type split makes each rule read only the
families its type genuinely emits, and the UI selects the panel set by the group's data type.

The queue / publish metrics stay **type-agnostic** (keyed on `group` + `operation`, not on a data-model
type) and keep their bare names.

**Measure** (`measure_*`):

| Unit | Metric | Description |
| ---- | ------ | ----------- |
| w/s | `measure_write_rate` | Write rate for the group |
| s | `measure_query_latency` | Mean query latency for the group |
| Count | `measure_total_data` | Total stored data elements for the group |
| o/s | `measure_merge_file_rate` | Merge-loop rate for the group |
| s | `measure_merge_file_latency` | Avg file-merge latency for the group |
| Count | `measure_merge_file_partitions` | Avg parts merged per loop (file path) for the group |
| o/s | `measure_series_write_rate` | Inverted-index write rate for the group |
| o/s | `measure_series_term_search_rate` | Inverted-index term-search rate for the group |
| Count | `measure_total_series` | Inverted-index documents for the group |

**Stream** (`stream_*` for the storage scope, `stream_tst_*` for the time-series-table scope):

| Unit | Metric | Description |
| ---- | ------ | ----------- |
| w/s | `stream_write_rate` | Write rate for the group |
| s | `stream_query_latency` | Mean query latency for the group |
| Count | `stream_total_data` | Total stored data elements for the group |
| o/s | `stream_merge_file_rate` | Merge-loop rate for the group |
| s | `stream_merge_file_latency` | Avg file-merge latency for the group |
| Count | `stream_merge_file_partitions` | Avg parts merged per loop (file path) for the group |
| o/s | `stream_series_write_rate` | Storage-scope inverted-index write rate for the group |
| o/s | `stream_series_term_search_rate` | Storage-scope inverted-index term-search rate for the group |
| Count | `stream_total_series` | Storage-scope inverted-index documents for the group |
| o/s | `stream_tst_index_write_rate` | Tst-scope inverted-index write rate for the group |
| Count | `stream_tst_total_series` | Tst-scope inverted-index documents for the group |

**Trace** (`trace_*`):

| Unit | Metric | Description |
| ---- | ------ | ----------- |
| w/s | `trace_write_rate` | Write rate for the group |
| s | `trace_query_latency` | Mean query latency for the group |
| Count | `trace_total_data` | Total stored data elements for the group |
| o/s | `trace_merge_file_rate` | Merge-loop rate for the group |
| s | `trace_merge_file_latency` | Avg file-merge latency for the group |
| Count | `trace_merge_file_partitions` | Avg parts merged per loop (file path) for the group |
| o/s | `trace_series_write_rate` | Storage-scope inverted-index write rate for the group |
| o/s | `trace_series_term_search_rate` | Storage-scope inverted-index term-search rate for the group |
| Count | `trace_total_series` | Storage-scope inverted-index documents for the group |

**Property** (`property_*`; the **new** data type — `sw_property` groups previously rendered all-empty
panels and now have their own metrics):

| Unit | Metric | Description |
| ---- | ------ | ----------- |
| o/s | `property_index_write_rate` | Inverted-index update rate (property "writes" are index updates) |
| o/s | `property_index_merge_rate` | Inverted-index segment merge rate |
| s | `property_index_merge_latency` | Mean inverted-index merge latency |
| o/s | `property_series_term_search_rate` | Term-search rate (property's real read-load signal — read via the registry/term-search path, not the liaison `query` method) |
| Count | `property_total_series` | Inverted-index documents for the group |

> Property has no `*_total_written`, no tst table and no storage scope: `write_rate` / `query_latency` /
> `total_data` are genuinely N/A for property and are not modeled — `property_index_*` /
> `property_series_term_search_rate` carry the equivalent write and read load instead.

**Queue / publish** (type-agnostic; keyed on `group` + `operation`):

| Unit | Metric | Description |
| ---- | ------ | ----------- |
| ops | `queue_throughput` | Subscribe-queue throughput by operation for the group |
| s | `queue_latency_p99` | Publish-queue latency p99 for the group |
| ops | `queue_batch_throughput` | Subscribe-queue batch throughput by operation for the group (BanyanDB #1169) |
| ops | `queue_message_throughput` | Subscribe-queue per-message throughput by operation for the group (BanyanDB #1169) |
| Bytes/s | `publish_bytes` | Publish bytes for the group |

### Instance-relation scope — deployment topology (`meter_banyandb_instance_relation_*`)

The intra-cluster instance topology (the Horizon UI **deployment** component) models the pod-to-pod
flows **within** the single BanyanDB cluster service — the OAP-native equivalent of BanyanDB's Grafana
"Topology: Pod-to-Pod Flows" view. Source and destination service are both the cluster, so the UI reads
these edges via a symmetric, same-service `getServiceInstanceTopology(svc, svc)` query; the Analyzer
emits the `ServiceInstanceRelation` server/client-side rows the deployment graph draws.

Each edge is detected from both ends (client + server resolve to the same relation id and share one edge
entity), and every per-edge metric keeps `operation` as a label so the dashboard can split per
operation. There are three edge kinds:

- **Publish** (`publish_*`, CLIENT side — the liaison fans writes/queries out across the cluster; the
  SUB side below is the same edge's SERVER half).
- **Queue-sub** (`queue_sub_*`, SERVER side — a node subscribes from its peers; the
  `remote_role=lifecycle` slice is the migration edge's SERVER half).
- **Migration** (`migration_*`, CLIENT side — the lifecycle sidecar publishes migrated data to the next
  tier hot → warm → cold).

Each edge kind carries the same four facets:

| Suffix | Unit | Description |
| ------ | ---- | ----------- |
| `_throughput` | msg/s | Per-second rate of finished operations on the edge |
| `_latency_p99` | ms | p99 latency on the edge |
| `_error_throughput` | err/s | Per-second rate of errors on the edge |
| `_bytes_throughput` | B/s | Per-second bytes sent / received on the edge |

| Metric | Description |
| ------ | ----------- |
| `publish_throughput` / `publish_latency_p99` / `publish_error_throughput` / `publish_bytes_throughput` | Liaison publish (CLIENT) edge metrics |
| `queue_sub_throughput` / `queue_sub_latency_p99` / `queue_sub_error_throughput` / `queue_sub_bytes_throughput` | Peer subscribe (SERVER) edge metrics |
| `migration_throughput` / `migration_latency_p99` / `migration_error_throughput` / `migration_bytes_throughput` | Lifecycle migration (CLIENT) edge metrics |

> The lifecycle's last-migration timestamp and status are **not** modeled as edge metrics (they are
> label-less per-instance gauges with no destination labels); they stay instance-scope as
> `lifecycle_last_run` / `lifecycle_last_run_success` / `lifecycle_migration_cycles`. The migration
> *traffic* (throughput / latency / error / bytes) above is already per-edge.

## Customizations

You can customize your own metrics / expressions. The metric definitions and expression rules are in
`/config/otel-rules/banyandb`. The dashboard panel configurations ship from the SkyWalking Horizon UI
bundle (apache/skywalking-horizon-ui); the OAP backend does not host UI dashboard JSONs.
