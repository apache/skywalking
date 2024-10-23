## 10.2.0

#### OAP Server

* Skip processing OTLP metrics data points with flag `FLAG_NO_RECORDED_VALUE`, which causes exceptional result.
* Add self observability metrics for GraphQL query, `graphql_query_latency`.
* Reduce the count of process index and adding time range when query process index.
* Bump up Apache commons-io to 2.17.0.
* Polish eBPF so11y metrics and add error count for query metrics.
* Support query endpoint list with duration parameter(optional).
* Change the endpoint_traffic to updatable for the additional column `last_ping`.
* Add Component ID(5023) for the GoZero framework.
* Support Kong monitoring.
* Support adding additional attr[0-4] for service level metrics.
* All self observability histogram metrics names are suffixed with `_seconds` to indicate the time unit, as per [Prometheus metrics names guide](https://prometheus.io/docs/practices/naming/),
  if you have any customized configuration files that utilize these metrics, you can migrate the metrics names with our helper script `tools/migrate-so11y-metrics-names.sh`,
  this script migrates the metrics names in files `*.yaml`, `*.java`, and `*.json` files, review and adjust the script if you have other file types.
  NOTE: the script doesn't migrate existing metrics data.
  * `browser_error_log_in_latency` -> `browser_error_log_in_latency_seconds`
  * `browser_perf_data_in_latency` -> `browser_perf_data_in_latency_seconds`
  * `envoy_als_in_latency` -> `envoy_als_in_latency_seconds`
  * `envoy_metric_in_latency` -> `envoy_metric_in_latency_seconds`
  * `event_in_latency` -> `event_in_latency_seconds`
  * `graphql_query_latency` -> `graphql_query_latency_seconds`
  * `k8s_als_in_latency` -> `k8s_als_in_latency_seconds`
  * `log_in_latency` -> `log_in_latency_seconds`
  * `mesh_analysis_latency` -> `mesh_analysis_latency_seconds`
  * `meter_batch_in_latency` -> `meter_batch_in_latency_seconds`
  * `meter_in_latency` -> `meter_in_latency_seconds`
  * `otel_logs_latency` -> `otel_logs_latency_seconds`
  * `otel_metrics_latency` -> `otel_metrics_latency_seconds`
  * `otel_spans_latency` -> `otel_spans_latency_seconds`
  * `persistence_timer_bulk_all_latency` -> `persistence_timer_bulk_all_latency_seconds`
  * `persistence_timer_bulk_execute_latency` -> `persistence_timer_bulk_execute_latency_seconds`
  * `persistence_timer_bulk_prepare_latency` -> `persistence_timer_bulk_prepare_latency_seconds`
  * `profile_task_in_latency` -> `profile_task_in_latency_seconds`
  * `remote_in_latency` -> `remote_in_latency_seconds`
  * `telegraf_in_latency` -> `telegraf_in_latency_seconds`
  * `trace_in_latency` -> `trace_in_latency_seconds`

#### UI

* Add support for case-insensitive search in the dashboard list.
* Add content decorations to Table and Card widgets.
* Support the endpoint list widget query with duration parameter.
* Support ranges for Value Mappings.

#### Documentation
* Update release document to adopt newly added revision-based process.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/224?closed=1)
