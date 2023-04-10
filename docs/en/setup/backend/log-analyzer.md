# Log Collection and Analysis

## Collection
There are various ways to collect logs from applications.

### Log files collector

You can use [Filebeat](https://www.elastic.co/cn/beats/filebeat), [Fluentd](https://www.fluentd.org/)
and [FluentBit](http://fluentbit.io) to collect logs, and then transport the logs to SkyWalking OAP through Kafka or
HTTP protocol, with the formats [Kafka JSON](../../api/log-data-protocol.md#native-kafka-protocol)
or [HTTP JSON array](../../api/log-data-protocol.md#http-api).

#### Filebeat
Filebeat supports using Kafka to transport logs. Open [kafka-fetcher](kafka-fetcher.md#kafka-fetcher) and enable configs `enableNativeJsonLog`.

Take the following Filebeat config YAML as an example to set up Filebeat:
- [filebeat.yml](../../../../test/e2e-v2/cases/kafka/log/filebeat.yml)

#### Fluentd
Fluentd supports using Kafka to transport logs. Open [kafka-fetcher](kafka-fetcher.md#kafka-fetcher) and enable configs `enableNativeJsonLog`.

Take the following fluentd config file as an example to set up Fluentd:
- [fluentd.conf](../../../../test/e2e-v2/cases/kafka/log/fluentd.conf)

#### Fluent-bit
Fluent-bit sends logs to OAP directly through HTTP(rest port). 
Point the output address to `restHost`:`restPort` of `receiver-sharing-server` or `core`(if `receiver-sharing-server` is inactivated)

Take the following fluent-bit config files as an example to set up Fluent-bit:
- [fluent-bit.conf](../../../../test/e2e-v2/cases/log/fluent-bit/fluent-bit.conf)

#### OpenTelemetry
You can use OpenTelemetry Collector to transport the logs to SkyWalking OAP.
Read the doc on [Skywalking Exporter](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter/skywalkingexporter) for a detailed guide.

### Java agent's toolkits
Java agent provides toolkits for 
[log4j](https://github.com/apache/skywalking-java/blob/main/docs/en/setup/service-agent/java-agent/Application-toolkit-log4j-1.x.md),
[log4j2](https://github.com/apache/skywalking-java/blob/main/docs/en/setup/service-agent/java-agent/Application-toolkit-log4j-2.x.md), and
[logback](https://github.com/apache/skywalking-java/blob/main/docs/en/setup/service-agent/java-agent/Application-toolkit-logback-1.x.md) 
to report logs through gRPC with automatically injected trace context.

[SkyWalking Satellite sidecar](https://github.com/apache/skywalking-satellite) is a recommended proxy/side that
forwards logs (including the use of Kafka MQ to transport logs). When using this, open [kafka-fetcher](kafka-fetcher.md#kafka-fetcher)
and enable configs `enableNativeProtoLog`.

Java agent provides toolkits for
[log4j](https://github.com/apache/skywalking-java/blob/main/docs/en/setup/service-agent/java-agent/Application-toolkit-log4j-1.x.md#print-skywalking-context-in-your-logs),
[log4j2](https://github.com/apache/skywalking-java/blob/main/docs/en/setup/service-agent/java-agent/Application-toolkit-log4j-2.x.md#print-skywalking-context-in-your-logs), and
[logback](https://github.com/apache/skywalking-java/blob/main/docs/en/setup/service-agent/java-agent/Application-toolkit-logback-1.x.md#print-skywalking-context-in-your-logs)
to report logs through files with automatically injected trace context.

Log framework config examples:
- [log4j1.x fileAppender](../../../../test/e2e-v2/java-test-service/e2e-service-provider/src/main/resources/log4j.properties)
- [log4j2.x fileAppender](../../../../test/e2e-v2/java-test-service/e2e-service-provider/src/main/resources/log4j2.xml)
- [logback fileAppender](../../../../test/e2e-v2/java-test-service/e2e-service-provider/src/main/resources/logback.xml)

### Python agent log reporter

[SkyWalking Python Agent](https://github.com/apache/skywalking-python) implements a log reporter for the [logging 
module](https://docs.python.org/3/library/logging.html) with functionalities aligning with the Java toolkits. 

To explore how to enable the reporting features for your use cases, please refer to the 
[Log Reporter Doc](https://github.com/apache/skywalking-python/blob/master/docs/en/setup/advanced/LogReporter.md) for a detailed guide.

## Log Analyzer

Log analyzer of OAP server supports native log data. OAP could use Log Analysis Language to
structure log content through parsing, extracting and saving logs. 
The analyzer also uses Meter Analysis Language Engine for further metrics calculation.

```yaml
log-analyzer:
  selector: ${SW_LOG_ANALYZER:default}
  default:
    lalFiles: ${SW_LOG_LAL_FILES:default}
    malFiles: ${SW_LOG_MAL_FILES:""}
```

Read the doc on [Log Analysis Language](../../concepts-and-designs/lal.md) for more on log structuring and metrics analysis.
