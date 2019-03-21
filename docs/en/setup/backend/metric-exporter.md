# Metric Exporter
SkyWalking provides basic and most important metric aggregation, alarm and analysis. 
In real world, people may want to forward the data to their 3rd party system, for deeper analysis or anything else.
**Metric Exporter** makes that possible.

Metric exporter is an independent module, you need manually active it.

Right now, we provide the following exporters
1. gRPC exporter

## gRPC exporter
gRPC exporter uses SkyWalking native exporter service definition. Here is proto definition.
```proto
service MetricExportService {
    rpc export (stream ExportMetricValue) returns (ExportResponse) {
    }
}

message ExportMetricValue {
    string metricName = 1;
    string entityName = 2;
    string entityId = 3;
    ValueType type = 5;
    int64 timeBucket = 6;
    int64 longValue = 7;
    double doubleValue = 8;
}

enum ValueType {
    LONG = 0;
    DOUBLE = 1;
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

`targetHost`:`targetPort` is the expected target service address. You could set any gRPC server to receive the data.