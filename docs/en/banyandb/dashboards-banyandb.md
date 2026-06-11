# BanyanDB self-observability dashboard

[Apache SkyWalking BanyanDB](https://skywalking.apache.org/docs/skywalking-banyandb/next/readme/) is the
native storage for SkyWalking. A production deployment is one **cluster** made of many **nodes**, each
running one or more **containers** with a role (`liaison` front door, `data` backend, and the `lifecycle`
tier-migration sidecar), and data is organized into **groups**. SkyWalking models that reality directly
and renders it on the `Layer: BANYANDB` dashboards in the Horizon UI:

| SkyWalking entity | BanyanDB concept | Identity |
| ----------------- | ---------------- | -------- |
| `Service` | one BanyanDB **cluster** | the `cluster` label |
| `ServiceInstance` | one **container** on a node | `pod_name` + `container_name` (joined by `@`) |
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
`otel-rules/banyandb/banyandb-service.yaml`, `banyandb-instance.yaml` and `banyandb-endpoint.yaml`.

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

**All roles** (every container emits these):

| Unit | Metric | Description |
| ---- | ------ | ----------- |
| s | `node_uptime` | Node uptime |
| Cores | `cpu_usage` | CPU usage |
| Bytes | `rss_memory` | Resident memory |
| percentunit | `system_memory_percent` | System memory used fraction |
| percentunit | `disk_usage_percent` | Disk used fraction (Σused/Σtotal) |
| Bytes | `disk_used_by_path` / `disk_total_by_path` | Disk used / total by mount path |
| percentunit | `disk_used_percent_by_path` | Disk used fraction by mount path |
| Bytes/s | `network_recv` / `network_sent` | Network throughput by interface |
| Count | `goroutines` | Go goroutines |
| s | `gc_pause_avg` | Average GC pause |
| Bytes | `heap_inuse` / `heap_next_gc` | Go heap in-use / next-GC threshold |
| Bytes/s | `alloc_rate` | Go allocation rate |

**Liaison** (front door; the dashboard gates these on `container_name == 'liaison'`):

| Unit | Metric | Description |
| ---- | ------ | ----------- |
| r/s | `query_rate_by_service` | Query rate by data-model service |
| c/m | `grpc_error_rate` | gRPC error rate |
| r/s | `non_query_op_rate` | Registry / non-query operation rate |
| w/s | `write_rate` | Write rate seen at the front door |
| ops | `publish_throughput` | Tier-2 publish throughput by operation |
| ops | `publish_batch_throughput` | Tier-2 publish batch throughput by operation |
| Bytes/s | `publish_bytes` | Publish bytes |
| s | `publish_latency_p99` | Publish send latency p99 |
| s | `publish_batch_latency_p99` | Publish batch send latency p99 |
| Count | `wqueue_pending` / `wqueue_file_parts` / `wqueue_mem_part` | Write-queue depth |

**Data** (backend; the dashboard gates these on `container_name == 'data'`):

| Unit | Metric | Description |
| ---- | ------ | ----------- |
| Count | `total_data` | Total stored data elements |
| o/s | `merge_file_rate` | Merge-loop rate |
| Count | `merge_file_partitions` | Avg parts merged per loop |
| s | `merge_file_latency` | Avg file-merge latency |
| o/s | `series_write_rate` / `series_term_search_rate` | Inverted-index write / term-search rate |
| Count | `total_series` | Inverted-index documents |
| o/s | `stream_tst_write_rate` / `stream_tst_term_search_rate` | Stream tst index write / term-search rate |
| Count | `stream_tst_total_docs` | Stream tst index documents |
| ops | `queue_sub_throughput` | Subscribe-queue throughput by operation |
| ops | `queue_sub_message_throughput` | Subscribe-queue per-message throughput by operation |
| s | `queue_sub_latency_p99` | Subscribe-queue latency p99 |
| percent | `retention_measure_disk_usage_percent` / `retention_stream_disk_usage_percent` / `retention_trace_disk_usage_percent` | Retention disk-usage % per scope |

**Lifecycle** (the tier-migration sidecar on hot/warm data pods; `container_name == 'lifecycle'`):

| Unit | Metric | Description |
| ---- | ------ | ----------- |
| Count | `lifecycle_cycles` | Cumulative migration cycles |
| s | `lifecycle_last_run` | Seconds since the last migration cycle started |
| Status | `lifecycle_last_run_success` | Last cycle status (1 = OK, 0 = failed) |

### Endpoint scope — per group (`meter_banyandb_endpoint_*`)

| Unit | Metric | Description |
| ---- | ------ | ----------- |
| w/s | `write_rate` | Write rate for the group |
| s | `query_latency` | Mean query latency for the group |
| Count | `total_data` | Total stored data elements for the group |
| o/s | `merge_file_rate` | Merge-loop rate for the group |
| s | `merge_file_latency` | Avg file-merge latency for the group |
| Count | `merge_file_partitions` | Avg parts merged per loop for the group |
| o/s | `series_write_rate` | Inverted-index write rate for the group |
| Count | `total_series` | Inverted-index documents for the group |
| ops | `queue_throughput` | Subscribe-queue throughput by operation for the group |
| ops | `queue_batch_throughput` | Subscribe-queue batch throughput by operation for the group |
| ops | `queue_message_throughput` | Subscribe-queue per-message throughput by operation for the group |
| s | `queue_latency_p99` | Publish-queue latency p99 for the group |
| Bytes/s | `publish_bytes` | Publish bytes for the group |

## Customizations

You can customize your own metrics / expressions. The metric definitions and expression rules are in
`/config/otel-rules/banyandb`. The dashboard panel configurations ship from the SkyWalking Horizon UI
bundle (apache/skywalking-horizon-ui); the OAP backend does not host UI dashboard JSONs.
