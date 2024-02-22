# OpenTelemetry Logging Format

SkyWalking can receive logs exported from OpenTelemetry collector, the data flow is:

```mermaid
graph LR
    B[OpenTelemetry SDK 1]
    C[FluentBit/FluentD, etc.]
    K[Other sources that OpenTelemetry supports ...]
    D[OpenTelemetry Collector]
    E[SkyWalking OAP Server]
    B --> D
    C --> D
    K --> D
    D -- exporter --> E
```

Recommend to use [OpenTelemetry OTLP Exporter](#opentelemetry-otlp-exporter) to forward collected logs to OAP server in OTLP
format, and SkyWalking OAP is responsible for transforming the data format into native log format with analysis support
powered by [LAL script](../../concepts-and-designs/lal.md).

___
Deprecated: unmaintained and not recommended to use, will be removed.

[OpenTelemetry SkyWalking Exporter](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/5133f4ccd69fa40d016c5b7f2198fb6ac61007b4/exporter/skywalkingexporter) was first added into `open-telemetry/opentelemetry-collector-contrib` before 
OAP OTLP support. It transforms the logs to SkyWalking format before sending them to SkyWalking OAP. Currently, from OTLP
community, it is not well maintained, and already being marked as `unmaintained`, and may be removed in 2024.
___

## OpenTelemetry OTLP Exporter

By using this exporter, you can send any log data to SkyWalking OAP as long as the data is in OTLP format, no matter
where the data is generated.

To enable this exporter, make sure the `receiver-otel` is enabled and the `otlp-logs` value is in
the `receiver-otel/default/enabledHandlers` configuration section:

```yaml
receiver-otel:
  selector: ${SW_OTEL_RECEIVER:default}
  default:
    enabledHandlers: ${SW_OTEL_RECEIVER_ENABLED_HANDLERS:"otlp-metrics,otlp-logs"}
```

Also, because most of the language SDKs of OpenTelemetry do not support logging feature (yet) or the logging feature is
experimental, it's your responsibility to make sure the reported log data contains the following attributes, otherwise
SkyWalking is not able to consume them:

- `service.name`: the name of the service that generates the log data, OpenTelemetry Java SDK (experimental) has this
  attribute set, if you're using other SDK or agent, please check the corresponding doc.
