# OpenTelemetry Trace Format

SkyWalking can receive traces in OTLP format and convert them to Zipkin Trace format eventually.
For data analysis and queries related to Zipkin Trace, please [refer to the relevant documentation](./zipkin-trace.md#zipkin-query).

OTLP Trace handler references the [Zipkin Exporter in the OpenTelemetry Collector](https://opentelemetry.io/docs/specs/otel/trace/sdk_exporters/zipkin/#summary) to convert the data format.

## Supported Protocols

Both **OTLP/gRPC** and **OTLP/HTTP** are supported for traces, logs, and metrics:

| Signal  | OTLP/gRPC (port 11800)       | OTLP/HTTP (port 12800)  |
|---------|------------------------------|-------------------------|
| Traces  | gRPC `TraceService/Export`    | `POST /v1/traces`       |
| Logs    | gRPC `LogsService/Export`     | `POST /v1/logs`         |
| Metrics | gRPC `MetricsService/Export`  | `POST /v1/metrics`      |

OTLP/HTTP supports both `application/x-protobuf` and `application/json` content types.

## Set up backend receiver

1. Make sure to enable **otlp-traces** handler in OTLP receiver of `application.yml`.
```yaml
receiver-otel:
  selector: default
  default:
    enabledHandlers: otlp-traces
```

2. Make sure to enable zipkin receiver and zipkin query in `application.yml` for config the zipkin.

## Setup Query and Lens UI 

Please read [deploy Lens UI documentation](./zipkin-trace.md#lens-ui) for query OTLP traces.