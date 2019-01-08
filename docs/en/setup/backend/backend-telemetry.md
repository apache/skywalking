# Telemetry for backend
In default, the telemetry is off, like this
```yaml
telemetry:
  none:
```

In order to open, we should set `prometheus` to provider. The endpoint open at http://0.0.0.0:1234/ 
```yaml
telemetry:
  prometheus:
```

You could set host and port
```yaml
telemetry:
  prometheus:
    host: 127.0.0.1
    port: 1543
```