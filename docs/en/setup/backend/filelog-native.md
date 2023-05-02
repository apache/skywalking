# Collecting File Log

Application's logs are important data for troubleshooting, usually they are persistent through local or network file
system.
SkyWalking provides ways to collect logs from those files by leveraging popular open-source tools.

## Log files collector

You can use [Filebeat](https://www.elastic.co/cn/beats/filebeat), [Fluentd](https://www.fluentd.org/)
and [FluentBit](http://fluentbit.io) to collect logs, and then transport the logs to SkyWalking OAP through Kafka or
HTTP protocol, with the formats [Kafka JSON](../../api/log-data-protocol.md#native-kafka-protocol)
or [HTTP JSON array](../../api/log-data-protocol.md#http-api).

### Filebeat

Filebeat supports using Kafka to transport logs. Open [kafka-fetcher](kafka-fetcher.md#kafka-fetcher) and enable
configs `enableNativeJsonLog`.

Take the following Filebeat config YAML as an example to set up Filebeat:

- [filebeat.yml](../../../../test/e2e-v2/cases/kafka/log/filebeat.yml)

### Fluentd

Fluentd supports using Kafka to transport logs. Open [kafka-fetcher](kafka-fetcher.md#kafka-fetcher) and enable
configs `enableNativeJsonLog`.

Take the following fluentd config file as an example to set up Fluentd:

- [fluentd.conf](../../../../test/e2e-v2/cases/kafka/log/fluentd.conf)

### Fluent-bit

Fluent-bit sends logs to OAP directly through HTTP(rest port).
Point the output address to `restHost`:`restPort` of `receiver-sharing-server` or `core`(if `receiver-sharing-server` is
inactivated)

Take the following fluent-bit config files as an example to set up Fluent-bit:

- [fluent-bit.conf](../../../../test/e2e-v2/cases/log/fluent-bit/fluent-bit.conf)