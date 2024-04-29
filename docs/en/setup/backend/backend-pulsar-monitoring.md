# Pulsar monitoring

SkyWalking leverages OpenTelemetry Collector to collect metrics data in Prometheus format from the Pulsar and transfer the metrics to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/meter.md).
Kafka entity as a `Service` in OAP and on the `Layer: PULSAR.

## Data flow

1. Pulsar exposes metrics through Prometheus endpoint.
2. OpenTelemetry Collector fetches metrics from Pulsar cluster via Prometheus Receiver and pushes metrics to SkyWalking OAP Server via OpenTelemetry gRPC exporter.
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to
   filter/calculate/aggregate and store the results.`

## Setup

1. Set up [Pulsar Cluster](https://pulsar.apache.org/docs/3.1.x/getting-started-docker-compose/). (Pulsar cluster includes pulsar broker cluster and Bookkeeper bookie cluster.)
2. Set up [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/getting-started/#kubernetes). The example
   for OpenTelemetry Collector configuration, refer
   to [here](../../../../test/e2e-v2/cases/pulsar/otel-collector-config.yaml).
3. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

## Pulsar Monitoring

Pulsar monitoring provides multidimensional metrics monitoring of Pulsar cluster as `Layer: PULSAR` `Service` in
the OAP. In each cluster, the nodes are represented as `Instance`.

### Pulsar Cluster Supported Metrics

| Monitoring Panel     | Metric Name                                | Description                                                                                            | Data Source    |
|----------------------|--------------------------------------------|--------------------------------------------------------------------------------------------------------|----------------|
| Total Topics         | meter_pulsar_total_topics                  | The number of Pulsar topics in this cluster.                                                           | Pulsar Cluster |
| Total Subscriptions  | meter_pulsar_total_subscriptions           | The number of Pulsar subscriptions in this cluster.                                                    | Pulsar Cluster |
| Total Producers      | meter_pulsar_total_producers               | The number of active producers connected to this cluster.                                              | Pulsar Cluster |
| Total Consumers      | meter_pulsar_total_consumers               | The number of active consumers connected to this cluster.                                              | Pulsar Cluster |
| Message Rate In      | meter_pulsar_message_rate_in               | The total message rate coming into this cluster (message per second).                                  | Pulsar Cluster |
| Message Rate Out     | meter_pulsar_message_rate_out              | The total message rate going out from this cluster (message per second).                               | Pulsar Cluster |
| Throughput In        | meter_pulsar_throughput_in                 | The total throughput coming into this cluster (byte per second).                                       | Pulsar Cluster |
| Throughput Out       | meter_pulsar_throughput_out                | The total throughput going out from this cluster (byte per second).                                    | Pulsar Cluster |
| Storage Size         | meter_pulsar_storage_size                  | The total storage size of all topics in this broker (in bytes).                                        | Pulsar Cluster |
| Storage Logical Size | meter_pulsar_storage_logical_size          | The storage size of all topics in this broker without replicas (in bytes).                             | Pulsar Cluster |
| Storage Write Rate   | meter_pulsar_storage_write_rate            | The total message batches (entries) written to the storage for this broker (message batch per second). | Pulsar Cluster |
| Storage Read Rate    | meter_pulsar_storage_read_rate             | The total message batches (entries) read from the storage for this broker (message batch per second).  | Pulsar Cluster |


### Pulsar Node Supported Metrics


| Monitoring Panel                | Metric Name                                                                                                                                                                         | Description                                             | Data Source    |
|---------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|----------------|
| Active Connections              | meter_pulsar_broker_active_connections                                                                                                                                              | The number of active connections.                       | Pulsar Broker  |
| Total Connections               | meter_pulsar_broker_total_connections                                                                                                                                               | The total number of connections.                        | Pulsar Broker  |
| Connection Create Success Count | meter_pulsar_broker_connection_create_success_count                                                                                                                                 | The number of successfully created connections.         | Pulsar Broker  |
| Connection Create Fail Count    | meter_pulsar_broker_connection_create_fail_count                                                                                                                                    | The number of failed connections.                       | Pulsar Broker  |
| Connection Closed Total Count   | meter_pulsar_broker_connection_closed_total_count                                                                                                                                   | The total number of closed connections.                 | Pulsar Broker  |
| JVM Buffer Pool Used            | meter_pulsar_broker_jvm_buffer_pool_used_bytes                                                                                                                                      | The usage of jvm buffer pool.                           | Pulsar Broker  |
| JVM Memory Pool Used            | meter_pulsar_broker_jvm_memory_pool_used                                                                                                                                            | The usage of jvm memory pool.                           | Pulsar Broker  |
| JVM Memory                      | meter_pulsar_broker_jvm_memory_init <br /> meter_pulsar_broker_jvm_memory_used <br /> meter_pulsar_broker_jvm_memory_committed                                                      | The usage of jvm memory.                                | Pulsar Broker  |
| JVM Threads                     | meter_pulsar_broker_jvm_threads_current <br /> meter_pulsar_broker_jvm_threads_daemon <br /> meter_pulsar_broker_jvm_threads_peak <br /> meter_pulsar_broker_jvm_threads_deadlocked | The usage of jvm threads.                               | Pulsar Broker  |
| GC Time                         | meter_pulsar_broker_jvm_gc_collection_seconds_sum                                                                                                                                   | Time spent in a given JVM garbage collector in seconds. | Pulsar Broker  |
| GC Count                        | meter_pulsar_broker_jvm_gc_collection_seconds_count                                                                                                                                 | The count of a given JVM garbage collector.             | Pulsar Broker  |

## Customizations

You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found
in `otel-rules/pulsar/pulsar-cluster.yaml, otel-rules/pulsar/pulsar-broker.yaml`.
The Pulsar dashboard panel configurations are found in `ui-initialized-templates/pulsar`.
