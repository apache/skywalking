The Kafka reporter plugin support to report Tracing, JVM Metric data, Instance Properties, and Profiling to Kafka Broker, which is disabled in default. We move the jar of the plugin from `optional-reporter-plugins` to `reporter-plugins` for activating.

Notice, currently, the agent has to communicate with OAP server by GRPC when a task of Profiling updated or created. But it reports nothing beside of this.
 
```properties
# Kafka producer configuration
collector.kafka.bootstrap_servers=${SW_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
collector.kafka.consumer_config[delivery.timeout.ms]=12000
```

Kafka reporter plugin support to customize all configurations of listed in [here](http://kafka.apache.org/24/documentation.html#producerconfigs).
