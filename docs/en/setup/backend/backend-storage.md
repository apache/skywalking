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
    # nameSpace: ${SW_NAMESPACE:""}
    # user: ${SW_ES_USER:""} # User needs to be set when Http Basic authentication is enabled
    # password: ${SW_ES_PASSWORD:""} # Password to be set when Http Basic authentication is enabled
    # secretsManagementFile: ${SW_ES_SECRETS_MANAGEMENT_FILE:""} # Secrets management file in the properties format includes the username, password, which are managed by 3rd party tool.
    #trustStorePath: ${SW_SW_STORAGE_ES_SSL_JKS_PATH:""}
    #trustStorePass: ${SW_SW_STORAGE_ES_SSL_JKS_PASS:""}
    enablePackedDownsampling: ${SW_STORAGE_ENABLE_PACKED_DOWNSAMPLING:true} # Hour and Day metrics will be merged into minute index.
    dayStep: ${SW_STORAGE_DAY_STEP:1} # Represent the number of days in the one minute/hour/day index.
    clusterNodes: ${SW_STORAGE_ES_CLUSTER_NODES:localhost:9200}
    protocol: ${SW_STORAGE_ES_HTTP_PROTOCOL:"http"}
    indexShardsNumber: ${SW_STORAGE_ES_INDEX_SHARDS_NUMBER:2}
    indexReplicasNumber: ${SW_STORAGE_ES_INDEX_REPLICAS_NUMBER:0}
    # Those data TTL settings will override the same settings in core module.
    recordDataTTL: ${SW_STORAGE_ES_RECORD_DATA_TTL:7} # Unit is day
    otherMetricsDataTTL: ${SW_STORAGE_ES_OTHER_METRIC_DATA_TTL:45} # Unit is day
    monthMetricsDataTTL: ${SW_STORAGE_ES_MONTH_METRIC_DATA_TTL:18} # Unit is month
    # Batch process setting, refer to https://www.elastic.co/guide/en/elasticsearch/client/java-api/5.5/java-docs-bulk-processor.html
    bulkActions: ${SW_STORAGE_ES_BULK_ACTIONS:2000} # Execute the bulk every 2000 requests
    bulkSize: ${SW_STORAGE_ES_BULK_SIZE:20} # flush the bulk every 20mb
    flushInterval: ${SW_STORAGE_ES_FLUSH_INTERVAL:10} # flush the bulk every 10 seconds whatever the number of requests
    concurrentRequests: ${SW_STORAGE_ES_CONCURRENT_REQUESTS:2} # the number of concurrent requests
    resultWindowMaxSize: ${SW_STORAGE_ES_QUERY_MAX_WINDOW_SIZE:10000}
    metadataQueryMaxSize: ${SW_STORAGE_ES_QUERY_MAX_SIZE:5000}
    segmentQueryMaxSize: ${SW_STORAGE_ES_QUERY_SEGMENT_SIZE:200}
    profileTaskQueryMaxSize: ${SW_STORAGE_ES_QUERY_PROFILE_TASK_SIZE:200}
    advanced: ${SW_STORAGE_ES_ADVANCED:""}
```

In order to use ElasticSearch 7, comment/remove the section `storage/elasticsearch` and find the corresponding config section(`storage/elasticsearch7`),
uncomment to enable it.

### Downsampling Data Packing

Downsampling data packing(`storage/elasticsearch/enablePackedDownsampling`, default activated) is a new feature since 7.0.0.  
Metrics data has 4 different precisions,based on `core/default/downsampling` configurations. 
In previous(6.x), every precision of each metrics had one separated
index. After this is activated, metrics of day and hour precisions are merged into minute precision. The number of indexes
decreased, and cause less payload to the ElasticSearch server.

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
    indexShardsNumber: ${SW_STORAGE_ES_INDEX_SHARDS_NUMBER:2}
    indexReplicasNumber: ${SW_STORAGE_ES_INDEX_REPLICAS_NUMBER:0}
    # Those data TTL settings will override the same settings in core module.
    recordDataTTL: ${SW_STORAGE_ES_RECORD_DATA_TTL:7} # Unit is day
    otherMetricsDataTTL: ${SW_STORAGE_ES_OTHER_METRIC_DATA_TTL:45} # Unit is day
    monthMetricsDataTTL: ${SW_STORAGE_ES_MONTH_METRIC_DATA_TTL:18} # Unit is month
    # Batch process setting, refer to https://www.elastic.co/guide/en/elasticsearch/client/java-api/5.5/java-docs-bulk-processor.html
    bulkActions: ${SW_STORAGE_ES_BULK_ACTIONS:2000} # Execute the bulk every 2000 requests
    bulkSize: ${SW_STORAGE_ES_BULK_SIZE:20} # flush the bulk every 20mb
    flushInterval: ${SW_STORAGE_ES_FLUSH_INTERVAL:10} # flush the bulk every 10 seconds whatever the number of requests
    concurrentRequests: ${SW_STORAGE_ES_CONCURRENT_REQUESTS:2} # the number of concurrent requests
    advanced: ${SW_STORAGE_ES_ADVANCED:""}
```
- File at `trustStorePath` is being monitored, once it is changed, the ElasticSearch client will do reconnecting.
- `trustStorePass` could be changed on the runtime through [**Secrets Management File Of ElasticSearch Authentication**](#secrets-management-file-of-elasticsearch-authentication).

### Data TTL
TTL in ElasticSearch overrides the settings of core, read [ElasticSearch section in TTL document](ttl.md#elasticsearch-6-storage-ttl)

### Daily Index Step
Daily index step(`storage/elasticsearch/dayStep`, default 1) represents the index creation period. In this period, several days(dayStep value)' metrics are saved.

Mostly, users don't need to change the value manually. As SkyWalking is designed to observe large scale distributed system.
But in some specific cases, users want to set a long TTL value, such as more than 60 days, but their ElasticSearch cluster isn't powerful due to the low traffic in the production environment.
This value could be increased to 5(or more), if users could make sure single one index could support these days(5 in this case) metrics and traces.

Such as, if dayStep == 11, 
1. data in [2000-01-01, 2000-01-11] will be merged into the index-20000101.
1. data in [2000-01-12, 2000-01-22] will be merged into the index-20000112.

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
    # Those data TTL settings will override the same settings in core module.
    recordDataTTL: ${SW_STORAGE_ES_RECORD_DATA_TTL:7} # Unit is day
    otherMetricsDataTTL: ${SW_STORAGE_ES_OTHER_METRIC_DATA_TTL:45} # Unit is day
    monthMetricsDataTTL: ${SW_STORAGE_ES_MONTH_METRIC_DATA_TTL:18} # Unit is month
    # Batch process setting, refer to https://www.elastic.co/guide/en/elasticsearch/client/java-api/5.5/java-docs-bulk-processor.html
    bulkActions: ${SW_STORAGE_ES_BULK_ACTIONS:2000} # Execute the bulk every 2000 requests
    bulkSize: ${SW_STORAGE_ES_BULK_SIZE:20} # flush the bulk every 20mb
    flushInterval: ${SW_STORAGE_ES_FLUSH_INTERVAL:10} # flush the bulk every 10 seconds whatever the number of requests
    concurrentRequests: ${SW_STORAGE_ES_CONCURRENT_REQUESTS:2} # the number of concurrent requests
```

### ElasticSearch 6 with Jaeger trace extension
This implementation shares most of `elasticsearch`, just extend to support zipkin span storage.
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
    # Those data TTL settings will override the same settings in core module.
    recordDataTTL: ${SW_STORAGE_ES_RECORD_DATA_TTL:7} # Unit is day
    otherMetricsDataTTL: ${SW_STORAGE_ES_OTHER_METRIC_DATA_TTL:45} # Unit is day
    monthMetricsDataTTL: ${SW_STORAGE_ES_MONTH_METRIC_DATA_TTL:18} # Unit is month
    # Batch process setting, refer to https://www.elastic.co/guide/en/elasticsearch/client/java-api/5.5/java-docs-bulk-processor.html
    bulkActions: ${SW_STORAGE_ES_BULK_ACTIONS:2000} # Execute the bulk every 2000 requests
    bulkSize: ${SW_STORAGE_ES_BULK_SIZE:20} # flush the bulk every 20mb
    flushInterval: ${SW_STORAGE_ES_FLUSH_INTERVAL:10} # flush the bulk every 10 seconds whatever the number of requests
    concurrentRequests: ${SW_STORAGE_ES_CONCURRENT_REQUESTS:2} # the number of concurrent requests
```


### About Namespace
When namespace is set, names of all indexes in ElasticSearch will use it as prefix.

### About Authentication
We only support [basic authentication](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/6.6/_basic_authentication.html). If you need that, you could set `user` and `password`.
For how to enable http basic authentication, you could read this https://brudtkuhl.com/blog/securing-elasticsearch/


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
Currently tested TiDB in version 2.0.9, and Mysql Client driver in version 8.0.13.
Active TiDB as storage, set storage provider to **mysql**. 

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
These settings can refer to the configuration of *MySQL* above.

## InfluxDB
InfluxDB as storage since SkyWalking 7.0. It depends on `H2/MySQL` storage-plugin to store `metadata` like `Inventory` and `ProfileTask`. So, when we set `InfluxDB` as storage provider. We need to configure properties of InfluxDB and Metabase.

```yaml
storage:
  selector: ${SW_STORAGE:influxdb}
  influxdb:
    # Metadata storage provider configuration
    metabaseType: ${SW_STORAGE_METABASE_TYPE:H2} # There are 2 options as Metabase provider, H2 or MySQL.
    h2Props:
      dataSourceClassName: ${SW_STORAGE_METABASE_DRIVER:org.h2.jdbcx.JdbcDataSource}
      dataSource.url: ${SW_STORAGE_METABASE_URL:jdbc:h2:mem:skywalking-oap-db}
      dataSource.user: ${SW_STORAGE_METABASE_USER:sa}
      dataSource.password: ${SW_STORAGE_METABASE_PASSWORD:}
    mysqlProps:
      jdbcUrl: ${SW_STORAGE_METABASE_URL:"jdbc:mysql://localhost:3306/swtest"}
      dataSource.user: ${SW_STORAGE_METABASE_USER:root}
      dataSource.password: ${SW_STORAGE_METABASE_PASSWORD:root@1234}
      dataSource.cachePrepStmts: ${SW_STORAGE_METABASE_CACHE_PREP_STMTS:true}
      dataSource.prepStmtCacheSize: ${SW_STORAGE_METABASE_PREP_STMT_CACHE_SQL_SIZE:250}
      dataSource.prepStmtCacheSqlLimit: ${SW_STORAGE_METABASE_PREP_STMT_CACHE_SQL_LIMIT:2048}
      dataSource.useServerPrepStmts: ${SW_STORAGE_METABASE_USE_SERVER_PREP_STMTS:true}
    metadataQueryMaxSize: ${SW_STORAGE_METABASE_QUERY_MAX_SIZE:5000}
    # InfluxDB configuration
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
