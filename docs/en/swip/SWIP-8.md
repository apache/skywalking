# Support Kong Monitoring

## Motivation

[**Kong**](https://github.com/Kong/kong) or **Kong API Gateway** is a cloud-native, platform-agnostic, scalable API Gateway 
distinguished for its high performance and extensibility via plugins. Now I want to add Kong monitoring via the OpenTelemetry Collector, 
which fetches metrics from it's own HTTP endpoint to expose metrics data for [Prometheus](https://prometheus.io/).

## Architecture Graph

There is no significant architecture-level change.

## Proposed Changes

1. Kong expose its own [metrics](https://docs.konghq.com/hub/kong-inc/prometheus/) via HTTP endpoint to opentelemetry collector, OpenTelemetry Collector fetches metrics from it and pushes metrics to SkyWalking OTEL Receiver via OpenTelemetry exporter.
2. The SkyWalking OAP Server parses the expression with MAL to filter/calculate/aggregate and store the results.
3. These metrics can be displayed via the SkyWalking UI, and the metrics can be customized for display on the UI dashboard.

### Kong Request Supported Metrics

| Monitoring Panel | Unit  | Metric Name                                                                                                       | Description                                          | Data Source |
|------------------|-------|-------------------------------------------------------------------------------------------------------------------|------------------------------------------------------|-------------|
| Bandwidth        | bytes | meter_kong_service_http_bandwidth<br />meter_kong_instance_http_bandwidth<br />meter_kong_endpoint_http_bandwidth | Total bandwidth (ingress/egress) throughput          | Kong        |
| HTTP Status      | count | meter_kong_service_http_status<br />meter_kong_instance_http_status<br />meter_kong_endpoint_http_status          | HTTP status codes per consumer/service/route in Kong | Kong        |
| HTTP Request     | count | meter_kong_service_http_requests<br />meter_kong_instance_http_requests                                           | Total number of requests                             | Kong        |

### Kong Database Supported Metrics

| Monitoring Panel | Unit  | Metric Name                                                                         | Description                               | Data Source |
|------------------|-------|-------------------------------------------------------------------------------------|-------------------------------------------|-------------|
| DB               | count | meter_kong_service_datastore_reachable<br />meter_kong_instance_datastore_reachable | Datastore reachable from Kong             | Kong        |
| DB               | bytes | meter_kong_instance_shared_dict_bytes                                               | Allocated slabs in bytes in a shared_dict | Kong        |
| DB               | bytes | meter_kong_instance_shared_dict_total_bytes                                         | Total capacity in bytes of a shared_dict  | Kong        |
| DB               | bytes | meter_kong_instance_memory_workers_lua_vms_bytes                                    | Allocated bytes in worker Lua VM          | Kong        |

### Kong Latencies Supported Metrics

| Monitoring Panel | Unit | Metric Name                                                                                                             | Description                                                              | Data Source |
|------------------|------|-------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|-------------|
| Latency          | ms   | meter_kong_service_kong_latency<br />meter_kong_instance_kong_latency<br />meter_kong_endpoint_kong_latency             | Latency added by Kong and enabled plugins for each service/route in Kong | Kong        |
| Latency          | ms   | meter_kong_service_request_latency<br />meter_kong_instance_request_latency<br />meter_kong_endpoint_request_latency    | Total latency incurred during requests for each service/route in Kong    | Kong        |
| Latency          | ms   | meter_kong_service_upstream_latency<br />meter_kong_instance_upstream_latency<br />meter_kong_endpoint_upstream_latency | Latency added by upstream response for each service/route in Kong        | Kong        |


### Kong Nginx Supported Metrics

| Monitoring Panel | Unit  | Metric Name                                                                                 | Description                           | Data Source |
|------------------|-------|---------------------------------------------------------------------------------------------|---------------------------------------|-------------|
| Nginx            | count | meter_kong_service_nginx_metric_errors_total                                                | Number of nginx-lua-prometheus errors | Kong        |
| Nginx            | count | meter_kong_service_nginx_connections_total<br />meter_kong_instance_nginx_connections_total | Number of connections by subsystem    | Kong        |
| Nginx            | count | meter_kong_service_nginx_timers<br />meter_kong_instance_nginx_timers                       | Number of Nginx timers                | Kong        |

## Imported Dependencies libs and their licenses.

No new dependency.

## Compatibility

no breaking changes.

## General usage docs
