# Progressive TTL

Progressive TTL provides a capability to manage data retention with different TTL settings and Segment Creation Policies based on the time granularity of metrics.

BanyanDB employs a Time-To-Live (TTL) mechanism to automatically delete data older than the specified duration. When using BanyanDB as the storage backend, **the `recordDataTTL` and `metricsDataTTL` configurations are deprecated**. Instead, TTL settings should be configured directly within `storage.banyandb`.

For detailed information, please refer to the [Storage BanyanDB](../setup/backend/storages/banyandb.md) documentation.

## Segment Interval and TTL

BanyanDB's data rotation mechanism manages data storage based on **Segment Interval** and **TTL** settings:

- **Segment Interval (`SIDays`)**: Specifies the time interval in days for creating a new data segment. Segments are time-based, facilitating efficient data retention and querying.
- **TTL (`TTLDays`)**: Defines the time-to-live for data within a group, in days. Data that exceeds the TTL will be automatically deleted.

### Best Practices for Setting `SIDays` and `TTLDays`

- **Data Retention Requirements**: Set the TTL based on how long you need to retain your data. For instance, to retain data for 30 days, set the TTL to 30 days.
- **Segment Management**: Avoid generating too many segments, as this increases the overhead for data management and querying.
- **Query Requirements**: Align segment intervals with your query patterns. For example:
    - If you frequently query data for the last 30 minutes, set `SIDays` to 1 day.
    - For querying data from the last 7 days, set `SIDays` to 7 days.

## Configuration Guidelines

### Record Data

For both standard and super datasets:

- **Recommended `SIDays`**: `1`
    - Most queries are performed within a day.
- **`TTLDays`**: Set according to your data retention needs.

### Metrics Data

Configure `SIDays` and `TTLDays` based on data retention and query requirements. Recommended settings include:

| Group                  | `SIDays` | `TTLDays` |
|------------------------|----------|-----------|
| Minute (`metricsMin`)  | 1        | 7         |
| Hour (`metricsHour`)   | 5        | 15        |
| Day (`metricsDay`)     | 15       | 15        |
| Index (`metadata`)     | 15       | 15        |

**Group Descriptions:**

- **Minute (`metricsMin`)**: Stores metrics with a 1-minute granularity. Suitable for recent data queries requiring minute-level detail. Consequently, it has shorter `SIDays` and `TTLDays` compared to other groups.
- **Hour (`metricsHour`)**: Stores metrics with a 1-hour granularity. Designed for queries that need hour-level detail over a longer period than minute-level data.
- **Day (`metricsDay`)**: Stores metrics with a 1-day granularity. This group handles the longest segment intervals and TTLs among all granularity groups.
- **Index (`metadata`)**: Stores metrics used solely for indexing without value columns. Since queries often scan all segments in the `index` group, it shares the same `SIDays` and `TTLDays` as the `day` group to optimize performance. This group's `TTL` must be set to the **max** value of all groups.

For more details on configuring `segmentIntervalDays` and `ttlDays`, refer to the [BanyanDB Rotation](https://skywalking.apache.org/docs/skywalking-banyandb/latest/concept/rotation/) documentation.
