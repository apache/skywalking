
## BanyanDB
[BanyanDB](https://github.com/apache/skywalking-banyandb) is a dedicated storage implementation developed by the SkyWalking Team and the community.
Activate BanyanDB as the storage, and set storage provider to **banyandb**.

```yaml
storage:
  banyandb:
    endpoints: ${SW_STORAGE_BANYANDB_ENDPOINTS:127.0.0.1:17912}
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