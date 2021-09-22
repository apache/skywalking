# TTL
In SkyWalking, besides metadata, there are two types of observability data:
1. Record: Includes trace and alarm. Log may also be included in the future.
1. Metric: Includes percentile, heat map, success rate, cpm (rpm), etc.

These are the settings for the different types:
```yaml
    # Set a timeout on metrics data. After the timeout has expired, the metrics data will automatically be deleted.
    recordDataTTL: ${SW_CORE_RECORD_DATA_TTL:3} # Unit is day
    metricsDataTTL: ${SW_CORE_METRICS_DATA_TTL:7} # Unit is day
```

- `recordDataTTL` applies to **record** data, including tracing, logging, topN sampled statements and alarm.
- `metricsDataTTL` applies to all metrics, including the metrics for service, instance, endpoint, and topology map.
