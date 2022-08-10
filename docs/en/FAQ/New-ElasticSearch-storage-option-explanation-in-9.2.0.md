## New ElasticSearch storage option explanation in 9.2.0
Since v9.2.0, SkyWalking OAP provides 2 storage options for all data, including metadata, metrics, traces, logs,  events, profiling data, etc.. OAP exposes a system environment variable (`SW_STORAGE_ES_LOGIC_SHARDING`) to control the running mode.

### No-Sharding Mode (OAP default setting, `SW_STORAGE_ES_LOGIC_SHARDING = false`)
This is the new mode introduced in 9.2.0. It prefers to keep data with similar properties in one index template, such as all metrics and metadata.

1. OAP merges all metrics/meter and records(without super datasets, such as segments) indices into one physical
   index template `metrics-all` and `records-all`.
2. The logic index name would be present in columns `metric_table` or `record_table`.
3. If the logic column name has an alias (configured through `@ElasticSearch.Column()`), the alias would be the real physical column name.

The super dataset would not be affected by this, such as traces and logs.

### Sharding Mode (`SW_STORAGE_ES_LOGIC_SHARDING = true `)
1. OAP shard metrics/meter indices into multi-physical indices as in the previous versions(one index template per metric/meter aggregation function).
2. Records and metrics without configuring aggregation functions with `@MetricsFunction` or `@MeterFunction` annotation would not be merged. They would be kept in a separate index template.
3. The shard template name would be `metrics-aggregation function name` or `meter-aggregation function name` such as `metrics-count`,
   and the logic index name would be present in column `metric_table`.
5. The OAP **would not** use the column alias, the logic column name would be the real physical column name.

___
**Notice**:
Users still could choose to adjust ElasticSearch's shard number(`SW_STORAGE_ES_INDEX_SHARDS_NUMBER`) to scale out in either mode.
