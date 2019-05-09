# TTL
In SkyWalking, there are two types of observability data, besides metadata.
1. Record, including trace and alarm. Maybe log in the future.
1. Metric, including such as p99/p95/p90/p75/p50, heatmap, success rate, cpm(rpm) etc.
Metric is separated in minute/hour/day/month dimensions in storage, different indexes or tables.

You have following settings for different types.
```yaml
    # Set a timeout on metrics data. After the timeout has expired, the metrics data will automatically be deleted.
    recordDataTTL: ${SW_CORE_RECORD_DATA_TTL:90} # Unit is minute
    minuteMetricsDataTTL: ${SW_CORE_MINUTE_METRIC_DATA_TTL:90} # Unit is minute
    hourMetricsDataTTL: ${SW_CORE_HOUR_METRIC_DATA_TTL:36} # Unit is hour
    dayMetricsDataTTL: ${SW_CORE_DAY_METRIC_DATA_TTL:45} # Unit is day
    monthMetricsDataTTL: ${SW_CORE_MONTH_METRIC_DATA_TTL:18} # Unit is month
```

- `recordDataTTL` affects **Record** data.
- `minuteMetricsDataTTL`, `hourMetricsDataTTL`, `dayMetricsDataTTL` and `monthMetricsDataTTL` affects
metrics data in minute/hour/day/month dimensions.