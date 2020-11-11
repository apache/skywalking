# TTL
In SkyWalking, there are two types of observability data, besides metadata.
1. Record, including trace and alarm. Maybe log in the future.
1. Metric, including such as percentile, heat map, success rate, cpm(rpm) etc.

You have following settings for different types.
```yaml
    # Set a timeout on metrics data. After the timeout has expired, the metrics data will automatically be deleted.
    recordDataTTL: ${SW_CORE_RECORD_DATA_TTL:3} # Unit is day
    metricsDataTTL: ${SW_CORE_METRICS_DATA_TTL:7} # Unit is day
```

- `recordDataTTL` affects **Record** data, including tracing and alarm.
- `metricsDataTTL` affects all metrics, including service, instance, endpoint metrics and topology map metrics.
