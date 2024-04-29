# Telemetry for backend
The OAP backend cluster itself is a distributed streaming process system. To assist the Ops team, we provide the telemetry for the OAP backend itself, also known as self-observability (so11y)

By default, the telemetry is disabled by setting `selector` to `none`, like this:

```yaml
telemetry:
  selector: ${SW_TELEMETRY:none}
  none:
  prometheus:
    host: ${SW_TELEMETRY_PROMETHEUS_HOST:0.0.0.0}
    port: ${SW_TELEMETRY_PROMETHEUS_PORT:1234}
    sslEnabled: ${SW_TELEMETRY_PROMETHEUS_SSL_ENABLED:false}
    sslKeyPath: ${SW_TELEMETRY_PROMETHEUS_SSL_KEY_PATH:""}
    sslCertChainPath: ${SW_TELEMETRY_PROMETHEUS_SSL_CERT_CHAIN_PATH:""}
```

You may also set `Prometheus` to enable them. For more information, refer to the details below.

## Self Observability
SkyWalking supports exposing telemetry data representing OAP running status through Prometheus endpoint.
Users could set up OpenTelemetry collector to scrap and forward telemetry data to OAP server for further analysis, 
eventually showing up UI or GraphQL API.

### Static IP or hostname
Add the following configuration to enable self-observability-related modules.

1. Set up prometheus telemetry.
```yaml
telemetry:
  selector: ${SW_TELEMETRY:prometheus}
  prometheus:
    host: 127.0.0.1
    port: 1543
```

2. Set up OpenTelemetry to scrape the metrics from OAP telemetry.

Refer to [the E2E test case](../../../../test/e2e-v2/cases/so11y/otel-collector-config.yaml) as an example.

For Kubernetes deployments, read the following section, otherwise you should be able to
adjust the configurations below to fit your scenarios.

### Service discovery on Kubernetes

If you deploy an OAP server cluster on Kubernetes, the oap-server instance (pod) would not have a static IP or hostname. We can leverage [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/getting-started/#kubernetes) to discover the oap-server instance, and scrape & transfer the metrics to OAP [OpenTelemetry receiver](opentelemetry-receiver.md).

On how to install SkyWalking on k8s, you can refer to [Apache SkyWalking Kubernetes](https://github.com/apache/skywalking-helm).

Set this up following these steps:

1. Set up oap-server.
- Set the metrics port.
  ```
  prometheus-port: 1234
  ```
- Set environment variables.
  ```
  SW_TELEMETRY=prometheus
  SW_OTEL_RECEIVER=default
  SW_OTEL_RECEIVER_ENABLED_OTEL_METRICS_RULES=oap
  ```

  Here is an example to install by Apache SkyWalking Kubernetes:
  ```
  helm -n istio-system install skywalking skywalking \
               --set elasticsearch.replicas=1 \
               --set elasticsearch.minimumMasterNodes=1 \
               --set elasticsearch.imageTag=7.5.1 \
               --set oap.replicas=2 \
               --set ui.image.repository=$HUB/skywalking-ui \
               --set ui.image.tag=$TAG \
               --set oap.image.tag=$TAG \
               --set oap.image.repository=$HUB/skywalking-oap \
               --set oap.storageType=elasticsearch \
               --set oap.ports.prometheus-port=1234 \ # <<< Expose self observability metrics port
               --set oap.env.SW_TELEMETRY=prometheus \
               --set oap.env.SW_OTEL_RECEIVER=default \ # <<< Enable Otel receiver
               --set oap.env.SW_OTEL_RECEIVER_ENABLED_OTEL_METRICS_RULES=oap # <<< Add oap analyzer for Otel metrics
  ```
2. Set up OpenTelemetry Collector and config a scrape job:
``` yaml
- job_name: 'skywalking-so11y' # make sure to use this in the so11y.yaml to filter only so11y metrics
  metrics_path: '/metrics'
  kubernetes_sd_configs:
  - role: pod
  relabel_configs:
  - source_labels: [__meta_kubernetes_pod_container_name, __meta_kubernetes_pod_container_port_name]
    action: keep
    regex: oap;prometheus-port
  - source_labels: []
    target_label: service
    replacement: oap-server
  - source_labels: [__meta_kubernetes_pod_name]
    target_label: host_name
    regex: (.+)
    replacement: $$1
```
For the full example for OpenTelemetry Collector configuration and recommended version, you can refer to [showcase](https://github.com/apache/skywalking-showcase/tree/main/deploy/platform/kubernetes/templates/feature-so11y).



___

Users also could leverage the Prometheus endpoint for their own Prometheus and Grafana.

**NOTE**: Since Apr 21, 2021, the **Grafana** project has been relicensed to **AGPL-v3**, and is no longer licensed for Apache 2.0. Check the LICENSE details.
The following Prometheus + Grafana solution is optional rather than recommended.

## Prometheus
Prometheus is supported as a telemetry implementor, which collects metrics from SkyWalking's backend.

Set `prometheus` to provider. The endpoint opens at `http://0.0.0.0:1234/` and `http://0.0.0.0:1234/metrics`.
```yaml
telemetry:
  selector: ${SW_TELEMETRY:prometheus}
  prometheus:
```

Set host and port if needed.
```yaml
telemetry:
  selector: ${SW_TELEMETRY:prometheus}
  prometheus:
    host: 127.0.0.1
    port: 1543
```

Set relevant SSL settings to expose a secure endpoint. Note that the private key file and cert chain file could be uploaded once
changes are applied to them.
```yaml
telemetry:
  selector: ${SW_TELEMETRY:prometheus}
  prometheus:
    host: 127.0.0.1
    port: 1543
    sslEnabled: true
    sslKeyPath: /etc/ssl/key.pem
    sslCertChainPath: /etc/ssl/cert-chain.pem
```

### Grafana Visualization
Provide the Grafana dashboard settings.
Check [SkyWalking OAP Cluster Monitor Dashboard](grafana-cluster.json) config and [SkyWalking OAP Instance Monitor Dashboard](grafana-instance.json) config.
