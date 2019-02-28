# Telemetry for backend
In default, the telemetry is off, like this
```yaml
telemetry:
  none:
```

## Prometheus
Prometheus is supported as telemetry implementor. 
By using this, prometheus collects metric from skywalking backend.

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