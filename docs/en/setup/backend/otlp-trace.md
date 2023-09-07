# OpenTelemetry Trace Format

SkyWalking can receive traces from Traces in OTLP format and convert them to Zipkin Trace format eventually. 
For data analysis and queries related to Zipkin Trace, please [refer to the relevant documentation](./zipkin-trace.md#zipkin-query).

OTLP Trace handler references the [Zipkin Exporter in the OpenTelemetry Collector](https://opentelemetry.io/docs/specs/otel/trace/sdk_exporters/zipkin/#summary) to convert the data format.

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