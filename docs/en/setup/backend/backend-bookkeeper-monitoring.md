# BookKeeper monitoring

SkyWalking leverages OpenTelemetry Collector to collect metrics data from the BookKeeper and leverages OpenTelemetry Collector to transfer the metrics to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/meter.md).
Kafka entity as a `Service` in OAP and on the `Layer: BOOKKEEPER.

## Data flow

1. BookKeeper exposes metrics through Prometheus endpoint.
2. OpenTelemetry Collector fetches metrics from BookKeeper cluster via Prometheus Receiver and pushes metrics to SkyWalking OAP Server via OpenTelemetry gRPC exporter.
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to
   filter/calculate/aggregate and store the results.`

## Setup

1. Set up [BookKeeper Cluster](https://bookkeeper.apache.org/docs/deployment/manual). 
2. Set up [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/getting-started/#kubernetes). The example
   for OpenTelemetry Collector configuration, refer
   to [here](../../../../test/e2e-v2/cases/pulsar/otel-collector-config.yaml).
3. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

## BookKeeper Monitoring

Bookkeeper monitoring provides multidimensional metrics monitoring of BookKeeper cluster as `Layer: BOOKKEEPER` `Service` in
the OAP. In each cluster, the nodes are represented as `Instance`.

### BookKeeper Cluster Supported Metrics

| Monitoring Panel               | Metric Name                                                      | Description                                       | Data Source         |
|--------------------------------|------------------------------------------------------------------|---------------------------------------------------|---------------------|
| Bookie Ledgers Count           | meter_bookkeeper_bookie_ledgers_count                            | The number of the bookie ledgers.                 | Bookkeeper Cluster  |
| Bookie Ledger Writable Dirs    | meter_bookkeeper_bookie_ledger_writable_dirs                     | The number of writable directories in the bookie. | Bookkeeper Cluster  |
| Bookie Ledger Dir Usage        | meter_bookkeeper_bookie_ledger_dir_data_bookkeeper_ledgers_usage | The number of successfully created connections.   | Bookkeeper Cluster  |
| Bookie Entries Count           | meter_bookkeeper_bookie_entries_count                            | The number of the bookie write entries.           | Bookkeeper Cluster  |
| Bookie Write Cache Size        | meter_bookkeeper_bookie_write_cache_size                         | The size of the bookie write cache.               | Bookkeeper Cluster  |
| Bookie Write Cache Entry Count | meter_bookkeeper_bookie_write_cache_count                        | The entry count in the bookie write cache.        | Bookkeeper Cluster  |
| Bookie Read Cache Size         | meter_bookkeeper_bookie_read_cache_size                          | The size of the bookie read cache.                | Bookkeeper Cluster  |
| Bookie Read Cache Entry Count  | meter_bookkeeper_bookie_read_cache_count                         | The entry count in the bookie read cache.         | Bookkeeper Cluster  |
| Bookie Read Rate               | meter_bookkeeper_bookie_read_rate                                | The bookie read rate.                             | Bookkeeper Cluster  |
| Bookie Write Rate              | meter_bookkeeper_bookie_write_rate                               | The bookie write rate.                            | Bookkeeper Cluster  |

### BookKeeper Node Supported Metrics

| Monitoring Panel              | Metric Name                                                                                                                                                                                 | Description                                             | Data Source        |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|--------------------|
| JVM Memory Pool Used          | meter_bookkeeper_node_jvm_memory_pool_used                                                                                                                                                  | The usage of the broker jvm memory pool.                | Bookkeeper Bookie  |
| JVM Memory                    | meter_bookkeeper_node_jvm_memory_used <br /> meter_bookkeeper_node_jvm_memory_committed <br /> meter_bookkeeper_node_jvm_memory_init                                                        | The usage of the broker jvm memory.                     | Bookkeeper Bookie  |
| JVM Threads                   | meter_bookkeeper_node_jvm_threads_current <br /> meter_bookkeeper_node_jvm_threads_daemon <br /> meter_bookkeeper_node_jvm_threads_peak <br /> meter_bookkeeper_node_jvm_threads_deadlocked | The count of the jvm threads.                           | Bookkeeper Bookie  |
| GC Time                       | meter_bookkeeper_node_jvm_gc_collection_seconds_sum                                                                                                                                         | Time spent in a given JVM garbage collector in seconds. | Bookkeeper Bookie  |
| GC Count                      | meter_bookkeeper_node_jvm_gc_collection_seconds_count                                                                                                                                       | The count of a given JVM garbage.                       | Bookkeeper Bookie  |
| Thread Executor               | meter_bookkeeper_node_thread_executor_completed                                                                                                                                             | The count of the executor thread.                       | Bookkeeper Bookie  |
| Thread Executor Tasks         | meter_bookkeeper_node_thread_executor_tasks_completed <br /> meter_bookkeeper_node_thread_executor_tasks_rejected <br /> meter_bookkeeper_node_thread_executor_tasks_failed                 | The count of the executor tasks.                        | Bookkeeper Bookie  |
| Pooled Threads                | meter_bookkeeper_node_high_priority_threads <br /> meter_bookkeeper_node_read_thread_pool_threads                                                                                           | The count of the pooled thread.                         | Bookkeeper Bookie  |
| Pooled Threads Max Queue Size | meter_bookkeeper_node_high_priority_thread_max_queue_size <br />  meter_bookkeeper_node_read_thread_pool_max_queue_size                                                                     | The count of the pooled threads max queue size.         | Bookkeeper Bookie  |

## Customizations

You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found
in `otel-rules/bookkeeper/bookkeeper-cluster.yaml, otel-rules/bookkeeper/bookkeeper-node.yaml`.
The RabbitMQ dashboard panel configurations are found in `/config/ui-initialized-templates/bookkeeper`.
