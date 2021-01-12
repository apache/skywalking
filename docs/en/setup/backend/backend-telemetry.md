# Telemetry for backend
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
Provide the grafana dashboard settings. Check [SkyWalking Telemetry dashboard](grafana.json) config.


## Self Observability

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
