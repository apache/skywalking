## Motivation
RocketMQ is a cloud native messaging and streaming platform, making it simple to build event-driven applications。Now that Skywalking has the capability to monitor OpenTelemetry metrics, I want to add RocketMQ monitoring via the OpenTelemetry Collector, which fetches metrics from the RocketMQ Exporter

## Architecture Graph
There is no significant architecture-level change.

## Proposed Changes
```rocketmq-exporter``` collects metrics from rocketmq and transport the data to opentelemetry collector,using skyWalking openTelemetry receiver to receive these metrics。
Provide cluster ,broker and topic dimensions monitoring.
### RocketMQ Cluster Supported Metrics

| Monitoring Panel                           |Unit        | Metric Name                                                             | Description                                                                                            | Data Source      |
|--------------------------------------------|------------|-------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|------------------|
| Messages Produced Today                    | Count      | meter_rocketmq_cluster_messages_produced_today                          | The number of the cluster messages produced today.                                                     | RocketMQ Exporter |
| Messages Consumed Today                    | Count      | meter_rocketmq_cluster_messages_consumed_today                          | The number of the cluster messages consumed today.                                                     | RocketMQ Exporter |
| Total Producer Tps                         | Msg/sec    | meter_rocketmq_cluster_total_producer_tps                               | The number of messages produced per second.                                                            | RocketMQ Exporter |
| Total Consume Tps                          | Msg/sec    | meter_rocketmq_cluster_total_consumer_tps                               | The number of messages consumed per second.                                                            | RocketMQ Exporter |
| Producer Message Size                      | Bytes/sec  | meter_rocketmq_cluster_producer_message_size                            | The size of a message produced per second.                                                             | RocketMQ Exporter |
| Consumer Message Size                      | Bytes/sec  | meter_rocketmq_cluster_consumer_message_size                            | The size of the consumed message per second.                                                           | RocketMQ Exporter |
| Messages Produced Until Yesterday          | Count      | meter_rocketmq_cluster_messages_produced_until_yesterday                | The total number of messages put until 12 o'clock last night.                                          | RocketMQ Exporter |
| Messages Consumed Until Yesterday          | Count      | meter_rocketmq_cluster_messages_consumed_until_yesterday                | The total number of messages read until 12 o'clock last night.                                         | RocketMQ Exporter |
| Max Consumer Latency                       | Ms         | meter_rocketmq_cluster_max_consumer_latency                             | The max number of consumer latency.                                                                    | RocketMQ Exporter |
| Max CommitLog Disk Ratio                   | %          | meter_rocketmq_cluster_max_commitLog_disk_ratio                         | The max utilization ratio of the commit log disk.                                                      | RocketMQ Exporter |
| CommitLog Disk Ratio                       | %          | meter_rocketmq_cluster_commitLog_disk_ratio                             | The utilization ratio of commit log disk per brokerIp.                                                 | RocketMQ Exporter |
| Pull ThreadPool Queue Head Wait Time       | Ms         | meter_rocketmq_cluster_pull_threadPool_queue_head_wait_time             | The wait time in milliseconds for pulling threadPool queue per brokerIp.                               | RocketMQ Exporter |
| Send ThreadPool Queue Head Wait Time       | Ms         | meter_rocketmq_cluster_send_threadPool_queue_head_wait_time             | The wait time in milliseconds for sending threadPool queue per brokerIp.                               | RocketMQ Exporter |

### RocketMQ Broker Supported Metrics

| Monitoring Panel                           |Unit        | Metric Name                                                             | Description                                                                                            | Data Source      |
|--------------------------------------------|------------|-------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|------------------|
| Produce Tps                                | Msg/sec    | meter_rocketmq_broker_produce_tps                                       | The number of broker produces messages per second.                                                     | RocketMQ Exporter |
| Consume Qps                                | Msg/sec    | meter_rocketmq_broker_consume_qps                                       | The number of broker consumes messages per second.                                                     | RocketMQ Exporter |
| Producer Message Size                      | Bytes/sec  | meter_rocketmq_broker_producer_message_size                             | The size of the messages produced per second.                                                                     | RocketMQ Exporter |
| Consumer Message Size                      | Bytes/sec  | meter_rocketmq_broker_consumer_message_size                             | The size of the messages consumed per second.                                                                     | RocketMQ Exporter |

### RocketMQ Topic Supported Metrics

| Monitoring Panel                           |Unit        | Metric Name                                                             | Description                                                                                            | Data Source      |
|--------------------------------------------|------------|-------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|------------------|
| Max Producer Message Size                  | Byte       | meter_rocketmq_topic_max_producer_message_size                          | The maximum number of messages produced.                                                               | RocketMQ Exporter |
| Max Consumer Message Size                  | Byte       | meter_rocketmq_topic_max_consumer_message_size                          | The maximum number of messages consumed.                                                               | RocketMQ Exporter |
| Consumer Latency                           | Ms         | meter_rocketmq_topic_consumer_latency                                   | Consumption delay time of a consumer group.                                                            | RocketMQ Exporter |
| Producer Tps                               | Msg/sec    | meter_rocketmq_topic_producer_tps                                       | The number of messages produced per second.                                                            | RocketMQ Exporter |
| Consumer Group Tps                         | Msg/sec    | meter_rocketmq_topic_consumer_group_tps                                 | The number of messages consumed per second per consumer group.                                         | RocketMQ Exporter |
| Producer Offset                            | Count      | meter_rocketmq_topic_producer_offset                                    | The progress of a topic's production message.                                                          | RocketMQ Exporter |
| Consumer Group Offset                      | Count      | meter_rocketmq_topic_consumer_group_offset                              | The progress of a topic's consumption message per consumer group.                                      | RocketMQ Exporter |
| Producer Message Size                      | Byte       | meter_rocketmq_topic_producer_message_size                              | The size of messages produced per second.                                                              | RocketMQ Exporter |
| Consumer Message Size                      | Byte       | meter_rocketmq_topic_consumer_message_size                              | The size of messages consumed per second.                                                              | RocketMQ Exporter |


## Imported Dependencies libs and their licenses.
No new dependency.

## Compatibility
no breaking changes.

## General usage docs

This feature is out of box.