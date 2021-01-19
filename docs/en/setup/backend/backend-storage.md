# Backend storage
SkyWalking storage is pluggable, we have provided the following storage solutions, you could easily
use one of them by specifying it as the `selector` in the `application.yml`：

```yaml
storage:
  selector: ${SW_STORAGE:elasticsearch7}
```

Native supported storage
- H2
- ElasticSearch 6, 7
- MySQL
- TiDB
- InfluxDB

Redistribution version with supported storage.
- ElasticSearch 5


## H2
Active H2 as storage, set storage provider to **H2** In-Memory Databases. Default in distribution package.
Please read `Database URL Overview` in [H2 official document](http://www.h2database.com/html/features.html),
you could set the target to H2 in **Embedded**, **Server** and **Mixed** modes.

Setting fragment example
```yaml
storage:
  selector: ${SW_STORAGE:h2}
  h2:
    driver: org.h2.jdbcx.JdbcDataSource
    url: jdbc:h2:mem:skywalking-oap-db
    user: sa
```

## ElasticSearch
- In order to activate ElasticSearch 6 as storage, set storage provider to **elasticsearch**
- In order to activate ElasticSearch 7 as storage, set storage provider to **elasticsearch7**

**Required ElasticSearch 6.3.2 or higher. HTTP RestHighLevelClient is used to connect server.**

- For ElasticSearch 6.3.2 ~ 7.0.0 (excluded), please download the `apache-skywalking-bin.tar.gz` or `apache-skywalking-bin.zip`,
- For ElasticSearch 7.0.0 ~ 8.0.0 (excluded), please download the `apache-skywalking-bin-es7.tar.gz` or `apache-skywalking-bin-es7.zip`.

For now, ElasticSearch 6 and ElasticSearch 7 share the same configurations, as follows:

```yaml
storage:
  selector: ${SW_STORAGE:elasticsearch}
  elasticsearch:
    nameSpace: ${SW_NAMESPACE:""}
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
    bulkActions: ${SW_STORAGE_ES_BULK_ACTIONS:1000} # Execute the async bulk record data every ${SW_STORAGE_ES_BULK_ACTIONS} requests
    syncBulkActions: ${SW_STORAGE_ES_SYNC_BULK_ACTIONS:50000} # Execute the sync bulk metrics data every ${SW_STORAGE_ES_SYNC_BULK_ACTIONS} requests
    flushInterval: ${SW_STORAGE_ES_FLUSH_INTERVAL:10} # flush the bulk every 10 seconds whatever the number of requests
    concurrentRequests: ${SW_STORAGE_ES_CONCURRENT_REQUESTS:2} # the number of concurrent requests
    resultWindowMaxSize: ${SW_STORAGE_ES_QUERY_MAX_WINDOW_SIZE:10000}
    metadataQueryMaxSize: ${SW_STORAGE_ES_QUERY_MAX_SIZE:5000}
    segmentQueryMaxSize: ${SW_STORAGE_ES_QUERY_SEGMENT_SIZE:200}
    profileTaskQueryMaxSize: ${SW_STORAGE_ES_QUERY_PROFILE_TASK_SIZE:200}
    advanced: ${SW_STORAGE_ES_ADVANCED:""}
```

### ElasticSearch 6 With Https SSL Encrypting communications.

example: 

```yaml
storage:
  selector: ${SW_STORAGE:elasticsearch}
  elasticsearch:
    # nameSpace: ${SW_NAMESPACE:""}
    user: ${SW_ES_USER:""} # User needs to be set when Http Basic authentication is enabled
    password: ${SW_ES_PASSWORD:""} # Password to be set when Http Basic authentication is enabled
    clusterNodes: ${SW_STORAGE_ES_CLUSTER_NODES:localhost:443}
    trustStorePath: ${SW_SW_STORAGE_ES_SSL_JKS_PATH:"../es_keystore.jks"}
    trustStorePass: ${SW_SW_STORAGE_ES_SSL_JKS_PASS:""}
    protocol: ${SW_STORAGE_ES_HTTP_PROTOCOL:"https"}
    ...
```
- File at `trustStorePath` is being monitored, once it is changed, the ElasticSearch client will do reconnecting.
- `trustStorePass` could be changed on the runtime through [**Secrets Management File Of ElasticSearch Authentication**](#secrets-management-file-of-elasticsearch-authentication).

### Daily Index Step
Daily index step(`storage/elasticsearch/dayStep`, default 1) represents the index creation period. In this period, several days(dayStep value)' metrics are saved.

Mostly, users don't need to change the value manually. As SkyWalking is designed to observe large scale distributed system.
But in some specific cases, users want to set a long TTL value, such as more than 60 days, but their ElasticSearch cluster isn't powerful due to the low traffic in the production environment.
This value could be increased to 5(or more), if users could make sure single one index could support these days(5 in this case) metrics and traces.

Such as, if dayStep == 11, 
1. data in [2000-01-01, 2000-01-11] will be merged into the index-20000101.
1. data in [2000-01-12, 2000-01-22] will be merged into the index-20000112.

`storage/elasticsearch/superDatasetDayStep` override the `storage/elasticsearch/dayStep` if the value is positive.
This would affect the record related entities, such as the trace segment. In some cases, the size of metrics is much less than the record(trace), this would help the shards balance in the ElasticSearch cluster.
 
NOTICE, TTL deletion would be affected by these. You should set an extra more dayStep in your TTL. Such as you want to TTL == 30 days and dayStep == 10, you actually need to set TTL = 40;

### Secrets Management File Of ElasticSearch Authentication
The value of `secretsManagementFile` should point to the secrets management file absolute path. 
The file includes username, password and JKS password of ElasticSearch server in the properties format.
```properties
user=xxx
password=yyy
trustStorePass=zzz
```

The major difference between using `user, password, trustStorePass` configs in the `application.yaml` file is, the **Secrets Management File** is being watched by the OAP server. 
Once it is changed manually or through 3rd party tool, such as [Vault](https://github.com/hashicorp/vault), 
the storage provider will use the new username, password and JKS password to establish the connection and close the old one. If the information exist in the file,
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
You could add following config to `elasticsearch.yml`, set the value based on your env.

```yml
# In tracing scenario, consider to set more than this at least.
thread_pool.index.queue_size: 1000 # Only suitable for ElasticSearch 6
thread_pool.write.queue_size: 1000 # Suitable for ElasticSearch 6 and 7

# When you face query error at trace page, remember to check this.
index.max_result_window: 1000000
```

We strongly advice you to read more about these configurations from ElasticSearch official document. 
This effects the performance of ElasticSearch very much.


### ElasticSearch 6 with Zipkin trace extension
This implementation shares most of `elasticsearch`, just extend to support zipkin span storage.
It has all same configs.
```yaml
storage:
  selector: ${SW_STORAGE:zipkin-elasticsearch}
  zipkin-elasticsearch:
    nameSpace: ${SW_NAMESPACE:""}
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

### ElasticSearch 6 with Jaeger trace extension
This implementation shares most of `elasticsearch`, just extend to support jaeger span storage.
It has all same configs.
```yaml
storage:
  selector: ${SW_STORAGE:jaeger-elasticsearch}
  jaeger-elasticsearch:
    nameSpace: ${SW_NAMESPACE:""}
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
When namespace is set, names of all indexes in ElasticSearch will use it as prefix.


## MySQL
Active MySQL as storage, set storage provider to **mysql**. 

**NOTICE:** MySQL driver is NOT allowed in Apache official distribution and source codes. 
Please download MySQL driver by yourself. Copy the connection driver jar to `oap-libs`.

```yaml
storage:
  selector: ${SW_STORAGE:mysql}
  mysql:
    properties:
      jdbcUrl: ${SW_JDBC_URL:"jdbc:mysql://localhost:3306/swtest"}
      dataSource.user: ${SW_DATA_SOURCE_USER:root}
      dataSource.password: ${SW_DATA_SOURCE_PASSWORD:root@1234}
      dataSource.cachePrepStmts: ${SW_DATA_SOURCE_CACHE_PREP_STMTS:true}
      dataSource.prepStmtCacheSize: ${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_SIZE:250}
      dataSource.prepStmtCacheSqlLimit: ${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_LIMIT:2048}
      dataSource.useServerPrepStmts: ${SW_DATA_SOURCE_USE_SERVER_PREP_STMTS:true}
    metadataQueryMaxSize: ${SW_STORAGE_MYSQL_QUERY_MAX_SIZE:5000}
```
All connection related settings including link url, username and password are in `application.yml`. 
Here are some of the settings, please follow [HikariCP](https://github.com/brettwooldridge/HikariCP) connection pool document for all the settings.

## TiDB
Tested TiDB Server 4.0.8 version and Mysql Client driver 8.0.13 version currently.
Active TiDB as storage, set storage provider to **tidb**. 

```yaml
storage:
  selector: ${SW_STORAGE:tidb}
  tidb:
    properties:
      jdbcUrl: ${SW_JDBC_URL:"jdbc:mysql://localhost:4000/swtest"}
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
```
All connection related settings including link url, username and password are in `application.yml`. 
These settings can refer to the configuration of *MySQL* above.

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
All connection related settings including link url, username and password are in `application.yml`. The Metadata storage provider settings can refer to the configuration of **H2/MySQL** above.

## ElasticSearch 5
ElasticSearch 5 is incompatible with ElasticSearch 6 Java client jar, so it could not be included in native distribution.
[OpenSkyWalking/SkyWalking-With-Es5x-Storage](https://github.com/OpenSkywalking/SkyWalking-With-Es5x-Storage) repo includes the distribution version. 

## More storage solution extension
Follow [Storage extension development guide](../../guides/storage-extention.md) 
in [Project Extensions document](../../guides/README.md#project-extensions) in development guide.
