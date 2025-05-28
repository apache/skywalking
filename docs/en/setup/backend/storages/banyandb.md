## BanyanDB

[BanyanDB](https://github.com/apache/skywalking-banyandb) is a dedicated storage implementation developed by the SkyWalking Team and the community. Activate BanyanDB as the storage by setting the storage provider to **banyandb**.

The BanyanDB server compatibility relies on API and release versions, which are defined in `bydb.dependencies.properties`
```shell
# BanyanDB version is the version number of BanyanDB Server release.
# This is the bundled and tested BanyanDB release version
bydb.version=x.y
# BanyanDB API version is the version number of the BanyanDB query APIs
# OAP server has bundled implementation of BanyanDB Java client.
# Please check BanyanDB documentation for the API version compatibility.
# https://skywalking.apache.org/docs/skywalking-banyandb/next/installation/versions
# Each `bydb.api.version` could have multiple compatible release version(`bydb.version`).
bydb.api.version=x.y
```

If the BanyanDB server API version is not compatible with the OAP server, the OAP server will not start, and the following error message will be displayed:
```shell
... ERROR [] - ... Incompatible BanyanDB server API version: 0.x. But accepted versions: 0.y
org.apache.skywalking.oap.server.library.module.ModuleStartException: Incompatible BanyanDB server API version...
```

### Configuration
In the `application.yml` file, select the BanyanDB storage provider:

```yaml
storage:
  selector: ${SW_STORAGE:banyandb}
```

Since 10.2.0, the banyandb configuration is separated to an independent configuration file: `bydb.yaml`:

```yaml
global:
  # Targets is the list of BanyanDB servers, separated by commas.
  # Each target is a BanyanDB server in the format of `host:port`.
  # If BanyanDB is deployed as a standalone server, the target should be the IP address or domain name and port of the BanyanDB server.
  # If BanyanDB is deployed in a cluster, the targets should be the IP address or domain name and port of the `liaison` nodes, separated by commas.
  targets: ${SW_STORAGE_BANYANDB_TARGETS:127.0.0.1:17912}
  # The maximum number of records in a bulk write request.
  # A larger value can improve write performance but also increases OAP and BanyanDB Server memory usage.
  maxBulkSize: ${SW_STORAGE_BANYANDB_MAX_BULK_SIZE:10000}
  # The minimum seconds between two bulk flushes.
  # If the data in a bulk is less than maxBulkSize, the data will be flushed after this period.
  # If the data in a bulk exceeds maxBulkSize, the data will be flushed immediately.
  # A larger value can reduce write pressure on BanyanDB Server but increase data latency.
  flushInterval: ${SW_STORAGE_BANYANDB_FLUSH_INTERVAL:15}
  # The timeout in seconds for a bulk flush.
  flushTimeout: ${SW_STORAGE_BANYANDB_FLUSH_TIMEOUT:10}
  # The number of threads that write data to BanyanDB concurrently.
  # A higher value can improve write performance but also increases CPU usage on both OAP and BanyanDB Server.
  concurrentWriteThreads: ${SW_STORAGE_BANYANDB_CONCURRENT_WRITE_THREADS:15}
  # The maximum size of the dataset when the OAP loads cache, such as network aliases.
  resultWindowMaxSize: ${SW_STORAGE_BANYANDB_QUERY_MAX_WINDOW_SIZE:10000}
  # The maximum size of metadata per query.
  metadataQueryMaxSize: ${SW_STORAGE_BANYANDB_QUERY_MAX_SIZE:10000}
  # The maximum number of trace segments per query.
  segmentQueryMaxSize: ${SW_STORAGE_BANYANDB_QUERY_SEGMENT_SIZE:200}
  # The maximum number of profile task queries in a request.
  profileTaskQueryMaxSize: ${SW_STORAGE_BANYANDB_QUERY_PROFILE_TASK_SIZE:200}
  # The batch size for querying profile data.
  profileDataQueryBatchSize: ${SW_STORAGE_BANYANDB_QUERY_PROFILE_DATA_BATCH_SIZE:100}
  asyncProfilerTaskQueryMaxSize: ${SW_STORAGE_BANYANDB_ASYNC_PROFILER_TASK_QUERY_MAX_SIZE:200}
  # If the BanyanDB server is configured with TLS, configure the TLS cert file path and enable TLS connection.
  sslTrustCAPath: ${SW_STORAGE_BANYANDB_SSL_TRUST_CA_PATH:""}

groups:
  # The group settings of record.
  #  - "ShardNum": Number of shards in the group. Shards are the basic units of data storage in BanyanDB. Data is distributed across shards based on the hash value of the series ID.
  #     Refer to the [BanyanDB Shard](https://skywalking.apache.org/docs/skywalking-banyandb/latest/concept/clustering/#52-data-sharding) documentation for more details.
  #  - "SIDays": Interval in days for creating a new segment. Segments are time-based, allowing efficient data retention and querying. `SI` stands for Segment Interval.
  #  - "TTLDays": Time-to-live for the data in the group, in days. Data exceeding the TTL will be deleted.
  #
  #  For more details on setting `segmentIntervalDays` and `ttlDays`, refer to the [BanyanDB TTL](https://skywalking.apache.org/docs/main/latest/en/banyandb/ttl) documentation.

  # The "recordsNormal" section defines settings for datasets not specified in records.
  # Each dataset will be grouped under a single group named "recordsNormal".
  recordsNormal:
    # The settings for the default "hot" stage.
    shardNum:  ${SW_STORAGE_BANYANDB_GR_NORMAL_SHARD_NUM:1}
    segmentInterval: ${SW_STORAGE_BANYANDB_GR_NORMAL_SI_DAYS:1}
    ttl: ${SW_STORAGE_BANYANDB_GR_NORMAL_TTL_DAYS:3}
    # If the "warm" stage is enabled, the data will be moved to the "warm" stage after the TTL of the "hot" stage.
    # If the "cold" stage is enabled and "warm" stage is disabled, the data will be moved to the "cold" stage after the TTL of the "hot" stage.
    # If both "warm" and "cold" stages are enabled, the data will be moved to the "warm" stage after the TTL of the "hot" stage, and then to the "cold" stage after the TTL of the "warm" stage.
    # OAP will query the data from the "hot and warm" stage by default if the "warm" stage is enabled.
    enableWarmStage: ${SW_STORAGE_BANYANDB_GR_NORMAL_ENABLE_WARM_STAGE:false}
    enableColdStage: ${SW_STORAGE_BANYANDB_GR_NORMAL_ENABLE_COLD_STAGE:false}
    # The settings for the "warm" stage.
    warm:
      shardNum: ${SW_STORAGE_BANYANDB_GR_NORMAL_WARM_SHARD_NUM:1}
      segmentInterval: ${SW_STORAGE_BANYANDB_GR_NORMAL_WARM_SI_DAYS:2}
      ttl: ${SW_STORAGE_BANYANDB_GR_NORMAL_WARM_TTL_DAYS:7}
      nodeSelector: ${SW_STORAGE_BANYANDB_GR_NORMAL_WARM_NODE_SELECTOR:"type=warm"}
    # The settings for the "cold" stage.
    cold:
      shardNum: ${SW_STORAGE_BANYANDB_GR_NORMAL_COLD_SHARD_NUM:1}
      segmentInterval: ${SW_STORAGE_BANYANDB_GR_NORMAL_COLD_SI_DAYS:3}
      ttl: ${SW_STORAGE_BANYANDB_GR_NORMAL_COLD_TTL_DAYS:30}
      nodeSelector: ${SW_STORAGE_BANYANDB_GR_NORMAL_COLD_NODE_SELECTOR:"type=cold"}
  # The group settings of super datasets.
  # Super datasets are used to store trace or log data that is too large for normal datasets.
  recordsTrace:
    shardNum: ${SW_STORAGE_BANYANDB_GR_TRACE_SHARD_NUM:2}
    segmentInterval: ${SW_STORAGE_BANYANDB_GR_TRACE_SI_DAYS:1}
    ttl: ${SW_STORAGE_BANYANDB_GR_TRACE_TTL_DAYS:3}
    enableWarmStage: ${SW_STORAGE_BANYANDB_GR_TRACE_ENABLE_WARM_STAGE:false}
    enableColdStage: ${SW_STORAGE_BANYANDB_GR_TRACE_ENABLE_COLD_STAGE:false}
    warm:
      shardNum: ${SW_STORAGE_BANYANDB_GR_TRACE_WARM_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_GR_TRACE_WARM_SI_DAYS:1}
      ttl: ${SW_STORAGE_BANYANDB_GR_TRACE_WARM_TTL_DAYS:7}
      nodeSelector: ${SW_STORAGE_BANYANDB_GR_TRACE_WARM_NODE_SELECTOR:"type=warm"}
    cold:
      shardNum: ${SW_STORAGE_BANYANDB_GR_TRACE_COLD_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_GR_TRACE_COLD_SI_DAYS:1}
      ttl: ${SW_STORAGE_BANYANDB_GR_TRACE_COLD_TTL_DAYS:30}
      nodeSelector: ${SW_STORAGE_BANYANDB_GR_TRACE_COLD_NODE_SELECTOR:"type=cold"}
  recordsZipkinTrace:
    shardNum: ${SW_STORAGE_BANYANDB_GR_ZIPKIN_TRACE_SHARD_NUM:2}
    segmentInterval: ${SW_STORAGE_BANYANDB_GR_ZIPKIN_TRACE_SI_DAYS:1}
    ttl: ${SW_STORAGE_BANYANDB_GR_ZIPKIN_TRACE_TTL_DAYS:3}
    enableWarmStage: ${SW_STORAGE_BANYANDB_GR_ZIPKIN_TRACE_ENABLE_WARM_STAGE:false}
    enableColdStage: ${SW_STORAGE_BANYANDB_GR_ZIPKIN_TRACE_ENABLE_COLD_STAGE:false}
    warm:
      shardNum: ${SW_STORAGE_BANYANDB_GR_ZIPKIN_TRACE_WARM_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_GR_ZIPKIN_TRACE_WARM_SI_DAYS:1}
      ttl: ${SW_STORAGE_BANYANDB_GR_ZIPKIN_TRACE_WARM_TTL_DAYS:7}
      nodeSelector: ${SW_STORAGE_BANYANDB_GR_ZIPKIN_TRACE_WARM_NODE_SELECTOR:"type=warm"}
    cold:
      shardNum: ${SW_STORAGE_BANYANDB_GR_ZIPKIN_TRACE_COLD_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_GR_ZIPKIN_TRACE_COLD_SI_DAYS:1}
      ttl: ${SW_STORAGE_BANYANDB_GR_ZIPKIN_TRACE_COLD_TTL_DAYS:30}
      nodeSelector: ${SW_STORAGE_BANYANDB_GR_ZIPKIN_TRACE_COLD_NODE_SELECTOR:"type=cold"}
  recordsLog:
    shardNum: ${SW_STORAGE_BANYANDB_GR_LOG_SHARD_NUM:2}
    segmentInterval: ${SW_STORAGE_BANYANDB_GR_LOG_SI_DAYS:1}
    ttl: ${SW_STORAGE_BANYANDB_GR_LOG_TTL_DAYS:3}
    enableWarmStage: ${SW_STORAGE_BANYANDB_GR_LOG_ENABLE_WARM_STAGE:false}
    enableColdStage: ${SW_STORAGE_BANYANDB_GR_LOG_ENABLE_COLD_STAGE:false}
    warm:
      shardNum: ${SW_STORAGE_BANYANDB_GR_LOG_WARM_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_GR_LOG_WARM_SI_DAYS:1}
      ttl: ${SW_STORAGE_BANYANDB_GR_LOG_WARM_TTL_DAYS:7}
      nodeSelector: ${SW_STORAGE_BANYANDB_GR_LOG_WARM_NODE_SELECTOR:"type=warm"}
    cold:
      shardNum: ${SW_STORAGE_BANYANDB_GR_LOG_COLD_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_GR_LOG_COLD_SI_DAYS:1}
      ttl: ${SW_STORAGE_BANYANDB_GR_LOG_COLD_TTL_DAYS:30}
      nodeSelector: ${SW_STORAGE_BANYANDB_GR_LOG_COLD_NODE_SELECTOR:"type=cold"}
  recordsBrowserErrorLog:
    shardNum: ${SW_STORAGE_BANYANDB_GR_BROWSER_ERROR_LOG_SHARD_NUM:2}
    segmentInterval: ${SW_STORAGE_BANYANDB_GR_BROWSER_ERROR_LOG_SI_DAYS:1}
    ttl: ${SW_STORAGE_BANYANDB_GR_BROWSER_ERROR_LOG_TTL_DAYS:3}
    enableWarmStage: ${SW_STORAGE_BANYANDB_GR_BROWSER_ERROR_LOG_ENABLE_WARM_STAGE:false}
    enableColdStage: ${SW_STORAGE_BANYANDB_GR_BROWSER_ERROR_LOG_ENABLE_COLD_STAGE:false}
    warm:
      shardNum: ${SW_STORAGE_BANYANDB_GR_BROWSER_ERROR_LOG_WARM_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_GR_BROWSER_ERROR_LOG_WARM_SI_DAYS:1}
      ttl: ${SW_STORAGE_BANYANDB_GR_BROWSER_ERROR_LOG_WARM_TTL_DAYS:7}
      nodeSelector: ${SW_STORAGE_BANYANDB_GR_BROWSER_ERROR_LOG_WARM_NODE_SELECTOR:"type=warm"}
    cold:
      shardNum: ${SW_STORAGE_BANYANDB_GR_BROWSER_ERROR_LOG_COLD_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_GR_BROWSER_ERROR_LOG_COLD_SI_DAYS:1}
      ttl: ${SW_STORAGE_BANYANDB_GR_BROWSER_ERROR_LOG_COLD_TTL_DAYS:30}
      nodeSelector: ${SW_STORAGE_BANYANDB_GR_BROWSER_ERROR_LOG_COLD_NODE_SELECTOR:"type=cold"}
  # The group settings of metrics.
  #
  # OAP stores metrics based its granularity.
  # Valid values are "day", "hour", and "minute". That means metrics will be stored in the three separate groups.
  # Non-"minute" are governed by the "core.downsampling" setting.
  # For example, if "core.downsampling" is set to "hour", the "hour" will be used, while "day" are ignored.
  metricsMin:
    shardNum: ${SW_STORAGE_BANYANDB_GM_MINUTE_SHARD_NUM:2}
    segmentInterval: ${SW_STORAGE_BANYANDB_GM_MINUTE_SI_DAYS:1}
    ttl: ${SW_STORAGE_BANYANDB_GM_MINUTE_TTL_DAYS:7}
    enableWarmStage: ${SW_STORAGE_BANYANDB_GM_MINUTE_ENABLE_WARM_STAGE:false}
    enableColdStage: ${SW_STORAGE_BANYANDB_GM_MINUTE_ENABLE_COLD_STAGE:false}
    warm:
      shardNum: ${SW_STORAGE_BANYANDB_GM_MINUTE_WARM_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_GM_MINUTE_WARM_SI_DAYS:3}
      ttl: ${SW_STORAGE_BANYANDB_GM_MINUTE_WARM_TTL_DAYS:15}
      nodeSelector: ${SW_STORAGE_BANYANDB_GM_MINUTE_WARM_NODE_SELECTOR:"type=warm"}
    cold:
      shardNum: ${SW_STORAGE_BANYANDB_GM_MINUTE_COLD_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_GM_MINUTE_COLD_SI_DAYS:5}
      ttl: ${SW_STORAGE_BANYANDB_GM_MINUTE_COLD_TTL_DAYS:60}
      nodeSelector: ${SW_STORAGE_BANYANDB_GM_MINUTE_COLD_NODE_SELECTOR:"type=cold"}
  metricsHour:
    shardNum: ${SW_STORAGE_BANYANDB_GM_HOUR_SHARD_NUM:1}
    segmentInterval: ${SW_STORAGE_BANYANDB_GM_HOUR_SI_DAYS:5}
    ttl: ${SW_STORAGE_BANYANDB_GM_HOUR_TTL_DAYS:15}
    enableWarmStage: ${SW_STORAGE_BANYANDB_GM_HOUR_ENABLE_WARM_STAGE:false}
    enableColdStage: ${SW_STORAGE_BANYANDB_GM_HOUR_ENABLE_COLD_STAGE:false}
    warm:
      shardNum: ${SW_STORAGE_BANYANDB_GM_HOUR_WARM_SHARD_NUM:1}
      segmentInterval: ${SW_STORAGE_BANYANDB_GM_HOUR_WARM_SI_DAYS:7}
      ttl: ${SW_STORAGE_BANYANDB_GM_HOUR_WARM_TTL_DAYS:30}
      nodeSelector: ${SW_STORAGE_BANYANDB_GM_HOUR_WARM_NODE_SELECTOR:"type=warm"}
    cold:
      shardNum: ${SW_STORAGE_BANYANDB_GM_HOUR_COLD_SHARD_NUM:1}
      segmentInterval: ${SW_STORAGE_BANYANDB_GM_HOUR_COLD_SI_DAYS:15}
      ttl: ${SW_STORAGE_BANYANDB_GM_HOUR_COLD_TTL_DAYS:120}
      nodeSelector: ${SW_STORAGE_BANYANDB_GM_HOUR_COLD_NODE_SELECTOR:"type=cold"}
  metricsDay:
    shardNum: ${SW_STORAGE_BANYANDB_GM_DAY_SHARD_NUM:1}
    segmentInterval: ${SW_STORAGE_BANYANDB_GM_DAY_SI_DAYS:15}
    ttl: ${SW_STORAGE_BANYANDB_GM_DAY_TTL_DAYS:15}
    enableWarmStage: ${SW_STORAGE_BANYANDB_GM_DAY_ENABLE_WARM_STAGE:false}
    enableColdStage: ${SW_STORAGE_BANYANDB_GM_DAY_ENABLE_COLD_STAGE:false}
    warm:
      shardNum: ${SW_STORAGE_BANYANDB_GM_DAY_WARM_SHARD_NUM:1}
      segmentInterval: ${SW_STORAGE_BANYANDB_GM_DAY_WARM_SI_DAYS:15}
      ttl: ${SW_STORAGE_BANYANDB_GM_DAY_WARM_TTL_DAYS:30}
      nodeSelector: ${SW_STORAGE_BANYANDB_GM_DAY_WARM_NODE_SELECTOR:"type=warm"}
    cold:
      shardNum: ${SW_STORAGE_BANYANDB_GM_DAY_COLD_SHARD_NUM:1}
      segmentInterval: ${SW_STORAGE_BANYANDB_GM_DAY_COLD_SI_DAYS:15}
      ttl: ${SW_STORAGE_BANYANDB_GM_DAY_COLD_TTL_DAYS:120}
      nodeSelector: ${SW_STORAGE_BANYANDB_GM_DAY_COLD_NODE_SELECTOR:"type=cold"}
  # If the metrics is marked as "index_mode", the metrics will be stored in the "metadata" group.
  # The "metadata" group is designed to store metrics that are used for indexing without value columns.
  # Such as `service_traffic`, `network_address_alias`, etc.
  # "index_mode" requires BanyanDB *0.8.0* or later.
  metadata:
    shardNum: ${SW_STORAGE_BANYANDB_GM_METADATA_SHARD_NUM:2}
    segmentInterval: ${SW_STORAGE_BANYANDB_GM_METADATA_SI_DAYS:15}
    ttl: ${SW_STORAGE_BANYANDB_GM_METADATA_TTL_DAYS:15}

  # The group settings of property, such as UI and profiling.
  property:
    shardNum: ${SW_STORAGE_BANYANDB_GP_PROPERTY_SHARD_NUM:1}

```

### Installation Modes

BanyanDB Server supports two installation modes:

- **Standalone Mode**: Suitable for small-scale deployments.
    - **Configuration**: `targets` is the IP address/hostname and port of the BanyanDB server.

Use the docker mode to run BanyanDB containerized. 
```shell
# The compatible version number could be found in /config/bydb.dependencies.properties
export BYDB_VERSION=xxx

docker pull apache/skywalking-banyandb:${BYDB_VERSION}

docker run -d \
  -p 17912:17912 \
  -p 17913:17913 \
  --name banyandb \
  apache/skywalking-banyandb:${BYDB_VERSION} \
  standalone
```

Or use the development builds for latest and unreleased features, all versions are available [here](https://github.com/apache/skywalking-banyandb/pkgs/container/skywalking-banyandb).
```shell
docker pull apache/skywalking-banyandb:latest

docker run -d \
  -p 17912:17912 \
  -p 17913:17913 \
  --name banyandb \
  ghcr.io/apache/skywalking-banyandb:xxxxxx \
  standalone
```

- **Cluster Mode**: Suitable for large-scale deployments.
    - **Configuration**: `targets` is the IP address/hostname and port of the `liaison` nodes, separated by commas. `Liaison` nodes are the entry points of the BanyanDB cluster.

For more details, refer to the documentation of [BanyanDB](https://skywalking.apache.org/docs/skywalking-banyandb/latest/readme/) and the [BanyanDB Java Client](https://github.com/apache/skywalking-banyandb-java-client) subprojects.
