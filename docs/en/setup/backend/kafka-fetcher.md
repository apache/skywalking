# Kafka Fetcher

The Kafka Fetcher pulls messages from the Kafka Broker to learn about what agents have delivered. Check the agent documentation for details on how to enable the Kafka reporter. Typically, tracing segments,  service/instance properties, JVM metrics, and meter system data are supported (depending on the agent implementation). Kafka Fetcher can work with gRPC/HTTP Receivers simultaneously for adopting different transport protocols.

Kafka Fetcher is disabled by default. To enable it, configure it as follows.

Namespace aims to isolate multi OAP clusters when using the same Kafka cluster.
If you set a namespace for Kafka fetcher, the OAP will add a prefix to the topic name. You should also set the namespace in the property named `plugin.kafka.namespace` in `agent.config`.

```yaml
kafka-fetcher:
  selector: ${SW_KAFKA_FETCHER:default}
  default:
    bootstrapServers: ${SW_KAFKA_FETCHER_SERVERS:localhost:9092}
    namespace: ${SW_NAMESPACE:""}
```

`skywalking-segments`, `skywalking-jvm-metrics`, `skywalking-clr-metrics`, `skywalking-profilings`, `skywalking-managements`, `skywalking-meters`, `skywalking-logs`
and `skywalking-logs-json` topics are required by `kafka-fetcher`.
If they do not exist, Kafka Fetcher will create them by default. Also, you can create them by yourself before the OAP server starts.

When using the OAP server automatic creation mechanism, you could modify the number of partitions and replications of the topics using the following configurations:

```yaml
kafka-fetcher:
  selector: ${SW_KAFKA_FETCHER:default}
  default:
    bootstrapServers: ${SW_KAFKA_FETCHER_SERVERS:localhost:9092}
    namespace: ${SW_NAMESPACE:""}
    partitions: ${SW_KAFKA_FETCHER_PARTITIONS:3}
    replicationFactor: ${SW_KAFKA_FETCHER_PARTITIONS_FACTOR:2}
    isSharding: ${SW_KAFKA_FETCHER_IS_SHARDING:false}
    consumePartitions: ${SW_KAFKA_FETCHER_CONSUME_PARTITIONS:""}
    consumers: ${SW_KAFKA_FETCHER_CONSUMERS:1}
```

In the cluster mode, all topics have the same number of partitions. Set `"isSharding"` to `"true"` and assign the partitions to consume for the OAP server. Use commas to separate multiple partitions for the OAP server.

The Kafka Fetcher allows you to configure all the Kafka producers listed [here](http://kafka.apache.org/24/documentation.html#consumerconfigs) in property `kafkaConsumerConfig`. For example:
```yaml
kafka-fetcher:
  selector: ${SW_KAFKA_FETCHER:default}
  default:
    bootstrapServers: ${SW_KAFKA_FETCHER_SERVERS:localhost:9092}
    namespace: ${SW_NAMESPACE:""}
    partitions: ${SW_KAFKA_FETCHER_PARTITIONS:3}
    replicationFactor: ${SW_KAFKA_FETCHER_PARTITIONS_FACTOR:2}
    isSharding: ${SW_KAFKA_FETCHER_IS_SHARDING:true}
    consumePartitions: ${SW_KAFKA_FETCHER_CONSUME_PARTITIONS:1,3,5}
    consumers: ${SW_KAFKA_FETCHER_CONSUMERS:1}
    kafkaConsumerConfig:
      enable.auto.commit: true
      ...
```

When using Kafka MirrorMaker 2.0 to replicate topics between Kafka clusters, you can set the source Kafka Cluster alias (mm2SourceAlias) and separator (mm2SourceSeparator) according to your Kafka MirrorMaker [config](https://github.com/apache/kafka/tree/trunk/connect/mirror#remote-topics).
```yaml
kafka-fetcher:
  selector: ${SW_KAFKA_FETCHER:default}
  default:
    bootstrapServers: ${SW_KAFKA_FETCHER_SERVERS:localhost:9092}
    namespace: ${SW_NAMESPACE:""}
    partitions: ${SW_KAFKA_FETCHER_PARTITIONS:3}
    replicationFactor: ${SW_KAFKA_FETCHER_PARTITIONS_FACTOR:2}
    isSharding: ${SW_KAFKA_FETCHER_IS_SHARDING:true}
    consumePartitions: ${SW_KAFKA_FETCHER_CONSUME_PARTITIONS:1,3,5}
    consumers: ${SW_KAFKA_FETCHER_CONSUMERS:1}
    mm2SourceAlias: ${SW_KAFKA_MM2_SOURCE_ALIAS:""}
    mm2SourceSeparator: ${SW_KAFKA_MM2_SOURCE_SEPARATOR:""}
    kafkaConsumerConfig:
      enable.auto.commit: true
      ...
```

## Other Fetcher Plugins
There are other transporter plugins. You can find these plugins from 3rd party repositories.

* [Pulsar Fetcher Plugin](https://github.com/SkyAPM/transporter-plugin-for-skywalking/blob/main/docs/en/pulsar/Pulsar-Fetcher.md)

* [RocketMQ Fetcher Plugin](https://github.com/SkyAPM/transporter-plugin-for-skywalking/blob/main/docs/en/rocketmq/Rocketmq-Fetcher.md)
