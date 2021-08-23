# How to enable Kafka Reporter

The Kafka reporter plugin support report traces, JVM metrics, Instance Properties, and profiled snapshots to Kafka cluster, which is disabled in default. Move the jar of the plugin, `kafka-reporter-plugin-x.y.z.jar`, from `agent/optional-reporter-plugins` to `agent/plugins` for activating.

If you configure to use `compression.type` such as `lz4`, `zstd`, `snappy`, etc., you also need to move the jar of the plugin, `lz4-java-x.y.z.jar` or `zstd-jni-x.y.z.jar` or `snappy-java.x.y.z.jar`, from `agent/optional-reporter-plugins` to `agent/plugins`.

Notice, currently, the agent still needs to configure GRPC receiver for delivering the task of profiling. In other words, the following configure cannot be omitted.

```properties
# Backend service addresses.
collector.backend_service=${SW_AGENT_COLLECTOR_BACKEND_SERVICES:127.0.0.1:11800}

# Kafka producer configuration
plugin.kafka.bootstrap_servers=${SW_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
plugin.kafka.get_topic_timeout=${SW_GET_TOPIC_TIMEOUT:10}
```

Before you activated the Kafka reporter, you have to make sure that [Kafka fetcher](../../backend/backend-fetcher.md#kafka-fetcher) has been opened in service.

### Advanced Kafka Producer Configurations

Kafka reporter plugin support to customize all configurations of listed in [here](http://kafka.apache.org/24/documentation.html#producerconfigs). For example:
```properties
plugin.kafka.producer_config[delivery.timeout.ms]=12000
```

Since SkyWalking 8.8.0, support to configure advanced Producer configurations in JSON format, like this:
```properties
plugin.kafka.producer_config_json={"delivery.timeout.ms": 12000, "compression.type": "snappy"}
```

Currently, there are 2 ways to configure advanced configurations below. Notice that, the new way, configured in JSON format, will be overridden by `plugin.kafka.producer_config[key]=value` when they have the duplication keys.
