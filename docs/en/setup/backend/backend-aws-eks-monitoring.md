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
[AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) provide multiple dimensions metrics for EKS  cluster, node , service , etc.
Accordingly, SkyWalking observes the status, payload of EKS cluster, which is cataloged as a `LAYER: AWS_EKS` `Service` in the OAP. Meanwhile, the k8s nodes would be recognized as `LAYER: AWS_EKS` `instance`s. The k8s service would be recognized as `endpoint`s.

#### Specify Job Name

SkyWalking distinguishes AWS Cloud EKS metrics by attributes `job_name`, which value is `aws-cloud-eks-monitoring`.
You could leverage OTEL Collector processor to add the attribute as following :

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
| Network RX Error Count                | count/s | eks_cluster_service_pod_net_rx_error       | Endpoint   | Network TX error count of the pod that belong to the service | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network TX                            | bytes/s | eks_cluster_service_pod_net_tx_bytes       | Endpoint   | Network TX bytes of the pod that belong to the service       | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |
| Network TX Error Count                | count/s | eks_cluster_node_pod_net_tx_error          | Endpoint   | Network TX error count of the pod that belong to the service | [AWS Container Insights Receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/awscontainerinsightreceiver/README.md) |

### Customizations
You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found in `/config/otel-rules/aws-eks/`.
The AWS Cloud EKS dashboard panel configurations are found in `/config/ui-initialized-templates/aws_eks`.

### AWS Container Insights Receiver Deploy Example

```yaml
# create service account and role binding
apiVersion: v1
kind: ServiceAccount
metadata:
  name: aws-otel-sa
  namespace: aws-otel-eks
---
kind: ClusterRole
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: aoc-agent-role
rules:
  - apiGroups: [""]
    resources: ["pods", "nodes", "endpoints"]
    verbs: ["list", "watch"]
  - apiGroups: ["apps"]
    resources: ["replicasets"]
    verbs: ["list", "watch"]
  - apiGroups: ["batch"]
    resources: ["jobs"]
    verbs: ["list", "watch"]
  - apiGroups: [""]
    resources: ["nodes/proxy"]
    verbs: ["get"]
  - apiGroups: [""]
    resources: ["nodes/stats", "configmaps", "events"]
    verbs: ["create", "get"]
  - apiGroups: [""]
    resources: ["configmaps"]
    resourceNames: ["otel-container-insight-clusterleader"]
    verbs: ["get","update"]
  - apiGroups: ["coordination.k8s.io"]
    resources: ["leases"]
    verbs: ["create","get","update"]    
---
kind: ClusterRoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: aoc-agent-role-binding
subjects:
  - kind: ServiceAccount
    name: aws-otel-sa
    namespace: aws-otel-eks
roleRef:
  kind: ClusterRole
  name: aoc-agent-role
  apiGroup: rbac.authorization.k8s.io
---
# OTEL configuration that specify OAP address("oap-service:11800") as otlp exporter address
apiVersion: v1
kind: ConfigMap
metadata:
  name: otel-agent-conf
  namespace: aws-otel-eks
  labels:
    app: opentelemetry
    component: otel-agent-conf
data:
  otel-agent-config: |
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
---
# AWS OTEL DaemonSet
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: aws-otel-eks-ci
  namespace: aws-otel-eks
spec:
  selector:
    matchLabels:
      name: aws-otel-eks-ci
  template:
    metadata:
      labels:
        name: aws-otel-eks-ci
    spec:
      containers:
        - name: aws-otel-collector
          image: amazon/aws-otel-collector:v0.23.0
          env:
            # Specify aws region
            - name: AWS_REGION
              value: "ap-northeast-1"
            - name: K8S_NODE_NAME
              valueFrom:
                fieldRef:
                  fieldPath: spec.nodeName
            - name: HOST_IP
              valueFrom:
                fieldRef:
                  fieldPath: status.hostIP
            - name: HOST_NAME
              valueFrom:
                fieldRef:
                  fieldPath: spec.nodeName
            - name: K8S_NAMESPACE
              valueFrom:
                 fieldRef:
                   fieldPath: metadata.namespace
          imagePullPolicy: Always
          command:
            - "/awscollector"
            - "--config=/conf/otel-agent-config.yaml"
          volumeMounts:
            - name: rootfs
              mountPath: /rootfs
              readOnly: true
            - name: dockersock
              mountPath: /var/run/docker.sock
              readOnly: true
            - name: varlibdocker
              mountPath: /var/lib/docker
              readOnly: true
            - name: containerdsock
              mountPath: /run/containerd/containerd.sock
              readOnly: true
            - name: sys
              mountPath: /sys
              readOnly: true
            - name: devdisk
              mountPath: /dev/disk
              readOnly: true
            - name: otel-agent-config-vol
              mountPath: /conf
            - name: otel-output-vol  
              mountPath: /otel-output
          resources:
            limits:
              cpu:  200m
              memory: 200Mi
            requests:
              cpu: 200m
              memory: 200Mi
      volumes:
        - configMap:
            name: otel-agent-conf
            items:
              - key: otel-agent-config
                path: otel-agent-config.yaml
          name: otel-agent-config-vol
        - name: rootfs
          hostPath:
            path: /
        - name: dockersock
          hostPath:
            path: /var/run/docker.sock
        - name: varlibdocker
          hostPath:
            path: /var/lib/docker
        - name: containerdsock
          hostPath:
            path: /run/containerd/containerd.sock
        - name: sys
          hostPath:
            path: /sys
        - name: devdisk
          hostPath:
            path: /dev/disk/
        - name: otel-output-vol  
          hostPath:
            path: /otel-output
      serviceAccountName: aws-otel-sa
```
Refer to [ContainerInsights](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/ContainerInsights.html) for more information
