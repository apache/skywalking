# OAP self observability dashboard

SkyWalking itself collects and exports metrics in Prometheus format for consuming,
it also provides a dashboard to visualize the self-observability metrics.

## Data flow
1. SkyWalking OAP collects metrics data internally and exposes a Prometheus http endpoint to retrieve the metrics.
2. SkyWalking OAP itself (or OpenTelemetry Collector, prefered in Kubernetes scenarios) fetches metrics from the Prometheus endpoint in step (1).
3. OAP (or OpenTelemetry Collector) pushes metrics to SkyWalking OAP Server via the OpenCensus gRPC Exporter or OpenTelemetry gRPC exporter.
4. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate and store the results.

## Set up
Follow [OAP Self Observability Telemetry doc](backend-telemetry.md) to set up OAP and OpenTelemetry Collector.

## Self observability monitoring
Self observability monitoring provides monitoring of the status and resources of the OAP server itself. `oap-server` is a `Service` in OAP, and land on the `Layer: SO11Y_OAP`.

### Self observability metrics

| Unit | Metric Name                                       | Description | Data Source |
|------|---------------------------------------------------|-----|-----|
| Count Per Minute | meter_oap_instance_jvm_gc_count                   | GC Count | oap self observability |
| MB | meter_oap_instance_jvm_memory_bytes_used          | Memory | oap self observability |
| ms / min | meter_oap_instance_jvm_young_gc_time              | GC Time (ms / min) | oap self observability |
| ms / min | meter_oap_instance_jvm_old_gc_time                | GC Time (ms / min) | oap self observability |
| Count Per Minute | meter_oap_instance_mesh_count                     | Mesh Analysis Count (Per Minute) | oap self observability |
| Count Per Minute | meter_oap_instance_mesh_analysis_error_count      | Mesh Analysis Count (Per Minute) | oap self observability |
| ms | meter_oap_instance_trace_latency_percentile       | Trace Analysis Latency (ms) | oap self observability |
| Count | meter_oap_jvm_class_loaded_count                  | Class Count | oap self observability |
| Count | meter_oap_jvm_class_total_unloaded_count          | Class Count | oap self observability |
| Count | meter_oap_jvm_class_total_loaded_count            | Class Count | oap self observability |
| Count | meter_oap_instance_persistence_prepare_count      | Persistence Count (Per 5 Minutes) | oap self observability |
| Count | meter_oap_instance_persistence_execute_count      | Persistence Count (Per 5 Minutes) | oap self observability |
| Count | meter_oap_jvm_thread_live_count                   | Thread Count | oap self observability |
| Count | meter_oap_jvm_thread_peak_count                   | Thread Count | oap self observability |
| Count | meter_oap_jvm_thread_daemon_count                 | Thread Count | oap self observability |
| ms | meter_oap_instance_persistence_execute_percentile | Persistence Execution Latency Per Metric Type (ms) | oap self observability |
| ms | meter_oap_instance_persistence_prepare_percentile | Persistence Preparing Latency Per Metric Type (ms) | oap self observability |
| Count | meter_oap_jvm_thread_runnable_count               | Thread State Count | oap self observability |
| Count | meter_oap_jvm_thread_timed_waiting_count          | Thread State Count | oap self observability |
| Count | meter_oap_jvm_thread_blocked_count                | Thread State Count | oap self observability |
| Count | meter_oap_jvm_thread_waiting_count                | Thread State Count | oap self observability |
| Count per minute | meter_oap_instance_metrics_aggregation            | Aggregation (Per Minute) | oap self observability |
| ms | meter_oap_instance_mesh_latency_percentile        | Mesh Analysis Latency (ms) | oap self observability |
| Count per minute | meter_oap_instance_trace_count                    | Trace Analysis Count (Per Minute) | oap self observability |
| Count per minute | meter_oap_instance_trace_analysis_error_count     | Trace Analysis Count (Per Minute) | oap self observability |
| Percentage | meter_oap_instance_cpu_percentage                 | CPU (%) | oap self observability |
| Count | meter_oap_instance_metrics_persistent_cache       | count of metrics cache hit and no-hit |oap self observability|

## Customizations
You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found in `/config/fetcher-prom-rules/self.yaml` and `config/otel-rules/oap.yaml`.
The self observability dashboard panel configurations are found in `/config/ui-initialized-templates/so11y_oap`.
