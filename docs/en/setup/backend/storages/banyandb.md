
## BanyanDB
[BanyanDB](https://github.com/apache/skywalking-banyandb) is a dedicated storage implementation developed by the SkyWalking Team and the community.
Activate BanyanDB as the storage, and set storage provider to **banyandb**.

The OAP requires BanyanDB 0.7 server. From this version, BanyanDB provides general compatibility.

```yaml
storage:
  banyandb:
    # Targets is the list of BanyanDB servers, separated by commas.
    # Each target is a BanyanDB server in the format of `host:port` 
    # If the BanyanDB is deployed as a standalone server, the target should be the IP address or domain name and port of the BanyanDB server.
    # If the BanyanDB is deployed in a cluster, the targets should be the IP address or domain name and port of the `liaison` nodes, separated by commas.
    targets: ${SW_STORAGE_BANYANDB_TARGETS:127.0.0.1:17912}
    # The max number of records in a bulk write request.
    # Bigger value can improve the write performance, but also increase the OAP and BanyanDB Server memory usage.
    maxBulkSize: ${SW_STORAGE_BANYANDB_MAX_BULK_SIZE:10000}
    # The minimum seconds between two bulk flushes.
    # If the data in a bulk is less than maxBulkSize, the data will be flushed after this period.
    # If the data in a bulk is more than maxBulkSize, the data will be flushed immediately.
    # Bigger value can reduce the write pressure on BanyanDB Server, but also increase the latency of the data.
    flushInterval: ${SW_STORAGE_BANYANDB_FLUSH_INTERVAL:15}
    # The timeout seconds of a bulk flush.
    flushTimeout: ${SW_STORAGE_BANYANDB_FLUSH_TIMEOUT:10}
    # The shard number of `measure` groups that store the metrics data.
    metricsShardsNumber: ${SW_STORAGE_BANYANDB_METRICS_SHARDS_NUMBER:1}
    # The shard number of `stream` groups that store the trace, log and profile data.
    recordShardsNumber: ${SW_STORAGE_BANYANDB_RECORD_SHARDS_NUMBER:1}
    # The multiplier of the number of shards of the super dataset.
    # Super dataset is a special dataset that stores the trace or log data that is too large to be stored in the normal dataset.
    # If the normal dataset has `n` shards, the super dataset will have `n * superDatasetShardsFactor` shards.
    # For example, supposing `recordShardsNumber` is 3, and `superDatasetShardsFactor` is 2,
    # `segment-default` is a normal dataset that has 3 shards, and `segment-minute` is a super dataset that has 6 shards.
    superDatasetShardsFactor: ${SW_STORAGE_BANYANDB_SUPERDATASET_SHARDS_FACTOR:2}
    # The number of threads that write data to BanyanDB concurrently.
    # Bigger value can improve the write performance, but also increase the OAP and BanyanDB Server CPU usage.
    concurrentWriteThreads: ${SW_STORAGE_BANYANDB_CONCURRENT_WRITE_THREADS:15}
       # The maximum size of dataset when the OAP loads cache, such as network aliases.
    resultWindowMaxSize: ${SW_STORAGE_BANYANDB_QUERY_MAX_WINDOW_SIZE:10000}
    # The maximum size of metadata per query.
    metadataQueryMaxSize: ${SW_STORAGE_BANYANDB_QUERY_MAX_SIZE:10000}
    # The maximum size of trace segments per query.
    segmentQueryMaxSize: ${SW_STORAGE_BANYANDB_QUERY_SEGMENT_SIZE:200}
    # The max number of profile task query in a request.
    profileTaskQueryMaxSize: ${SW_STORAGE_BANYANDB_QUERY_PROFILE_TASK_SIZE:200}
    # The batch size of query profiling data.
    profileDataQueryBatchSize: ${SW_STORAGE_BANYANDB_QUERY_PROFILE_DATA_BATCH_SIZE:100}
    # Data is stored in BanyanDB in segments. A segment is a time range of data.
    # The segment interval is the time range of a segment.
    # The value should be less or equal to data TTL relevant settings.
    segmentIntervalDays: ${SW_STORAGE_BANYANDB_SEGMENT_INTERVAL_DAYS:1}
    # The super dataset segment interval is the time range of a segment in the super dataset.
    superDatasetSegmentIntervalDays: ${SW_STORAGE_BANYANDB_SUPER_DATASET_SEGMENT_INTERVAL_DAYS:1}
    # Specific groups settings.
    # For example, {"group1": {"blockIntervalHours": 4, "segmentIntervalDays": 1}}
    # Please refer to https://github.com/apache/skywalking-banyandb/blob/${BANYANDB_RELEASE}/docs/crud/group.md#create-operation
    # for group setting details.
    specificGroupSettings: ${SW_STORAGE_BANYANDB_SPECIFIC_GROUP_SETTINGS:""}
    # If the BanyanDB server is configured with TLS, config the TLS cert file path and open tls connection.
    sslTrustCAPath: ${SW_STORAGE_BANYANDB_SSL_TRUST_CA_PATH:""}
```

BanyanDB Server supports two installation modes: standalone and cluster. The standalone mode is suitable for small-scale deployments, while the cluster mode is suitable for large-scale deployments.

* Standalone mode: `targets` is the IP address/host name and port of the BanyanDB server.
* Cluster mode: `targets` is the IP address/host name and port of the `liaison` nodes, separated by commas. `liaison` nodes are the entry points of the BanyanDB cluster.

For more details, please refer to the documents of [BanyanDB](https://skywalking.apache.org/docs/skywalking-banyandb/latest/readme/)
and [BanyanDB Java Client](https://github.com/apache/skywalking-banyandb-java-client) subprojects.
