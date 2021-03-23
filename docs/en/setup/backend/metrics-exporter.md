# Metrics Exporter
SkyWalking provides basic and most important metrics aggregation, alarm and analysis. 
In real world, people may want to forward the data to their 3rd party system, for deeper analysis or anything else.
**Metrics Exporter** makes that possible.

Metrics exporter is an independent module, you need manually active it.

Right now, we provide the following exporters
1. gRPC exporter

## gRPC exporter
gRPC exporter uses SkyWalking native exporter service definition. Here is proto definition.
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

To active the exporter, you should add this into your `application.yml`
```yaml
exporter:
  grpc:
    targetHost: 127.0.0.1
    targetPort: 9870
```

- `targetHost`:`targetPort` is the expected target service address. You could set any gRPC server to receive the data.
- Target gRPC service needs to be standby, otherwise, the OAP starts up failure.

## For target exporter service 
### subscription implementation
Return the expected metrics name list with event type(increment or total), all the names must match the OAL/MAL script definition. 
Return empty list, if you want to export all metrics in increment event type.

### export implementation
Stream service, all subscribed metrics will be sent to here, based on OAP core schedule. Also, if the OAP deployed as cluster, 
then this method will be called concurrently. For metrics value, you need follow `#type` to choose `#longValue` or `#doubleValue`.