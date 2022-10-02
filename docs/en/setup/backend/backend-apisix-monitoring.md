# APISIX monitoring
## APISIX  performance from `apisix prometheus plugin`
SkyWalking leverages OpenTelemetry Collector to transfer the metrics to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/meter.md).

### Data flow
1. [APXSIX prometheus plugin](https://apisix.apache.org/docs/apisix/plugins/prometheus/) collect metrics data from APSIX.
2. OpenTelemetry Collector fetches metrics from [APXSIX Prometheus plugin](https://apisix.apache.org/docs/apisix/plugins/prometheus/) via Prometheus Receiver and pushes metrics to SkyWalking OAP Server via the OpenCensus gRPC Exporter or OpenTelemetry gRPC exporter.
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate and store the results.

### Set up
1. Enable APISIX [APXSIX Prometheus plugin](https://apisix.apache.org/docs/apisix/plugins/prometheus/) .
2. Set up [OpenTelemetry Collector ](https://opentelemetry.io/docs/collector/getting-started/#docker). For details on Prometheus Receiver in OpenTelemetry Collector, refer to [here](../../../../test/e2e-v2/cases/apisix/otel-collector/otel-collector-config.yaml).
3. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

### MySQL Monitoring
[APXSIX prometheus plugin](https://apisix.apache.org/docs/apisix/plugins/prometheus/) provide multiple dimensions metrics for APISIX server , node , route , etc. 
Accordingly, Skywalking observes the status, payload, and latency of the APISIX server, which is cataloged as a `LAYER: APISIX` `Service` in the OAP. Meanwhile, the server node would be recognized as `instance, and the route rule would be recognized as `endpoint`.

#### Supported Metrics 
| Monitoring Panel | Unit | Metric Name |Catalog | Description | Data Source |
|-----|------|-----|-----|-----|-----|
|HTTP status  |  | meter_apisix_sv_http_status |Service | The rate of http status | APXSIX prometheus plugin|
|HTTP latency |  | meter_apisix_sv_http_latency |Service | The rate of http latency | APXSIX prometheus plugin|
|HTTP bandwidth  | KB | meter_apisix_sv_bandwidth |Service | The rate of http latency | APXSIX prometheus plugin|
|HTTP connection |  | meter_apisix_sv_http_connections |Service | The avg number of current connection | APXSIX prometheus plugin|
|HTTP request  |  | meter_apisix_http_requests_total |Service | The number of request since APISIX startup | APXSIX prometheus plugin|
|Sharded dict capacity | MB  | meter_apisix_shared_dict_capacity_bytes |Service | The  avg capacity of sharded dict capacity | APXSIX prometheus plugin|
|Sharded free space| MB  | meter_apisix_shared_dict_free_space_bytes |Service | The  avg free space of sharded dict capacity | APXSIX prometheus plugin|
|Etcd index|   | meter_apisix_sv_etcd_indexes |Service | Etcd modify index for APISIX keys | APXSIX prometheus plugin|
|HTTP status  |  | meter_apisix_node_http_status |Instance | The rate of http status | APXSIX prometheus plugin|
|HTTP latency |  | meter_apisix_node_http_latency |Instance | The rate of http latency | APXSIX prometheus plugin|
|HTTP bandwidth  | KB | meter_apisix_node_bandwidth |Endpoint | The rate of http latency | APXSIX prometheus plugin|
|HTTP status  |  | meter_apisix_route_http_status |Endpoint | The rate of http status | APXSIX prometheus plugin|
|HTTP latency |  | meter_apisix_route_http_latency |Endpoint | The rate of http latency | APXSIX prometheus plugin|
|HTTP bandwidth  | KB | meter_apisix_route_bandwidth |Endpoint | The rate of http latency | APXSIX prometheus plugin|

### Customizations
You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found in `/config/otel-rules/apisix.yaml`.
The MySQL dashboard panel configurations are found in `/config/ui-initialized-templates/apisix`.