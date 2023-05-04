# Exporter
SkyWalking provides the essential functions of observability, including metrics aggregation, trace, log, alerting, and profiling.
In many real-world scenarios, users may want to forward their data to a 3rd party system for further in-depth analysis.
**Exporter** has made that possible.

The exporter is an independent module that has to be manually activated.

Right now, we provide the following exporting channels:
1. gRPC Exporter
- [Metrics](#metrics-grpc-exporter)
1. Kafka Exporter
- [Trace](#trace-kafka-exporter)
- [Log](#log-kafka-exporter)

## gRPC Exporter
### Metrics gRPC Exporter
Metrics gRPC exporter uses SkyWalking's native export service definition. Here is the proto definition: [metric-exporter.proto](https://github.com/apache/skywalking/blob/master/oap-server/exporter/src/main/proto/metric-exporter.proto).
```proto
service MetricExportService {
    rpc export (stream ExportMetricValue) returns (ExportResponse) {
    }

    rpc subscription (SubscriptionReq) returns (SubscriptionsResp) {
    }
}
```

To activate the exporter, you should set `${SW_EXPORTER_ENABLE_GRPC_METRICS:true}` and config the target gRPC server address.
```yaml
exporter:
  default:
    # gRPC exporter
    enableGRPCMetrics: ${SW_EXPORTER_ENABLE_GRPC_METRICS:true}
    gRPCTargetHost: ${SW_EXPORTER_GRPC_HOST:127.0.0.1}
    gRPCTargetPort: ${SW_EXPORTER_GRPC_PORT:9870}
    ...
```

- `gRPCTargetHost`:`gRPCTargetPort` is the expected target service address. You could set any gRPC server to receive the data.
- Target gRPC service needs to go on standby; otherwise, the OAP startup may fail.

#### Target exporter service
1. Subscription implementation.
Return the expected metrics name list with event type (incremental or total). All names must match the OAL/MAL script definition.
Return empty list, if you want to export all metrics in the incremental event type.

2. Export implementation.
Stream service. All subscribed metrics will be sent here based on the OAP core schedule. Also, if the OAP is deployed as a cluster,
this method will be called concurrently. For metrics value, you need to follow `#type` to choose `#longValue` or `#doubleValue`.

## Kafka Exporter
### Trace Kafka Exporter
Trace kafka exporter pushes messages to the Kafka Broker and Topic `skywalking-trace` to export the trace. Here is the message:
```
ProducerRecord<String, Bytes>
Key: TraceSegmentId
Value: Bytes of SegmentObject
```

The `SegmentObject` definition follows the protocol:
[SkyWalking data collect protocol#Tracing.proto](https://github.com/apache/skywalking-data-collect-protocol/blob/master/language-agent/Tracing.proto).
```proto
// The segment is a collection of spans. It includes all collected spans in a simple one request context, such as a HTTP request process.
message SegmentObject {
    string traceId = 1;
    string traceSegmentId = 2;
    repeated SpanObject spans = 3;
    string service = 4;
    string serviceInstance = 5;
    bool isSizeLimited = 6;
}
```

To activate the exporter, you should set `${SW_EXPORTER_ENABLE_KAFKA_TRACE:true}` and config the Kafka server.
```yaml
exporter:
  default:
    # Kafka exporter
    enableKafkaTrace: ${SW_EXPORTER_ENABLE_KAFKA_TRACE:true}
    kafkaBootstrapServers: ${SW_EXPORTER_KAFKA_SERVERS:localhost:9092}
    # Kafka producer config, JSON format as Properties.
    kafkaProducerConfig: ${SW_EXPORTER_KAFKA_PRODUCER_CONFIG:""}
    kafkaTopicTrace: ${SW_EXPORTER_KAFKA_TOPIC_TRACE:skywalking-trace}
    # Trace filter
    kafkaTraceFilterError: ${SW_EXPORTER_KAFKA_TRACE_FILTER_ERROR:false}
    ...
```

### Log Kafka Exporter
Log kafka exporter pushes messages to the Kafka Broker and Topic `skywalking-log` to export the log. Here is the message:
```
ProducerRecord<String, Bytes>
Key: LogRecordId
Value: Bytes of LogData
```

The `LogData` definition follows the protocol:
[SkyWalking data collect protocol#Logging.proto](https://github.com/apache/skywalking-data-collect-protocol/blob/master/logging/Logging.proto).
```proto
message LogData {
    int64 timestamp = 1;
    string service = 2;
    string serviceInstance = 3;
    string endpoint = 4;
    LogDataBody body = 5;
    TraceContext traceContext = 6;
    LogTags tags = 7;
    string layer = 8;
}
```

To activate the exporter, you should set `${SW_EXPORTER_ENABLE_KAFKA_LOG:true}` and config the Kafka server.
```yaml
exporter:
  default:
    # Kafka exporter
    enableKafkaLog: ${SW_EXPORTER_ENABLE_KAFKA_LOG:true}
    kafkaBootstrapServers: ${SW_EXPORTER_KAFKA_SERVERS:localhost:9092}
    # Kafka producer config, JSON format as Properties.
    kafkaProducerConfig: ${SW_EXPORTER_KAFKA_PRODUCER_CONFIG:""}
    kafkaTopicLog: ${SW_EXPORTER_KAFKA_TOPIC_LOG:skywalking-log}
    ...
```
