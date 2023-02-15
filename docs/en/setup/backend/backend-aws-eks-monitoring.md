# AWS Cloud EKS monitoring
SkyWalking leverages OpenTelemetry Collector with [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) to transfer the metrics to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/meter.md).

### Data flow
1. OpenTelemetry Collector fetches metrics from EKS via [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) and pushes metrics to SkyWalking OAP Server via the OpenCensus gRPC Exporter or OpenTelemetry gRPC exporter.
2. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate and store the results.

### Set up
1. Deploy [amazon/aws-otel-collector](https://hub.docker.com/r/amazon/aws-otel-collector) with [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) to EKS
2. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

### EKS Monitoring
[AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) provides multiple dimensions metrics for EKS  cluster, node, service, etc.
Accordingly, SkyWalking observes the status, and payload of the EKS cluster, which is cataloged as a `LAYER: AWS_EKS` `Service` in the OAP. Meanwhile, the k8s nodes would be recognized as `LAYER: AWS_EKS` `instance`s. The k8s service would be recognized as `endpoint`s.

#### Specify Job Name

SkyWalking distinguishes AWS Cloud EKS metrics by attributes `job_name`, which value is `aws-cloud-eks-monitoring`.
You could leverage OTEL Collector processor to add the attribute as follows:

```yaml      
processors:
  resource/job-name:
    attributes:
      - key: job_name
        value: aws-cloud-eks-monitoring
        action: insert     
```

Notice, if you don't specify `job_name` attribute, SkyWalking OAP will ignore the metrics

#### Supported Metrics
| Monitoring Panel                      | Unit    | Metric Name                                | Catalog    | Description                                                  | Data Source                                                                                                                                                   |
|---------------------------------------|---------|--------------------------------------------|------------|--------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Node Count                            |         | eks_cluster_node_count                     | Service    | The node count of the EKS cluster                            | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Failed Node Count                     |         | eks_cluster_failed_node_count              | Service    | The failed node count of the EKS cluster                     | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Pod Count (namespace dimension)       |         | eks_cluster_namespace_count                | Service    | The count of pod in the EKS cluster(namespace dimension)     | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Pod Count (service dimension)         |         | eks_cluster_service_count                  | Service    | The count of pod in the EKS cluster(service dimension)       | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network RX Dropped Count (per second) | count/s | eks_cluster_net_rx_dropped                 | Service    | Network RX dropped count                                     | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network RX Error Count (per second)   | count/s | eks_cluster_net_rx_error                   | Service    | Network RX error count                                       | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network TX Dropped Count (per second) | count/s | eks_cluster_net_rx_dropped                 | Service    | Network TX dropped count                                     | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network TX Error Count (per second)   | count/s | eks_cluster_net_rx_error                   | Service    | Network TX error count                                       | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Pod Count                             |         | eks_cluster_node_pod_number                | Instance   | The count of pod running on the node                         | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| CPU Utilization                       | percent | eks_cluster_node_cpu_utilization           | Instance   | The CPU Utilization of the node                              | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Memory Utilization                    | percent | eks_cluster_node_memory_utilization        | Instance   | The Memory Utilization of the node                           | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network RX                            | bytes/s | eks_cluster_node_net_rx_bytes              | Instance   | Network RX bytes of the node                                 | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network RX Error Count                | count/s | eks_cluster_node_net_rx_bytes              | Instance   | Network RX error count of the node                           | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network TX                            | bytes/s | eks_cluster_node_net_rx_bytes              | Instance   | Network TX bytes of the node                                 | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network TX Error Count                | count/s | eks_cluster_node_net_rx_bytes              | Instance   | Network TX error count of the node                           | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Disk IO Write                         | bytes/s | eks_cluster_node_net_rx_bytes              | Instance   | The IO write bytes of the node                               | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Disk IO Read                          | bytes/s | eks_cluster_node_net_rx_bytes              | Instance   | The IO read bytes of the node                                | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| FS Utilization                        | percent | eks_cluster_node_net_rx_bytes              | Instance   | The filesystem utilization of the node                       | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| CPU Utilization                       | percent | eks_cluster_node_pod_cpu_utilization       | Instance   | The CPU Utilization of the pod running on the node           | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Memory Utilization                    | percent | eks_cluster_node_pod_memory_utilization    | Instance   | The Memory Utilization of the pod running on the node        | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network RX                            | bytes/s | eks_cluster_node_pod_net_rx_bytes          | Instance   | Network RX bytes of the pod running on the node              | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network RX Error Count                | count/s | eks_cluster_node_pod_net_rx_error          | Instance   | Network RX error count of the pod running on the node        | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network TX                            | bytes/s | eks_cluster_node_pod_net_tx_bytes          | Instance   | Network RX bytes of the pod running on the node              | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network TX Error Count                | count/s | eks_cluster_node_pod_net_tx_error          | Instance   | Network RX error count of the pod running on the node        | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| CPU Utilization                       | percent | eks_cluster_service_pod_cpu_utilization    | Endpoint   | The CPU Utilization of pod that belong to the service        | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Memory Utilization                    | percent | eks_cluster_service_pod_memory_utilization | Endpoint   | The Memory Utilization of pod that belong to the service     | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network RX                            | bytes/s | eks_cluster_service_pod_net_rx_bytes       | Endpoint   | Network RX bytes of the pod that belong to the service       | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network RX Error Count                | count/s | eks_cluster_service_pod_net_rx_error       | Endpoint   | Network TX error count of the pod that belongs to the service | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network TX                            | bytes/s | eks_cluster_service_pod_net_tx_bytes       | Endpoint   | Network TX bytes of the pod that belong to the service       | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network TX Error Count                | count/s | eks_cluster_node_pod_net_tx_error          | Endpoint   | Network TX error count of the pod that belongs to the service | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |

### Customizations
You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found in `/config/otel-rules/aws-eks/`.
The AWS Cloud EKS dashboard panel configurations are found in `/config/ui-initialized-templates/aws_eks`.

### OTEL Configuration Sample With AWS Container Insights Receiver

```yaml
extensions:
  health_check:
receivers:
  awscontainerinsightreceiver:
processors:
  resource/job-name:
    attributes:
      - key: job_name
        value: aws-cloud-eks-monitoring
        action: insert
exporters:
  otlp:
    endpoint: oap-service:11800
    tls:
      insecure: true
  logging:
    loglevel: debug
service:
  pipelines:
    metrics:
      receivers: [awscontainerinsightreceiver]
      processors: [resource/job-name]
      exporters: [otlp,logging]
  extensions: [health_check]
```
Refer to [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) for more information
