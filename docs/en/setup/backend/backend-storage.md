# Backend storage
The SkyWalking storage is pluggable. We have provided the following storage solutions, which allows you to easily
use one of them by specifying it as the `selector` in `application.yml`：

```yaml
storage:
  selector: ${SW_STORAGE:elasticsearch}
```

Natively supported storage:
- H2
- OpenSearch
- ElasticSearch 6, 7
- MySQL
- TiDB
- InfluxDB
- PostgreSQL


## H2
Activate H2 as storage, set storage provider to **H2** In-Memory Databases. Default in distribution package.
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

OpenSearch storage shares the same configurations as ElasticSearch.
In order to activate OpenSearch as storage, set storage provider to **elasticsearch**.

## ElasticSearch

**NOTE:** Elastic announced through their blog that Elasticsearch will be moving over to a Server Side Public
License (SSPL), which is incompatible with Apache License 2.0. This license change is effective from Elasticsearch
version 7.11. So please choose the suitable ElasticSearch version according to your usage.

Since 8.8.0, SkyWalking rebuilds the ElasticSearch client on top of ElasticSearch REST API and automatically picks up
correct request formats according to the server side version, hence you don't need to download different binaries
and don't need to configure different storage selector for different ElasticSearch server side version anymore.

For now, SkyWalking supports ElasticSearch 6.x, ElasticSearch 7.x, and OpenSearch 1.x, their configurations are as
follows:

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
    # Super data set has been defined in the codes, such as trace segments.The following 3 config would be improve es performance when storage super size data in es.
    superDatasetDayStep: ${SW_SUPERDATASET_STORAGE_DAY_STEP:-1} # Represent the number of days in the super size dataset record index, the default value is the same as dayStep when the value is less than 0
    superDatasetIndexShardsFactor: ${SW_STORAGE_ES_SUPER_DATASET_INDEX_SHARDS_FACTOR:5} #  This factor provides more shards for the super data set, shards number = indexShardsNumber * superDatasetIndexShardsFactor. Also, this factor effects Zipkin and Jaeger traces.
    superDatasetIndexReplicasNumber: ${SW_STORAGE_ES_SUPER_DATASET_INDEX_REPLICAS_NUMBER:0} # Represent the replicas number in the super size dataset record index, the default value is 0.
    indexTemplateOrder: ${SW_STORAGE_ES_INDEX_TEMPLATE_ORDER:0} # the order of index template
    bulkActions: ${SW_STORAGE_ES_BULK_ACTIONS:1000} # Execute the async bulk record data every ${SW_STORAGE_ES_BULK_ACTIONS} requests
    flushInterval: ${SW_STORAGE_ES_FLUSH_INTERVAL:10} # flush the bulk every 10 seconds whatever the number of requests
    concurrentRequests: ${SW_STORAGE_ES_CONCURRENT_REQUESTS:2} # the number of concurrent requests
    resultWindowMaxSize: ${SW_STORAGE_ES_QUERY_MAX_WINDOW_SIZE:10000}
    metadataQueryMaxSize: ${SW_STORAGE_ES_QUERY_MAX_SIZE:5000}
    segmentQueryMaxSize: ${SW_STORAGE_ES_QUERY_SEGMENT_SIZE:200}
    profileTaskQueryMaxSize: ${SW_STORAGE_ES_QUERY_PROFILE_TASK_SIZE:200}
    oapAnalyzer: ${SW_STORAGE_ES_OAP_ANALYZER:"{\"analyzer\":{\"oap_analyzer\":{\"type\":\"stop\"}}}"} # the oap analyzer.
    oapLogAnalyzer: ${SW_STORAGE_ES_OAP_LOG_ANALYZER:"{\"analyzer\":{\"oap_log_analyzer\":{\"type\":\"standard\"}}}"} # the oap log analyzer. It could be customized by the ES analyzer configuration to support more language log formats, such as Chinese log, Japanese log and etc.
    advanced: ${SW_STORAGE_ES_ADVANCED:""}
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

In most cases, users don't need to change the value manually, as SkyWalking is designed to observe large scale distributed systems.
But in some cases, users may want to set a long TTL value, such as more than 60 days. However, their ElasticSearch cluster may not be powerful enough due to low traffic in the production environment.
This value could be increased to 5 (or more), if users could ensure a single index could support the metrics and traces for these days (5 in this case).

For example, if dayStep == 11, 
1. Data in [2000-01-01, 2000-01-11] will be merged into the index-20000101.
1. Data in [2000-01-12, 2000-01-22] will be merged into the index-20000112.

`storage/elasticsearch/superDatasetDayStep` overrides the `storage/elasticsearch/dayStep` if the value is positive.
This would affect the record-related entities, such as trace segments. In some cases, the size of metrics is much smaller than the record (trace). This would improve the shards balance in the ElasticSearch cluster.
 
NOTE: TTL deletion would be affected by these steps. You should set an extra dayStep in your TTL. For example, if you want to have TTL == 30 days and dayStep == 10, you are commended to set TTL = 40.

### Secrets Management File Of ElasticSearch Authentication
The value of `secretsManagementFile` should point to the secrets management file absolute path. 
The file includes username, password, and JKS password of the ElasticSearch server in the properties format.
```properties
user=xxx
password=yyy
trustStorePass=zzz
```

The major difference between using `user, password, trustStorePass` configs in the `application.yaml` file is that the **Secrets Management File** is being watched by the OAP server. 
Once it is changed manually or through a 3rd party tool, such as [Vault](https://github.com/hashicorp/vault), 
the storage provider will use the new username, password, and JKS password to establish the connection and close the old one. If the information exists in the file,
the `user/password` will be overrided.

### Advanced Configurations For Elasticsearch Index
You can add advanced configurations in `JSON` format to set `ElasticSearch index settings` by following [ElasticSearch doc](https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules.html)

For example, set [translog](https://www.elastic.co/guide/en/elasticsearch/reference/master/index-modules-translog.html) settings:

```yaml
storage:
  elasticsearch:
    # ......
    advanced: ${SW_STORAGE_ES_ADVANCED:"{\"index.translog.durability\":\"request\",\"index.translog.sync_interval\":\"5s\"}"}
```

### Recommended ElasticSearch server-side configurations
You could add the following configuration to `elasticsearch.yml`, and set the value based on your environment.

```yml
# In tracing scenario, consider to set more than this at least.
thread_pool.index.queue_size: 1000 # Only suitable for ElasticSearch 6
thread_pool.write.queue_size: 1000 # Suitable for ElasticSearch 6 and 7

# When you face query error at trace page, remember to check this.
index.max_result_window: 1000000
```

We strongly recommend that you read more about these configurations from ElasticSearch's official document, since they have a direct impact on the performance of ElasticSearch.


### ElasticSearch with Zipkin trace extension
This implementation is very similar to `elasticsearch`, except that it extends to support Zipkin span storage.
The configurations are largely the same.
```yaml
storage:
  selector: ${SW_STORAGE:zipkin-elasticsearch}
  zipkin-elasticsearch:
    namespace: ${SW_NAMESPACE:""}
    clusterNodes: ${SW_STORAGE_ES_CLUSTER_NODES:localhost:9200}
    protocol: ${SW_STORAGE_ES_HTTP_PROTOCOL:"http"}
    user: ${SW_ES_USER:""}
    password: ${SW_ES_PASSWORD:""}
    indexShardsNumber: ${SW_STORAGE_ES_INDEX_SHARDS_NUMBER:2}
    indexReplicasNumber: ${SW_STORAGE_ES_INDEX_REPLICAS_NUMBER:0}
    # Batch process setting, refer to https://www.elastic.co/guide/en/elasticsearch/client/java-api/5.5/java-docs-bulk-processor.html
    bulkActions: ${SW_STORAGE_ES_BULK_ACTIONS:2000} # Execute the bulk every 2000 requests
    bulkSize: ${SW_STORAGE_ES_BULK_SIZE:20} # flush the bulk every 20mb
    flushInterval: ${SW_STORAGE_ES_FLUSH_INTERVAL:10} # flush the bulk every 10 seconds whatever the number of requests
    concurrentRequests: ${SW_STORAGE_ES_CONCURRENT_REQUESTS:2} # the number of concurrent requests
```

### About Namespace
When namespace is set, all index names in ElasticSearch will use it as prefix.

## MySQL
Active MySQL as storage, set storage provider to **mysql**. 

**NOTE:** MySQL driver is NOT allowed in Apache official distribution and source codes. 
Please download MySQL driver on your own. Copy the connection driver jar to `oap-libs`.

```yaml
storage:
  selector: ${SW_STORAGE:mysql}
  mysql:
    properties:
      jdbcUrl: ${SW_JDBC_URL:"jdbc:mysql://localhost:3306/swtest?rewriteBatchedStatements=true"}
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
All connection-related settings, including URL link, username, and password are found in `application.yml`. 
Only part of the settings are listed here. See the [HikariCP](https://github.com/brettwooldridge/HikariCP) connection pool document for full settings.
To understand the function of the parameter `rewriteBatchedStatements=true` in MySQL, see the [MySQL official document](https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-connp-props-performance-extensions.html#cj-conn-prop_rewriteBatchedStatements).

## TiDB
Tested TiDB Server 4.0.8 version and MySQL Client driver 8.0.13 version are currently available.
Activate TiDB as storage, and set storage provider to **tidb**. 

```yaml
storage:
  selector: ${SW_STORAGE:tidb}
  tidb:
    properties:
      jdbcUrl: ${SW_JDBC_URL:"jdbc:mysql://localhost:4000/swtest?rewriteBatchedStatements=true"}
      dataSource.user: ${SW_DATA_SOURCE_USER:root}
      dataSource.password: ${SW_DATA_SOURCE_PASSWORD:""}
      dataSource.cachePrepStmts: ${SW_DATA_SOURCE_CACHE_PREP_STMTS:true}
      dataSource.prepStmtCacheSize: ${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_SIZE:250}
      dataSource.prepStmtCacheSqlLimit: ${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_LIMIT:2048}
      dataSource.useServerPrepStmts: ${SW_DATA_SOURCE_USE_SERVER_PREP_STMTS:true}
      dataSource.useAffectedRows: ${SW_DATA_SOURCE_USE_AFFECTED_ROWS:true}
    metadataQueryMaxSize: ${SW_STORAGE_MYSQL_QUERY_MAX_SIZE:5000}
    maxSizeOfArrayColumn: ${SW_STORAGE_MAX_SIZE_OF_ARRAY_COLUMN:20}
    numOfSearchableValuesPerTag: ${SW_STORAGE_NUM_OF_SEARCHABLE_VALUES_PER_TAG:2}
    maxSizeOfBatchSql: ${SW_STORAGE_MAX_SIZE_OF_BATCH_SQL:2000}
    asyncBatchPersistentPoolSize: ${SW_STORAGE_ASYNC_BATCH_PERSISTENT_POOL_SIZE:4}
```
All connection-related settings, including URL link, username, and password are found in `application.yml`. 
For details on settings, refer to the configuration of *MySQL* above.
To understand the function of the parameter `rewriteBatchedStatements=true` in TiDB, see the document of [TiDB best practices](https://docs.pingcap.com/tidb/stable/java-app-best-practices#use-batch-api).

## InfluxDB
InfluxDB storage provides a time-series database as a new storage option.

```yaml
storage:
  selector: ${SW_STORAGE:influxdb}
  influxdb:
    url: ${SW_STORAGE_INFLUXDB_URL:http://localhost:8086}
    user: ${SW_STORAGE_INFLUXDB_USER:root}
    password: ${SW_STORAGE_INFLUXDB_PASSWORD:}
    database: ${SW_STORAGE_INFLUXDB_DATABASE:skywalking}
    actions: ${SW_STORAGE_INFLUXDB_ACTIONS:1000} # the number of actions to collect
    duration: ${SW_STORAGE_INFLUXDB_DURATION:1000} # the time to wait at most (milliseconds)
    fetchTaskLogMaxSize: ${SW_STORAGE_INFLUXDB_FETCH_TASK_LOG_MAX_SIZE:5000} # the max number of fetch task log in a request
```
All connection related settings, including URL link, username, and password are found in `application.yml`. For metadata storage provider settings, refer to the configurations of **H2/MySQL** above.

## PostgreSQL
PostgreSQL jdbc driver uses version 42.2.18. It supports PostgreSQL 8.2 or newer.
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
All connection-related settings, including URL link, username, and password are found in `application.yml`. 
Only part of the settings are listed here. Please follow [HikariCP](https://github.com/brettwooldridge/HikariCP) connection pool document for full settings.

## More storage extension solutions
Follow the [Storage extension development guide](../../guides/storage-extention.md) 
in the [Project Extensions document](../../guides/README.md#project-extensions).
