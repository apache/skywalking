# Backend storage
SkyWalking storage is pluggable, we have provided the following storage solutions, you could easily 
use is by changing the `application.yml`

- [**H2**](#h2)
- [**ElasticSearch 6**](#elasticsearch-6)
- [**MySQL**](#mysql)

## H2
Active H2 as storage, set storage provider to **H2** In-Memory Databases. Default in distribution package.
Please read `Database URL Overview` in [H2 official document](http://www.h2database.com/html/features.html),
you could set the target to H2 in **Embedded**, **Server** and **Mixed** modes.

Setting fragment example
```yaml
storage:
  h2:
    driver: org.h2.jdbcx.JdbcDataSource
    url: jdbc:h2:mem:skywalking-oap-db
    user: sa
```

## ElasticSearch 6
Active ElasticSearch 6 as storage, set storage provider to **elasticsearch**.

> Required ElasticSearch 6.3.0 or higher.

Setting fragment example

```yaml
storage:
  elasticsearch:
    # nameSpace: ${SW_STORAGE_ES_NAMESPACE:""}
    clusterNodes: ${SW_STORAGE_ES_CLUSTER_NODES:localhost:9200}
    indexShardsNumber: ${SW_STORAGE_ES_INDEX_SHARDS_NUMBER:2}
    indexReplicasNumber: ${SW_STORAGE_ES_INDEX_REPLICAS_NUMBER:0}
    # Batch process setting, refer to https://www.elastic.co/guide/en/elasticsearch/client/java-api/5.5/java-docs-bulk-processor.html
    bulkActions: ${SW_STORAGE_ES_BULK_ACTIONS:2000} # Execute the bulk every 2000 requests
    bulkSize: ${SW_STORAGE_ES_BULK_SIZE:20} # flush the bulk every 20mb
    flushInterval: ${SW_STORAGE_ES_FLUSH_INTERVAL:10} # flush the bulk every 10 seconds whatever the number of requests
    concurrentRequests: ${SW_STORAGE_ES_CONCURRENT_REQUESTS:2} # the number of concurrent requests
```
### About Namespace
Follow [Namespace](namespace.md) to understand and config the `namesapce` between agent and collector.

## MySQL
Active MySQL as storage, set storage provider to **mysql**. 

**NOTICE:** MySQL driver is NOT allowed in Apache official distribution and source codes. 
Please download MySQL driver by yourself. Copy the connection driver jar to `oap-libs`.

```yaml
storage:
  mysql:
```

All connection related settings including link url, username and password
are in `databsource-settings.properties`. 
This setting file follow [HikariCP](https://github.com/brettwooldridge/HikariCP) connection pool document.

## TiDB
Currently tested in version 2.0.9.
Active TiDB as storage, set storage provider to **mysql**. 

```yaml
storage:
  mysql:
```

All connection related settings including link url, username and password
are in `databsource-settings.properties`. And these settings can refer to the configuration of *MySQL* above.

## More storage solution extension
Follow [Storage extension development guide](../../guides/storage-extention.md) 
in [Project Extensions document](../../guides/README.md#project-extensions) in development guide.
