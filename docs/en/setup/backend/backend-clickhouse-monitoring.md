# ClickHouse monitoring

## ClickHouse server performance from built-in metrics data

SkyWalking leverages ClickHouse built-in metrics data since v20.1.2.4. It leverages OpenTelemetry Collector to transfer
the metrics to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/mal.md).

### Data flow

1. Configure ClickHouse to expose metrics data for scraping from Prometheus.
2. OpenTelemetry Collector fetches metrics from ClickeHouse server through Prometheus endpoint, and pushes metrics to SkyWalking OAP Server via
   OpenTelemetry gRPC exporter.
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to
   filter/calculate/aggregate and store the results.

### Set up

1. Set
   up [built-in prometheus endpoint](https://clickhouse.com/docs/en/operations/server-configuration-parameters/settings#prometheus)
   .
2. Set up [OpenTelemetry Collector ](https://opentelemetry.io/docs/collector/getting-started/#docker). For details on
   Prometheus Receiver in OpenTelemetry Collector, refer
   to [here](../../../../test/e2e-v2/cases/clickhouse/clickhouse-prometheus-endpoint/otel-collector-config.yaml).
3. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

### ClickHouse Monitoring

ClickHouse monitoring provides monitoring of the metrics 、events and asynchronous_metrics of the ClickHouse server.
ClickHouse cluster is cataloged as a `Layer: CLICKHOUSE` `Service` in OAP. Each ClickHouse server is cataloged as
an `Instance` in OAP.

#### ClickHouse Instance Supported Metrics

| Monitoring Panel | Unit       | Metric Name                                | Description                                                                                                      | Data Source |
| ---------------- | ---------- | ------------------------------------------ | ---------------------------------------------------------------------------------------------------------------- | ----------- |
| CpuUsage         | count      | meter_clickhouse_instance_cpu_usage        | CPU time spent seen by OS per second(according to ClickHouse.system.dashboard.CPU Usage (cores)).                | ClickHouse  |
| MemoryUsage      | percentage | meter_clickhouse_instance_memory_usage     | Total amount of memory (bytes) allocated by the server/ total amount of OS memory.                               | ClickHouse  |
| MemoryAvailable  | percentage | meter_clickhouse_instance_memory_available | Total amount of memory (bytes) available for program / total amount of OS memory.                                | ClickHouse  |
| Uptime           | sec        | meter_clickhouse_instance_uptime           | The server uptime in seconds. It includes the time spent for server initialization before accepting connections. | ClickHouse  |
| Version          | string     | meter_clickhouse_instance_version          | Version of the server in a single integer number in base-1000.                                                   | ClickHouse  |
| FileOpen         | count      | meter_clickhouse_instance_file_open        | Number of files opened.                                                                                          | ClickHouse  |

#### ClickHouse Network Supported Metrics

| Monitoring Panel       | Unit  | Metric Name                                                                                      | Description                                                | Data Source |
| ---------------------- | ----- | ------------------------------------------------------------------------------------------------ | ---------------------------------------------------------- | ----------- |
| TcpConnections         | count | meter_clickhouse_instance_tcp_connections<br/>meter_clickhouse_tcp_connections                   | Number of connections to TCP server.                       | ClickHouse  |
| MysqlConnections       | count | meter_clickhouse_instance_mysql_connections<br/>meter_clickhouse_mysql_connections               | Number of client connections using MySQL protocol.         | ClickHouse  |
| HttpConnections        | count | meter_clickhouse_instance_http_connections<br/>meter_clickhouse_mysql_connections                | Number of connections to HTTP server.                      | ClickHouse  |
| InterserverConnections | count | meter_clickhouse_instance_interserver_connections<br/>meter_clickhouse_interserver_connections   | Number of connections from other replicas to fetch parts.  | ClickHouse  |
| PostgresqlConnections  | count | meter_clickhouse_instance_postgresql_connections<br/>meter_clickhouse_postgresql_connections     | Number of client connections using PostgreSQL protocol.    | ClickHouse  |
| ReceiveBytes           | bytes | meter_clickhouse_instance_network_receive_bytes<br/>meter_clickhouse_network_receive_bytes       | Total number of bytes received from network.               | ClickHouse  |
| SendBytes              | bytes | meter_clickhouse_instance_network_send_bytes<br/>meter_clickhouse_network_send_bytes             | Total number of bytes send to network.                     | ClickHouse  |

#### ClickHouse Query Supported Metrics

| Monitoring Panel | Unit      | Metric Name                                                                                                 | Description                                               | Data Source |
| ---------------- | --------- | ----------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- | ----------- |
| QueryCount       | count     | meter_clickhouse_instance_query<br/>meter_clickhouse_query                                                  | Number of executing queries.                              | ClickHouse  |
| SelectQueryCount | count     | meter_clickhouse_instance_query_select<br/>meter_clickhouse_query_select                                    | Number of executing queries, but only for SELECT queries. | ClickHouse  |
| InsertQueryCount | count     | meter_clickhouse_instance_query_insert<br/>meter_clickhouse_query_insert                                    | Number of executing queries, but only for INSERT queries. | ClickHouse  |
| SelectQueryRate  | count/sec | meter_clickhouse_instance_query_select_rate<br/>meter_clickhouse_query_select_rate                          | Number of SELECT queries per second.                      | ClickHouse  |
| InsertQueryRate  | count/sec | meter_clickhouse_instance_query_insert_rate<br/>meter_clickhouse_query_insert_rate                          | Number of INSERT queries per second.                      | ClickHouse  |
| Querytime        | microsec  | meter_clickhouse_instance_querytime_microseconds<br/>meter_clickhouse_querytime_microseconds                | Total time of all queries.                                | ClickHouse  |
| SelectQuerytime  | microsec  | meter_clickhouse_instance_querytime_select_microseconds<br/>meter_clickhouse_querytime_select_microseconds  | Total time of SELECT queries.                             | ClickHouse  |
| InsertQuerytime  | microsec  | meter_clickhouse_instance_querytime_insert_microseconds<br/>meter_clickhouse_querytime_insert_microseconds  | Total time of INSERT queries.                             | ClickHouse  |
| OtherQuerytime   | microsec  | meter_clickhouse_instance_querytime_other_microseconds<br/>meter_clickhouse_querytime_other_microseconds    | Total time of queries that are not SELECT or INSERT.      | ClickHouse  |
| QuerySlowCount   | count     | meter_clickhouse_instance_query_slow<br/>meter_clickhouse_query_slow                                        | Number of reads from a file that were slow.               | ClickHouse  |

#### ClickHouse Insertion Supported Metrics

| Monitoring Panel   | Unit  | Metric Name                                                                  | Description                                                                                                                     | Data Source |
| ------------------ | ----- | ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- | ----------- |
| InsertQueryCount   | count | meter_clickhouse_instance_query_insert<br/>meter_clickhouse_query_insert     | Number of executing queries, but only for INSERT queries.                                                                       | ClickHouse  |
| InsertedRowCount   | count | meter_clickhouse_instance_inserted_rows<br/>meter_clickhouse_inserted_rows   | Number of rows INSERTed to all tables.                                                                                          | ClickHouse  |
| InsertedBytes      | bytes | meter_clickhouse_instance_inserted_bytes<br/>meter_clickhouse_inserted_bytes | Number of bytes INSERTed to all tables.                                                                                         | ClickHouse  |
| DelayedInsertCount | count | meter_clickhouse_instance_delayed_insert<br/>meter_clickhouse_delayed_insert | Number of times the INSERT of a block to a MergeTree table was throttled due to high number of active data parts for partition. | ClickHouse  |

#### ClickHouse Replicas Supported Metrics

| Monitoring Panel | Unit  | Metric Name                                                                        | Description                                      | Data Source |
| ---------------- | ----- | ---------------------------------------------------------------------------------- | ------------------------------------------------ | ----------- |
| ReplicatedChecks | count | meter_clickhouse_instance_replicated_checks<br/>meter_clickhouse_replicated_checks | Number of data parts checking for consistency.   | ClickHouse  |
| ReplicatedFetch  | count | meter_clickhouse_instance_replicated_fetch<br/>meter_clickhouse_replicated_fetch   | Number of data parts being fetched from replica. | ClickHouse  |
| ReplicatedSend   | count | meter_clickhouse_instance_replicated_send<br/>meter_clickhouse_replicated_send     | Number of data parts being sent to replicas.     | ClickHouse  |

#### ClickHouse MergeTree Supported Metrics

| Monitoring Panel       | Unit  | Metric Name                                                                                      | Description                                                                                                                     | Data Source |
| ---------------------- | ----- | ------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------- | ----------- |
| BackgroundMergeCount   | count | meter_clickhouse_instance_background_merge<br/>meter_clickhouse_background_merge                 | Number of executing background merges.                                                                                          | ClickHouse  |
| MergeRows              | count | meter_clickhouse_instance_merge_rows<br/>meter_clickhouse_merge_rows                             | Rows read for background merges. This is the number of rows before merge.                                                       | ClickHouse  |
| MergeUncompressedBytes | bytes | meter_clickhouse_instance_merge_uncompressed_bytes<br/>meter_clickhouse_merge_uncompressed_bytes | Uncompressed bytes (for columns as they stored in memory) that was read for background merges. This is the number before merge. | ClickHouse  |
| MoveCount              | count | meter_clickhouse_instance_move<br/>meter_clickhouse_move                                         | Number of currently executing moves.                                                                                            | ClickHouse  |
| PartsActive            | Count | meter_clickhouse_instance_parts_active<br/>meter_clickhouse_parts_active                         | Active data part, used by current and upcoming SELECTs.                                                                         | ClickHouse  |
| MutationsCount         | count | meter_clickhouse_instance_mutations<br/>meter_clickhouse_mutations                               | Number of mutations (ALTER DELETE/UPDATE).                                                                                      | ClickHouse  |

#### ClickHouse Kafka Table Engine Supported Metrics

When [table engine](https://clickhouse.com/docs/en/engines/table-engines/integrations/kafka) works
with [Apache Kafka](http://kafka.apache.org/).

Kafka lets you:

- Publish or subscribe to data flows.
- Organize fault-tolerant storage.
- Process streams as they become available.

| Monitoring Panel  | Unit  | Metric Name                                                                            | Description                                               | Data Source |
| ----------------- | ----- | -------------------------------------------------------------------------------------- | --------------------------------------------------------- | ----------- |
| KafkaMessagesRead | count | meter_clickhouse_instance_kafka_messages_read<br/>meter_clickhouse_kafka_messages_read | Number of Kafka messages already processed by ClickHouse. | ClickHouse  |
| KafkaWrites       | count | meter_clickhouse_instance_kafka_writes<br/>meter_clickhouse_kafka_writes               | Number of writes (inserts) to Kafka tables.               | ClickHouse  |
| KafkaConsumers    | count | meter_clickhouse_instance_kafka_consumers<br/>meter_clickhouse_kafka_consumers         | Number of active Kafka consumers.                         | ClickHouse  |
| KafkaProducers     | count | meter_clickhouse_instance_kafka_producers<br/>meter_clickhouse_kafka_producers         | Number of active Kafka producer created.                  | ClickHouse  |

#### ClickHouse ZooKeeper Supported Metrics

ClickHouse uses ZooKeeper for storing metadata of replicas when using replicated tables. If replicated tables are not
used, this section of parameters can be omitted.

| Monitoring Panel      | Unit  | Metric Name                                                                                       | Description                                                           | Data Source |
| --------------------- | ----- | ------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------- | ----------- |
| ZookeeperSession      | count | meter_clickhouse_instance_zookeeper_session<br/>meter_clickhouse_zookeeper_session                | Number of sessions (connections) to ZooKeeper.                        | ClickHouse  |
| ZookeeperWatch        | count | meter_clickhouse_instance_zookeeper_watch<br/>meter_clickhouse_zookeeper_watch                    | Number of watches (event subscriptions) in ZooKeeper.                 | ClickHouse  |
| ZookeeperBytesSent    | bytes | meter_clickhouse_instance_zookeeper_bytes_sent<br/>meter_clickhouse_zookeeper_bytes_sent          | Number of bytes send over network while communicating with ZooKeeper. | ClickHouse  |
| ZookeeperBytesReceive | bytes | meter_clickhouse_instance_zookeeper_bytes_received<br/>meter_clickhouse_zookeeper_bytes_received  | Number of bytes send over network while communicating with ZooKeeper. | ClickHouse  |

### ClickHouse Keeper Supported Metrics

| Monitoring Panel         | Unit  | Metric Name                                                                                           | Description                                                              | Data Source |
| ------------------------ | ----- | ----------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ | ----------- |
| KeeperAliveConnections   | count | meter_clickhouse_instance_keeper_connections_alive<br/>meter_clickhouse_keeper_connections_alive      | Number of alive connections for embedded ClickHouse Keeper.              | ClickHouse  |
| KeeperOutstandingRequets | count | meter_clickhouse_instance_keeper_outstanding_requests<br/>meter_clickhouse_keeper_outstanding_requests| Number of outstanding requests for embedded ClickHouse Keeper.           | ClickHouse  |

### Customizations

You can customize your own metrics/expression/dashboard panel. The metrics definition and expression rules are found
in `/config/otel-rules/clickhouse`. The ClickHouse dashboard panel configurations are found
in `/config/ui-initialized-templates/clickhouse`.
