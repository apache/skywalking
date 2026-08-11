# APISIX monitoring

## APISIX performance metrics

SkyWalking uses the OpenTelemetry Collector to transfer metrics exported by the Apache APISIX [`prometheus` plugin](https://apisix.apache.org/docs/apisix/plugins/prometheus/) to the SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md). The OAP Server then processes the metrics with [Meter Analysis Language](../../concepts-and-designs/mal.md) rules.

### Data flow

1. The APISIX `prometheus` plugin exports APISIX metrics.
2. The OpenTelemetry Collector Prometheus receiver scrapes the APISIX metrics endpoint and sends the metrics to the SkyWalking OAP Server through OTLP.
3. The OAP Server applies the APISIX MAL rules and stores the resulting service, instance, and endpoint metrics.

### Set up

1. Enable the APISIX [`prometheus` plugin](https://apisix.apache.org/docs/apisix/plugins/prometheus/).
2. Configure the OpenTelemetry Collector [Prometheus receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/prometheusreceiver) to scrape the APISIX metrics endpoint. The APISIX E2E test provides a repository-local [Collector configuration](../../../../test/e2e-v2/cases/apisix/otel-collector/otel-collector-config.yaml).
3. Configure the SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

### APISIX entities in SkyWalking

The APISIX MAL rule creates a service in the `APISIX` layer. APISIX data-plane nodes are represented as instances. Matched routes and upstream nodes are represented as endpoints with `route/` and `upstream/` prefixes.

#### Specify the SkyWalking service name

The APISIX MAL rule reads the `skywalking_service` label and prefixes the resulting service name with `APISIX::`. Add the label through `static_configs` in the Collector Prometheus receiver:

```yaml
receivers:
  prometheus:
    config:
      scrape_configs:
        - job_name: apisix-monitoring
          metrics_path: /apisix/prometheus/metrics
          static_configs:
            - targets:
                - apisix:9091
              labels:
                skywalking_service: example_service_name
```

Keep `job_name: apisix-monitoring` unless you also update the APISIX MAL filter in OAP. If `skywalking_service` is absent or blank, the MAL rule uses `APISIX`, resulting in the default service name `APISIX::APISIX`.

#### Supported metrics

| Monitoring Panel | Unit | Metric Name | Catalog | Description | Data Source |
| --- | --- | --- | --- | --- | --- |
| HTTP Request Trend | | `meter_apisix_sv_http_requests` | Service | Request rate across the APISIX service | APISIX Prometheus plugin |
| HTTP Connections | | `meter_apisix_sv_http_connections` | Service | Current connections grouped by state | APISIX Prometheus plugin |
| HTTP Status Trend | | `meter_apisix_sv_http_status_matched` | Service | Matched request rate grouped by HTTP status code | APISIX Prometheus plugin |
| HTTP Latency | ms | `meter_apisix_sv_http_latency_matched` | Service | Matched request latency grouped by type and percentile | APISIX Prometheus plugin |
| HTTP Bandwidth | KB | `meter_apisix_sv_bandwidth_matched` | Service | Matched ingress and egress bandwidth | APISIX Prometheus plugin |
| Non-matched Status Trend | | `meter_apisix_sv_http_status_unmatched` | Service | Non-matched request rate grouped by HTTP status code | APISIX Prometheus plugin |
| Non-matched Latency | ms | `meter_apisix_sv_http_latency_unmatched` | Service | Request latency for traffic that did not match a route | APISIX Prometheus plugin |
| Non-matched Bandwidth | KB | `meter_apisix_sv_bandwidth_unmatched` | Service | Bandwidth for traffic that did not match a route | APISIX Prometheus plugin |
| HTTP Request Trend | | `meter_apisix_instance_http_requests` | Instance | Request rate for an APISIX data-plane node | APISIX Prometheus plugin |
| HTTP Connections | | `meter_apisix_instance_http_connections` | Instance | Current connections for a node, grouped by state | APISIX Prometheus plugin |
| HTTP Status Trend | | `meter_apisix_instance_http_status_matched` | Instance | Matched request rate for a node, grouped by HTTP status code | APISIX Prometheus plugin |
| HTTP Latency | ms | `meter_apisix_instance_http_latency_matched` | Instance | Matched request latency for a node | APISIX Prometheus plugin |
| HTTP Bandwidth | KB | `meter_apisix_instance_bandwidth_matched` | Instance | Matched ingress and egress bandwidth for a node | APISIX Prometheus plugin |
| Shared Dict | MB | `meter_apisix_instance_shared_dict_capacity_bytes` | Instance | Shared dictionary capacity for a node | APISIX Prometheus plugin |
| Shared Dict | MB | `meter_apisix_instance_shared_dict_free_space_bytes` | Instance | Free shared dictionary space for a node | APISIX Prometheus plugin |
| etcd Reachable | | `meter_apisix_instance_etcd_reachable` | Instance | Whether a node can reach etcd | APISIX Prometheus plugin |
| etcd Indexes | | `meter_apisix_instance_etcd_indexes` | Instance | Latest etcd modification index observed by a node | APISIX Prometheus plugin |
| Non-matched Traffic | | `meter_apisix_instance_http_status_unmatched` | Instance | Non-matched request rate for a node, grouped by HTTP status code | APISIX Prometheus plugin |
| Non-matched Traffic | ms | `meter_apisix_instance_http_latency_unmatched` | Instance | Non-matched request latency for a node | APISIX Prometheus plugin |
| Non-matched Traffic | KB | `meter_apisix_instance_bandwidth_unmatched` | Instance | Non-matched bandwidth for a node | APISIX Prometheus plugin |
| HTTP Status Trend | | `meter_apisix_endpoint_http_status` | Endpoint | Request rate for a route or upstream node, grouped by HTTP status code | APISIX Prometheus plugin |
| HTTP Latency | ms | `meter_apisix_endpoint_http_latency` | Endpoint | Request latency for a route or upstream node | APISIX Prometheus plugin |
| HTTP Bandwidth | KB | `meter_apisix_endpoint_bandwidth` | Endpoint | Ingress and egress bandwidth for a route or upstream node | APISIX Prometheus plugin |

For Dashboard widget semantics and display units, see the Horizon UI [APISIX Dashboard reference](https://github.com/apache/skywalking-horizon-ui/blob/main/docs/dashboards/apisix.md).

### Customizations

The APISIX metric definitions and expressions are in [`oap-server/server-starter/src/main/resources/otel-rules/apisix.yaml`](../../../../oap-server/server-starter/src/main/resources/otel-rules/apisix.yaml).

The Horizon UI bundled APISIX Dashboard is maintained in the [`apache/skywalking-horizon-ui`](https://github.com/apache/skywalking-horizon-ui) repository; the OAP backend no longer hosts the UI Dashboard JSON.
