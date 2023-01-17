# Log Data Protocol

Report log data via protocol.

## Native Proto Protocol

Report `native-proto` format log via gRPC.

[gRPC service define](https://github.com/apache/skywalking-data-collect-protocol/blob/master/logging/Logging.proto)

```protobuf
syntax = "proto3";

package skywalking.v3;

option java_multiple_files = true;
option java_package = "org.apache.skywalking.apm.network.logging.v3";
option csharp_namespace = "SkyWalking.NetworkProtocol.V3";
option go_package = "skywalking.apache.org/repo/goapi/collect/logging/v3";

import "common/Common.proto";
import "common/Command.proto";

// Report collected logs into the OAP backend
service LogReportService {
    // Recommend to report log data in a stream mode.
    // The service/instance/endpoint of the log could share the previous value if they are not set.
    // Reporting the logs of same service in the batch mode could reduce the network cost.
    rpc collect (stream LogData) returns (Commands) {
    }
}

// Log data is collected through file scratcher of agent.
// Natively, Satellite provides various ways to collect logs.
message LogData {
    // [Optional] The timestamp of the log, in millisecond.
    // If not set, OAP server would use the received timestamp as log's timestamp, or relies on the OAP server analyzer.
    int64 timestamp = 1;
    // [Required] **Service**. Represents a set/group of workloads which provide the same behaviours for incoming requests.
    //
    // The logic name represents the service. This would show as a separate node in the topology.
    // The metrics analyzed from the spans, would be aggregated for this entity as the service level.
    //
    // If this is not the first element of the streaming, use the previous not-null name as the service name.
    string service = 2;
    // [Optional] **Service Instance**. Each individual workload in the Service group is known as an instance. Like `pods` in Kubernetes, it
    // doesn't need to be a single OS process, however, if you are using instrument agents, an instance is actually a real OS process.
    //
    // The logic name represents the service instance. This would show as a separate node in the instance relationship.
    // The metrics analyzed from the spans, would be aggregated for this entity as the service instance level.
    string serviceInstance = 3;
    // [Optional] **Endpoint**. A path in a service for incoming requests, such as an HTTP URI path or a gRPC service class + method signature.
    //
    // The logic name represents the endpoint, which logs belong.
    string endpoint = 4;
    // [Required] The content of the log.
    LogDataBody body = 5;
    // [Optional] Logs with trace context
    TraceContext traceContext = 6;
    // [Optional] The available tags. OAP server could provide search/analysis capabilities based on these.
    LogTags tags = 7;
    // [Optional] Since 9.0.0
    // The layer of the service and servce instance. If absent, the OAP would set `layer`=`ID: 2, NAME: general`
    string layer = 8;
}

// The content of the log data
message LogDataBody {
    // A type to match analyzer(s) at the OAP server.
    // The data could be analyzed at the client side, but could be partial
    string type = 1;
    // Content with extendable format.
    oneof content {
        TextLog text = 2;
        JSONLog json = 3;
        YAMLLog yaml = 4;
    }
}

// Literal text log, typically requires regex or split mechanism to filter meaningful info.
message TextLog {
    string text = 1;
}

// JSON formatted log. The json field represents the string that could be formatted as a JSON object.
message JSONLog {
    string json = 1;
}

// YAML formatted log. The yaml field represents the string that could be formatted as a YAML map.
message YAMLLog {
    string yaml = 1;
}

// Logs with trace context, represent agent system has injects context(IDs) into log text.
message TraceContext {
    // [Optional] A string id represents the whole trace.
    string traceId = 1;
    // [Optional] A unique id represents this segment. Other segments could use this id to reference as a child segment.
    string traceSegmentId = 2;
    // [Optional] The number id of the span. Should be unique in the whole segment.
    // Starting at 0.
    int32 spanId = 3;
}

message LogTags {
    // String key, String value pair.
    repeated KeyStringValuePair data = 1;
}

```

## Native Kafka Protocol

Report `native-json` format log via kafka.

Json log record example:
```json
{
    "timestamp":1618161813371,
    "service":"Your_ApplicationName",
    "serviceInstance":"3a5b8da5a5ba40c0b192e91b5c80f1a8@192.168.1.8",
    "layer":"GENERAL",
    "traceContext":{
        "traceId":"ddd92f52207c468e9cd03ddd107cd530.69.16181331190470001",
        "spanId":"0",
        "traceSegmentId":"ddd92f52207c468e9cd03ddd107cd530.69.16181331190470000"
    },
    "tags":{
        "data":[
            {
                "key":"level",
                "value":"INFO"
            },
            {
                "key":"logger",
                "value":"com.example.MyLogger"
            }
        ]
    },
    "body":{
        "text":{
            "text":"log message"
        }
    }
}
```

## HTTP API

Report `json` format logs via HTTP API, the endpoint is `http://<oap-address>:12800/v3/logs`.

Json log record example:

```json
[
  {
    "timestamp": 1618161813371,
    "service": "Your_ApplicationName",
    "serviceInstance": "3a5b8da5a5ba40c0b192e91b5c80f1a8@192.168.1.8",
    "layer":"GENERAL",
    "traceContext": {
      "traceId": "ddd92f52207c468e9cd03ddd107cd530.69.16181331190470001",
      "spanId": "0",
      "traceSegmentId": "ddd92f52207c468e9cd03ddd107cd530.69.16181331190470000"
    },
    "tags": {
      "data": [
        {
          "key": "level",
          "value": "INFO"
        },
        {
          "key": "logger",
          "value": "com.example.MyLogger"
        }
      ]
    },
    "body": {
      "text": {
        "text": "log message"
      }
    }
  }
]
```

