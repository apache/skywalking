# Open Fetcher
Fetcher is a concept in SkyWalking backend. It uses pulling mode rather than [receiver](backend-receivers.md), which
read the data from the target systems. This mode is typically in some metrics SDKs, such as Prometheus.

## Prometheus Fetcher
Suppose you want to enable some `metric-custom.yaml` files stored at `fetcher-prom-rules`, append its name to `enabledRules` of 
 `promethues-fetcher` as below:
 
```yaml
prometheus-fetcher:
  selector: ${SW_PROMETHEUS_FETCHER:default}
  default:
    enabledRules: ${SW_PROMETHEUS_FETCHER_ENABLED_RULES:"self,metric-custom"}
```

### Configuration file
Prometheus fetcher is configured via a configuration file. The configuration file defines everything related to fetching
 services and their instances, as well as which rule files to load.

OAP can load the configuration at bootstrap. If the new configuration is not well-formed, OAP fails to start up. The files
are located at `$CLASSPATH/fetcher-prom-rules`.

The file is written in YAML format, defined by the scheme described below. Brackets indicate that a parameter is optional.

A full example can be found [here](../../../../oap-server/server-bootstrap/src/main/resources/fetcher-prom-rules/self.yaml)

Generic placeholders are defined as follows:

 * `<duration>`: a duration This will parse a textual representation of a duration. The formats accepted are based on
                 the ISO-8601 duration format `PnDTnHnMn.nS` with days considered to be exactly 24 hours.
 * `<labelname>`: a string matching the regular expression \[a-zA-Z_\]\[a-zA-Z0-9_\]*
 * `<labelvalue>`: a string of unicode characters
 * `<host>`: a valid string consisting of a hostname or IP followed by an optional port number
 * `<path>`: a valid URL path
 * `<string>`: a regular string

```yaml
# How frequently to fetch targets.
fetcherInterval: <duration>
# Per-fetch timeout when fetching this target.
fetcherTimeout: <duration>
# The HTTP resource path on which to fetch metrics from targets.
metricsPath: <path>
#Statically configured targets.
staticConfig:
  # The targets specified by the static config.
  targets:
    [ - <target> ]
  # Labels assigned to all metrics fetched from the targets.
  labels:
    [ <labelname>: <labelvalue> ... ]
# expSuffix is appended to all expression in this file.
expSuffix: <string>
# insert metricPrefix into metric name:  <metricPrefix>_<raw_metric_name>
metricPrefix: <string>
# Metrics rule allow you to recompute queries.
metricsRules:
   [ - <metric_rules> ]
```

#### <target>

```yaml
# The url of target exporter. the format should be complied with "java.net.URI"
url: <string>
# The path of root CA file.
sslCaFilePath: <string>
```

#### <metric_rules>

```yaml
# The name of rule, which combinates with a prefix 'meter_' as the index/table name in storage.
name: <string>
# MAL expression.
exp: <string>
```

More about MAL, please refer to [mal.md](../../concepts-and-designs/mal.md)

## Kafka Fetcher

Kafka Fetcher pulls messages from Kafka Broker(s) what is the Agent delivered. Check the agent documentation about the details. Typically Tracing Segments, Service/Instance properties, JVM Metrics, and Meter system data are supported.  Kafka Fetcher can work with gRPC/HTTP Receivers at the same time for adopting different transport protocols.

Kafka Fetcher is disabled in default, and we configure as following to enable.

```yaml
kafka-fetcher:
  selector: ${SW_KAFKA_FETCHER:default}
  default:
    bootstrapServers: ${SW_KAFKA_FETCHER_SERVERS:localhost:9092}
```

`skywalking-segments`, `skywalking-metrics`, `skywalking-profile`, `skywalking-managements` and `skywalking-meters` topics are required by `kafka-fetcher`.
If they do not exist, Kafka Fetcher will create them in default. Also, you can create them by yourself before the OAP server started.

When using the OAP server automatical creation mechanism, you could modify the number of partitions and replications of the topics through the following configurations:

```yaml
kafka-fetcher:
  selector: ${SW_KAFKA_FETCHER:default}
  default:
    bootstrapServers: ${SW_KAFKA_FETCHER_SERVERS:localhost:9092}
    partitions: ${SW_KAFKA_FETCHER_PARTITIONS:3}
    replicationFactor: ${SW_KAFKA_FETCHER_PARTITIONS_FACTOR:2}
    enableMeterSystem: ${SW_KAFKA_FETCHER_ENABLE_METER_SYSTEM:false}
    isSharding: ${SW_KAFKA_FETCHER_IS_SHARDING:false}
    consumePartitions: ${SW_KAFKA_FETCHER_CONSUME_PARTITIONS:""}
```

In cluster mode, all topics have the same number of partitions. Then we have to set `"isSharding"` to `"true"` and assign the partitions to consume for OAP server. The OAP server can use commas to separate multiple partitions.

Kafka Fetcher allows to configure all the Kafka producers listed [here](http://kafka.apache.org/24/documentation.html#consumerconfigs) in property `kafkaConsumerConfig`. Such as:
```yaml
kafka-fetcher:
  selector: ${SW_KAFKA_FETCHER:default}
  default:
    bootstrapServers: ${SW_KAFKA_FETCHER_SERVERS:localhost:9092}
    partitions: ${SW_KAFKA_FETCHER_PARTITIONS:3}
    replicationFactor: ${SW_KAFKA_FETCHER_PARTITIONS_FACTOR:2}
    enableMeterSystem: ${SW_KAFKA_FETCHER_ENABLE_METER_SYSTEM:false}
    isSharding: ${SW_KAFKA_FETCHER_IS_SHARDING:true}
    consumePartitions: ${SW_KAFKA_FETCHER_CONSUME_PARTITIONS:1,3,5}
    kafkaConsumerConfig:
      enable.auto.commit: true
      ...
```

When use Kafka MirrorMaker 2.0 to replicate topics between Kafka clusters, you can set the source Kafka Cluster alias(mm2SourceAlias) and separator(mm2SourceSeparator) according to your Kafka MirrorMaker [config](https://github.com/apache/kafka/tree/trunk/connect/mirror#remote-topics).
```yaml
kafka-fetcher:
  selector: ${SW_KAFKA_FETCHER:default}
  default:
    bootstrapServers: ${SW_KAFKA_FETCHER_SERVERS:localhost:9092}
    partitions: ${SW_KAFKA_FETCHER_PARTITIONS:3}
    replicationFactor: ${SW_KAFKA_FETCHER_PARTITIONS_FACTOR:2}
    enableMeterSystem: ${SW_KAFKA_FETCHER_ENABLE_METER_SYSTEM:false}
    isSharding: ${SW_KAFKA_FETCHER_IS_SHARDING:true}
    consumePartitions: ${SW_KAFKA_FETCHER_CONSUME_PARTITIONS:1,3,5}
    mm2SourceAlias: ${SW_KAFKA_MM2_SOURCE_ALIAS:""}
    mm2SourceSeparator: ${SW_KAFKA_MM2_SOURCE_SEPARATOR:""}
    kafkaConsumerConfig:
      enable.auto.commit: true
      ...
```
