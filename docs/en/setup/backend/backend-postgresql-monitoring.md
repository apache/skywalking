# PostgreSQL monitoring
## PostgreSQL server performance from `postgres-exporter`
SkyWalking leverages postgres-exporter for collecting metrics data from PostgreSQL. It leverages OpenTelemetry Collector to transfer the metrics to
[OpenTelemetry receiver](opentelemetry-receiver.md) and into the [Meter System](./../../concepts-and-designs/meter.md).

### Data flow
1. postgres-exporter collect metrics data from PostgreSQL.
2. OpenTelemetry Collector fetches metrics from postgres-exporter via Prometheus Receiver and pushes metrics to SkyWalking OAP Server via OpenTelemetry gRPC exporter.
3. The SkyWalking OAP Server parses the expression with [MAL](../../concepts-and-designs/mal.md) to filter/calculate/aggregate and store the results.

### Set up
1. Set up [postgres-exporter](https://github.com/prometheus-community/postgres_exporter#quick-start).
2. Set up [OpenTelemetry Collector ](https://opentelemetry.io/docs/collector/getting-started/#docker). For details on Prometheus Receiver in OpenTelemetry Collector, refer to [here](../../../../test/e2e-v2/cases/postgresql/postgres-exporter/otel-collector-config.yaml).
3. Config SkyWalking [OpenTelemetry receiver](opentelemetry-receiver.md).

### PostgreSQL Monitoring
PostgreSQL cluster is cataloged as a `Layer: PostgreSQL` `Service` in OAP. Each PostgreSQL server is cataloged as an `Instance` in OAP.
#### Supported Metrics
| Monitoring Panel | Unit | Metric Name | Description | Data Source |
|-----|------|-----|-----|-----|
| Shared Buffers |   MB   | meter_pg_shared_buffers | The number of shared memory buffers used by the server | postgres-exporter|
| Effective Cache |   GB   | meter_pg_effective_cache | The planner's assumption about the total size of the data caches | postgres-exporter|
| Maintenance Work Mem |  MB    | meter_pg_maintenance_work_mem | The maximum memory to be used for maintenance operations| postgres-exporter|
| Seq Page Cost |      | meter_pg_seq_page_cost | The planner's estimate of the cost of a sequentially fetched disk page.| postgres-exporter|
| Random Page Cost|      | meter_pg_random_page_cost | The planner's estimate of the cost of a nonsequentially fetched disk page. | postgres-exporter|
| Max Worker Processes |      | meter_pg_max_worker_processes | Maximum number of concurrent worker processes | postgres-exporter|
| Max WAL Size |    GB  | meter_max_wal_size | The WAL size that triggers a checkpoint | postgres-exporter|
| Max Parallel Workers |      | meter_pg_max_parallel_workers | The maximum number of parallel processes per executor node| postgres-exporter|
| Work Mem |  MB    | meter_pg_max_work_mem | The maximum memory to be used for query workspaces.  | postgres-exporter|
| Fetched Row Trend |      | meter_pg_fetched_rows_rate | The trend of the number of rows fetched by queries in this database. | postgres-exporter|
| Inserted Row Trend |      | meter_pg_inserted_rows_rate | The trend of the number of rows inserted by queries in this database. | postgres-exporter|
| Updated Row Trend |      | meter_pg_updated_rows_rate | The trend of the number of rows updated by queries in this database. | postgres-exporter|
| Deleted Row Trend |      | meter_pg_deleted_rows_rate | The trend of the number of rows deleted by queries in this database. | postgres-exporter|
| Returned Row Trend |      | meter_pg_returned_rows_rate | The trend of the number of rows returned by queries in this database. | postgres-exporter|
| Committed Transactions Trend |      | meter_pg_committed_transactions_rate | The trend of the number of transactions in this database that have been committed | postgres-exporter|
| Rolled Back Transactions Trend |      | meter_pg_rolled_back_transactions_rate | The trend of the number of transactions in this database that have been rolled back | postgres-exporter|
| Buffers Trend |      | meter_pg_buffers_alloc </br> meter_pg_buffers_checkpoint </br>meter_pg_buffers_clean </br>meter_pg_buffers_backend_fsync </br>meter_pg_buffers_backend | The trend of the number of buffers | postgres-exporter|
| Conflicts Trend |      | meter_pg_conflicts_rate | The trend of the number of queries canceled due to conflicts with recovery in this database | postgres-exporter|
| Deadlock Trend |      | meter_pg_deadlocks_rate | The trend of the number of deadlocks detected in this database | postgres-exporter|
| Cache Hit Rate |   %   | meter_pg_cache_hit_rate | The rate of cache hit | postgres-exporter|
| Temporary Files Trend |      | meter_pg_temporary_files_rate | The rate of total amount of data written to temporary files by queries in this database. All temporary files are counted, regardless of why the temporary file was created, and regardless of the log_temp_files setting | postgres-exporter|
| Checkpoint Stat Trend |      | meter_pg_checkpoint_write_time_rate </br> meter_pg_checkpoint_sync_time_rate </br> meter_pg_checkpoint_req_rate </br>meter_pg_checkpoint_timed_rate | The trend of checkpoint stat | postgres-exporter|
| Active Sessions |      | meter_pg_active_sessions | The number of connections which state is active  | postgres-exporter|
| Idle Sessions |      | meter_pg_idle_sessions | The number of connections which state is idle,idle in transaction or idle in transaction (aborted)  | postgres-exporter|
| Locks Count |      | meter_pg_locks_count | Number of locks  | postgres-exporter|

### Customizations
You can customize your own metrics/expression/dashboard panel.
The metrics definition and expression rules are found in `/config/otel-rules/postgresql`.
The PostgreSQL dashboard panel configurations are found in `/config/ui-initialized-templates/postgresql`.

## Collect sampled slow SQLs
SkyWalking leverages [fluentbit](https://fluentbit.io/) or other log agents for collecting slow SQL statements from PostgreSQL.

### Data flow
1. fluentbit agent collects slow sql logs from PostgreSQL.
2. fluentbit agent sends data to SkyWalking OAP Server using native log APIs via HTTP.
3. The SkyWalking OAP Server parses the expression with [LAL](../../concepts-and-designs/lal.md) to parse/extract and store the results.

### Set up
1. Set up [fluentbit](https://docs.fluentbit.io/manual/installation/docker).
2. Config [fluentbit](../../../../test/e2e-v2/cases/postgresql/postgres-exporter/fluent-bit.conf)
3. Config PostgreSQL to enable slow log. [Example](../../../../test/e2e-v2/cases/postgresql/postgres-exporter/postgresql.conf).

### Slow SQL Monitoring
Slow SQL monitoring provides monitoring of the slow SQL statements of the PostgreSQL server. PostgreSQL Cluster is cataloged as a `Layer: POSTGRESQL` `Service` in OAP.
Each PostgreSQL server is cataloged as an `Instance` in OAP.
#### Supported Metrics
| Monitoring Panel | Unit | Metric Name | Description | Data Source |
|-----|------|-----|-----|-----|
|Slow Statements |   ms   | top_n_database_statement | The latency and statement of PostgreSQL slow SQLs | fluentbit|

### Customizations
You can customize your own metrics/expression/dashboard panel.
The slowsql expression rules are found in `/config/lal/pgsql-slowsql.yaml`
The PostgreSQL dashboard panel configurations are found in `/config/ui-initialized-templates/postgresql`.