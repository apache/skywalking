
# Virtual Message Queue (MQ)

Virtual MQ represent the MQ nodes detected by [server agents' plugins](server-agents.md). The performance
metrics of the MQ are also from the MQ client-side perspective.

For example, Kafka plugins in the Java agent could detect the transmission latency of message
As a result, SkyWalking would show message count, transmission latency, success rate powered by backend analysis capabilities in this dashboard.

The MQ operation span should have
- It is an **Exit**(at producer side) or **Entry**(at consumer side) span
- **Span's layer == MQ**
- Tag key = `mq.queue`, value = MQ queue name
- Tag key = `mq.topic`, value = MQ queue topic , it's optional as some MQ don't have topic concept.
- Tag key = `transmission.latency`, value = Transmission latency from consumer to producer
- Set `peer` at both sides(producer and consumer). And the value of peer should represent the MQ server cluster.