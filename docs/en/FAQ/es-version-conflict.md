# Elasticsearch exception `type=version_conflict_engine_exception` since 8.7.0

Since 8.7.0, we did the following optimization to reduce Elasticsearch load.

```markdown
Performance: remove the synchronous persistence mechanism from batch ElasticSearch DAO. Because the current enhanced
persistent session mechanism, don't require the data queryable immediately after the insert and update anymore.
```

Due to this, we flush the metrics into Elasticsearch without using `WriteRequest.RefreshPolicy.WAIT_UNTIL`. This reduces
the load of persistent works in OAP server and load of Elasticsearch CPU dramatically.

Meanwhile, there is little chance you could see following **warn**s in your logs.

```
{
  "timeMillis": 1626247722647,
  "thread": "I/O dispatcher 4",
  "level": "WARN",
  "loggerName": "org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient",
  "message": "Bulk [70] executed with failures:[failure in bulk execution:\n[18875]: index [sw8_service_relation_client_side-20210714], type [_doc], id [20210714_b3BlcmF0aW9uLXJ1bGUtc2VydmVyQDExNDgx.1-bWFya2V0LXJlZmVycmFsLXNlcnZlckAxMDI1MQ==.1], message [[sw8_service_relation_client_side-20210714/D7qzncbeRq6qh2QF5MogTw][[sw8_service_relation_client_side-20210714][0]] ElasticsearchException[Elasticsearch exception [type=version_conflict_engine_exception, reason=[20210714_b3BlcmF0aW9uLXJ1bGUtc2VydmVyQDExNDgx.1-bWFya2V0LXJlZmVycmFsLXNlcnZlckAxMDI1MQ==.1]: version conflict, required seqNo [14012594], primary term [1]. current document has seqNo [14207928] and primary term [1]]]]]",
  "endOfBatch": false,
  "loggerFqcn": "org.apache.logging.slf4j.Log4jLogger",
  "threadId": 44,
  "threadPriority": 5,
  "timestamp": "2021-07-14 15:28:42.647"
}
```

This would not affect the system much, just a possibility of inaccurate of metrics. If this wouldn't show up in high
frequency, you could ignore this directly.

In case you could see many logs like this. Then it is a signal, that the flush period of your ElasticSearch template can't
catch up your setting. Or you set the `persistentPeriod` less than the flush period.

