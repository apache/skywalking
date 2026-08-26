## BanyanDB

[BanyanDB](https://github.com/apache/skywalking-banyandb) is a dedicated storage implementation developed by the SkyWalking Team and the community. Activate BanyanDB as the storage by setting the storage provider to **banyandb**.

The BanyanDB server compatibility relies on API and release versions,
The compatible BanyanDB API version number could be found in `/config/bydb.yml` 
```
${SW_STORAGE_BANYANDB_COMPATIBLE_SERVER_API_VERSIONS}
```
The compatible BanyanDB Server version number can be found via the [API versions mapping](https://skywalking.apache.org/docs/skywalking-banyandb/latest/installation/versions/).

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
  # Also the row cap sent for any query that has no limit of its own, so that a query never falls back to
  # BanyanDB's own default (100 rows for measures, 20 for streams/traces), which truncates results silently.
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
  # This file is watched: rotating it rebuilds the gRPC channel so the new CA is used, without restarting the OAP.
  sslTrustCAPath: ${SW_STORAGE_BANYANDB_SSL_TRUST_CA_PATH:""}
  # Secrets management file in the properties format, including the user and password, which are managed by a 3rd party tool.
  # When set, it overrides the user/password above, and a rotation of the file is applied without restarting the OAP.
  secretsManagementFile: ${SW_STORAGE_BANYANDB_SECRETS_MANAGEMENT_FILE:""}
  # Cleanup TopN rules in BanyanDB server that are not configured in the bydb-topn.yml config.
  cleanupUnusedTopNRules: ${SW_STORAGE_BANYANDB_CLEANUP_UNUSED_TOPN_RULES:true}
  # The namespace in BanyanDB to store the data of OAP, if not set, the default is "sw".
  # OAP will create BanyanDB Groups using the format of "{namespace}_{group name}", such as "sw_records".
  namespace: ${SW_NAMESPACE:"sw"}
  # The compatible server API versions of BanyanDB.
  # The compatible BanyanDB Server version number can be found via the [API versions mapping](https://skywalking.apache.org/docs/skywalking-banyandb/latest/installation/versions/).
  compatibleServerApiVersions: ${SW_STORAGE_BANYANDB_COMPATIBLE_SERVER_API_VERSIONS:"0.11"}

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
      segmentInterval: ${SW_STORAGE_BANYANDB_RECORDS_COLD_SI_DAYS:4}
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
    # Group-scoped trace retention pipeline (sampler plugins). See "Trace retention pipeline" below.
    pipeline:
      enabled: ${SW_STORAGE_BANYANDB_TRACE_PIPELINE_ENABLED:false}
      enabledEvents: ${SW_STORAGE_BANYANDB_TRACE_PIPELINE_ENABLED_EVENTS:PIPELINE_EVENT_MERGE}
      mergeGraceSeconds: ${SW_STORAGE_BANYANDB_TRACE_PIPELINE_MERGE_GRACE_SECONDS:1800}
      finalizeGraceSeconds: ${SW_STORAGE_BANYANDB_TRACE_PIPELINE_FINALIZE_GRACE_SECONDS:-1}
      plugins:
        - name: sw-trace-sampler
          path: ${SW_STORAGE_BANYANDB_TRACE_SAMPLER_SO:sw-trace-sampler.so}
          abiVersion: 1
          config:
            durationThresholdMs: ${SW_STORAGE_BANYANDB_TRACE_SAMPLER_DURATION_THRESHOLD_MS:500}
            keepErrors: ${SW_STORAGE_BANYANDB_TRACE_SAMPLER_KEEP_ERRORS:true}
            healthySampleRate: ${SW_STORAGE_BANYANDB_TRACE_SAMPLER_HEALTHY_SAMPLE_RATE:0.1}
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
    # The Zipkin schema has no is_error column, so keepErrors here detects Zipkin's
    # conventional "error" span tag inside the flattened "query" attributes.
    pipeline:
      enabled: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_PIPELINE_ENABLED:false}
      enabledEvents: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_PIPELINE_ENABLED_EVENTS:PIPELINE_EVENT_MERGE}
      mergeGraceSeconds: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_PIPELINE_MERGE_GRACE_SECONDS:1800}
      finalizeGraceSeconds: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_PIPELINE_FINALIZE_GRACE_SECONDS:-1}
      plugins:
        - name: zipkin-trace-sampler
          path: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_SAMPLER_SO:zipkin-trace-sampler.so}
          abiVersion: 1
          config:
            durationThresholdMs: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_SAMPLER_DURATION_THRESHOLD_MS:1000}
            keepErrors: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_SAMPLER_KEEP_ERRORS:true}
            healthySampleRate: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_SAMPLER_HEALTHY_SAMPLE_RATE:0.05}
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
      segmentInterval: ${SW_STORAGE_BANYANDB_METRICS_MINUTE_COLD_SI_DAYS:6}
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
      segmentInterval: ${SW_STORAGE_BANYANDB_METRICS_HOUR_WARM_SI_DAYS:10}
      ttl: ${SW_STORAGE_BANYANDB_METRICS_HOUR_WARM_TTL_DAYS:30}
      replicas: ${SW_STORAGE_BANYANDB_METRICS_HOUR_WARM_REPLICAS:0}
      nodeSelector: ${SW_STORAGE_BANYANDB_METRICS_HOUR_WARM_NODE_SELECTOR:"type=warm"}
    cold:
      shardNum: ${SW_STORAGE_BANYANDB_METRICS_HOUR_COLD_SHARD_NUM:1}
      segmentInterval: ${SW_STORAGE_BANYANDB_METRICS_HOUR_COLD_SI_DAYS:20}
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

### Secrets Management File Of BanyanDB Authentication
The value of `secretsManagementFile` should point to the absolute path of the secrets management file.
The file includes the user and password of the BanyanDB server in the properties format.
```properties
user=xxx
password=yyy
```

The difference from setting `user` and `password` directly in `bydb.yml` is that the **Secrets Management File** is
watched by the OAP server. Once it is changed manually or through a 3rd party tool, such as
[Vault](https://github.com/hashicorp/vault), the new credentials are attached to the following requests, and the
`user/password` in `bydb.yml` are overridden. The gRPC connection is not re-established, so rotating the credentials
does not interrupt in-flight queries or writes.

Whatever the file contains is applied, including a file that carries only one of the two entries, or none. This is
deliberate: a mistake in the file then shows up at the next request instead of being quietly refused, which would
leave the previous credentials working and the mistake unnoticed until the OAP is next restarted.

A username and a password are only ever sent together, so an incomplete file is applied as no credentials at all:
requests go out unauthenticated and BanyanDB answers them with `UNAUTHENTICATED`, and it never authenticates as the
named user with the wrong password. Both halves are cleared together, so a file that is only briefly incomplete
while a 3rd party tool rewrites it cannot stop the OAP from starting — it behaves the same whether it is read at
boot or while the OAP is running. Note this also clears any `user`/`password` set in `bydb.yml`, since the file
overrides them. The OAP logs an error naming the file when it applies an incomplete pair:

```text
Applied incomplete credentials from /etc/skywalking/bydb-secrets.properties: requests are now sent without
authentication, ...
```

Writing a complete pair back to the file restores service without a restart.

Only the credentials are reloaded this way. For the TLS trust CA, see
[Reloading The TLS Trust CA](#reloading-the-tls-trust-ca).

### Reloading The TLS Trust CA

The file at `sslTrustCAPath` is watched by the OAP server, separately from `secretsManagementFile`. Overwriting it —
manually, or through a tool such as [cert-manager](https://cert-manager.io) — rebuilds the gRPC channel so that the
new CA is used. No restart is needed.

The trust CA cannot be swapped in place the way the credentials can, because it is resolved when the channel is
built rather than per request. Rebuilding the channel is therefore what a rotation costs, and it behaves as follows:

- The change is detected within 10 seconds, and the replacement channel is created before the old one is released,
  so a failure to build it leaves the current channel serving with the previous CA. That case is logged as an error.
- Requests already in flight finish on the old channel; new requests go to the replacement. The replacement is
  **not** verified before it is swapped in: a gRPC channel connects lazily, so the TLS handshake only happens on
  the first request that uses it. A file that parses but does not validate the server therefore replaces a healthy
  connection, and queries and writes start failing until material that does validate is written back. Rotating to
  a CA the server's certificate chains to is interruption-free; rotating to the wrong one is not, and recovers
  only on the next rotation.

  To rotate with no interruption at all, write a PEM holding both the current and the new CA, confirm the OAP is
  healthy, and only then let the server switch and drop the old CA at a later rotation. A certificate collection
  in one PEM file is supported.
- The replacement picks a target from `targets` again, so with more than one BanyanDB address configured the OAP may
  end up connected to a different node after a rotation. This is harmless, but worth knowing when correlating a
  rotation with a change of peer in the logs.

A successful rotation is logged as:

```text
Rebuilt the BanyanDB channel to reload the TLS trust CA from /etc/skywalking/bydb-ca.crt
```

Two limitations to keep in mind:

- The file is only watched when `sslTrustCAPath` is set at startup. Populating a path that was empty when the OAP
  booted does not enable TLS at runtime; that still requires a restart.
- TLS is enabled by the presence of the file, so a path that does not point to one would leave the connection on
  plaintext. Rather than degrade silently, building the channel fails:

  ```text
  BanyanDB TLS trust CA path /etc/skywalking/bydb-ca.crt does not point to a file. Fix the path, or unset
  sslTrustCAPath to connect without TLS.
  ```

  At startup that stops the OAP. If the file disappears while it is running, the rebuild fails the same way and
  is logged, and the channel already in use keeps serving with the trust material it was built with.
- Only the trust CA is configurable. There is no client-certificate (mutual TLS) option for BanyanDB, so there is
  no client keystore to reload.

Independently of this watch, the channel is also rebuilt in reaction to failures: a request that fails with a
network-class gRPC status (`UNAVAILABLE`, `UNKNOWN`, `RESOURCE_EXHAUSTED`, `PERMISSION_DENIED`, `UNAUTHENTICATED`)
flags the channel, and it is replaced within 30 seconds if it is still not `READY`. That is the client's failover
path across `targets`, and it re-reads the CA as a side effect.

### Trace retention pipeline

> This section is the configuration reference. For how a trace is actually judged — the rule
> chain, the duration envelope, the sampling hash, and what happens when a plugin is missing —
> see [Trace Tail Sampling](../../../banyandb/tail-sampling.md).
The `trace` and `zipkinTrace` groups may run a **sampler plugin inside the BanyanDB data node**,
which drops traces during Hot-phase LSM compaction. Unlike
[server-side trace sampling](../trace-sampling.md), which decides at ingest and prevents writes,
this runs *after* storage: it reclaims space from data already written, and its verdict is per
whole trace, so all of a trace's segments (or spans) are seen together and kept or dropped as a
unit. It is disabled by default (`enabled: false`).

Each data node must run with `-trace-pipeline-native-plugin-enabled=true` and
`-trace-pipeline-trusted-plugin-dir` pointing at a directory holding the sampler `.so`.

| Setting | Meaning |
|---|---|
| `enabled` | Activates the pipeline for this group. **Off by default** — this deletes stored traces, so it is opt-in. It also only has any effect on a data node running the plugin-capable image with `-trace-pipeline-native-plugin-enabled=true` and the sampler `.so` present in its trusted directory; elsewhere the config is pushed and ignored, and merges run unfiltered. |
| `enabledEvents` | When the chain runs: `PIPELINE_EVENT_MERGE` (during compaction) and/or `PIPELINE_EVENT_FINALIZE` (once a segment has settled). Accepts a comma-separated string, so it can be set from the environment (`SW_STORAGE_BANYANDB_TRACE_PIPELINE_ENABLED_EVENTS`, and the `ZIPKIN_` variant), or a YAML block list in the file. An empty value falls back to MERGE for backward compatibility, so FINALIZE runs only when named explicitly. |
| `mergeGraceSeconds` | Maturity window before a trace may be dropped at merge, so traces whose remaining spans may still arrive are not destroyed prematurely. Ships as **1800** (30 minutes): a trace is usually read soon after it is written, so the data node's own 30s default would let the sampler drop traces still being looked at. Set `-1` to hand the decision back to the node. |
| `finalizeGraceSeconds` | Settling window for the finalization pass. `-1` uses the data node default (5m). |
| `plugins[].path` | The `.so` filename, resolved inside the trusted plugin directory. |
| `plugins[].config` | Passed verbatim to the plugin constructor; the engine does not interpret the keys. The first-party samplers **reject an unrecognized key** rather than ignoring it — every option they have is a keep rule, so a key that silently missed would leave a sampler that drops the whole group. Note the reference `_example` plugin uses `snake_case` names; these two use `camelCase`, so a config copied from it is refused outright instead of quietly retaining nothing. |

Only a **positive** grace overrides the data node default. `0` is not "no grace": the data node
treats any non-positive value as unset, so zero grace can only be set with the node's
`-trace-pipeline-merge-grace-default=0` flag. A blank value is treated as unset and leaves the
field at its default, the same as omitting the line.

The first-party samplers accept:

| Config key | Meaning |
|---|---|
| `durationThresholdMs` | Keep a trace whose end-to-end duration reaches this many milliseconds. `0` disables the rule. |
| `keepErrors` | Keep a trace that carries an error. `sw-trace-sampler` reads the `is_error` column. `zipkin-trace-sampler` has no such column, so it detects Zipkin's conventional `error` span tag inside the flattened `query` attributes — a tag convention, so failures signalled only by `http.status_code` 5xx or `otel.status_code` need an explicit rule instead. **On Zipkin it also misses long errors**: OAP drops both the bare key and `key=value` from `query` when either exceeds 256 characters, so an `error` tag carrying a long exception message is invisible to this option. Catch those with a `keepTagRules` entry on a short-valued tag. |
| `healthySampleRate` | Fraction (0–1) of the remaining traces to keep, chosen by a deterministic hash of the trace ID. `0` keeps none of them. |
| `keepTagRules` | Sure-keep rules matched against the searchable tags: a list of `{tagKey, exists\|equals\|in\|regex}`. Empty list = no tag rules. An `exists` rule matches the key in either stored form — the bare key or `key=value` — using an exact comparison, so `exists` on `error` is not satisfied by `error_rate`. |
| `errorTag` | Overrides which tag `keepErrors` reads, for instrumentation that signals failure through a non-standard tag. Defaults to `is_error` for `sw-trace-sampler` and `error` for `zipkin-trace-sampler`; ignored unless `keepErrors` is `true`. |

A `tagKey` may only name a **searchable tag**, because all of them are flattened into one
array column (`tags` for segments, `query` for Zipkin). A rule naming a first-class column
such as `service_id` or `local_endpoint_service_name` can never match. Each sampler carries
its model's full column list and rejects such a rule at startup rather than let it silently
never fire — so `{tagKey: service_id, equals: ...}`, the natural way to write "keep
everything from the payment service", fails loudly instead of dropping exactly those traces.
"Keep everything from service X" has to key off a tag the instrumentation actually emits.

If a searchable tag legitimately shares a name with a column — a Zipkin span tag called
`duration`, say — match it through the array column itself, which sees the raw
`key=value` entries: `{tagKey: query, regex: "^duration="}`.

`keepTagRules` is a list of objects, so an environment override must be a **one-line YAML/JSON
flow sequence**:

```shell
SW_STORAGE_BANYANDB_TRACE_SAMPLER_KEEP_TAG_RULES='[{tagKey: db.type, equals: PostgreSQL},{tagKey: mq.queue, equals: queue-songs-ping}]'
SW_STORAGE_BANYANDB_ZIPKIN_TRACE_SAMPLER_KEEP_TAG_RULES='[{tagKey: query, regex: "http\.status_code=5\d\d"}]'
```

Nested values work too (`{tagKey: http.method, in: [GET, POST]}`). To set the rules in the file
instead, replace the whole `${...}` placeholder with a normal block list. If you write an inline
flow sequence *inside* `bydb.yml`, quote it — the `": "` in it would otherwise be parsed as a
nested mapping.

A trace is kept when **any** rule matches; otherwise the `healthySampleRate` hash decides.

Expect a CPU cost on merge. BanyanDB can normally copy a single-block trace through a merge
as raw bytes without decoding it, but a sampler that projects any tag — which every useful
config does — disables that fast path, so each block is decoded in full during merges that
run the filter. Budget for it before enabling the pipeline on a busy cluster; the reward is
that dropped traces are never re-written, reclaiming the space instead of leaving tombstones.
Note that this composes with ingest-side sampling — enabling both multiplies the drop rate; see
[Trace Sampling at server side](../trace-sampling.md).

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

For more details, refer to the documentation of [BanyanDB](https://skywalking.apache.org/docs/skywalking-banyandb/latest/readme/).
