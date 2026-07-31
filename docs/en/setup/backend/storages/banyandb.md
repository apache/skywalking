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
  sslTrustCAPath: ${SW_STORAGE_BANYANDB_SSL_TRUST_CA_PATH:""}
  # Cleanup TopN rules in BanyanDB server that are not configured in the bydb-topn.yml config.
  cleanupUnusedTopNRules: ${SW_STORAGE_BANYANDB_CLEANUP_UNUSED_TOPN_RULES:true}
  # The namespace in BanyanDB to store the data of OAP, if not set, the default is "sw".
  # OAP will create BanyanDB Groups using the format of "{namespace}_{group name}", such as "sw_records".
  namespace: ${SW_NAMESPACE:"sw"}
  # The compatible server API versions of BanyanDB.
  # The compatible BanyanDB Server version number can be found via the [API versions mapping](https://skywalking.apache.org/docs/skywalking-banyandb/latest/installation/versions/).
  compatibleServerApiVersions: ${SW_STORAGE_BANYANDB_COMPATIBLE_SERVER_API_VERSIONS:"0.10"}

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
      mergeGraceSeconds: ${SW_STORAGE_BANYANDB_TRACE_PIPELINE_MERGE_GRACE_SECONDS:-1}
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
      mergeGraceSeconds: ${SW_STORAGE_BANYANDB_ZIPKIN_TRACE_PIPELINE_MERGE_GRACE_SECONDS:-1}
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

### Trace retention pipeline
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
| `enabled` | Activates the pipeline for this group. |
| `enabledEvents` | When the chain runs: `PIPELINE_EVENT_MERGE` (during compaction) and/or `PIPELINE_EVENT_FINALIZE` (once a segment has settled). Accepts a comma-separated string, so it can be set from the environment (`SW_STORAGE_BANYANDB_TRACE_PIPELINE_ENABLED_EVENTS`, and the `ZIPKIN_` variant), or a YAML block list in the file. An empty value falls back to MERGE for backward compatibility, so FINALIZE runs only when named explicitly. |
| `mergeGraceSeconds` | Maturity window before a trace may be dropped at merge, so traces whose remaining spans may still arrive are not destroyed prematurely. `-1` means "not set here" and uses the data node default (30s). |
| `finalizeGraceSeconds` | Settling window for the finalization pass. `-1` uses the data node default (5m). |

Only a **positive** grace overrides the data node default. `0` is not "no grace": the data node
treats any non-positive value as unset, so zero grace can only be set with the node's
`-trace-pipeline-merge-grace-default=0` flag. Do not leave these keys blank — a blank YAML value
fails OAP startup.
| `plugins[].path` | The `.so` filename, resolved inside the trusted plugin directory. |
| `plugins[].config` | Passed verbatim to the plugin constructor; the engine does not interpret the keys. The first-party samplers **reject an unrecognized key** rather than ignoring it — every option they have is a keep rule, so a key that silently missed would leave a sampler that drops the whole group. Note the reference `_example` plugin uses `snake_case` names; these two use `camelCase`, so a config copied from it is refused outright instead of quietly retaining nothing. |

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

#### How each rule is evaluated

Both samplers share one engine and differ only in which columns each schema stores the
inputs in. Their source and the authoritative behavioral reference live in
[`plugins/README.md`](https://github.com/apache/skywalking-banyandb/blob/main/plugins/README.md)
in the BanyanDB repository:

| Input | `sw-trace-sampler` | `zipkin-trace-sampler` |
|---|---|---|
| Searchable tags | `tags` | `query` |
| Error signal | `is_error` column | `error` key inside `query` |
| Per-row start | `start_time` | `timestamp_millis` |
| Per-row duration | `latency` (milliseconds) | `duration` (**micro**seconds) |

Despite its name, `timestamp_millis` is stored as a BanyanDB timestamp column, so both
schemas supply the start time in **nanoseconds**. Only the duration units differ, and the
plugin scales them internally — `durationThresholdMs` is always milliseconds on both.

**`durationThresholdMs`** is measured against the trace's **end-to-end envelope**, not
against any single span:

```
envelope = max(start + duration) − min(start)     over every row of the trace
keep     if envelope ≥ durationThresholdMs
```

This is the distinction that matters when choosing a value: three chained 400 ms calls have
no span above 400 ms, but an envelope of 1.2 s, so `durationThresholdMs: 1000` keeps that
trace. A max-span-duration test would miss it. The envelope is also *not* BanyanDB's
`MaxTS − MinTS`, which is the spread of per-row **start** timestamps — that ignores how long
the final span ran and is `0` for a single-span trace.

**`healthySampleRate`** hashes the trace ID with FNV-1a and keeps the trace when the hash,
mapped into `[0,1)`, falls below the rate. Because it depends only on the trace ID, the same
trace gets the same answer every time it is re-evaluated — at merge and again at
finalization — so a partially written trace is never half-kept. `1.0` keeps every trace and
`0` keeps none.

**Evaluation order** is duration, then errors, then tag rules, then the healthy sample. The rules are OR-ed, so the first match wins and the rest are skipped;
ordering therefore affects only cost, never the verdict.

**When a rule cannot be evaluated, the trace is kept.** If the duration or error columns
are absent the sampler answers "can't tell", not "not slow" / "no error" — those columns are
part of the schema, so their absence means the block was written under a different one
(usually the wrong plugin attached to the group), and dropping there would silently discard
exactly what you configured it to keep.

Two cases are deliberately *not* treated this way, because they are ordinary data rather
than a schema mismatch: a trace carrying no searchable tags simply matches no tag rule, and
an error column that is present but not truthy really does mean "not an error". Note also
that Zipkin's `duration` is optional in the Zipkin model — a span with a start but no
duration still anchors the start of the trace's envelope, and a trace whose spans all omit
it is kept rather than measured.

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
