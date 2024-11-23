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

## BanyanDB TTL

BanyanDB has a TTL mechanism to automatically delete data that is older than the specified time. When you use BanyanDB as the storage backend, `recordDataTTL` and `metricsDataTTL` are not used. Instead, you should configure the TTL settings in `storage.banyandb`.

Please refer to the [Storage BanyanDB](storages/banyandb.md) document for more information.
