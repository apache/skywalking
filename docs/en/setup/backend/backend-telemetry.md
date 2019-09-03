# Telemetry for backend
In default, the telemetry is off, like this
```yaml
telemetry:
  none:
```

## Prometheus
Prometheus is supported as telemetry implementor. 
By using this, prometheus collects metrics from skywalking backend.

Set `prometheus` to provider. The endpoint open at `http://0.0.0.0:1234/` and `http://0.0.0.0:1234/metrics`.
```yaml
telemetry:
  prometheus:
```

Set host and port if needed.
```yaml
telemetry:
  prometheus:
    host: 127.0.0.1
    port: 1543
```

### Grafana Visualization
Provide two grafana dashboard settings.
1. Use [SkyWalking trace-mode dashboard](telemetry/trace-mode-grafana.json) when SkyWalking is used with tracing agent.
1. Use [SkyWalking mesh-mode dashboard](telemetry/mesh-mode-grafana.json) when SkyWalking is used with service mesh
telemetry, including istio, envoy. 

## Self Observability

SkyWalking supports to collect telemetry data into OAP backend directly. Users could check them out through UI or
GraphQL API then.

Adding following configuration to enable `so11y`(self-observability) related modules.

```yaml
receiver-so11y:
  default:
telemetry:
  so11y:
```

Another example represents how to combine `promethues` and `so11y`. Adding some items in `so11y` to make it happen.

```yaml
telemetry:
  so11y:
    prometheusExporterEnabled: true
    prometheusExporterHost: 0.0.0.0
    prometheusExporterPort: 1234
```

Then prometheus exporter is listening on `0.0.0.0:1234`.
