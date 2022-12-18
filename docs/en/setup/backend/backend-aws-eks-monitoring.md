# AWS Cloud EKS monitoring
## APISIX  performance from `apisix prometheus plugin`
SkyWalking leverages OpenTelemetry Collector to transfer the metrics to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/meter.md).

### Data flow
1. OpenTelemetry Collector fetches metrics from EKS via [awscontainerinsightreceiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) and pushes metrics to SkyWalking OAP Server via the OpenCensus gRPC Exporter or OpenTelemetry gRPC exporter.
2. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate and store the results.

### Set up
1. Deploy [amazon/aws-otel-collector](https://hub.docker.com/r/amazon/aws-otel-collector) as `DaemonSet` to EKS
3. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

### APISIX Monitoring
[APISIX prometheus plugin](https://apisix.apache.org/docs/apisix/plugins/prometheus/) provide multiple dimensions metrics for APISIX server , upstream , route , etc.
Accordingly, SkyWalking observes the status, payload, and latency of the APISIX server, which is cataloged as a `LAYER: APISIX` `Service` in the OAP. Meanwhile, the instances would be recognized as `LAYER: APISIX` `instance`s. The route rules and nodes would be recognized as `endpoint`s with `route/` and `upstream/` prefixes.

#### Specify Job Name

SkyWalking distinguishes AWS Cloud EKS metrics by attributes `job_name`, which value is `aws-cloud-eks-monitoring`
You could leverage OTEL Collector processor to add `job_name` attribute , as following :

```yaml      
processors:
    resource/job-name:
        attributes:
        - key: job_name   
          value: aws-cloud-eks-monitoring
          action: insert     
```

Notice , if you don't specify `job_name` attribute, SkyWalking OAP will ignore the metrics

#### Supported Metrics
| Monitoring Panel                    | Unit | Metric Name                                        | Catalog  | Description                                                                                                            | Data Source              |
|-------------------------------------|------|----------------------------------------------------|----------|------------------------------------------------------------------------------------------------------------------------|--------------------------|
| HTTP status                         |      | meter_apisix_sv_http_status                        | Service  | The increment rate of the status of HTTP requests                                                                      | APISIX Prometheus plugin |
| HTTP latency                        |      | meter_apisix_sv_http_latency                       | Service  | The increment rate of the latency of HTTP requests                                                                     | APISIX Prometheus plugin |
| HTTP bandwidth                      | KB   | meter_apisix_sv_bandwidth                          | Service  | The increment rate of the bandwidth of HTTP requests                                                                   | APISIX Prometheus plugin |
| HTTP status of non-matched requests |      | meter_apisix_sv_http_status                        | Service  | The increment rate of the status of HTTP requests, which don't match any route                                         | APISIX Prometheus plugin |
| HTTP latency non-matched requests   |      | meter_apisix_sv_http_latency                       | Service  | The increment rate of the latency of HTTP requests, which don't match any route                                        | APISIX Prometheus plugin |
| HTTP bandwidth non-matched requests | KB   | meter_apisix_sv_bandwidth                          | Service  | The increment rate of the bandwidth of HTTP requests ,which don't match any route                                      | APISIX Prometheus plugin |
| HTTP connection                     |      | meter_apisix_sv_http_connections                   | Service  | The avg number of the connections                                                                                      | APISIX Prometheus plugin |
| HTTP Request Trend                  |      | meter_apisix_http_requests                         | Service  | The increment rate of HTTP requests                                                                                    | APISIX Prometheus plugin |
| HTTP status                         |      | meter_apisix_instance_http_status                  | Instance | The increment rate of the status of HTTP requests                                                                      | APISIX Prometheus plugin |
| HTTP latency                        |      | meter_apisix_instance_http_latency                 | Instance | The increment rate of the latency of HTTP requests                                                                     | APISIX Prometheus plugin |
| HTTP bandwidth                      | KB   | meter_apisix_instance_bandwidth                    | Instance | The increment rate of the bandwidth of HTTP requests                                                                   | APISIX Prometheus plugin |
| HTTP status of non-matched requests |      | meter_apisix_instance_http_status                  | Instance | The increment rate of the status of HTTP requests, which don't match any route                                         | APISIX Prometheus plugin |
| HTTP latency non-matched requests   |      | meter_apisix_instance_http_latency                 | Instance | The increment rate of the latency of HTTP requests, which don't match any route                                        | APISIX Prometheus plugin |
| HTTP bandwidth non-matched requests | KB   | meter_apisix_instance_bandwidth                    | Instance | The increment rate of the bandwidth of HTTP requests ,which don't match any route                                      | APISIX Prometheus plugin |
| HTTP connection                     |      | meter_apisix_instance_http_connections             | Instance | The avg number of the connections                                                                                      | APISIX Prometheus plugin |
| HTTP Request Trend                  |      | meter_apisix_instance_http_requests                | Instance | The increment rate of HTTP requests                                                                                    | APISIX Prometheus plugin |
| Shared dict capacity                | MB   | meter_apisix_instance_shared_dict_capacity_bytes   | Instance | The avg capacity of shared dict capacity                                                                               | APISIX Prometheus plugin |
| Shared free space                   | MB   | meter_apisix_instance_shared_dict_free_space_bytes | Instance | The avg free space of shared dict capacity                                                                             | APISIX Prometheus plugin |
| etcd index                          |      | meter_apisix_instance_sv_etcd_indexes              | Instance | etcd modify index for APISIX keys                                                                                      | APISIX Prometheus plugin |
| etcd latest reachability            |      | meter_apisix_instance_sv_etcd_reachable            | Instance | etcd latest reachable , Refer to [APISIX Prometheus plugin](https://apisix.apache.org/docs/apisix/plugins/prometheus/) | APISIX Prometheus plugin |
| HTTP status                         |      | meter_apisix_endpoint_node_http_status             | Endpoint | The increment rate of the status of HTTP requests                                                                      | APISIX Prometheus plugin |
| HTTP latency                        |      | meter_apisix_endpoint_node_http_latency            | Endpoint | The increment rate of the latency of HTTP requests                                                                     | APISIX Prometheus plugin |
| HTTP bandwidth                      | KB   | meter_apisix_endpoint_node_bandwidth               | Endpoint | The increment rate of the bandwidth of HTTP requests                                                                   | APISIX Prometheus plugin |

### Customizations
You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found in `/config/otel-rules/apisix.yaml`.
The APISIX dashboard panel configurations are found in `/config/ui-initialized-templates/apisix`.