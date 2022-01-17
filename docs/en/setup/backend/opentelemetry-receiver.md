# OpenTelemetry receiver

The OpenTelemetry receiver supports ingesting agent metrics by meter-system. The OAP can load the configuration at bootstrap.
If the new configuration is not well-formed, the OAP may fail to start up. The files are located at `$CLASSPATH/otel-<handler>-rules`.
E.g. The `oc` handler loads rules from `$CLASSPATH/otel-oc-rules`.

Supported handlers:

* `oc`: [OpenCensus](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/a08903f05d3a544f548535c222b1c205b9f5a154/exporter/opencensusexporter/README.md) gRPC service handler.

**Notice:**  Set `SW_OTEL_RECEIVER=default` through system environment or change `receiver-otel/selector=${SW_OTEL_RECEIVER:default}` to activate the OpenTelemetry receiver.

The rule file should be in YAML format, defined by the scheme described in [prometheus-fetcher](./prometheus-metrics.md).
Note: `receiver-otel` only supports the `group`, `defaultMetricLevel`, and `metricsRules` nodes of the scheme due to its push mode.

To activate the `oc` handler and relevant rules of `istio`:

```yaml
receiver-otel:
  // Change selector value to default, for activating the otel receiver.
  selector: ${SW_OTEL_RECEIVER:default}
  default:
    enabledHandlers: ${SW_OTEL_RECEIVER_ENABLED_HANDLERS:"oc"}
    enabledOcRules: ${SW_OTEL_RECEIVER_ENABLED_OC_RULES:"istio-controlplane"}
```
The receiver adds labels with `key = node_identifier_host_name` and `key = node_identifier_pid` to the collected data samples,
and values from `Node.identifier.host_name` and `Node.identifier.pid` defined in OpenCensus Agent Proto,
for identification of the metric data.

| Rule Name | Description | Configuration File | Data Source |
|----|----|-----|----|
|istio-controlplane| Metrics of Istio control panel | otel-oc-rules/istio-controlplane.yaml | Istio Control Panel -> OpenTelemetry Collector --OC format--> SkyWalking OAP Server |
|oap| Metrics of SkyWalking OAP server itself | otel-oc-rules/oap.yaml | SkyWalking OAP Server(SelfObservability) -> OpenTelemetry Collector --OC format--> SkyWalking OAP Server |
|vm| Metrics of VMs | otel-oc-rules/vm.yaml | Prometheus node-exporter(VMs) -> OpenTelemetry Collector --OC format--> SkyWalking OAP Server |
|k8s-cluster| Metrics of K8s cluster | otel-oc-rules/k8s-cluster.yaml | K8s kube-state-metrics -> OpenTelemetry Collector --OC format--> SkyWalking OAP Server |
|k8s-node| Metrics of K8s cluster | otel-oc-rules/k8s-node.yaml | cAdvisor & K8s kube-state-metrics -> OpenTelemetry Collector --OC format--> SkyWalking OAP Server |
|k8s-service| Metrics of K8s cluster | otel-oc-rules/k8s-service.yaml | cAdvisor & K8s kube-state-metrics -> OpenTelemetry Collector --OC format--> SkyWalking OAP Server |

Note: You can also use OpenTelemetry exporter to directly transport the metrics to SkyWalking OAP. See [OpenTelemetry Exporter](./backend-meter.md#OpenTelemetry Exporter).