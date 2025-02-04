# KONG monitoring

## KONG performance from `kong prometheus plugin`
The [kong-prometheus](https://github.com/Kong/kong/tree/master/kong/plugins/prometheus) is a lua library that can be used with Kong to collect metrics.
It exposes metrics related to Kong and proxied upstream services in Prometheus exposition format, which can be scraped by a Prometheus Server.
SkyWalking leverages OpenTelemetry Collector to transfer the metrics to[OpenTelemetry receiver](opentelemetry-receiver.md) 
and into the [Meter System](./../../concepts-and-designs/mal.md).

### Data flow
1. [KONG Prometheus plugin](https://docs.konghq.com/hub/kong-inc/prometheus/) collects metrics data from KONG.
2. OpenTelemetry Collector fetches metrics from [KONG Prometheus plugin](https://docs.konghq.com/hub/kong-inc/prometheus/) via 
   Prometheus Receiver and pushes metrics to SkyWalking OAP Server via OpenTelemetry gRPC exporter.
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate and store the results.

### Set up
1. Enable KONG [KONG Prometheus plugin](https://docs.konghq.com/hub/kong-inc/prometheus/). Note that if need to monitor per_consumer, 
   status_code_metrics, ai_metrics, latency_metrics, bandwidth_metrics or upstream_health_metrics, **need to enable them manually as needed**, 
   which can be enabled in the [konga](https://pantsel.github.io/konga/) dashboard or through the Admin API, such as the following command
   ~~~bash
   curl -i -X POST http://{KONG-HOST}:{KONG_ADMIN_PORT}/plugins \
    --data name=prometheus \
    --data config.per_consumer=true \
    --data config.status_code_metrics=true \
    --data config.ai_metrics=true \
    --data config.latency_metrics=true \
    --data config.bandwidth_metrics=true \
    --data config.upstream_health_metrics=true
   ~~~
2. Set up [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/getting-started/#docker). 
   For details on Prometheus Receiver in OpenTelemetry Collector, refer to [here](../../../../test/e2e-v2/cases/kong/otel-collector-config.yaml).
3. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

### KONG Monitoring

[KONG prometheus plugin](https://docs.konghq.com/hub/kong-inc/prometheus/) provide multiple dimensions metrics for KONG server, upstream, route etc.
Accordingly, SkyWalking observes the status, requests, and latency of the KONG server, which is cataloged as a `LAYER: KONG` `Service` in the OAP.
Each Kong server is cataloged as a `LAYER: KONG` `instance`, meanwhile, the route rules would be recognized as a `LAYER: KONG` `endpoint`.


#### Kong Request Supported Metrics

| Monitoring Panel | Unit  | Metric Name                                                                                                       | Description                                          | Data Source |
|------------------|-------|-------------------------------------------------------------------------------------------------------------------|------------------------------------------------------|-------------|
| Bandwidth        | bytes | meter_kong_service_http_bandwidth<br />meter_kong_instance_http_bandwidth<br />meter_kong_endpoint_http_bandwidth | Total bandwidth (ingress/egress) throughput          | Kong        |
| HTTP Status      | count | meter_kong_service_http_status<br />meter_kong_instance_http_status<br />meter_kong_endpoint_http_status          | HTTP status codes per consumer/service/route in Kong | Kong        |
| HTTP Request     | count | meter_kong_service_http_requests<br />meter_kong_instance_http_requests                                           | Total number of requests                             | Kong        |

#### Kong Database Supported Metrics

| Monitoring Panel | Unit  | Metric Name                                                                         | Description                               | Data Source |
|------------------|-------|-------------------------------------------------------------------------------------|-------------------------------------------|-------------|
| DB               | count | meter_kong_service_datastore_reachable<br />meter_kong_instance_datastore_reachable | Datastore reachable from Kong             | Kong        |
| DB               | bytes | meter_kong_instance_shared_dict_bytes                                               | Allocated slabs in bytes in a shared_dict | Kong        |
| DB               | bytes | meter_kong_instance_shared_dict_total_bytes                                         | Total capacity in bytes of a shared_dict  | Kong        |
| DB               | bytes | meter_kong_instance_memory_workers_lua_vms_bytes                                    | Allocated bytes in worker Lua VM          | Kong        |

#### Kong Latencies Supported Metrics

| Monitoring Panel | Unit | Metric Name                                                                                                             | Description                                                              | Data Source |
|------------------|------|-------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|-------------|
| Latency          | ms   | meter_kong_service_kong_latency<br />meter_kong_instance_kong_latency<br />meter_kong_endpoint_kong_latency             | Latency added by Kong and enabled plugins for each service/route in Kong | Kong        |
| Latency          | ms   | meter_kong_service_request_latency<br />meter_kong_instance_request_latency<br />meter_kong_endpoint_request_latency    | Total latency incurred during requests for each service/route in Kong    | Kong        |
| Latency          | ms   | meter_kong_service_upstream_latency<br />meter_kong_instance_upstream_latency<br />meter_kong_endpoint_upstream_latency | Latency added by upstream response for each service/route in Kong        | Kong        |


#### Kong Nginx Supported Metrics

| Monitoring Panel | Unit  | Metric Name                                                                                 | Description                           | Data Source |
|------------------|-------|---------------------------------------------------------------------------------------------|---------------------------------------|-------------|
| Nginx            | count | meter_kong_service_nginx_metric_errors_total                                                | Number of nginx-lua-prometheus errors | Kong        |
| Nginx            | count | meter_kong_service_nginx_connections_total<br />meter_kong_instance_nginx_connections_total | Number of connections by subsystem    | Kong        |
| Nginx            | count | meter_kong_service_nginx_timers<br />meter_kong_instance_nginx_timers                       | Number of Nginx timers                | Kong        |

### Customizations
You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found in `/config/otel-rules/kong`.
The KONG dashboard panel configurations are found in `/config/ui-initialized-templates/kong`.