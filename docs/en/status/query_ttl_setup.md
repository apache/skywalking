# Get Effective TTL Configurations

Time To Live (TTL) mechanism has different behaviors according to different storage implementations. By default, the
core module provides two TTL configurations: [`recordDataTTL` and `metricsDataTTL`](../setup/backend/ttl.md).
But some storage implementations could override these settings and provide its own TTL configurations, for example,
BanyanDB provides its native TTL mechanism to support [progressive TTL](../banyandb/ttl.md) feature.

This API is used to get the unified and effective TTL configurations.
- URL, `http://{core restHost}:{core restPort}/status/config/ttl`
- HTTP GET method.

```shell
> curl -X GET "http://127.0.0.1:12800/status/config/ttl"
# Metrics TTL includes the definition of the TTL of the metrics-ish data in the storage,
# e.g.
# 1. The metadata of the service, instance, endpoint, topology map, etc.
# 2. Generated metrics data from OAL and MAL engines.
#
# TTLs for each granularity metrics are listed separately.
metrics.minute=7
metrics.hour=7
metrics.day=7

# Records TTL includes the definition of the TTL of the records data in the storage,
# Records include traces, logs, sampled slow SQL statements, HTTP requests(by Rover), alarms, etc.
# Super dataset of records are traces and logs, which volume should be much larger.
records.default=3
records.superDataset=3
```

This API also provides the response in JSON format, which is more friendly for programmatic usage.

```shell
> curl -X GET "http://127.0.0.1:12800/status/config/ttl" \
       -H "Accept: application/json"

{
    "metrics": {
        "minute": 7,
        "hour": 7,
        "day": 7
    },
    "records": {
        "default": 3,
        "superDataset": 3
    }
}
```