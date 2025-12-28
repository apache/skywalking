# Progressive TTL

Progressive TTL provides a capability to manage data retention with different TTL settings and Segment Creation Policies based on the time granularity of metrics.

BanyanDB employs a Time-To-Live (TTL) mechanism to automatically delete data older than the specified duration. When using BanyanDB as the storage backend, **the `recordDataTTL` and `metricsDataTTL` configurations are deprecated**. Instead, TTL settings should be configured directly within `storage.banyandb`.

For detailed information, please refer to the [Storage BanyanDB](../setup/backend/storages/banyandb.md) documentation.

## How TTL works in BanyanDB

BanyanDB uses time-based rotation with two key settings per group (and per stage, if enabled):

- segmentInterval (days): How often a new segment is created.
- ttl (days): How long data is retained before automatic deletion.

Some groups support progressive stages:
- hot (default stage, always present)
- warm (optional, used in the default query if present)
- cold (optional)

When warm/cold stages are enabled, data flows hot → warm → cold as it ages, each stage having its own ttl, segmentInterval, and placement (nodeSelector).

## Default TTLs by kind (from bydb.yml)

Warm and Cold are disabled by default. The values listed for warm/cold apply if you enable those stages.

- ttl: retention (days) in the hot stage
- warm ttl / cold ttl: retention (days) in warm/cold stages

| Kind (group)           | ttl | warm ttl | cold ttl |
|------------------------|-----|----------|----------|
| records                | 3   | 7        | 30       |
| trace                  | 3   | 7        | 30       |
| zipkinTrace            | 3   | 7        | 30       |
| recordsLog             | 3   | 7        | 30       |
| recordsBrowserErrorLog | 3   | 7        | 30       |
| metricsMinute          | 7   | 15       | 60       |
| metricsHour            | 15  | 30       | 120      |
| metricsDay             | 15  | 30       | 120      |
| metadata (index-mode)  | 15  | —        | —        |
| property               | —   | —        | —        |

Notes:
- “—” = not specified in the default bydb.yml. Metadata don't support warm/cold stages.
- Property kind does not have TTL because it uses a different KV-engine mechanism.
- The metadata group should have ttl greater than or equal to the maximum ttl of your metrics groups to ensure index coverage for retained data.

For more details on configuring `segmentIntervalDays` and `ttlDays`, refer to the [BanyanDB Rotation](https://skywalking.apache.org/docs/skywalking-banyandb/latest/concept/rotation/) documentation.
