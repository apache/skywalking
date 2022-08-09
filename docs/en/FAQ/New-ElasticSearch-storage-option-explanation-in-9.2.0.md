## New ElasticSearch storage option explanation in 9.2.0
Since v9.2.0, SkyWalking OAP provide 2 storage options for metrics/meter and records, 
system environment variable is (`SW_STORAGE_ES_LOGIC_SHARDING`):

### No-Sharding Model (OAP default setting, `SW_STORAGE_ES_LOGIC_SHARDING = false`)
1. OAP merge all metrics/meter and records(without super datasets, such as segments) indices into one physical 
index template `metrics-all` and `records-all`.
2. The logic index name would present in column `metric_table` and `record_table`.
3. If logic column name has alias (configured by `@ElasticSearch.Column()`), the alias would be the real physical column name.

### No-Sharding Model (`SW_STORAGE_ES_LOGIC_SHARDING = true `)
1. OAP shard metrics/meter indices into multi-physical indices as the previous versions(one index template per metric/meter aggregation function).
2. Records and metrics without configure aggregation function in `@MetricsFunction` and `@MeterFunction` would not be sharded.
3. The shard template name would be `metrics-aggregation function name` or `meter-aggregation function name` such as `metrics-count`,
and the logic index name would present in column `metric_table`.
4. The OAP **would not** use the column alias, the logic column name would be the real physical column name.

**Notice**: 
Users still could choose to adjust ElasticSearch's shard number(`SW_STORAGE_ES_INDEX_SHARDS_NUMBER`) to scale out no matter the option is.
