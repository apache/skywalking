# TTL
In SkyWalking, there are two types of observability data, besides metadata.
1. Record, including trace and alarm. Maybe log in the future.
1. Metric, including such as p99/p95/p90/p75/p50, heatmap, success rate, cpm(rpm) etc.
Metric is separated in minute/hour/day/month dimensions in storage, different indexes or tables.

You have following settings for different types.
```yaml
    # Set a timeout on metrics data. After the timeout has expired, the metrics data will automatically be deleted.
    enableDataKeeperExecutor: ${SW_CORE_ENABLE_DATA_KEEPER_EXECUTOR:true} # Turn it off then automatically metrics data delete will be close.
    dataKeeperExecutePeriod: ${SW_CORE_DATA_KEEPER_EXECUTE_PERIOD:5} # How often the data keeper executor runs periodically, unit is minute
    recordDataTTL: ${SW_CORE_RECORD_DATA_TTL:90} # Unit is minute
    minuteMetricsDataTTL: ${SW_CORE_MINUTE_METRIC_DATA_TTL:90} # Unit is minute
    hourMetricsDataTTL: ${SW_CORE_HOUR_METRIC_DATA_TTL:36} # Unit is hour
    dayMetricsDataTTL: ${SW_CORE_DAY_METRIC_DATA_TTL:45} # Unit is day
    monthMetricsDataTTL: ${SW_CORE_MONTH_METRIC_DATA_TTL:18} # Unit is month
```

- `recordDataTTL` affects **Record** data.
- `minuteMetricsDataTTL`, `hourMetricsDataTTL`, `dayMetricsDataTTL` and `monthMetricsDataTTL` affects
metrics data in minute/hour/day/month dimensions.

## ElasticSearch 6 storage TTL 
**Specifically:**  
Because of the feature of ElasticSearch, it rebuilds the index after executing delete by query command.
That is a heavy operation, it will hang up the ElasticSearch server for a few seconds each time. The fact is there are above hundred indexes which may cause ElasticSearch out of service unexpected. 
So, we create the index by day to avoid execute delete by query operation, 
then delete the index directly, this is a high performance operation, say goodbye to hung up.

You have following settings in Elasticsearch storage.
```yaml
    # Those data TTL settings will override the same settings in core module.
    recordDataTTL: ${SW_STORAGE_ES_RECORD_DATA_TTL:7} # Unit is day
    otherMetricsDataTTL: ${SW_STORAGE_ES_OTHER_METRIC_DATA_TTL:45} # Unit is day
    monthMetricsDataTTL: ${SW_STORAGE_ES_MONTH_METRIC_DATA_TTL:18} # Unit is month
``` 

- `recordDataTTL` affects **Record** data.
- `otherMetricsDataTTL` affects minute/hour/day dimensions of metrics. `minuteMetricsDataTTL`, `hourMetricsDataTTL` and `dayMetricsDataTTL` are still there, but the **Unit** of them changed to **DAY** too. If you want to set them manually, please remove `otherMetricsDataTTL`.
- `monthMetricsDataTTL` affects month dimension of metrics.
