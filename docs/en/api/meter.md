# Meter APIs
SkyWalking has a native metrics format, and supports widely used metric formats, such as Prometheus, OpenTelemetry, and Zabbix.

```protobuf
syntax = "proto3";

package skywalking.v3;

option java_multiple_files = true;
option java_package = "org.apache.skywalking.apm.network.language.agent.v3";
option go_package = "skywalking.apache.org/repo/goapi/collect/language/agent/v3";

import "common/Command.proto";

service MeterReportService {
    // Meter data is reported in a certain period. The agent/SDK should report all collected metrics in this period through one stream.
    // The whole stream is an input data set, client should onComplete the stream per report period.
    rpc collect (stream MeterData) returns (Commands) {
    }

    // Reporting meter data in bulk mode as MeterDataCollection.
    // By using this, each one in the stream would be treated as a complete input for MAL engine,
    // comparing to `collect (stream MeterData)`, which is using one stream as an input data set.
    rpc collectBatch (stream MeterDataCollection) returns (Commands) {
    }
}

// Label of the meter
message Label {
    string name = 1;
    string value = 2;
}

// The histogram element definition. It includes the bucket lower boundary and the count in the bucket.
message MeterBucketValue {
    // The value represents the min value of the bucket,
    // the  upper boundary is determined by next MeterBucketValue$bucket,
    // if it doesn't exist, the upper boundary is positive infinity.
    double bucket = 1;
    int64 count = 2;
    // If is negative infinity, the value of the bucket is invalid
    bool isNegativeInfinity = 3;
}

// Meter single value
message MeterSingleValue {
    // Meter name
    string name = 1;
    // Labels
    repeated Label labels = 2;
    // Single value
    double value = 3;
}

// Histogram
message MeterHistogram {
    // Meter name
    string name = 1;
    // Labels
    repeated Label labels = 2;
    // Customize the buckets
    repeated MeterBucketValue values = 3;
}

// Single meter data, if the same metrics have a different label, they will separate.
message MeterData {
    // Meter data could be a single value or histogram.
    oneof metric {
        MeterSingleValue singleValue = 1;
        MeterHistogram histogram = 2;
    }
    // Service name, be set value in the first element in the stream-call.
    string service = 3;
    // Service instance name, be set value in the first element in the stream-call.
    string serviceInstance = 4;
    // Meter data report time, be set value in the first element in the stream-call.
    int64 timestamp = 5;
}

message MeterDataCollection {
    repeated MeterData meterData = 1;
}
```

OpenTelemetry collector, Telegraf agents, Zabbix agents could use their native protocol(e.g. OTLP)
and OAP server would convert metrics into native format and forward them to [Meter Analysis Language](../concepts-and-designs/mal.md) engine.

To learn more about receiving 3rd party formats metrics, see 
- [Meter receiver](../setup/backend/backend-meter.md)
- [OpenTelemetry receiver](../setup/backend/opentelemetry-receiver.md).
- [Zabbix receiver](../setup/backend/backend-zabbix.md)
