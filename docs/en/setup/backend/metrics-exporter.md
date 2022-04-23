# Metrics Exporter
SkyWalking provides the essential functions of metrics aggregation, alarm, and analysis. 
In many real-world scenarios, users may want to forward their data to a 3rd party system for further in-depth analysis.
**Metrics Exporter** has made that possible.

The metrics exporter is an independent module that has to be manually activated.

Right now, we provide the following exporters:
1. gRPC exporter

## gRPC exporter
gRPC exporter uses SkyWalking's native exporter service definition. Here is the proto definition.
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
  grpc:
    targetHost: 127.0.0.1
    targetPort: 9870
```

- `targetHost`:`targetPort` is the expected target service address. You could set any gRPC server to receive the data.
- Target gRPC service needs to go on standby; otherwise, the OAP startup may fail.

## Target exporter service 
### Subscription implementation
Return the expected metrics name list with event type (incremental or total). All names must match the OAL/MAL script definition. 
Return empty list, if you want to export all metrics in the incremental event type.

### Export implementation
Stream service. All subscribed metrics will be sent here based on the OAP core schedule. Also, if the OAP is deployed as a cluster, 
this method will be called concurrently. For metrics value, you need to follow `#type` to choose `#longValue` or `#doubleValue`.
