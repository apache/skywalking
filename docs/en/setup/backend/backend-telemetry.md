# Telemetry for backend
The OAP backend cluster itself is a distributed streaming process system. To assist the Ops team,
we provide the telemetry for the OAP backend itself. 

By default, the telemetry is disabled by setting `selector` to `none`, like this

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

but you can set one of `prometheus` to enable them, for more information, refer to the details below.

## Self Observability
### Dedicated IP or hostname
SkyWalking supports to collect telemetry data into OAP backend directly. Users could check them out through UI or
GraphQL API then.

Adding following configuration to enable self-observability related modules.

1. Setting up prometheus telemetry.
```yaml
telemetry:
  selector: ${SW_TELEMETRY:prometheus}
  prometheus:
    host: 127.0.0.1
    port: 1543
```

2. Setting up prometheus fetcher

```yaml
prometheus-fetcher:
  selector: ${SW_PROMETHEUS_FETCHER:default}
  default:
    enabledRules: ${SW_PROMETHEUS_FETCHER_ENABLED_RULES:"self"}
``` 

3. Make sure `config/fetcher-prom-rules/self.yaml` exists. 

Once you deploy an oap-server cluster, the target host should be replaced with a dedicated IP or hostname. For instances,
there are three oap server in your cluster, their host is `service1`, `service2` and `service3` respectively. You should
update each `self.yaml` to twist target host.

service1: 
```yaml
fetcherInterval: PT15S
fetcherTimeout: PT10S
metricsPath: /metrics
staticConfig:
  # targets will be labeled as "instance"
  targets:
    - service1:1234
  labels:
    service: oap-server
...
```

service2: 
```yaml
fetcherInterval: PT15S
fetcherTimeout: PT10S
metricsPath: /metrics
staticConfig:
  # targets will be labeled as "instance"
  targets:
    - service2:1234
  labels:
    service: oap-server
...
```

service3: 
```yaml
fetcherInterval: PT15S
fetcherTimeout: PT10S
metricsPath: /metrics
staticConfig:
  # targets will be labeled as "instance"
  targets:
    - service3:1234
  labels:
    service: oap-server
...
```
### Service discovery (k8s)
If you deploy an oap-server cluster on k8s, the oap-server instance(pod) could not has the dedicated IP or hostname. We can leverage [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/getting-started/#kubernetes) to discover the oap-server instance and scrape & transfer the metrics to OAP [OpenTelemetry receiver](backend-receivers.md#opentelemetry-receiver). 

How to install SkyWalking on k8s can refer to [Apache SkyWalking Kubernetes](https://github.com/apache/skywalking-kubernetes)

To set this up by the following steps:

1. Set up oap-server
- Set the metrics port 
  ```
  prometheus-port: 1234
  ```
- Set environment variables 
  ```
  SW_TELEMETRY=prometheus 
  SW_OTEL_RECEIVER=default 
  SW_OTEL_RECEIVER_ENABLED_OC_RULES=oap
  ```
2. Set up OpenTelemetry Collector and config a scrape job:
``` yaml
- job_name: 'skywalking'
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
 The full example for OpenTelemetry Collector configuration and recommend version can refer to [otel-collector-oap.yaml](otel-collector-oap.yaml).



___

**WARNING**, since Apr 21, 2021, **Grafana** project has been relicensed to **AGPL-v3**, no as Apache 2.0 anymore. Check the LICENSE details.
The following Prometheus + Grafana solution is optional, not a recommendation.

## Prometheus
Prometheus is supported as telemetry implementor. 
By using this, prometheus collects metrics from SkyWalking backend.

Set `prometheus` to provider. The endpoint open at `http://0.0.0.0:1234/` and `http://0.0.0.0:1234/metrics`.
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

Set SSL relevant settings to expose a secure endpoint. Notice private key file and cert chain file could be uploaded once
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
Provide the grafana dashboard settings. 
Check [SkyWalking OAP Cluster Monitor Dashboard](grafana-cluster.json) config and [SkyWalking OAP Instance Monitor Dashboard](grafana-instance.json) config.



