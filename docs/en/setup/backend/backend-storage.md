# Backend storage
SkyWalking storage is pluggable, we have provided the following storage solutions, you could easily 
use is by changing the `application.yml`

- [**H2**](#h2)
- [**ElasticSearch 6**](#elasticsearch-6)

## H2
Active H2 as storage, set storage provider to **H2**. Default in distribution package.

Setting fragment example
```yaml

```

## ElasticSearch 6
Active ElasticSearch 6 as storage, set storage provider to **elasticsearch**.

Setting fragment example
```yaml
storage:
  elasticsearch:
    clusterNodes: localhost:9200
    indexShardsNumber: 2
    indexReplicasNumber: 0
    # Batch process setting, refer to https://www.elastic.co/guide/en/elasticsearch/client/java-api/5.5/java-docs-bulk-processor.html
    bulkActions: 2000 # Execute the bulk every 2000 requests
    bulkSize: 20 # flush the bulk every 20mb
    flushInterval: 10 # flush the bulk every 10 seconds whatever the number of requests
    concurrentRequests: 2 # the number of concurrent requests
    # Set a timeout on metric data. After the timeout has expired, the metric data will automatically be deleted.
    traceDataTTL: 90 # Unit is minute
    minuteMetricDataTTL: 90 # Unit is minute
    hourMetricDataTTL: 36 # Unit is hour
    dayMetricDataTTL: 45 # Unit is day
    monthMetricDataTTL: 18 # Unit is month
```


## More storage solution extension
Follow [Storage extension development guide](../../guides/storage-extention.md) 
in [Project Extensions document](../../guides/README.md#project-extensions) in development guide.