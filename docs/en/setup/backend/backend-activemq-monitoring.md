# ActiveMQ classic monitoring

SkyWalking leverages jmx prometheus exporter for collecting metrics data from ActiveMQ classic. It leverages OpenTelemetry
Collector to transfer the metrics to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/mal.md).

## Data flow

1. ActiveMQ classic has extensive support for JMX to allow you to monitor and control the behavior of the broker via the JMX MBeans.
2. The [jmx prometheus exporter](https://github.com/prometheus/jmx_exporter) collects metrics data from ActiveMQ classic, this exporter is intended to be run as a Java Agent, exposing a HTTP server and serving metrics of the local JVM.  
3. OpenTelemetry Collector fetches metrics from jmx prometheus exporter via Prometheus Receiver and pushes metrics to
   SkyWalking OAP Server via OpenTelemetry gRPC exporter.
4. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to
   filter/calculate/aggregate and store the results.

## Setup

1. [Enable JMX](https://activemq.apache.org/components/classic/documentation/jmx) in `activemq.xml`, the JMX remote port defaults to `1616`, you can change it through `ACTIVEMQ_SUNJMX_START`. The example for ActiveMQ configuration, refer
   to [here](../../../../test/e2e-v2/cases/activemq/config/amq/activemq.xml).
2. Set up [jmx prometheus exporter](https://github.com/prometheus/jmx_exporter) which runs as a Java Agent(recommended) of ActiveMQ classic. If you work with docker, you also can set up [a single server](https://github.com/bitnami/containers/tree/main/bitnami/jmx-exporter) for exporter, refer
   to [here](../../../../test/e2e-v2/cases/activemq/config/amq/config.yaml)(note the configuration of `includeObjectNames`).
3. Set up [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/getting-started/#docker). The example for OpenTelemetry Collector configuration, refer
   to [here](../../../../test/e2e-v2/cases/activemq/otel-collector-config.yaml).
4. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

## ActiveMQ classic Monitoring

ActiveMQ classic monitoring provides multidimensional metrics monitoring of ActiveMQ Exporter as `Layer: ActiveMQ` `Service` in
the OAP. In each cluster, the broker is represented as `Instance` and the destination is represented as `Endpoint`.

### ActiveMQ Cluster Supported Metrics

| Monitoring Panel                           |Unit        | Metric Name                                                             | Description                                                                           | Data Source             |
|--------------------------------------------|------------|-------------------------------------------------------------------------|---------------------------------------------------------------------------------------|-------------------------|
| System Load Average                        | Count      | meter_activemq_cluster_system_load_average                              | The average system load, range:[0, 10000].                                            | JMX Prometheus Exporter |
| Thread Count                               | Count      | meter_activemq_cluster_thread_count                                     | Threads currently used by the JVM.                                                    | JMX Prometheus Exporter |
| Init Heap Memory Usage                     | Bytes      | meter_activemq_cluster_heap_memory_usage_init                           | The initial amount of heap memory available.                                          | JMX Prometheus Exporter |
| Committed Heap Memory Usage                | Bytes      | meter_activemq_cluster_heap_memory_usage_committed                      | The memory is guaranteed to be available for the JVM to use.                          | JMX Prometheus Exporter |
| Used Heap Memory Usage                     | Bytes      | meter_activemq_cluster_heap_memory_usage_used                           | The amount of JVM heap memory currently in use.                                       | JMX Prometheus Exporter |
| Max Heap Memory Usage                      | Bytes      | meter_activemq_cluster_heap_memory_usage_max                            | The maximum possible size of the heap memory.                                         | JMX Prometheus Exporter |
| GC G1 Old Collection Count                 | Count      | meter_activemq_cluster_gc_g1_old_collection_count                       | The gc count of G1 Old Generation(JDK[9,17]).                                         | JMX Prometheus Exporter |
| GC G1 Young Collection Count               | Count      | meter_activemq_cluster_gc_g1_young_collection_count                     | The gc count of G1 Young Generation(JDK[9,17]).                                       | JMX Prometheus Exporter |
| GC G1 Old Collection Time                  | ms         | meter_activemq_cluster_gc_g1_old_collection_time                        | The gc time spent in G1 Old Generation in milliseconds(JDK[9,17]).                    | JMX Prometheus Exporter |
| GC G1 Young Collection Time                | ms         | meter_activemq_cluster_gc_g1_young_collection_time                      | The gc time spent in G1 Young Generation in milliseconds(JDK[9,17]).                  | JMX Prometheus Exporter |
| GC Parallel Old Collection Count           | Count      | meter_activemq_cluster_gc_parallel_old_collection_count                 | The gc count of Parallel Old Generation(JDK[6,8]).                                    | JMX Prometheus Exporter |
| GC Parallel Young Collection Count         | Count      | meter_activemq_cluster_gc_parallel_young_collection_count               | The gc count of Parallel Young Generation(JDK[6,8]).                                  | JMX Prometheus Exporter |
| GC Parallel Old Collection Time            | ms         | meter_activemq_cluster_gc_parallel_old_collection_time                  | The gc time spent in Parallel Old Generation in milliseconds(JDK[6,8]).               | JMX Prometheus Exporter |
| GC Parallel Young Collection Time          | ms         | meter_activemq_cluster_gc_parallel_young_collection_time                | The gc time spent in Parallel Young Generation in milliseconds(JDK[6,8]).             | JMX Prometheus Exporter |
| Enqueue Rate                               | Count/s    | meter_activemq_cluster_enqueue_rate                                     | Number of messages that have been sent to the cluster per second(JDK[6,8]).           | JMX Prometheus Exporter |
| Dequeue Rate                               | Count/s    | meter_activemq_cluster_dequeue_rate                                     | Number of messages that have been acknowledged or discarded on the cluster per second.| JMX Prometheus Exporter |
| Dispatch Rate                              | Count/s    | meter_activemq_cluster_dispatch_rate                                    | Number of messages that has been delivered to consumers per second.                   | JMX Prometheus Exporter |
| Expired Rate                               | Count/s    | meter_activemq_cluster_expired_rate                                     | Number of messages that have been expired per second.                                 | JMX Prometheus Exporter |
| Average Enqueue Time                       | ms         | meter_activemq_cluster_average_enqueue_time                             | The average time a message was held on this cluster.                                  | JMX Prometheus Exporter |
| Max Enqueue Time                           | ms         | meter_activemq_cluster_max_enqueue_time                                 | The max time a message was held on this cluster.                                      | JMX Prometheus Exporter |

### ActiveMQ Broker Supported Metrics

| Monitoring Panel                           |Unit       | Metric Name                                                             | Description                                                                                    | Data Source             |
|--------------------------------------------|-----------|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------- |-------------------------|
| Uptime                                     | sec       | meter_activemq_broker_uptime                                            | Uptime of the broker in day.                                                                   | JMX Prometheus Exporter |
| State                                      |           | meter_activemq_broker_state                                             | If slave broker 1 else 0.                                                                      | JMX Prometheus Exporter |
| Current Connections                        | Count     | meter_activemq_broker_current_connections                               | The number of clients connected to the broker currently.                                       | JMX Prometheus Exporter |
| Current Producer Count                     | Count     | meter_activemq_broker_current_producer_count                            | The number of producers currently attached to the broker.                                      | JMX Prometheus Exporter |
| Current Consumer Count                     | Count     | meter_activemq_broker_current_consumer_count                            | The number of consumers consuming messages from the broker.                                    | JMX Prometheus Exporter |
| Producer Count                             | Count     | meter_activemq_broker_producer_count                                    | Number of message producers active on destinations.                                            | JMX Prometheus Exporter |
| Consumer Count                             | Count     | meter_activemq_broker_consumer_count                                    | Number of message consumers subscribed to destinations.                                        | JMX Prometheus Exporter |
| Enqueue Count                              | Count     | meter_activemq_broker_enqueue_count                                     | The total number of messages sent to the broker.                                               | JMX Prometheus Exporter |
| Dequeue Count                              | Count     | meter_activemq_broker_dequeue_count                                     | The total number of messages the broker has delivered to consumers.                            | JMX Prometheus Exporter |
| Enqueue Rate                               | Count/sec | meter_activemq_broker_enqueue_rate                                      | The total number of messages sent to the broker per second.                                    | JMX Prometheus Exporter |
| Dequeue Rate                               | Count/sec | meter_activemq_broker_dequeue_rate                                      | The total number of messages the broker has delivered to consumers per second.                 | JMX Prometheus Exporter |
| Memory Percent Usage                       | %         | meter_activemq_broker_memory_percent_usage                              | Percentage of configured memory used by the broker.                                            | JMX Prometheus Exporter |
| Memory Usage                               | Bytes     | meter_activemq_broker_memory_percent_usage                              | Memory used by undelivered messages in bytes.                                                  | JMX Prometheus Exporter |
| Memory Limit                               | Bytes     | meter_activemq_broker_memory_limit                                      | Memory limited used for holding undelivered messages before paging to temporary storage.       | JMX Prometheus Exporter |
| Store Percent Usage                        | %         | meter_activemq_broker_store_percent_usage                               | Percentage of available disk space used for persistent message storage.                        | JMX Prometheus Exporter |
| Store Limit                                | Bytes     | meter_activemq_broker_store_limit                                       | Disk limited  used for persistent messages before producers are blocked.                       | JMX Prometheus Exporter |
| Temp Percent Usage                         | Bytes     | meter_activemq_broker_temp_percent_usage                                | Percentage of available disk space used for non-persistent message storage.                    | JMX Prometheus Exporter |
| Temp Limit                                 | Bytes     | meter_activemq_broker_temp_limit                                        | Disk limited used for non-persistent messages and temporary data before producers are blocked. | JMX Prometheus Exporter |
| Average Message Size                       | Bytes     | meter_activemq_broker_average_message_size                              | Average message size on this broker.                                                           | JMX Prometheus Exporter |
| Max Message Size                           | Bytes     | meter_activemq_broker_max_message_size                                  | Max message size on this broker.                                                               | JMX Prometheus Exporter |
| Queue Size                                 | Count     | meter_activemq_broker_queue_size                                        | Number of messages on this broker that have been dispatched but not acknowledged.              | JMX Prometheus Exporter |

### ActiveMQ Destination Supported Metrics

| Monitoring Panel                           |Unit        | Metric Name                                                             | Description                                                                             | Data Source             |
|--------------------------------------------|------------|-------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|-------------------------|
| Producer Count                             | Count      | meter_activemq_destination_producer_count                               | Number of producers attached to this destination.                                       | JMX Prometheus Exporter |
| Consumer Count                             | Count      | meter_activemq_destination_consumer_count                               | Number of consumers subscribed to this destination.                                     | JMX Prometheus Exporter |
| Topic Consumer Count                       | Count      | meter_activemq_destination_topic_consumer_count                         | Number of consumers subscribed to the topics.                                           | JMX Prometheus Exporter |
| Queue Size                                 | Count      | meter_activemq_destination_queue_size                                   | The number of messages that have not been acknowledged by a consumer.                   | JMX Prometheus Exporter |
| Memory Usage                               | Bytes      | meter_activemq_destination_memory_usage                                 | Memory used by undelivered messages in bytes.                                           | JMX Prometheus Exporter |
| Memory Percent Usage                       | %          | meter_activemq_destination_memory_percent_usage                         | Percentage of configured memory used by the destination.                                | JMX Prometheus Exporter |
| Enqueue Count                              | Count      | meter_activemq_destination_enqueue_count                                | The number of messages sent to the destination.                                         | JMX Prometheus Exporter |
| Dequeue Count                              | Count      | meter_activemq_destination_dequeue_count                                | The number of messages the destination has delivered to consumers.                      | JMX Prometheus Exporter |
| Average Enqueue Time                       | ms         | meter_activemq_destination_average_enqueue_time                         | The average time a message was held on this destination.                                | JMX Prometheus Exporter |
| Max Enqueue Time                           | ms         | meter_activemq_destination_max_enqueue_time                             | The max time a message was held on this destination.                                    | JMX Prometheus Exporter |
| Dispatch Count                             | Count      | meter_activemq_destination_dispatch_count                               | Number of messages that has been delivered to consumers.                                | JMX Prometheus Exporter |
| Expired Count                              | Count      | meter_activemq_destination_expired_count                                | Number of messages that have been expired.                                              | JMX Prometheus Exporter |
| Inflight Count                             | Count      | meter_activemq_destination_inflight_count                               | Number of messages that have been dispatched to but not acknowledged by consumers.      | JMX Prometheus Exporter |
| Average Message Size                       | Bytes      | meter_activemq_destination_average_message_size                         | Average message size on this destination.                                               | JMX Prometheus Exporter |
| Max Message Size                           | Bytes      | meter_activemq_destination_max_message_size                             | Max message size on this destination.                                                   | JMX Prometheus Exporter |

## Customizations

You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found
in `otel-rules/activemq/activemq-cluster.yaml, otel-rules/activemq/activemq-broker.yaml, otel-rules/activemq/activemq-destination.yaml`.
The ActiveMQ dashboard panel configurations are found in `ui-initialized-templates/activemq`.
