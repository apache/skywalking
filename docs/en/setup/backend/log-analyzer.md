# Log Collecting And Analysis

## Collecting
There are various ways to collect logs from application.

### Java agent's toolkits
Java agent provides toolkit for 
[log4j](../service-agent/java-agent/Application-toolkit-log4j-1.x.md),
[log4j2](../service-agent/java-agent/Application-toolkit-log4j-2.x.md), 
[logback](../service-agent/java-agent/Application-toolkit-logback-1.x.md) 
to report logs through gRPC with automatic injected trace context.

[SkyWalking Satellite sidecar](https://github.com/apache/skywalking-satellite) is a recommended proxy/side to
forward logs including to use Kafka MQ to transport logs. When use this, need to open [kafka-fetcher](backend-fetcher.md#kafka-fetcher)
and enable configs `enableNativeProtoLog`.

### Log files collector

Java agent provides toolkit for
[log4j](../service-agent/java-agent/Application-toolkit-log4j-1.x.md#print-skywalking-context-in-your-logs),
[log4j2](../service-agent/java-agent/Application-toolkit-log4j-2.x.md#print-skywalking-context-in-your-logs),
[logback](../service-agent/java-agent/Application-toolkit-logback-1.x.md#print-skywalking-context-in-your-logs)
to report logs through files with automatic injected trace context.

Log framework config examples:
- [log4j1.x fileAppender](../../../../test/e2e/e2e-service-provider/src/main/resources/log4j.properties)
- [log4j2.x fileAppender](../../../../test/e2e/e2e-service-provider/src/main/resources/log4j2.xml)
- [logback fileAppender](../../../../test/e2e/e2e-service-provider/src/main/resources/logback.xml)

You can use [Filebeat](https://www.elastic.co/cn/beats/filebeat) 、[Fluentd](https://fluentd.org) to
collect file logs including to use Kafka MQ to transport logs. When use this, need to open [kafka-fetcher](backend-fetcher.md#kafka-fetcher)
and enable configs `enableNativeJsonLog`.

Collector config examples:
- [filebeat.yml](../../../../test/e2e/e2e-test/docker/kafka/filebeat.yml)
- [fluentd.conf](../../../../test/e2e/e2e-test/docker/kafka/fluentd.conf)

Collector output to kafka record examples:
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
                "value":"ERROR"
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

## Log Analyzer

Log analyzer of OAP server supports native log data. OAP could use Log Analysis Language to
structurize log content through parse, extract, and save logs. 
Also the analyzer leverages Meter Analysis Language Engine for further metrics calculation.

```yaml
log-analyzer:
  selector: ${SW_LOG_ANALYZER:default}
  default:
    lalFiles: ${SW_LOG_LAL_FILES:default}
    malFiles: ${SW_LOG_MAL_FILES:""}
```

Read [Log Analysis Language](../../concepts-and-designs/lal.md) documentation to learn log structurize and metrics analysis.