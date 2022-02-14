# TTL
In SkyWalking, there are two types of observability data:
1. Records include traces, logs, topN sampled statements and alarm. `recordDataTTL` applies to **record** data.
1. Metrics include all metrics for service, instance, endpoint, and topology map. Metadata(lists of services, instances, or endpoints) also belongs to metrics. `metricsDataTTL` applies to **Metrics** data.

These are the settings for the different types:
```yaml
    # Set a timeout on metrics data. After the timeout has expired, the metrics data will automatically be deleted.
    recordDataTTL: ${SW_CORE_RECORD_DATA_TTL:3} # Unit is day
    metricsDataTTL: ${SW_CORE_METRICS_DATA_TTL:7} # Unit is day
```

