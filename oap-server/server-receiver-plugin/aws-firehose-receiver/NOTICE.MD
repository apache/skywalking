### Notice
The OTEL proto in this module copy from [opentelemetry-proto v0.7.0](https://github.com/open-telemetry/opentelemetry-proto/tree/v0.7.0) as AWS firehose only support OTEL 0.7, and rewrite follows :

1. Only remain metrics part
2. Change `java_package` to avoid class conflict with existing OTEL proto (oap-server/server-receiver-plugin/receiver-proto)

To reduce change of `otel-receiver-plugin` and reuse MAL, `org.apache.skywalking.oap.server.receiver.aws.firehose.OtelMetricsConvertor` converts OTEL 0.7 to existing OTEL proto (oap-server/server-receiver-plugin/receiver-proto)
