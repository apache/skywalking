# Kubernetes (K8s) monitoring
SkyWalking leverages K8s kube-state-metrics (KSM) and cAdvisor for collecting metrics data from K8s. It leverages OpenTelemetry Collector to transfer the metrics to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/meter.md). This feature requires authorizing the OAP Server to access K8s's `API Server`.

## Data flow
1. K8s kube-state-metrics and cAdvisor collect metrics data from K8s.
2. OpenTelemetry Collector fetches metrics from kube-state-metrics and cAdvisor via Prometheus Receiver and pushes metrics to SkyWalking OAP Server via the OpenCensus gRPC Exporter or OpenTelemetry gRPC exporter.
3. The SkyWalking OAP Server access to K8s's `API Server` gets meta info and parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate and store the results.

## Setup
1. Setup [kube-state-metric](https://github.com/kubernetes/kube-state-metrics#kubernetes-deployment).
2. cAdvisor is integrated into `kubelet` by default.
3. Set up [OpenTelemetry Collector ](https://opentelemetry.io/docs/collector/getting-started/#kubernetes). For details on Prometheus Receiver in OpenTelemetry Collector for K8s, refer to [here](https://github.com/prometheus/prometheus/blob/main/documentation/examples/prometheus-kubernetes.yml).
For a quick start, we have provided a complete example of configuration and recommended version; you can refer to [showcase](https://github.com/apache/skywalking-showcase/tree/main/deploy/platform/kubernetes/templates/feature-kubernetes-monitor).
4. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

## Kubernetes Cluster Monitoring
K8s cluster monitoring provides monitoring of the status and resources of the whole cluster and each node. K8s cluster as a `Service` in OAP, K8s node as an `Instance` in OAP, and land on the `Layer: K8S`.

### Kubernetes Cluster Supported Metrics
| Monitoring Panel | Unit | Metric Name | Description | Data Source |
|-----|------|-----|-----|-----|
| Node Total |      | k8s_cluster_node_total | The number of nodes | K8s kube-state-metrics|
| Namespace Total |      | k8s_cluster_namespace_total | The number of namespaces | K8s kube-state-metrics|
| Deployment Total |      | k8s_cluster_deployment_total | The number of deployments | K8s kube-state-metrics|
| Service Total |      | k8s_cluster_service_total | The number of services | K8s kube-state-metrics|
| Pod Total |      | k8s_cluster_pod_total | The number of pods | K8s kube-state-metrics|
| Container Total |      | k8s_cluster_container_total | The number of containers | K8s kube-state-metrics|
| CPU Resources | m    | k8s_cluster_cpu_cores<br />k8s_cluster_cpu_cores_requests<br />k8s_cluster_cpu_cores_limits<br />k8s_cluster_cpu_cores_allocatable | The capacity and the Requests / Limits / Allocatable of the CPU | K8s kube-state-metrics|
| Memory Resources | Gi   | k8s_cluster_memory_total<br />k8s_cluster_memory_requests<br />k8s_cluster_memory_limits<br />k8s_cluster_memory_allocatable | The capacity and the Requests / Limits / Allocatable of the memory | K8s kube-state-metrics|
| Storage Resources | Gi   | k8s_cluster_storage_total<br />k8s_cluster_storage_allocatable | The capacity and allocatable of the storage | K8s kube-state-metrics|
| Node Status |      | k8s_cluster_node_status | The current status of the nodes | K8s kube-state-metrics|
| Deployment Status |      | k8s_cluster_deployment_status | The current status of the deployment | K8s kube-state-metrics|
| Deployment Spec Replicas |      | k8s_cluster_deployment_spec_replicas | The number of desired pods for a deployment | K8s kube-state-metrics|
| Service Status |      | k8s_cluster_service_pod_status | The services current status, depending on the related pods' status | K8s kube-state-metrics|
| Pod Status Not Running |      | k8s_cluster_pod_status_not_running | The pods which are not running in the current phase | K8s kube-state-metrics|
| Pod Status Waiting |      | k8s_cluster_pod_status_waiting | The pods and containers which are currently in the waiting status, with reasons shown | K8s kube-state-metrics|
| Pod Status Terminated |      | k8s_cluster_container_status_terminated | The pods and containers which are currently in the terminated status, with reasons shown | K8s kube-state-metrics|

### Kubernetes Cluster Node Supported Metrics
| Monitoring Panel | Unit | Metric Name | Description | Data Source |
|-----|------|-----|-----|-----|
| Pod Total |      | k8s_node_pod_total | The number of pods in this node | K8s kube-state-metrics |
| Node Status |      | k8s_node_node_status | The current status of this node | K8s kube-state-metrics |
| CPU Resources | m    | k8s_node_cpu_cores<br />k8s_node_cpu_cores_allocatable<br />k8s_node_cpu_cores_requests<br />k8s_node_cpu_cores_limits |  The capacity and the requests / Limits / Allocatable of the CPU  | K8s kube-state-metrics |
| Memory Resources | Gi   | k8s_node_memory_total<br />k8s_node_memory_allocatable<br />k8s_node_memory_requests<br />k8s_node_memory_limits | The capacity and the requests / Limits / Allocatable of the memory | K8s kube-state-metrics |
| Storage Resources | Gi   | k8s_node_storage_total<br />k8s_node_storage_allocatable | The capacity and allocatable of the storage | K8s kube-state-metrics |
| CPU Usage | m    | k8s_node_cpu_usage | The total usage of the CPU core, if there are 2 cores the maximum usage is 2000m | cAdvisor |
| Memory Usage | Gi   | k8s_node_memory_usage | The totaly memory usage | cAdvisor |
| Network I/O| KB/s | k8s_node_network_receive<br />k8s_node_network_transmit | The network receive and transmit | cAdvisor |

## Kubernetes Service Monitoring
K8s Service Monitoring provides observabilities into service status and resources from Kubernetes.
K8s Service as a `Service` in OAP and land on the `Layer: K8S_SERVICE`.

### Kubernetes Service Supported Metrics
| Monitoring Panel | Unit | Metric Name | Description | Data Source |
|-----|-----|-----|-----|-----|
| Service Pod Total |  | k8s_service_pod_total | The number of pods | K8s kube-state-metrics |
| Service Pod Status |  | k8s_service_pod_status | The current status of pods | K8s kube-state-metrics |
| Service CPU Resources | m | k8s_service_cpu_cores_requests<br />k8s_service_cpu_cores_limits | The CPU resources requests / Limits of this service | K8s kube-state-metrics |
| Service Memory Resources | MB | k8s_service_memory_requests<br />k8s_service_memory_limits | The memory resources requests / Limits of this service | K8s kube-state-metrics |
| Pod CPU Usage | m | k8s_service_pod_cpu_usage | The CPU resources total usage of pods | cAdvisor |
| Pod Memory Usage | MB | k8s_service_pod_memory_usage | The memory resources total usage of pods | cAdvisor |
| Pod Waiting |  | k8s_service_pod_status_waiting | The pods and containers which are currently in the waiting status, with reasons shown | K8s kube-state-metrics |
| Pod Terminated |  | k8s_service_pod_status_terminated | The pods and containers which are currently in the terminated status, with reasons shown | K8s kube-state-metrics |
| Pod Restarts |  | k8s_service_pod_status_restarts_total | The number of per container restarts related to the pods | K8s kube-state-metrics |

## Customizations
You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found in `/config/otel-rules/k8s/k8s-cluster.yaml，/config/otel-rules/k8s/k8s-node.yaml, /config/otel-rules/k8s/k8s-service.yaml`.
The K8s Cluster dashboard panel configurations are found in `/config/ui-initialized-templates/k8s`.
The K8s Service dashboard panel configurations are found in `/config/ui-initialized-templates/k8s_service`.
