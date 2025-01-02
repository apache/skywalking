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
# Each `bydb.api.version` could have multiple compatible release version(`bydb.version`).
bydb.api.version=x.y
```

### Configuration

```yaml
storage:
  banyandb:
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
    # If the BanyanDB server is configured with TLS, configure the TLS cert file path and enable TLS connection.
    sslTrustCAPath: ${SW_STORAGE_BANYANDB_SSL_TRUST_CA_PATH:""}
    # The group settings of record.
    # `gr` is the short name of the group settings of record.
    #
    # The "normal" section defines settings for datasets not specified in "super".
    # Each dataset will be grouped under a single group named "normal".
    grNormalShardNum: ${SW_STORAGE_BANYANDB_GR_NORMAL_SHARD_NUM:1}
    grNormalSIDays: ${SW_STORAGE_BANYANDB_GR_NORMAL_SI_DAYS:1}
    grNormalTTLDays: ${SW_STORAGE_BANYANDB_GR_NORMAL_TTL_DAYS:3}
    # "super" is a special dataset designed to store trace or log data that is too large for normal datasets.
    # Each super dataset will be a separate group in BanyanDB, following the settings defined in the "super" section.
    grSuperShardNum: ${SW_STORAGE_BANYANDB_GR_SUPER_SHARD_NUM:2}
    grSuperSIDays: ${SW_STORAGE_BANYANDB_GR_SUPER_SI_DAYS:1}
    grSuperTTLDays: ${SW_STORAGE_BANYANDB_GR_SUPER_TTL_DAYS:3}
    # The group settings of metrics.
    # `gm` is the short name of the group settings of metrics.
    #
    # OAP stores metrics based its granularity.
    # Valid values are "day", "hour", and "minute". That means metrics will be stored in the three separate groups.
    # Non-"minute" are governed by the "core.downsampling" setting.
    # For example, if "core.downsampling" is set to "hour", the "hour" will be used, while "day" are ignored.
    gmMinuteShardNum: ${SW_STORAGE_BANYANDB_GM_MINUTE_SHARD_NUM:2}
    gmMinuteSIDays: ${SW_STORAGE_BANYANDB_GM_MINUTE_SI_DAYS:1}
    gmMinuteTTLDays: ${SW_STORAGE_BANYANDB_GM_MINUTE_TTL_DAYS:7}
    gmHourShardNum: ${SW_STORAGE_BANYANDB_GM_HOUR_SHARD_NUM:1}
    gmHourSIDays: ${SW_STORAGE_BANYANDB_GM_HOUR_SI_DAYS:5}
    gmHourTTLDays: ${SW_STORAGE_BANYANDB_GM_HOUR_TTL_DAYS:15}
    gmDayShardNum: ${SW_STORAGE_BANYANDB_GM_DAY_SHARD_NUM:1}
    gmDaySIDays: ${SW_STORAGE_BANYANDB_GM_DAY_SI_DAYS:15}
    gmDayTTLDays: ${SW_STORAGE_BANYANDB_GM_DAY_TTL_DAYS:15}
    # If the metrics is marked as "index_mode", the metrics will be stored in the "index" group.
    # The "index" group is designed to store metrics that are used for indexing without value columns.
    # Such as `service_traffic`, `network_address_alias`, etc.
    # "index_mode" requires BanyanDB *0.8.0* or later.
    gmIndexShardNum: ${SW_STORAGE_BANYANDB_GM_INDEX_SHARD_NUM:2}
    gmIndexSIDays: ${SW_STORAGE_BANYANDB_GM_INDEX_SI_DAYS:15}
    gmIndexTTLDays: ${SW_STORAGE_BANYANDB_GM_INDEX_TTL_DAYS:15}

```

### Installation Modes

BanyanDB Server supports two installation modes:

- **Standalone Mode**: Suitable for small-scale deployments.
    - **Configuration**: `targets` is the IP address/hostname and port of the BanyanDB server.

Use the docker mode to run BanyanDB containerized. 
```shell
# The compatible version number could be found in /config/bydb.dependencies.properties
export BYDB_VERSION=xxx

docker pull apache/skywalking-banyandb:$BYDB_VERSION

docker run -d \
  -p 17912:17912 \
  -p 17913:17913 \
  --name banyandb \
  apache/skywalking-banyandb:$BYDB_VERSION \
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

### Group Settings

BanyanDB supports **group settings** to configure storage groups, shards, segment intervals, and TTL (Time-To-Live). The group settings file is a YAML file required when using BanyanDB as the storage.

#### Basic Group Settings

- `ShardNum`: Number of shards in the group. Shards are the basic units of data storage in BanyanDB. Data is distributed across shards based on the hash value of the series ID. Refer to the [BanyanDB Shard](https://skywalking.apache.org/docs/skywalking-banyandb/latest/concept/clustering/#52-data-sharding) documentation for more details.
- `SIDays`: Interval in days for creating a new segment. Segments are time-based, allowing efficient data retention and querying. `SI` stands for Segment Interval.
- `TTLDays`: Time-to-live for the data in the group, in days. Data exceeding the TTL will be deleted.

For more details on setting `segmentIntervalDays` and `ttlDays`, refer to the [BanyanDB TTL](../../../banyandb/ttl.md) documentation.

#### Record Group Settings

The `gr` prefix is used for record group settings. The `normal` and `super` sections are used to define settings for normal and super datasets, respectively.

Super datasets are used to store trace or log data that is too large for normal datasets. Each super dataset is stored in a separate group in BanyanDB. The settings defined in the `super` section are applied to all super datasets.

Normal datasets are stored in a single group named `normal`. The settings defined in the `normal` section are applied to all normal datasets.

#### Metrics Group Settings

The `gm` prefix is used for metrics group settings. The `minute`, `hour`, and `day` sections are used to define settings for metrics stored based on granularity.

The `index` group is designed to store metrics used for indexing without value columns. For example, `service_traffic`, `network_address_alias`, etc.

For more details, refer to the documentation of [BanyanDB](https://skywalking.apache.org/docs/skywalking-banyandb/latest/readme/) and the [BanyanDB Java Client](https://github.com/apache/skywalking-banyandb-java-client) subprojects.
