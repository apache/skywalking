# Exporter
SkyWalking provides the essential functions of metrics aggregation, trace, log, alarm, and analysis.
In many real-world scenarios, users may want to forward their data to a 3rd party system for further in-depth analysis.
**Exporter** has made that possible.

The exporter is an independent module that has to be manually activated.

Right now, we provide the following exporters:
1. gRPC Exporter
- Metrics
2. Kafka Exporter
- Trace
- Log

## gRPC Exporter
### Metrics gRPC Exporter
Metrics gRPC exporter uses SkyWalking's native exporter service definition. Here is the proto definition.
```proto
service MetricExportService {
    rpc export (stream ExportMetricValue) returns (ExportResponse) {
    }

    rpc subscription (SubscriptionReq) returns (SubscriptionsResp) {
    }
}

message ExportMetricValue {
    string metricName = 1;
    string entityName = 2;
    string entityId = 3;
    ValueType type = 4;
    int64 timeBucket = 5;
    int64 longValue = 6;
    double doubleValue = 7;
    repeated int64 longValues = 8;
}

message SubscriptionsResp {
    repeated SubscriptionMetric metrics = 1;
}

message SubscriptionMetric {
    string metricName = 1;
    EventType eventType = 2;
}

enum ValueType {
    LONG = 0;
    DOUBLE = 1;
    MULTI_LONG = 2;
}

enum EventType {
    // The metrics aggregated in this bulk, not include the existing persistent data.
    INCREMENT = 0;
    // Final result of the metrics at this moment.
    TOTAL = 1;
}

message SubscriptionReq {

}

message ExportResponse {
}
```

To activate the exporter, you should add this into your `application.yml`
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

To activate the exporter, you should add this into your `application.yml`
```yaml
exporter:
  default:
    # Kafka exporter
    enableKafkaTrace: ${SW_EXPORTER_ENABLE_KAFKA_TRACE:true}
    kafkaBootstrapServers: ${SW_EXPORTER_KAFKA_SERVERS:localhost:9092}
    # Kafka producer config, JSON format as Properties.
    kafkaProducerConfig: ${SW_EXPORTER_KAFKA_PRODUCER_CONFIG:""}
    kafkaTopicTrace: ${SW_EXPORTER_KAFKA_TOPIC_TRACE:skywalking-trace}
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

To activate the exporter, you should add this into your `application.yml`
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
