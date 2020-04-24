# Telemetry for backend
By default, the telemetry is disabled by setting `selector` to `none`, like this

```yaml
telemetry:
  selector: ${SW_TELEMETRY:none}
  none:
  prometheus:
    host: ${SW_TELEMETRY_PROMETHEUS_HOST:0.0.0.0}
    port: ${SW_TELEMETRY_PROMETHEUS_PORT:1234}
  so11y:
    prometheusExporterEnabled: ${SW_TELEMETRY_SO11Y_PROMETHEUS_ENABLED:true}
    prometheusExporterHost: ${SW_TELEMETRY_PROMETHEUS_HOST:0.0.0.0}
    prometheusExporterPort: ${SW_TELEMETRY_PROMETHEUS_PORT:1234}
```

but you can set one of `prometheus` or `so11y` to enable them, for more information, refer to the details below.

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

### Grafana Visualization
Provide the grafana dashboard settings. Check [SkyWalking Telemetry dashboard](grafana.json) config.


## Self Observability

SkyWalking supports to collect telemetry data into OAP backend directly. Users could check them out through UI or
GraphQL API then.

Adding following configuration to enable `so11y`(self-observability) related modules.

```yaml
receiver-so11y:
  selector: ${SW_RECEIVER_SO11Y:default}
  default:
telemetry:
  selector: ${SW_TELEMETRY:so11y}
  # ... other configurations
```

Another example represents how to combine `promethues` and `so11y`. Adding some items in `so11y` to make it happen.

```yaml
telemetry:
  selector: ${SW_TELEMETRY:so11y}
  so11y:
    prometheusExporterEnabled: true
    prometheusExporterHost: 0.0.0.0
    prometheusExporterPort: 1234
```

Then prometheus exporter is listening on `0.0.0.0:1234`.
