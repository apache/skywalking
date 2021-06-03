# Log Data Protocol

Report log data via protocol.

## Native Proto Protocol

Report `native-proto` format log via gRPC.

[gRPC service define](https://github.com/apache/skywalking-data-collect-protocol/blob/master/logging/Logging.proto)

## Native Kafka Protocol

Report `native-json` format log via kafka.

Json log record example:
```json
{
    "timestamp":1618161813371,
    "service":"Your_ApplicationName",
    "serviceInstance":"3a5b8da5a5ba40c0b192e91b5c80f1a8@192.168.1.8",
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

Report `json` format logs via HTTP API, the endpoint is http://<oap-address>:12800/v3/logs.

Json log record example:

```json
[
  {
    "timestamp": 1618161813371,
    "service": "Your_ApplicationName",
    "serviceInstance": "3a5b8da5a5ba40c0b192e91b5c80f1a8@192.168.1.8",
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

