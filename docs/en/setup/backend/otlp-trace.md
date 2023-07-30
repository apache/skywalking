# OpenTelemetry Trace Format
OpenTelemetry metrics and log formats are supported, the trace format is not supported directly.

OpenTelemetry and Zipkin formats are generally logically consistent. 
If the Zipkin server alternative mode is expected, user could use OpenTelemetry Collector's [Zipkin Exporter](https://opentelemetry.io/docs/specs/otel/trace/sdk_exporters/zipkin/)
to transfer the format and forward to OAP as alternative Zipkin server.

Read [Zipkin Trace Doc](zipkin-trace.md) for more details about **Zipkin Server Alternative Mode**.

To contributors, if you want to contribute `otlp-trace` handler in `receiver-otel` receiver, we could accept that PR.
But still, we could require the trace transferred into Zipkin format in the handler.
 