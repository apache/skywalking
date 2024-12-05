# Kafka monitoring

SkyWalking leverages Prometheus JMX Exporter to collect metrics data from the Kafka and leverages OpenTelemetry Collector to transfer the metrics to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/meter.md).
Kafka entity as a `Service` in OAP and on the `Layer: KAFKA`.

## Data flow

1. The `prometheus_JMX_Exporter` collect metrics data from Kafka. Note: Running the exporter as a Java agent.
2. OpenTelemetry Collector fetches metrics from `prometheus_JMX_Exporter` via Prometheus Receiver and pushes metrics to SkyWalking OAP Server via OpenTelemetry gRPC exporter.
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to
   filter/calculate/aggregate and store the results.

## Setup

1. Setup [prometheus_JMX_Exporter](https://github.com/prometheus/jmx_exporter). This is an example for JMX Exporter configuration [kafka-2_0_0.yml](https://raw.githubusercontent.com/prometheus/jmx_exporter/91622ad478015364444e5d63475d1673c09e6268/examples/kafka-2_0_0.yml).
2. Set up [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/getting-started/#kubernetes). The example
   for OpenTelemetry Collector configuration, refer
   to [here](../../../../test/e2e-v2/cases/kafka/kafka-monitoring/otel-collector-config.yaml).
3. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

## Kafka Monitoring

Kafka monitoring provides multidimensional metrics monitoring of Kafka cluster as `Layer: KAFKA` `Service` in
the OAP. In each cluster, the kafka brokers are represented as `Instance`.

### Kafka Cluster Supported Metrics

| Monitoring Panel                    | Metric Name                                     | Description                                                                                                                 | Data Source               |
|-------------------------------------|-------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|---------------------------|
| Under-Replicated Partitions         | meter_kafka_under_replicated_partitions         | Number of under-replicated partitions in the broker. A higher number is a sign of potential issues.                         | Prometheus JMX Exporter   |
| Offline Partitions Count            | meter_kafka_offline_partitions_count            | Number of partitions that are offline. Non-zero values indicate a problem.                                                  | Prometheus JMX Exporter   |
| Partition Count                     | meter_kafka_partition_count                     | Total number of partitions on the broker.                                                                                   | Prometheus JMX Exporter   |
| Leader Count                        | meter_kafka_leader_count                        | Number of leader partitions on this broker.                                                                                 | Prometheus JMX Exporter   |
| Active Controller Count             | meter_kafka_active_controller_count             | The number of active controllers in the cluster. Typically should be 1.                                                     | Prometheus JMX Exporter   |
| Leader Election Rate                | meter_kafka_leader_election_rate                | The rate of leader elections per minute. High rate could be a sign of instability.                                          | Prometheus JMX Exporter   |
| Unclean Leader Elections Per Second | meter_kafka_unclean_leader_elections_per_second | The rate of unclean leader elections per second. Non-zero values indicate a serious problem.                                | Prometheus JMX Exporter   |
| Max Lag                             | meter_kafka_max_lag                             | The maximum lag between the leader and followers in terms of messages still needed to be sent. Higher lag indicates delays. | Prometheus JMX Exporter   |

### Kafka Broker Supported Metrics

| Monitoring Panel                  | Unit      | Metric Name                                           | Description                                                   | Data Source                   |
|-----------------------------------|-----------|-------------------------------------------------------|---------------------------------------------------------------|-------------------------------|
| CPU Usage                         | %         | meter_kafka_broker_cpu_time_total                     | CPU usage in percentage                                       | Prometheus JMX Exporter       |
| Memory Usage                      | %         | meter_kafka_broker_memory_usage_percentage            | JVM heap memory usage in percentage                           | Prometheus JMX Exporter       |
| Incoming Messages                 | Msg/sec   | meter_kafka_broker_messages_per_second                | Rate of incoming messages                                     | Prometheus JMX Exporter       |
| Bytes In                          | Bytes/sec | meter_kafka_broker_bytes_in_per_second                | Rate of incoming bytes                                        | Prometheus JMX Exporter       |
| Bytes Out                         | Bytes/sec | meter_kafka_broker_bytes_out_per_second               | Rate of outgoing bytes                                        | Prometheus JMX Exporter       |
| Replication Bytes In              | Bytes/sec | meter_kafka_broker_replication_bytes_in_per_second    | Rate of incoming bytes for replication                        | Prometheus JMX Exporter       |
| Replication Bytes Out             | Bytes/sec | meter_kafka_broker_replication_bytes_out_per_second   | Rate of outgoing bytes for replication                        | Prometheus JMX Exporter       |
| Under-Replicated Partitions       | Count     | meter_kafka_broker_under_replicated_partitions        | Number of under-replicated partitions                         | Prometheus JMX Exporter       |
| Under Min ISR Partition Count     | Count     | meter_kafka_broker_under_min_isr_partition_count      | Number of partitions below the minimum ISR (In-Sync Replicas) | Prometheus JMX Exporter       |
| Partition Count                   | Count     | meter_kafka_broker_partition_count                    | Total number of partitions                                    | Prometheus JMX Exporter       |
| Leader Count                      | Count     | meter_kafka_broker_leader_count                       | Number of partitions for which this broker is the leader      | Prometheus JMX Exporter       |
| ISR Shrinks                       | Count/sec | meter_kafka_broker_isr_shrinks_per_second             | Rate of ISR (In-Sync Replicas) shrinking                      | Prometheus JMX Exporter       |
| ISR Expands                       | Count/sec | meter_kafka_broker_isr_expands_per_second             | Rate of ISR (In-Sync Replicas) expanding                      | Prometheus JMX Exporter       |
| Max Lag                           | Count     | meter_kafka_broker_max_lag                            | Maximum lag between the leader and follower for a partition   | Prometheus JMX Exporter       |
| Purgatory Size                    | Count     | meter_kafka_broker_purgatory_size                     | Size of purgatory for Produce and Fetch operations            | Prometheus JMX Exporter       |
| Garbage Collector Count           | Count/sec | meter_kafka_broker_garbage_collector_count            | Rate of garbage collection cycles                             | Prometheus JMX Exporter       |
| Requests Per Second               | Req/sec   | meter_kafka_broker_requests_per_second                | Rate of requests to the broker                                | Prometheus JMX Exporter       |
| Request Queue Time                | ms        | meter_kafka_broker_request_queue_time_ms              | Average time a request spends in the request queue            | Prometheus JMX Exporter       |
| Remote Time                       | ms        | meter_kafka_broker_remote_time_ms                     | Average time taken for a remote operation                     | Prometheus JMX Exporter       |
| Response Queue Time               | ms        | meter_kafka_broker_response_queue_time_ms             | Average time a response spends in the response queue          | Prometheus JMX Exporter       |
| Response Send Time                | ms        | meter_kafka_broker_response_send_time_ms              | Average time taken to send a response                         | Prometheus JMX Exporter       |
| Network Processor Avg Idle        | %         | meter_kafka_broker_network_processor_avg_idle_percent | Percentage of idle time for the network processor             | Prometheus JMX Exporter       |
| Topic Messages In Total           | Count     | meter_kafka_broker_topic_messages_in_total            | Total number of messages per topic                            | Prometheus JMX Exporter       |
| Topic Bytes Out Per Second        | Bytes/sec | meter_kafka_broker_topic_bytesout_per_second          | Rate of outgoing bytes per topic                              | Prometheus JMX Exporter       |
| Topic Bytes In Per Second         | Bytes/sec | meter_kafka_broker_topic_bytesin_per_second           | Rate of incoming bytes per topic                              | Prometheus JMX Exporter       |
| Topic Fetch Requests Per Second   | Req/sec   | meter_kafka_broker_topic_fetch_requests_per_second    | Rate of fetch requests per topic                              | Prometheus JMX Exporter       |
| Topic Produce Requests Per Second | Req/sec   | meter_kafka_broker_topic_produce_requests_per_second  | Rate of produce requests per topic                            | Prometheus JMX Exporter       |

## Customizations

You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found
in `/config/otel-rules/kafka/kafka-cluster.yaml, /config/otel-rules/kafka/kafka-node.yaml`.
The Kafka dashboard panel configurations are found in `/config/ui-initialized-templates/kafka`.

## Reference
For more details on monitoring Kafka and the metrics to focus on, see the following articles:

- [Monitoring Kafka Streams Applications](https://docs.confluent.io/platform/current/streams/monitoring.html)
- [Kafka Monitoring](https://kafka.apache.org/documentation/#monitoring)

