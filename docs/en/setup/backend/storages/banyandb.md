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
  user: ${SW_STORAGE_BANYANDB_USER:""}
  password: ${SW_STORAGE_BANYANDB_PASSWORD:""}
  # If the BanyanDB server is configured with TLS, configure the TLS cert file path and enable TLS connection.
  sslTrustCAPath: ${SW_STORAGE_BANYANDB_SSL_TRUST_CA_PATH:""}
  # Cleanup TopN rules in BanyanDB server that are not configured in the bydb-topn.yml config.
  cleanupUnusedTopNRules: ${SW_STORAGE_BANYANDB_CLEANUP_UNUSED_TOPN_RULES:true}

groups:
  # The group settings of record.
  #  - "shardNum": Number of shards in the group. Shards are the basic units of data storage in BanyanDB. Data is distributed across shards based on the hash value of the series ID.
  #     Refer to the [BanyanDB Shard](https://skywalking.apache.org/docs/skywalking-banyandb/latest/concept/clustering/#52-data-sharding) documentation for more details.
  #  - "segmentInterval": Interval in days for creating a new segment. Segments are time-based, allowing efficient data retention and querying. `SI` stands for Segment Interval.
  #  - "ttl": Time-to-live for the data in the group, in days. Data exceeding the TTL will be deleted.
  #  - "replicas": Number of replicas for the group/stage. Replicas are used for data redundancy and high availability, a value of 0 means no replicas, while a value of 1 means one primary shard and one replica, higher values indicate more replicas.
  #
  #  For more details on setting `segmentInterval` and `ttl`, refer to the [BanyanDB TTL](https://skywalking.apache.org/docs/main/latest/en/banyandb/ttl) documentation.

  # The "records" section defines settings for normal datasets not specified in records.
  # Each dataset will be grouped under a single group named "records".
  records:
    # The settings for the default "hot" stage.
    shardNum:  ${SW_STORAGE_BANYANDB_RECORDS_SHARD_NUM:1}
    segmentInterval: ${SW_STORAGE_BANYANDB_RECORDS_SI_DAYS:1}
    ttl: ${SW_STORAGE_BANYANDB_RECORDS_TTL_DAYS:3}
    replicas: ${SW_STORAGE_BANYANDB_RECORDS_REPLICAS:0}
    # If the "warm" stage is enabled, the data will be moved to the "warm" stage after the TTL of the "hot" stage.
    # If the "cold" stage is enabled and "warm" stage is disabled, the data will be moved to the "cold" stage after the TTL of the "hot" stage.
    # If both "warm" and "cold" stages are enabled, the data will be moved to the "warm" stage after the TTL of the "hot" stage, and then to the "cold" stage after the TTL of the "warm" stage.
    # OAP will query the data from the "hot and warm" stage by default if the "warm" stage is enabled.
    enableWarmStage: ${SW_STORAGE_BANYANDB_RECORDS_ENABLE_WARM_STAGE:false}
    enableColdStage: ${SW_STORAGE_BANYANDB_RECORDS_ENABLE_COLD_STAGE:false}
    # The settings for the "warm" stage.
    warm:
      shardNum: ${SW_STORAGE_BANYANDB_RECORDS_WARM_SHARD_NUM:1}
      segmentInterval: ${SW_STORAGE_BANYANDB_RECORDS_WARM_SI_DAYS:2}
      ttl: ${SW_STORAGE_BANYANDB_RECORDS_WARM_TTL_DAYS:7}
      replicas: ${SW_STORAGE_BANYANDB_RECORDS_WARM_REPLICAS:0}
      nodeSelector: ${SW_STORAGE_BANYANDB_RECORDS_WARM_NODE_SELECTOR:"type=warm"}
    # The settings for the "cold" stage.
    cold:
      shardNum: ${SW_STORAGE_BANYANDB_RECORDS_COLD_SHARD_NUM:1}
      segmentInterval: ${SW_STORAGE_BANYANDB_RECORDS_COLD_SI_DAYS:3}
      ttl: ${SW_STORAGE_BANYANDB_RECORDS_COLD_TTL_DAYS:30}
      replicas: ${SW_STORAGE_BANYANDB_RECORDS_COLD_REPLICAS:0}
      nodeSelector: ${SW_STORAGE_BANYANDB_RECORDS_COLD_NODE_SELECTOR:"type=cold"}
  trace:
    shardNum: ${SW_STORAGE_BANYANDB_TRACE_SHARD_NUM:2}
    segmentInterval: ${SW_STORAGE_BANYANDB_TRACE_SI_DAYS:1}
    ttl: ${SW_STORAGE_BANYANDB_TRACE_TTL_DAYS:3}
    replicas: ${SW_STORAGE_BANYANDB_TRACE_REPLICAS:0}
    enableWarmStage: ${SW_STORAGE_BANYANDB_TRACE_ENABLE_WARM_STAGE:false}
    enableColdStage: ${SW_STORAGE_BANYANDB_TRACE_ENABLE_COLD_STAGE:false}
    warm:
      shardNum: ${SW_STORAGE_BANYANDB_TRACE_WARM_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_TRACE_WARM_SI_DAYS:1}
      ttl: ${SW_STORAGE_BANYANDB_TRACE_WARM_TTL_DAYS:7}
      replicas: ${SW_STORAGE_BANYANDB_TRACE_WARM_REPLICAS:0}
      nodeSelector: ${SW_STORAGE_BANYANDB_TRACE_WARM_NODE_SELECTOR:"type=warm"}
    cold:
      shardNum: ${SW_STORAGE_BANYANDB_TRACE_COLD_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_TRACE_COLD_SI_DAYS:1}
      ttl: ${SW_STORAGE_BANYANDB_TRACE_COLD_TTL_DAYS:30}
      replicas: ${SW_STORAGE_BANYANDB_TRACE_COLD_REPLICAS:0}
      nodeSelector: ${SW_STORAGE_BANYANDB_TRACE_COLD_NODE_SELECTOR:"type=cold"}
  zipkinTrace:
    shardNum: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_SHARD_NUM:2}
    segmentInterval: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_SI_DAYS:1}
    ttl: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_TTL_DAYS:3}
    replicas: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_REPLICAS:0}
    enableWarmStage: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_ENABLE_WARM_STAGE:false}
    enableColdStage: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_ENABLE_COLD_STAGE:false}
    warm:
      shardNum: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_WARM_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_WARM_SI_DAYS:1}
      ttl: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_WARM_TTL_DAYS:7}
      replicas: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_WARM_REPLICAS:0}
      nodeSelector: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_WARM_NODE_SELECTOR:"type=warm"}
    cold:
      shardNum: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_COLD_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_COLD_SI_DAYS:1}
      ttl: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_COLD_TTL_DAYS:30}
      replicas: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_COLD_REPLICAS:0}
      nodeSelector: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_COLD_NODE_SELECTOR:"type=cold"}
  recordsLog:
    shardNum: ${SW_STORAGE_BANYANDB_LOG_SHARD_NUM:2}
    segmentInterval: ${SW_STORAGE_BANYANDB_LOG_SI_DAYS:1}
    ttl: ${SW_STORAGE_BANYANDB_LOG_TTL_DAYS:3}
    replicas: ${SW_STORAGE_BANYANDB_LOG_REPLICAS:0}
    enableWarmStage: ${SW_STORAGE_BANYANDB_LOG_ENABLE_WARM_STAGE:false}
    enableColdStage: ${SW_STORAGE_BANYANDB_LOG_ENABLE_COLD_STAGE:false}
    warm:
      shardNum: ${SW_STORAGE_BANYANDB_LOG_WARM_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_LOG_WARM_SI_DAYS:1}
      ttl: ${SW_STORAGE_BANYANDB_LOG_WARM_TTL_DAYS:7}
      replicas: ${SW_STORAGE_BANYANDB_LOG_WARM_REPLICAS:0}
      nodeSelector: ${SW_STORAGE_BANYANDB_LOG_WARM_NODE_SELECTOR:"type=warm"}
    cold:
      shardNum: ${SW_STORAGE_BANYANDB_LOG_COLD_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_LOG_COLD_SI_DAYS:1}
      ttl: ${SW_STORAGE_BANYANDB_LOG_COLD_TTL_DAYS:30}
      replicas: ${SW_STORAGE_BANYANDB_LOG_COLD_REPLICAS:0}
      nodeSelector: ${SW_STORAGE_BANYANDB_LOG_COLD_NODE_SELECTOR:"type=cold"}
  recordsBrowserErrorLog:
    shardNum: ${SW_STORAGE_BANYANDB_BROWSER_ERROR_LOG_SHARD_NUM:2}
    segmentInterval: ${SW_STORAGE_BANYANDB_BROWSER_ERROR_LOG_SI_DAYS:1}
    ttl: ${SW_STORAGE_BANYANDB_BROWSER_ERROR_LOG_TTL_DAYS:3}
    replicas: ${SW_STORAGE_BANYANDB_BROWSER_ERROR_LOG_REPLICAS:0}
    enableWarmStage: ${SW_STORAGE_BANYANDB_BROWSER_ERROR_LOG_ENABLE_WARM_STAGE:false}
    enableColdStage: ${SW_STORAGE_BANYANDB_BROWSER_ERROR_LOG_ENABLE_COLD_STAGE:false}
    warm:
      shardNum: ${SW_STORAGE_BANYANDB_BROWSER_ERROR_LOG_WARM_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_BROWSER_ERROR_LOG_WARM_SI_DAYS:1}
      ttl: ${SW_STORAGE_BANYANDB_BROWSER_ERROR_LOG_WARM_TTL_DAYS:7}
      replicas: ${SW_STORAGE_BANYANDB_BROWSER_ERROR_LOG_WARM_REPLICAS:0}
      nodeSelector: ${SW_STORAGE_BANYANDB_BROWSER_ERROR_LOG_WARM_NODE_SELECTOR:"type=warm"}
    cold:
      shardNum: ${SW_STORAGE_BANYANDB_BROWSER_ERROR_LOG_COLD_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_BROWSER_ERROR_LOG_COLD_SI_DAYS:1}
      ttl: ${SW_STORAGE_BANYANDB_BROWSER_ERROR_LOG_COLD_TTL_DAYS:30}
      replicas: ${SW_STORAGE_BANYANDB_BROWSER_ERROR_LOG_COLD_REPLICAS:0}
      nodeSelector: ${SW_STORAGE_BANYANDB_BROWSER_ERROR_LOG_COLD_NODE_SELECTOR:"type=cold"}
  # The group settings of metrics.
  #
  # OAP stores metrics based its granularity.
  # Valid values are "day", "hour", and "minute". That means metrics will be stored in the three separate groups.
  # Non-"minute" are governed by the "core.downsampling" setting.
  # For example, if "core.downsampling" is set to "hour", the "hour" will be used, while "day" are ignored.
  metricsMinute:
    shardNum: ${SW_STORAGE_BANYANDB_METRICS_MINUTE_SHARD_NUM:2}
    segmentInterval: ${SW_STORAGE_BANYANDB_METRICS_MINUTE_SI_DAYS:1}
    ttl: ${SW_STORAGE_BANYANDB_METRICS_MINUTE_TTL_DAYS:7}
    replicas: ${SW_STORAGE_BANYANDB_METRICS_MINUTE_REPLICAS:0}
    enableWarmStage: ${SW_STORAGE_BANYANDB_METRICS_MINUTE_ENABLE_WARM_STAGE:false}
    enableColdStage: ${SW_STORAGE_BANYANDB_METRICS_MINUTE_ENABLE_COLD_STAGE:false}
    warm:
      shardNum: ${SW_STORAGE_BANYANDB_METRICS_MINUTE_WARM_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_METRICS_MINUTE_WARM_SI_DAYS:3}
      ttl: ${SW_STORAGE_BANYANDB_METRICS_MINUTE_WARM_TTL_DAYS:15}
      replicas: ${SW_STORAGE_BANYANDB_METRICS_MINUTE_WARM_REPLICAS:0}
      nodeSelector: ${SW_STORAGE_BANYANDB_METRICS_MINUTE_WARM_NODE_SELECTOR:"type=warm"}
    cold:
      shardNum: ${SW_STORAGE_BANYANDB_METRICS_MINUTE_COLD_SHARD_NUM:2}
      segmentInterval: ${SW_STORAGE_BANYANDB_METRICS_MINUTE_COLD_SI_DAYS:5}
      ttl: ${SW_STORAGE_BANYANDB_METRICS_MINUTE_COLD_TTL_DAYS:60}
      replicas: ${SW_STORAGE_BANYANDB_METRICS_MINUTE_COLD_REPLICAS:0}
      nodeSelector: ${SW_STORAGE_BANYANDB_METRICS_MINUTE_COLD_NODE_SELECTOR:"type=cold"}
  metricsHour:
    shardNum: ${SW_STORAGE_BANYANDB_METRICS_HOUR_SHARD_NUM:1}
    segmentInterval: ${SW_STORAGE_BANYANDB_METRICS_HOUR_SI_DAYS:5}
    ttl: ${SW_STORAGE_BANYANDB_METRICS_HOUR_TTL_DAYS:15}
    replicas: ${SW_STORAGE_BANYANDB_METRICS_HOUR_REPLICAS:0}
    enableWarmStage: ${SW_STORAGE_BANYANDB_METRICS_HOUR_ENABLE_WARM_STAGE:false}
    enableColdStage: ${SW_STORAGE_BANYANDB_METRICS_HOUR_ENABLE_COLD_STAGE:false}
    warm:
      shardNum: ${SW_STORAGE_BANYANDB_METRICS_HOUR_WARM_SHARD_NUM:1}
      segmentInterval: ${SW_STORAGE_BANYANDB_METRICS_HOUR_WARM_SI_DAYS:7}
      ttl: ${SW_STORAGE_BANYANDB_METRICS_HOUR_WARM_TTL_DAYS:30}
      replicas: ${SW_STORAGE_BANYANDB_METRICS_HOUR_WARM_REPLICAS:0}
      nodeSelector: ${SW_STORAGE_BANYANDB_METRICS_HOUR_WARM_NODE_SELECTOR:"type=warm"}
    cold:
      shardNum: ${SW_STORAGE_BANYANDB_METRICS_HOUR_COLD_SHARD_NUM:1}
      segmentInterval: ${SW_STORAGE_BANYANDB_METRICS_HOUR_COLD_SI_DAYS:15}
      ttl: ${SW_STORAGE_BANYANDB_METRICS_HOUR_COLD_TTL_DAYS:120}
      replicas: ${SW_STORAGE_BANYANDB_METRICS_HOUR_COLD_REPLICAS:0}
      nodeSelector: ${SW_STORAGE_BANYANDB_METRICS_HOUR_COLD_NODE_SELECTOR:"type=cold"}
  metricsDay:
    shardNum: ${SW_STORAGE_BANYANDB_METRICS_DAY_SHARD_NUM:1}
    segmentInterval: ${SW_STORAGE_BANYANDB_METRICS_DAY_SI_DAYS:15}
    ttl: ${SW_STORAGE_BANYANDB_METRICS_DAY_TTL_DAYS:15}
    replicas: ${SW_STORAGE_BANYANDB_METRICS_DAY_REPLICAS:0}
    enableWarmStage: ${SW_STORAGE_BANYANDB_METRICS_DAY_ENABLE_WARM_STAGE:false}
    enableColdStage: ${SW_STORAGE_BANYANDB_METRICS_DAY_ENABLE_COLD_STAGE:false}
    warm:
      shardNum: ${SW_STORAGE_BANYANDB_METRICS_DAY_WARM_SHARD_NUM:1}
      segmentInterval: ${SW_STORAGE_BANYANDB_METRICS_DAY_WARM_SI_DAYS:15}
      ttl: ${SW_STORAGE_BANYANDB_METRICS_DAY_WARM_TTL_DAYS:30}
      replicas: ${SW_STORAGE_BANYANDB_METRICS_DAY_WARM_REPLICAS:0}
      nodeSelector: ${SW_STORAGE_BANYANDB_METRICS_DAY_WARM_NODE_SELECTOR:"type=warm"}
    cold:
      shardNum: ${SW_STORAGE_BANYANDB_METRICS_DAY_COLD_SHARD_NUM:1}
      segmentInterval: ${SW_STORAGE_BANYANDB_METRICS_DAY_COLD_SI_DAYS:15}
      ttl: ${SW_STORAGE_BANYANDB_METRICS_DAY_COLD_TTL_DAYS:120}
      replicas: ${SW_STORAGE_BANYANDB_METRICS_DAY_COLD_REPLICAS:0}
      nodeSelector: ${SW_STORAGE_BANYANDB_METRICS_DAY_COLD_NODE_SELECTOR:"type=cold"}
  # If the metrics is marked as "index_mode", the metrics will be stored in the "metadata" group.
  # The "metadata" group is designed to store metrics that are used for indexing without value columns.
  # Such as `service_traffic`, `network_address_alias`, etc.
  # "index_mode" requires BanyanDB *0.8.0* or later.
  metadata:
    shardNum: ${SW_STORAGE_BANYANDB_METADATA_SHARD_NUM:2}
    segmentInterval: ${SW_STORAGE_BANYANDB_METADATA_SI_DAYS:15}
    ttl: ${SW_STORAGE_BANYANDB_METADATA_TTL_DAYS:15}
    replicas: ${SW_STORAGE_BANYANDB_METADATA_REPLICAS:0}

  # The group settings of property, such as UI and profiling.
  property:
    shardNum: ${SW_STORAGE_BANYANDB_PROPERTY_SHARD_NUM:1}
    replicas: ${SW_STORAGE_BANYANDB_PROPERTY_REPLICAS:0}

```
### TopN Rules Configuration
The BanyanDB storage supports TopN pre-aggregation in the BanyanDB server side, which trades off more disk_volume for pre-aggregation to save CPU cost, and perform faster query in the query stage. 
You can define the TopN rules for different metrics. The configuration is defined in the `bydb-topn.yaml` file:

```yaml
# This file is used to configure the TopN rules for BanyanDB in SkyWalking OAP server.
# The rules define how to aggregate and sort `metrics (Measure)` for services, endpoints, and instances.
#
# - name: Required. The name of the TopN rule, uniquely identifies the rule.
# - metricName: Required. The name of the metric to be aggregated.
# - groupByTagNames: Optional, default `[]`. The tag names to group the metrics by. If not specified, the metrics will sort without grouped.
# - countersNumber: Optional, default `1000`. The max size of entries in a time window for the pre-aggregation.

# The size of LRU determines the maximally tolerated time range.
# The buffers in the time range are kept in the memory so that
# the data in [T - lruSize * n, T] would be accepted in the pre-aggregation process.
# T = the current time in the current dimensionality.
# n = interval in the current dimensionality.
# - lruSizeMinute: Optional, default `10`. Defines how many time_buckets are held in the memory for minute-level metrics.
# - lruSizeHourDay: Optional, default `2`. Defines how many time_buckets are held in the memory for hour and day-level metrics.

# - sort: Optional, default `all`. The sorting order for the metrics, asc, des or all(include both asc and des).
# - excludes: Optional, default `[]`. The tag values to be excluded from the candidates. If specified, the candidates will not include the entries with the specified tag values.

TopN-Rules:
   # endpoint metrics
   # `attr0` is defined in the `EndpointDecorator` as the Layer.
  - name: endpoint_cpm
    metricName: endpoint_cpm
    sort: des
  - name: endpoint_cpm-layer
    metricName: endpoint_cpm
    groupByTagNames:
      - attr0
    sort: des
  - name: endpoint_cpm-service
    metricName: endpoint_cpm
    groupByTagNames:
      - service_id
    sort: des
  - name: endpoint_sla
    metricName: endpoint_sla
    sort: asc
  - name: endpoint_sla-layer
    metricName: endpoint_sla
    groupByTagNames:
      - attr0
    sort: asc
  - name: endpoint_sla-service
    metricName: endpoint_sla
    groupByTagNames:
      - service_id
    sort: asc
  - name: endpoint_resp_time
    metricName: endpoint_resp_time
    sort: des
  - name: endpoint_resp_time-layer
    metricName: endpoint_resp_time
    groupByTagNames:
      - attr0
    sort: des
  - name: endpoint_resp_time-service
    metricName: endpoint_resp_time
    groupByTagNames:
      - service_id
    sort: des
  # browser_app_page_pv metrics
  - name: browser_app_page_pv-service
    metricName: browser_app_page_pv
    groupByTagNames:
      - service_id
    sort: des
  - name: browser_app_page_error_sum-service
    metricName: browser_app_page_error_sum
    groupByTagNames:
      - service_id
    sort: des
  - name: browser_app_page_error_rate-service
    metricName: browser_app_page_error_rate
    groupByTagNames:
      - service_id
    sort: des
# The following rule can be used to filter out the mesh endpoints.
# You MUST add `attr0!= MESH` to the MQE topN query to hit this rule.
#  - name: endpoint_cpm-service
#    metricName: endpoint_cpm
#    groupByTagNames:
#      - service_id
#    sort: des
#    excludes:
#      - tag: attr0
#        value: MESH
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

### Benchmark

This benchmark was conducted on a Kubernetes cluster with a single node (16 cores, 64GB RAM), deploying a complete BanyanDB cluster with lifecycle support using the [skywalking-banyandb-helm chart](https://github.com/apache/skywalking-banyandb-helm). 
Each BanyanDB Pod is configured with resource limits of 2 cores and 4GB memory. The benchmark represents 3 days of continuous data ingestion, with all resource usage and query performance metrics captured after this period. The specific cluster configuration is as follows:

```yaml
cluster:
  enabled: true
  liaison:
    replicas: 2
    env:
      - name: BYDB_MEASURE_WRITE_TIMEOUT
        value: "1m"
      - name: BYDB_STREAM_WRITE_TIMEOUT
        value: "1m"
      - name: BYDB_TRACE_WRITE_TIMEOUT
        value: "1m"
      - name: BYDB_STREAM_FLUSH_TIMEOUT
        value: "0s"
    resources:
      requests:
        - key: cpu
          value: 2
        - key: memory
          value: 4Gi
      limits:
        - key: cpu
          value: 2
        - key: memory
          value: 4Gi
  data:
    nodeTemplate:
      replicas: 2
      resources:
        requests:
          - key: cpu
            value: 2
          - key: memory
            value: 4Gi
        limits:
          - key: cpu
            value: 2
          - key: memory
            value: 4Gi

storage:
  data:
    enabled: true
    persistentVolumeClaims:
      - mountTargets: [ "measure" ]
        nodeRole: hot
        existingClaimName: null
        claimName: hot-measure-data
        size: 50Gi
        accessModes:
          - ReadWriteOnce
        storageClass: null
        volumeMode: Filesystem
      - mountTargets: [ "stream" ]
        nodeRole: hot
        existingClaimName: null
        claimName: hot-stream-data
        size: 100Gi
        accessModes:
          - ReadWriteOnce
        storageClass: null
        volumeMode: Filesystem
      - mountTargets: [ "property" ]
        nodeRole: hot
        existingClaimName: null
        claimName: hot-property-data
        size: 5Gi
        accessModes:
          - ReadWriteOnce
        storageClass: null
        volumeMode: Filesystem
      - mountTargets: [ "trace" ]
        nodeRole: hot
        existingClaimName: null
        claimName: hot-trace-data
        size: 500Gi
        accessModes:
          - ReadWriteOnce
        storageClass: null
        volumeMode: Filesystem
```

#### Measure

The test involves 852 services, 5,640 instances, and 9,000 endpoints. These entities produce over 571,000 metric data points per minute for ingestion, with some metrics from identical entities written every 30 seconds.

The following graphs illustrate the resource usage during write operations, showing CPU and memory consumption across the BanyanDB cluster:

![Measure Write Basic Usage](https://skywalking.apache.org/doc-graph/banyandb/v0.9.0/benchmark/measure-write-basic-usage.jpg)
![Measure Write Detail Usage](https://skywalking.apache.org/doc-graph/banyandb/v0.9.0/benchmark/measure-write-detail-usage.jpg)

During concurrent write operations, queries were executed against the most recent 15 minutes of data with 50 concurrent requests. The query performance metrics per request are as follows:

Per Request min, max, mean, median, p90, p95, p98, p99 duration (milliseconds): 1.312920, 618.915122, 48.609702, 26.664489, 118.913909, 171.488923, 229.585007, 288.611386

The graphs below show resource utilization during combined read and write operations, demonstrating BanyanDB's ability to handle concurrent workloads:

![Measure Read and Write Basic Usage](https://skywalking.apache.org/doc-graph/banyandb/v0.9.0/benchmark/measure-write-read-basic-usage.jpg)
![Measure Read and Write Detail Usage](https://skywalking.apache.org/doc-graph/banyandb/v0.9.0/benchmark/measure-write-read-detail-usage.jpg)

#### Stream

The test involves 54 services and 1,080 instances writing stream data, generating 151,000 stream records per minute.

The graphs below display resource usage and index performance during stream write operations:

![Stream Write Basic Usage](https://skywalking.apache.org/doc-graph/banyandb/v0.9.0/benchmark/stream-write-basic-usage.jpg)
![Stream Write Detail Usage](https://skywalking.apache.org/doc-graph/banyandb/v0.9.0/benchmark/stream-write-detail-usage.jpg)
![Stream Write Index Usage](https://skywalking.apache.org/doc-graph/banyandb/v0.9.0/benchmark/stream-write-index-usage.jpg)

During concurrent write operations, queries were executed to retrieve stream data from the most recent 15 minutes. The stream query performance per request is as follows:

Per Request min, max, mean, median, p90, p95, p98, p99 duration (milliseconds): 3.394290, 237.884627, 10.186698, 7.443185, 13.951209, 21.687960, 49.968980, 72.631009

The following graphs show resource usage during combined read and write operations for stream data:

![Stream Read and Write Basic Usage](https://skywalking.apache.org/doc-graph/banyandb/v0.9.0/benchmark/stream-write-read-basic-usage.jpg)
![Stream Read and Write Detail Usage](https://skywalking.apache.org/doc-graph/banyandb/v0.9.0/benchmark/stream-write-read-detail-usage.jpg)

#### Trace

The test involves 48 services and 1,800 instances writing trace data, generating 6,600 traces within 133,200 spans per minute.

The graphs below illustrate resource consumption during trace write operations:

![Trace Write Basic Usage](https://skywalking.apache.org/doc-graph/banyandb/v0.9.0/benchmark/trace-write-basic-usage.jpg)
![Trace Write Detail Usage](https://skywalking.apache.org/doc-graph/banyandb/v0.9.0/benchmark/trace-write-detail-usage.jpg)

During concurrent write operations, three different query patterns were executed against the most recent 15 minutes of trace data. The trace query performance per request is as follows:

Per Request min, max, mean, median, p90, p95, p98, p99 duration (milliseconds): 37.750859, 1249.673034, 472.815065, 436.212444, 931.519287, 1007.228856, 1043.850870, 1097.840105

The following graphs show resource usage during combined read and write operations for trace data:

![Trace Read and Write Basic Usage](https://skywalking.apache.org/doc-graph/banyandb/v0.9.0/benchmark/trace-write-read-basic-usage.jpg)
![Trace Read and Write Detail Usage](https://skywalking.apache.org/doc-graph/banyandb/v0.9.0/benchmark/trace-write-read-detail-usage.jpg)

#### Summary

The benchmark demonstrates BanyanDB's ability to handle high-throughput observability workloads efficiently. Over a sustained 3-day period, the cluster successfully ingested over 571,000 metric data points, 151,000 stream records, and 133,200 trace spans per minute while maintaining stable resource utilization with each pod limited to just 2 cores and 4GB memory.

Query performance remained responsive even under concurrent read/write workloads. Measure queries showed median latency of 26ms with p99 at 288ms, while stream queries achieved even better performance with median latency of 7ms and p99 at 72ms. Trace queries, which are typically more complex, showed median latency of 436ms with p99 at approximately 1.1 seconds.

The resource graphs demonstrate consistent CPU and memory usage throughout the test period, indicating efficient resource management and the absence of memory leaks or performance degradation over time. BanyanDB's ability to handle concurrent read and write operations without significant resource spikes makes it well-suited for production observability workloads.

These results were achieved on a modest single-node setup (16 cores, 64GB RAM), suggesting that BanyanDB can deliver strong performance even in resource-constrained environments. For larger deployments, the cluster mode with multiple liaison and data nodes can provide additional scalability and throughput.

For more details, refer to the documentation of [BanyanDB](https://skywalking.apache.org/docs/skywalking-banyandb/latest/readme/) and the [BanyanDB Java Client](https://github.com/apache/skywalking-banyandb-java-client) subprojects.
