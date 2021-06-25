# Log Collecting And Analysis

## Collecting
There are various ways to collect logs from application.

### Log files collector

You can use [Filebeat](https://www.elastic.co/cn/beats/filebeat), [Fluentd](https://fluentd.org)
and [FluentBit](http://fluentbit.io) to collect logs, and then transport the logs to SkyWalking OAP through Kafka or
HTTP protocol, with the formats [Kafka JSON](../../protocols/Log-Data-Protocol.md#native-kafka-protocol)
or [HTTP JSON array](../../protocols/Log-Data-Protocol.md#http-api).

#### Filebeat
Filebeat supports using Kafka to transport logs, you need to
open [kafka-fetcher](backend-fetcher.md#kafka-fetcher) and enable configs `enableNativeJsonLog`.

Take the following filebeat config yaml as an example to set up Filebeat
- [filebeat.yml](../../../../test/e2e/e2e-test/docker/kafka/filebeat.yml)

#### Fluentd
Fluentd supports using Kafka to transport logs, you need to
open [kafka-fetcher](backend-fetcher.md#kafka-fetcher) and enable configs `enableNativeJsonLog`.

Take the following fluentd config file as an example to set up Fluentd
- [fluentd.conf](../../../../test/e2e/e2e-test/docker/kafka/fluentd.conf)

#### Fluent-bit
Fluent-bit sends logs to OAP through HTTP(rest port) directly. 
Point the output address to `restHost`:`restPort` of `receiver-sharing-server` or `core`(if `receiver-sharing-server` inactivated)

Take the following fluent-bit config files as an example to set up Fluent-bit
- [fluent-bit.conf](../../../../test/e2e/e2e-test/docker/log/fluent-bit)

### Java agent's toolkits
Java agent provides toolkit for 
[log4j](../service-agent/java-agent/Application-toolkit-log4j-1.x.md),
[log4j2](../service-agent/java-agent/Application-toolkit-log4j-2.x.md), 
[logback](../service-agent/java-agent/Application-toolkit-logback-1.x.md) 
to report logs through gRPC with automatic injected trace context.

[SkyWalking Satellite sidecar](https://github.com/apache/skywalking-satellite) is a recommended proxy/side to
forward logs including to use Kafka MQ to transport logs. When use this, need to open [kafka-fetcher](backend-fetcher.md#kafka-fetcher)
and enable configs `enableNativeProtoLog`.

Java agent provides toolkit for
[log4j](../service-agent/java-agent/Application-toolkit-log4j-1.x.md#print-skywalking-context-in-your-logs),
[log4j2](../service-agent/java-agent/Application-toolkit-log4j-2.x.md#print-skywalking-context-in-your-logs),
[logback](../service-agent/java-agent/Application-toolkit-logback-1.x.md#print-skywalking-context-in-your-logs)
to report logs through files with automatic injected trace context.

Log framework config examples:
- [log4j1.x fileAppender](../../../../test/e2e/e2e-service-provider/src/main/resources/log4j.properties)
- [log4j2.x fileAppender](../../../../test/e2e/e2e-service-provider/src/main/resources/log4j2.xml)
- [logback fileAppender](../../../../test/e2e/e2e-service-provider/src/main/resources/logback.xml)

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
