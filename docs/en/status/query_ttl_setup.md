# Get Effective TTL Configurations

Time To Live (TTL) mechanism has different behaviors according to different storage implementations. By default, the
core module provides two TTL configurations: [`recordDataTTL` and `metricsDataTTL`](../setup/backend/ttl.md).
But some storage implementations could override these settings and provide its own TTL configurations, for example,
BanyanDB provides its native TTL mechanism to support [progressive TTL](../banyandb/ttl.md) feature and [Data Lifecycle Stages(Hot/Warm/Cold)](../banyandb/stages.md) feature.


This API is used to get the unified and effective TTL configurations.
- URL, `http://{core restHost}:{core restPort}/status/config/ttl`
- HTTP GET method.

```shell
> curl -X GET "http://127.0.0.1:12800/status/config/ttl"
# Metrics TTL includes the definition of the TTL of the metrics-ish data in the storage,
# e.g.
# 1. The metadata of the service, instance, endpoint, topology map, etc.
# 2. Generated metrics data from OAL and MAL engines.
# 3. Banyandb storage provides Data Lifecycle Stages(Hot/Warm/Cold).
#
# TTLs for each granularity metrics are listed separately.
#
# Cover hot and warm data for BanyanDB.
metrics.minute=7
metrics.hour=15
metrics.day=15
# Cold data for BanyanDB, '-1' means no cold stage.
metrics.minute.cold=-1
metrics.hour.cold=-1
metrics.day.cold=-1

# Records TTL includes the definition of the TTL of the records data in the storage,
# Records include traces, logs, sampled slow SQL statements, HTTP requests(by Rover), alarms, etc.
# Super dataset of records are traces and logs, which volume should be much larger.
#
# Cover hot and warm data for BanyanDB.
records.default=3
records.superDataset=3
# Cold data for BanyanDB, '-1' means no cold stage.
records.default.cold=3
records.superDataset.cold=3
```

This API also provides the response in JSON format, which is more friendly for programmatic usage.

```shell
> curl -X GET "http://127.0.0.1:12800/status/config/ttl" \
       -H "Accept: application/json"

{
{
    "metrics": {
        "minute": 7,
        "hour": 15,
        "day": 15,
        "coldMinute": -1,
        "coldHour": -1,
        "coldDay": -1
    },
    "records": {
        "default": 3,
        "superDataset": 3,
        "coldValue": 3,
        "coldSuperDataset": 3
    }
}
}
```
