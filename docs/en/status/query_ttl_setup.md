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
# Cold data, '-1' represents no cold stage data.
metrics.minute.cold=-1
metrics.hour.cold=-1
metrics.day.cold=-1

# Records TTL includes the definition of the TTL of the records data in the storage,
# Records include traces, logs, sampled slow SQL statements, HTTP requests(by Rover), alarms, etc.
# Super dataset of records are traces and logs, which volume should be much larger.
#
# Cover hot and warm data for BanyanDB.
records.normal=3
records.trace=10
records.zipkinTrace=3
records.log=3
records.browserErrorLog=3
# Cold data, '-1' represents no cold stage data.
records.normal.cold=-1
records.trace.cold=30
records.zipkinTrace.cold=-1
records.log.cold=-1
records.browserErrorLog.cold=-1
```

This API also provides the response in JSON format, which is more friendly for programmatic usage.

```shell
> curl -X GET "http://127.0.0.1:12800/status/config/ttl" \
       -H "Accept: application/json"

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
    "normal": 3,
    "trace": 10,
    "zipkinTrace": 3,
    "log": 3,
    "browserErrorLog": 3,
    "coldNormal": -1,
    "coldTrace": 30,
    "coldZipkinTrace": -1,
    "coldLog": -1,
    "coldBrowserErrorLog": -1
  }
}
```
