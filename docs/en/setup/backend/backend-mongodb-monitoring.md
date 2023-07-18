# MongoDB monitoring

SkyWalking leverages mongodb-exporter for collecting metrics data from MongoDB. It leverages OpenTelemetry
Collector to transfer the metrics to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/meter.md).

## Data flow

1. The mongodb-exporter collect metrics data from MongoDB.
2. OpenTelemetry Collector fetches metrics from mongodb-exporter via Prometheus Receiver and pushes metrics to
   SkyWalking OAP Server via OpenTelemetry gRPC exporter.
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to
   filter/calculate/aggregate and store the results.

## Setup

1. Setup [mongodb-exporter](https://github.com/percona/mongodb_exporter).
2. Set up [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/getting-started/#docker). The example for OpenTelemetry Collector configuration, refer
   to [here](../../../../test/e2e-v2/cases/mongodb/otel-collector-config.yaml).
3. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

## MongoDB Monitoring

MongoDB monitoring provides multidimensional metrics monitoring of MongoDB clusters as `Layer: MONGODB` `Service` in the OAP. In each cluster, the nodes are represented as `Instance`.

### MongoDB Cluster Supported Metrics

| Monitoring Panel                  | Unit | Metric Name                               | Description                                                         | Data Source      |
|-----------------------------------|------|-------------------------------------------|---------------------------------------------------------------------|------------------|
| Uptime (day)                      | day  | meter_mongodb_cluster_uptime              | Maximum uptime of nodes in the cluster                              | mongodb-exporter |
| Data Size (GB)                    | GB   | meter_mongodb_cluster_data_size           | Total data size of the cluster                                      | mongodb-exporter |
| Collection Count                  |      | meter_mongodb_cluster_collection_count    | Number of collection of the cluster                                 | mongodb-exporter |
| Object Count                      |      | meter_mongodb_cluster_object_count        | Number of object of the cluster                                     | mongodb-exporter |
| Document Avg QPS                  |      | meter_mongodb_cluster_document_avg_qps    | Avg document operations rate of nodes                               | mongodb-exporter |
| Operation Avg QPS                 |      | meter_mongodb_cluster_operation_avg_qps   | Avg operations rate of nodes                                        | mongodb-exporter |
| Total Connections                 |      | meter_mongodb_cluster_connections         | Cluster total connections of nodes                                  | mongodb-exporter |
| Cursor Avg                        |      | meter_mongodb_cluster_cursor_avg          | Avg Opened cursor of nodes                                          | mongodb-exporter |
| Replication Lag (ms)              | ms   | meter_mongodb_cluster_repl_lag            | Repl set member avg replication lag, this metric works in repl mode | mongodb-exporter |
| DB Avg Data Size Per Shard (GB)   | GB   | meter_mongodb_cluster_db_data_size        | Avg data size per shard (replSet) of every database                 | mongodb-exporter |
| DB Avg Index Size Per Shard (GB)  | GB   | meter_mongodb_cluster_db_index_size       | Avg index size per shard (replSet) of every database                | mongodb-exporter |
| DB Avg Collection Count Per Shard |      | meter_mongodb_cluster_db_collection_count | Avg collection count per shard (replSet) of every database          | mongodb-exporter |
| DB Avg Index Count Per Shard      |      | meter_mongodb_cluster_db_index_count      | Avg index count per shard (replSet) of every database               | mongodb-exporter |

### MongoDB Node Supported Metrics

| Monitoring Panel             | Unit | Metric Name                                                                                                           | Description                                                | Data Source      |
|------------------------------|------|-----------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------|------------------|
| Uptime (day)                 | day  | meter_mongodb_node_uptime                                                                                             | Uptime of the node                                         | mongodb-exporter |
| QPS                          |      | meter_mongodb_node_qps                                                                                                | Operations per second of the node                          | mongodb-exporter | 
| Latency                      | µs   | meter_mongodb_node_latency                                                                                            | Latency of operations                                      | mongodb-exporter |
| Memory Usage                 | %    | meter_mongodb_node_memory_usage                                                                                       | Memory usage percent of RAM                                | mongodb-exporter |
| Version                      |      | meter_mongodb_node_version                                                                                            | MongoDB edition and version                                | mongodb-exporter |
| ReplSet State                |      | meter_mongodb_node_rs_state                                                                                           | Repl set state of the node, this metric works in repl mode | mongodb-exporter |
| CPU Usage (%)                | %    | meter_mongodb_node_cpu_total_percentage                                                                               | Cpu usage percent of the node                              | mongodb-exporter |
| Network (KB/s)               | KB/s | meter_mongodb_node_network_bytes_in<br/>meter_mongodb_node_network_bytes_out                                          | Inbound and outbound network bytes of node                 | mongodb-exporter |
| Memory Free (GB)             | GB   | meter_mongodb_node_memory_free_kb<br/>meter_mongodb_node_swap_memory_free_kb                                          | Free memory of RAM and swap                                | mongodb-exporter |
| Disk (GB)                    | GB   | meter_mongodb_node_fs_used_size<br/>meter_mongodb_node_fs_total_size                                                  | Used and total size of disk                                | mongodb-exporter |
| Connections                  |      | meter_mongodb_node_connections                                                                                        | Connection nums of node                                    | mongodb-exporter |
| Active Client                |      | meter_mongodb_node_active_total_num<br/>meter_mongodb_node_active_reader_num<br/>meter_mongodb_node_active_writer_num | Count of active reader and writer                          | mongodb-exporter |
| Transactions                 |      | meter_mongodb_node_transactions_active<br/>meter_mongodb_node_transactions_inactive                                   | Count of transactions running on the node                  | mongodb-exporter |
| Document QPS                 |      | meter_mongodb_node_document_qps                                                                                       | Document operations per second                             | mongodb-exporter |
| Operation QPS                |      | meter_mongodb_node_operation_qps                                                                                      | Operations per second                                      | mongodb-exporter |
| Repl Operation QPS           |      | meter_mongodb_node_repl_operation_qps                                                                                 | Repl operations per second                                 | mongodb-exporter |
| Operation Latency (µs)       | µs   | meter_mongodb_node_operation_latency                                                                                  | Latencies for different operation type                     | mongodb-exporter |
| Cursor                       |      | meter_mongodb_node_cursor                                                                                             | Opened cursor of the node                                  | mongodb-exporter |
| Server Status Memory (MB)    | MB   | meter_mongodb_node_mem_virtual<br/>meter_mongodb_node_mem_resident                                                    | Virtual and resident memory of the node                    | mongodb-exporter |
| Asserts                      |      | meter_mongodb_node_asserts                                                                                            | The rate of raised assertions                              | mongodb-exporter |
| Repl Buffer Count            |      | meter_mongodb_node_repl_buffer_count                                                                                  | The current number of operations in the oplog buffer       | mongodb-exporter |
| Repl Buffer Size (MB)        | MB   | meter_mongodb_node_repl_buffer_size<br/>meter_mongodb_node_repl_buffer_size_max                                       | The maximum size of the oplog buffer                       | mongodb-exporter |
| Queued Operation             |      | meter_mongodb_node_queued_operation                                                                                   | The number of operations queued because of a lock          | mongodb-exporter |
| getLastError Write Num       |      | meter_mongodb_node_write_wait_num<br/>meter_mongodb_node_write_wait_timeout_num                                       | The number of write concern operation                      | mongodb-exporter |
| getLastError Write Time (ms) | ms   | meter_mongodb_node_write_wait_time                                                                                    | The wait time of write concern operation                   | mongodb-exporter |

## Customizations

You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found
in `/config/otel-rules/mongodb/mongodb-cluster.yaml, /config/otel-rules/mongodb/mongodb-node.yaml`.
The MongoDB dashboard panel configurations are found in `/config/ui-initialized-templates/mongodb`.
