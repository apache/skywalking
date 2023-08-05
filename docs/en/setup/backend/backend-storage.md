# Backend storage
The SkyWalking storage is pluggable. We have provided the following storage solutions, which allow you to easily
use one of them by specifying it as the `selector` in `application.yml`：

```yaml
storage:
  selector: ${SW_STORAGE:elasticsearch}
```

Natively supported storage:
- H2
- OpenSearch
- ElasticSearch 7 and 8. 
- MySQL
- PostgreSQL
- BanyanDB


## H2
Activate H2 as storage, set storage provider to **H2** In-Memory Databases. Default in the distribution package.
Please read `Database URL Overview` in [H2 official document](http://www.h2database.com/html/features.html).
You can set the target to H2 in **Embedded**, **Server** and **Mixed** modes.

Setting fragment example
```yaml
storage:
  selector: ${SW_STORAGE:h2}
  h2:
    driver: org.h2.jdbcx.JdbcDataSource
    url: jdbc:h2:mem:skywalking-oap-db
    user: sa
    maxSizeOfBatchSql: ${SW_STORAGE_MAX_SIZE_OF_BATCH_SQL:100}
    asyncBatchPersistentPoolSize: ${SW_STORAGE_ASYNC_BATCH_PERSISTENT_POOL_SIZE:1}
```

## OpenSearch

OpenSearch is a fork from ElasticSearch 7.11 but licensed in Apache 2.0.
OpenSearch storage shares the same configurations as ElasticSearch.
In order to activate OpenSearch as storage, set the storage provider to **elasticsearch**.

We support and tested the following versions of OpenSearch:

- 1.1.0, 1.3.10
- 2.4.0, 2.8.0

## ElasticSearch

**NOTE:** Elastic announced through their blog that Elasticsearch will be moving over to a Server Side Public
License (SSPL) and/or Elastic License 2.0(ELv2), since Feb. 2021, which is **incompatible with Apache License 2.0**.
Both of these licenses are not OSS licenses approved by the Open Source Initiative (OSI).
This license change is effective from Elasticsearch version 7.11. 
So please choose the suitable ElasticSearch version according to your usage.
If you have concerns about SSPL/ELv2, choose the versions before 7.11 or switch to OpenSearch.

By default, SkyWalking uses following indices for various telemetry data.

* sw_ui_template (UI dashboard settings)
* sw_metrics-all-`${day-format}` (All metrics/meters generated through MAL and OAL engines, and metadata of service/instance/endpoint)
* sw_log-`${day-format}` (Collected logs, exclude browser logs)
* sw_segment-`${day-format}` (Native trace segments)
* sw_browser_error_log-`${day-format}` (Collected browser logs)
* sw_zipkin_span-`${day-format}` (Zipkin trace spans)
* sw_records-all-`${day-format}` (All sampled records, e.g. slow SQLs, agent profiling, and ebpf profiling)

SkyWalking rebuilds the ElasticSearch client on top of ElasticSearch REST API and automatically picks up
correct request formats according to the server-side version, hence you don't need to download different binaries
and don't need to configure different storage selectors for different ElasticSearch server-side versions anymore.

For now, SkyWalking supports ElasticSearch 7.x, ElasticSearch 8.x, and OpenSearch 1.x, their
configurations are as follows:

_Notice, ElasticSearch 6 worked and is not promised due to end of life officially._

```yaml
storage:
  selector: ${SW_STORAGE:elasticsearch}
  elasticsearch:
    namespace: ${SW_NAMESPACE:""}
    clusterNodes: ${SW_STORAGE_ES_CLUSTER_NODES:localhost:9200}
    protocol: ${SW_STORAGE_ES_HTTP_PROTOCOL:"http"}
    trustStorePath: ${SW_STORAGE_ES_SSL_JKS_PATH:""}
    trustStorePass: ${SW_STORAGE_ES_SSL_JKS_PASS:""}
    user: ${SW_ES_USER:""}
    password: ${SW_ES_PASSWORD:""}
    secretsManagementFile: ${SW_ES_SECRETS_MANAGEMENT_FILE:""} # Secrets management file in the properties format includes the username, password, which are managed by 3rd party tool.
    dayStep: ${SW_STORAGE_DAY_STEP:1} # Represent the number of days in the one minute/hour/day index.
    indexShardsNumber: ${SW_STORAGE_ES_INDEX_SHARDS_NUMBER:1} # Shard number of new indexes
    indexReplicasNumber: ${SW_STORAGE_ES_INDEX_REPLICAS_NUMBER:1} # Replicas number of new indexes
    # Specify the settings for each index individually.
    # If configured, this setting has the highest priority and overrides the generic settings.
    specificIndexSettings: ${SW_STORAGE_ES_SPECIFIC_INDEX_SETTINGS:""}
    # Super data set has been defined in the codes, such as trace segments.The following 3 config would be improve es performance when storage super size data in es.
    superDatasetDayStep: ${SW_STORAGE_ES_SUPER_DATASET_DAY_STEP:-1} # Represent the number of days in the super size dataset record index, the default value is the same as dayStep when the value is less than 0
    superDatasetIndexShardsFactor: ${SW_STORAGE_ES_SUPER_DATASET_INDEX_SHARDS_FACTOR:5} #  This factor provides more shards for the super data set, shards number = indexShardsNumber * superDatasetIndexShardsFactor. Also, this factor effects Zipkin traces.
    superDatasetIndexReplicasNumber: ${SW_STORAGE_ES_SUPER_DATASET_INDEX_REPLICAS_NUMBER:0} # Represent the replicas number in the super size dataset record index, the default value is 0.
    indexTemplateOrder: ${SW_STORAGE_ES_INDEX_TEMPLATE_ORDER:0} # the order of index template
    bulkActions: ${SW_STORAGE_ES_BULK_ACTIONS:1000} # Execute the async bulk record data every ${SW_STORAGE_ES_BULK_ACTIONS} requests
    flushInterval: ${SW_STORAGE_ES_FLUSH_INTERVAL:10} # flush the bulk every 10 seconds whatever the number of requests
    concurrentRequests: ${SW_STORAGE_ES_CONCURRENT_REQUESTS:2} # the number of concurrent requests
    resultWindowMaxSize: ${SW_STORAGE_ES_QUERY_MAX_WINDOW_SIZE:10000}
    metadataQueryMaxSize: ${SW_STORAGE_ES_QUERY_MAX_SIZE:5000}
    segmentQueryMaxSize: ${SW_STORAGE_ES_QUERY_SEGMENT_SIZE:200}
    profileTaskQueryMaxSize: ${SW_STORAGE_ES_QUERY_PROFILE_TASK_SIZE:200}
    profileDataQueryScrollBatchSize: ${SW_STORAGE_ES_QUERY_PROFILE_DATA_SCROLLING_BATCH_SIZE:100}
    oapAnalyzer: ${SW_STORAGE_ES_OAP_ANALYZER:"{\"analyzer\":{\"oap_analyzer\":{\"type\":\"stop\"}}}"} # the oap analyzer.
    oapLogAnalyzer: ${SW_STORAGE_ES_OAP_LOG_ANALYZER:"{\"analyzer\":{\"oap_log_analyzer\":{\"type\":\"standard\"}}}"} # the oap log analyzer. It could be customized by the ES analyzer configuration to support more language log formats, such as Chinese log, Japanese log and etc.
    advanced: ${SW_STORAGE_ES_ADVANCED:""}
    # Set it to `true` could shard metrics indices into multi-physical indices
    # as same as the versions(one index template per metric/meter aggregation function) before 9.2.0.
    logicSharding: ${SW_STORAGE_ES_LOGIC_SHARDING:false}
    # Custom routing can reduce the impact of searches. Instead of having to fan out a search request to all the shards in an index, the request can be sent to just the shard that matches the specific routing value (or values).
    enableCustomRouting: ${SW_STORAGE_ES_ENABLE_CUSTOM_ROUTING:false}
```

### ElasticSearch With Https SSL Encrypting communications.

Example:

```yaml
storage:
  selector: ${SW_STORAGE:elasticsearch}
  elasticsearch:
    namespace: ${SW_NAMESPACE:""}
    user: ${SW_ES_USER:""} # User needs to be set when Http Basic authentication is enabled
    password: ${SW_ES_PASSWORD:""} # Password to be set when Http Basic authentication is enabled
    clusterNodes: ${SW_STORAGE_ES_CLUSTER_NODES:localhost:443}
    trustStorePath: ${SW_SW_STORAGE_ES_SSL_JKS_PATH:"../es_keystore.jks"}
    trustStorePass: ${SW_SW_STORAGE_ES_SSL_JKS_PASS:""}
    protocol: ${SW_STORAGE_ES_HTTP_PROTOCOL:"https"}
    ...
```
- File at `trustStorePath` is being monitored. Once it is changed, the ElasticSearch client will reconnect.
- `trustStorePass` could be changed in the runtime through [**Secrets Management File Of ElasticSearch Authentication**](#secrets-management-file-of-elasticsearch-authentication).

### Daily Index Step
Daily index step(`storage/elasticsearch/dayStep`, default 1) represents the index creation period. In this period, metrics for several days (dayStep value) are saved.

In most cases, users don't need to change the value manually, as SkyWalking is designed to observe large-scale distributed systems.
But in some cases, users may want to set a long TTL value, such as more than 60 days. However, their ElasticSearch cluster may not be powerful enough due to low traffic in the production environment.
This value could be increased to 5 (or more) if users could ensure a single index could support the metrics and traces for these days (5 in this case).

For example, if dayStep == 11,
1. Data in [2000-01-01, 2000-01-11] will be merged into the index-20000101.
1. Data in [2000-01-12, 2000-01-22] will be merged into the index-20000112.

`storage/elasticsearch/superDatasetDayStep` overrides the `storage/elasticsearch/dayStep` if the value is positive.
This would affect the record-related entities, such as trace segments. In some cases, the size of metrics is much smaller than the record (trace). This would improve the shards balance in the ElasticSearch cluster.

NOTE: TTL deletion would be affected by these steps. You should set an extra dayStep in your TTL. For example, if you want to have TTL == 30 days and dayStep == 10, you are recommended to set TTL = 40.

### Secrets Management File Of ElasticSearch Authentication
The value of `secretsManagementFile` should point to the secrets management file absolute path.
The file includes the username, password, and JKS password of the ElasticSearch server in the properties format.
```properties
user=xxx
password=yyy
trustStorePass=zzz
```

The major difference between using `user, password, trustStorePass` configs in the `application.yaml` file is that the **Secrets Management File** is being watched by the OAP server.
Once it is changed manually or through a 3rd party tool, such as [Vault](https://github.com/hashicorp/vault),
the storage provider will use the new username, password, and JKS password to establish the connection and close the old one. If the information exists in the file,
the `user/password` will be overridden.


### Index Settings
The following settings control the number of shards and replicas for new and existing index templates. The update only got applied after OAP reboots.
```yaml
storage:
  elasticsearch:
    # ......
    indexShardsNumber: ${SW_STORAGE_ES_INDEX_SHARDS_NUMBER:1}
    indexReplicasNumber: ${SW_STORAGE_ES_INDEX_REPLICAS_NUMBER:1}
    specificIndexSettings: ${SW_STORAGE_ES_SPECIFIC_INDEX_SETTINGS:""}
    superDatasetIndexShardsFactor: ${SW_STORAGE_ES_SUPER_DATASET_INDEX_SHARDS_FACTOR:5}
    superDatasetIndexReplicasNumber: ${SW_STORAGE_ES_SUPER_DATASET_INDEX_REPLICAS_NUMBER:0}
```
The following table shows the relationship between those config items and Elasticsearch `index number_of_shards/number_of_replicas`.
And also you can [specify the settings for each index individually.](#specify-settings-for-each-elasticsearch-index-individually)

| index                                | number_of_shards | number_of_replicas   |
|--------------------------------------|------------------|----------------------|
| sw_ui_template                       | indexShardsNumber | indexReplicasNumber  |
| sw_metrics-all-`${day-format}`       | indexShardsNumber | indexReplicasNumber  |
| sw_log-`${day-format}`               | indexShardsNumber * superDatasetIndexShardsFactor | superDatasetIndexReplicasNumber  |
| sw_segment-`${day-format}`           | indexShardsNumber * superDatasetIndexShardsFactor | superDatasetIndexReplicasNumber  |
| sw_browser_error_log-`${day-format}` | indexShardsNumber * superDatasetIndexShardsFactor | superDatasetIndexReplicasNumber  |
| sw_zipkin_span-`${day-format}`       | indexShardsNumber * superDatasetIndexShardsFactor | superDatasetIndexReplicasNumber  |
| sw_records-all-`${day-format}`       | indexShardsNumber | indexReplicasNumber  |

#### Advanced Configurations For Elasticsearch Index
You can add advanced configurations in `JSON` format to set `ElasticSearch index settings` by following [ElasticSearch doc](https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules.html)

For example, set [translog](https://www.elastic.co/guide/en/elasticsearch/reference/master/index-modules-translog.html) settings:

```yaml
storage:
  elasticsearch:
    # ......
    advanced: ${SW_STORAGE_ES_ADVANCED:"{\"index.translog.durability\":\"request\",\"index.translog.sync_interval\":\"5s\"}"}
```

#### Specify Settings For Each Elasticsearch Index Individually
You can specify the settings for one or more indexes individually by using `SW_STORAGE_ES_SPECIFIC_INDEX_SETTINGS`.

**NOTE:**
Supported settings:
- number_of_shards
- number_of_replicas

**NOTE:** These settings have the highest priority and will override the existing
generic settings mentioned in [index settings doc](#index-settings).

The settings are in `JSON` format. The index name here is logic entity name, which should exclude the `${SW_NAMESPACE}` which is `sw` by default, e.g.
```json
{
  "metrics-all":{
    "number_of_shards":"3",
    "number_of_replicas":"2"
  },
  "segment":{
    "number_of_shards":"6",
    "number_of_replicas":"1"
  }
}
```

This configuration in the YAML file is like this,
```yaml
storage:
  elasticsearch:
    # ......
    specificIndexSettings: ${SW_STORAGE_ES_SPECIFIC_INDEX_SETTINGS:"{\"metrics-all\":{\"number_of_shards\":\"3\",\"number_of_replicas\":\"2\"},\"segment\":{\"number_of_shards\":\"6\",\"number_of_replicas\":\"1\"}}"}
```

### Recommended ElasticSearch server-side configurations
You could add the following configuration to `elasticsearch.yml`, and set the value based on your environment.

```yml
# In tracing scenario, consider to set more than this at least.
thread_pool.index.queue_size: 1000 # Only suitable for ElasticSearch 6
thread_pool.write.queue_size: 1000 # Suitable for ElasticSearch 6 and 7

# When you face a query error on the traces page, remember to check this.
index.max_result_window: 1000000
```

We strongly recommend that you read more about these configurations from ElasticSearch's official documentation since they directly impact the performance of ElasticSearch.

### About Namespace
When a namespace is set, all index names in ElasticSearch will use it as the prefix.

## MySQL
Activate MySQL as storage, and set storage provider to **mysql**.

**NOTE:** MySQL driver is NOT allowed in Apache official distribution and source codes.
Please download the MySQL driver on your own. Copy the connection driver jar to `oap-libs`.

```yaml
storage:
  selector: ${SW_STORAGE:mysql}
  mysql:
    properties:
      jdbcUrl: ${SW_JDBC_URL:"jdbc:mysql://localhost:3306/swtest?rewriteBatchedStatements=true&allowMultiQueries=true"}
      dataSource.user: ${SW_DATA_SOURCE_USER:root}
      dataSource.password: ${SW_DATA_SOURCE_PASSWORD:root@1234}
      dataSource.cachePrepStmts: ${SW_DATA_SOURCE_CACHE_PREP_STMTS:true}
      dataSource.prepStmtCacheSize: ${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_SIZE:250}
      dataSource.prepStmtCacheSqlLimit: ${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_LIMIT:2048}
      dataSource.useServerPrepStmts: ${SW_DATA_SOURCE_USE_SERVER_PREP_STMTS:true}
    metadataQueryMaxSize: ${SW_STORAGE_MYSQL_QUERY_MAX_SIZE:5000}
    maxSizeOfBatchSql: ${SW_STORAGE_MAX_SIZE_OF_BATCH_SQL:2000}
    asyncBatchPersistentPoolSize: ${SW_STORAGE_ASYNC_BATCH_PERSISTENT_POOL_SIZE:4}
```
All connection-related settings, including URL link, username, and password, are found in `application.yml`.
Only part of the settings is listed here. See the [HikariCP](https://github.com/brettwooldridge/HikariCP) connection pool document for full settings.
To understand the function of the parameter `rewriteBatchedStatements=true` in MySQL, see the [MySQL official document](https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-connp-props-performance-extensions.html#cj-conn-prop_rewriteBatchedStatements).

In theory, all other databases that are compatible with MySQL protocol should be able to use this storage plugin,
such as TiDB. Please compose the JDBC URL according to the database's documentation.

## PostgreSQL
PostgreSQL JDBC driver uses version 42.3.2. It supports PostgreSQL 8.2 or newer.
Activate PostgreSQL as storage, and set storage provider to **postgresql**.

```yaml
storage:
  selector: ${SW_STORAGE:postgresql}
  postgresql:
    properties:
      jdbcUrl: ${SW_JDBC_URL:"jdbc:postgresql://localhost:5432/skywalking"}
      dataSource.user: ${SW_DATA_SOURCE_USER:postgres}
      dataSource.password: ${SW_DATA_SOURCE_PASSWORD:123456}
      dataSource.cachePrepStmts: ${SW_DATA_SOURCE_CACHE_PREP_STMTS:true}
      dataSource.prepStmtCacheSize: ${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_SIZE:250}
      dataSource.prepStmtCacheSqlLimit: ${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_LIMIT:2048}
      dataSource.useServerPrepStmts: ${SW_DATA_SOURCE_USE_SERVER_PREP_STMTS:true}
    metadataQueryMaxSize: ${SW_STORAGE_MYSQL_QUERY_MAX_SIZE:5000}
    maxSizeOfArrayColumn: ${SW_STORAGE_MAX_SIZE_OF_ARRAY_COLUMN:20}
    numOfSearchableValuesPerTag: ${SW_STORAGE_NUM_OF_SEARCHABLE_VALUES_PER_TAG:2}
    maxSizeOfBatchSql: ${SW_STORAGE_MAX_SIZE_OF_BATCH_SQL:2000}
    asyncBatchPersistentPoolSize: ${SW_STORAGE_ASYNC_BATCH_PERSISTENT_POOL_SIZE:4}
```
All connection-related settings, including URL link, username, and password, are found in `application.yml`.
Only part of the settings is listed here. Please follow [HikariCP](https://github.com/brettwooldridge/HikariCP) connection pool document for full settings.

## BanyanDB
[BanyanDB](https://github.com/apache/skywalking-banyandb) is a dedicated storage implementation developed by the SkyWalking Team and the community.
Activate BanyanDB as the storage, and set storage provider to **banyandb**.

```yaml
storage:
  banyandb:
    host: ${SW_STORAGE_BANYANDB_HOST:127.0.0.1}
    port: ${SW_STORAGE_BANYANDB_PORT:17912}
    maxBulkSize: ${SW_STORAGE_BANYANDB_MAX_BULK_SIZE:5000}
    flushInterval: ${SW_STORAGE_BANYANDB_FLUSH_INTERVAL:15}
    metricsShardsNumber: ${SW_STORAGE_BANYANDB_METRICS_SHARDS_NUMBER:1}
    recordShardsNumber: ${SW_STORAGE_BANYANDB_RECORD_SHARDS_NUMBER:1}
    superDatasetShardsFactor: ${SW_STORAGE_BANYANDB_SUPERDATASET_SHARDS_FACTOR:2}
    concurrentWriteThreads: ${SW_STORAGE_BANYANDB_CONCURRENT_WRITE_THREADS:15}
    profileTaskQueryMaxSize: ${SW_STORAGE_BANYANDB_PROFILE_TASK_QUERY_MAX_SIZE:200} # the max number of fetch task in a request
    streamBlockInterval: ${SW_STORAGE_BANYANDB_STREAM_BLOCK_INTERVAL:4} # Unit is hour
    streamSegmentInterval: ${SW_STORAGE_BANYANDB_STREAM_SEGMENT_INTERVAL:24} # Unit is hour
    measureBlockInterval: ${SW_STORAGE_BANYANDB_MEASURE_BLOCK_INTERVAL:4} # Unit is hour
    measureSegmentInterval: ${SW_STORAGE_BANYANDB_MEASURE_SEGMENT_INTERVAL:24} # Unit is hour
```

For more details, please refer to the documents of [BanyanDB](https://skywalking.apache.org/docs/skywalking-banyandb/next/readme/)
and [BanyanDB Java Client](https://github.com/apache/skywalking-banyandb-java-client) subprojects.

