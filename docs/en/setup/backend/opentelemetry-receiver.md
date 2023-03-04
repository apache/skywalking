# OpenTelemetry receiver

The OpenTelemetry receiver supports ingesting agent metrics by meter-system. The OAP can load the configuration at bootstrap.
If the new configuration is not well-formed, the OAP may fail to start up. The files are located at `$CLASSPATH/otel-rules`.

Supported handlers:

* `oc`: [OpenCensus](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/a08903f05d3a544f548535c222b1c205b9f5a154/exporter/opencensusexporter/README.md) gRPC service handler.
* `otlp`: [OpenTelemetry](https://github.com/open-telemetry/opentelemetry-collector/tree/1c217b366fbdb209044d8f4c3fece079ae23bd3b/exporter/otlpexporter) gRPC service handler.

**Notice:**  Set `SW_OTEL_RECEIVER=default` through system environment or change `receiver-otel/selector=${SW_OTEL_RECEIVER:default}` to activate the OpenTelemetry receiver.

The rule file should be in YAML format, defined by the scheme described in [MAL](../../concepts-and-designs/mal.md).
Note: `receiver-otel` only supports the `group`, `defaultMetricLevel`, and `metricsRules` nodes of the scheme due to its push mode.

To activate the `oc` handler and relevant rules of `istio`:

```yaml
receiver-otel:
  // Change selector value to default, for activating the otel receiver.
  selector: ${SW_OTEL_RECEIVER:default}
  default:
    enabledHandlers: ${SW_OTEL_RECEIVER_ENABLED_HANDLERS:"oc,otlp"}
    enabledOtelRules: ${SW_OTEL_RECEIVER_ENABLED_OTEL_RULES:"istio-controlplane"}
```

The receiver adds label with key `node_identifier_host_name` to the collected data samples,
and its value is from `Node.identifier.host_name` defined in OpenCensus Agent Proto,
or `net.host.name` (or `host.name` for some OTLP versions) resource attributes defined in OpenTelemetry proto,
for identification of the metric data.

| Description                             | Configuration File                             | Data Source                                                                                                       |
|-----------------------------------------|------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| Metrics of Istio Control Plane          | otel-rules/istio-controlplane.yaml             | Istio Control Plane -> OpenTelemetry Collector -- OC/OTLP exporter --> SkyWalking OAP Server                      |
| Metrics of SkyWalking OAP server itself | otel-rules/oap.yaml                            | SkyWalking OAP Server(SelfObservability) -> OpenTelemetry Collector -- OC/OTLP exporter --> SkyWalking OAP Server |
| Metrics of VMs                          | otel-rules/vm.yaml                             | Prometheus node-exporter(VMs) -> OpenTelemetry Collector -- OC/OTLP exporter --> SkyWalking OAP Server            |
| Metrics of K8s cluster                  | otel-rules/k8s/k8s-cluster.yaml                | K8s kube-state-metrics -> OpenTelemetry Collector -- OC/OTLP exporter --> SkyWalking OAP Server                   |
| Metrics of K8s cluster                  | otel-rules/k8s/k8s-node.yaml                   | cAdvisor & K8s kube-state-metrics -> OpenTelemetry Collector -- OC/OTLP exporter --> SkyWalking OAP Server        |
| Metrics of K8s cluster                  | otel-rules/k8s/k8s-service.yaml                | cAdvisor & K8s kube-state-metrics -> OpenTelemetry Collector -- OC/OTLP exporter --> SkyWalking OAP Server        |
| Metrics of MYSQL                        | otel-rules/mysql/mysql-instance.yaml           | prometheus/mysqld_exporter -> OpenTelemetry Collector -- OC/OTLP exporter --> SkyWalking OAP Server               |
| Metrics of MYSQL                        | otel-rules/mysql/mysql-service.yaml            | prometheus/mysqld_exporter -> OpenTelemetry Collector -- OC/OTLP exporter --> SkyWalking OAP Server               |
| Metrics of PostgreSQL                   | otel-rules/postgresql/postgresql-instance.yaml | postgres_exporter -> OpenTelemetry Collector -- OC/OTLP exporter --> SkyWalking OAP Server                        |
| Metrics of PostgreSQL                   | otel-rules/postgresql/postgresql-service.yaml  | postgres_exporter -> OpenTelemetry Collector -- OC/OTLP exporter --> SkyWalking OAP Server                        |
| Metrics of Apache APISIX                | otel-rules/apisix.yaml                         | apisix prometheus plugin -> OpenTelemetry Collector -- OC/OTLP exporter --> SkyWalking OAP Server                 |
| Metrics of AWS Cloud EKS                | otel-rules/aws-eks/eks-cluster.yaml            | AWS Container Insights Receiver -> OpenTelemetry Collector -- OC/OTLP exporter --> SkyWalking OAP Server          |
| Metrics of AWS Cloud EKS                | otel-rules/aws-eks/eks-service.yaml            | AWS Container Insights Receiver -> OpenTelemetry Collector -- OC/OTLP exporter --> SkyWalking OAP Server          |
| Metrics of AWS Cloud EKS                | otel-rules/aws-eks/eks-node.yaml               | AWS Container Insights Receiver -> OpenTelemetry Collector -- OC/OTLP exporter --> SkyWalking OAP Server          |

**Note**: You can also use OpenTelemetry exporter to transport the metrics to SkyWalking OAP directly. See [OpenTelemetry Exporter](./backend-meter.md#opentelemetry-exporter).
