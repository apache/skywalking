# RocketMQ monitoring

SkyWalking leverages rocketmq-exporter for collecting metrics data from RocketMQ. It leverages OpenTelemetry
Collector to transfer the metrics to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/meter.md).

## Data flow

1. The `rocketmq-exporter` (https://github.com/apache/rocketmq-exporter?tab=readme-ov-file#readme) collects metrics data from RocketMQ, The RocketMQ version is required to be 4.3.2+.
2. OpenTelemetry Collector fetches metrics from rocketmq-exporter via Prometheus Receiver and pushes metrics to
   SkyWalking OAP Server via OpenTelemetry gRPC exporter.
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to
   filter/calculate/aggregate and store the results.

## Setup

1. Setup [rocketmq-exporter](https://github.com/apache/rocketmq-exporter).
2. Set up [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/getting-started/#docker). The example for OpenTelemetry Collector configuration, refer
   to [here](../../../../test/e2e-v2/cases/rocketmq/otel-collector-config.yaml).
3. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

## RocketMQ Monitoring

RocketMQ monitoring provides multidimensional metrics monitoring of RocketMQ Exporter as `Layer: RocketMQ` `Service` in
the OAP. In each cluster, the broker is represented as `Instance` and the topic is represented as `Endpoint`.

### RocketMQ Cluster Supported Metrics

| Monitoring Panel                           |Unit        | Metric Name                                                             | Description                                                              | Data Source      |
|--------------------------------------------|------------|-------------------------------------------------------------------------|--------------------------------------------------------------------------|------------------|
| Messages Produced Today                    | Count      | meter_rocketmq_cluster_messages_produced_today                          | The number of cluster messages produced today.                       | RocketMQ Exporter |
| Messages Consumed Today                    | Count      | meter_rocketmq_cluster_messages_consumed_today                          | The number of cluster messages consumed today.                       | RocketMQ Exporter |
| Total Producer Tps                         | Msg/sec    | meter_rocketmq_cluster_total_producer_tps                               | The number of messages produced per second.                              | RocketMQ Exporter |
| Total Consume Tps                          | Msg/sec    | meter_rocketmq_cluster_total_consumer_tps                               | The number of messages consumed per second.                              | RocketMQ Exporter |
| Producer Message Size                      | Bytes/sec  | meter_rocketmq_cluster_producer_message_size                            | The max size of a message produced per second.                           | RocketMQ Exporter |
| Consumer Message Size                      | Bytes/sec  | meter_rocketmq_cluster_consumer_message_size                            | The max size of the consumed message per second.                         | RocketMQ Exporter |
| Messages Produced Until Yesterday          | Count      | meter_rocketmq_cluster_messages_produced_until_yesterday                | The total number of messages put until 12 o'clock last night.            | RocketMQ Exporter |
| Messages Consumed Until Yesterday          | Count      | meter_rocketmq_cluster_messages_consumed_until_yesterday                | The total number of messages read until 12 o'clock last night.           | RocketMQ Exporter |
| Max Consumer Latency                       | ms         | meter_rocketmq_cluster_max_consumer_latency                             | The max number of consumer latency.                                      | RocketMQ Exporter |
| Max CommitLog Disk Ratio                   | %          | meter_rocketmq_cluster_max_commitLog_disk_ratio                         | The max utilization ratio of the commit log disk.                        | RocketMQ Exporter |
| CommitLog Disk Ratio                       | %          | meter_rocketmq_cluster_commitLog_disk_ratio                             | The utilization ratio of the commit log disk per broker IP.               | RocketMQ Exporter |
| Pull ThreadPool Queue Head Wait Time       | ms         | meter_rocketmq_cluster_pull_threadPool_queue_head_wait_time             | The wait time in milliseconds for pulling threadPool queue per broker IP. | RocketMQ Exporter |
| Send ThreadPool Queue Head Wait Time       | ms         | meter_rocketmq_cluster_send_threadPool_queue_head_wait_time             | The wait time in milliseconds for sending threadPool queue per broker IP. | RocketMQ Exporter |

### RocketMQ Broker Supported Metrics

| Monitoring Panel                           |Unit        | Metric Name                                                             | Description                                        | Data Source       |
|--------------------------------------------|------------|-------------------------------------------------------------------------|----------------------------------------------------|-------------------|
| Produce TPS                                | Msg/sec    | meter_rocketmq_broker_produce_tps                                       | The number of broker produces messages per second. | RocketMQ Exporter |
| Consume QPS                                | Msg/sec    | meter_rocketmq_broker_consume_qps                                       | The number of broker consumes messages per second. | RocketMQ Exporter |
| Producer Message Size                      | Bytes/sec  | meter_rocketmq_broker_producer_message_size                             | The max size of the messages produced per second.  | RocketMQ Exporter |
| Consumer Message Size                      | Bytes/sec  | meter_rocketmq_broker_consumer_message_size                             | The max size of the messages consumed per second.  | RocketMQ Exporter |

### RocketMQ Topic Supported Metrics

| Monitoring Panel                           |Unit        | Metric Name                                                             | Description                                                           | Data Source       |
|--------------------------------------------|------------|-------------------------------------------------------------------------|-----------------------------------------------------------------------|-------------------|
| Max Producer Message Size                  | Byte       | meter_rocketmq_topic_max_producer_message_size                          | The maximum number of messages produced.                              | RocketMQ Exporter |
| Max Consumer Message Size                  | Byte       | meter_rocketmq_topic_max_consumer_message_size                          | The maximum number of messages consumed.                              | RocketMQ Exporter |
| Consumer Latency                           | ms         | meter_rocketmq_topic_consumer_latency                                   | Consumption delay time of a consumer group.                           | RocketMQ Exporter |
| Producer Tps                               | Msg/sec    | meter_rocketmq_topic_producer_tps                                       | The number of messages produced per second.                           | RocketMQ Exporter |
| Consumer Group Tps                         | Msg/sec    | meter_rocketmq_topic_consumer_group_tps                                 | The number of messages consumed per second per consumer group.        | RocketMQ Exporter |
| Producer Offset                            | Count      | meter_rocketmq_topic_producer_offset                                    | The max progress of a topic's production message.                     | RocketMQ Exporter |
| Consumer Group Offset                      | Count      | meter_rocketmq_topic_consumer_group_offset                              | The max progress of a topic's consumption message per consumer group. | RocketMQ Exporter |
| Producer Message Size                      | Byte/sec   | meter_rocketmq_topic_producer_message_size                              | The max size of messages produced per second.                         | RocketMQ Exporter |
| Consumer Message Size                      | Byte/sec   | meter_rocketmq_topic_consumer_message_size                              | The max size of messages consumed per second.                         | RocketMQ Exporter |

## Customizations

You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found
in `otel-rules/rocketmq/rocketmq-cluster.yaml, otel-rules/rocketmq/rocketmq-broker.yaml, otel-rules/rocketmq/rocketmq-topic.yaml`.
The RocketMQ dashboard panel configurations are found in `ui-initialized-templates/rocketmq`.