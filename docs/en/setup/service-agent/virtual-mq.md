# Virtual Message Queue (MQ)

Virtual MQ represent the MQ nodes detected by [server agents' plugins](server-agents.md). The performance
metrics of the MQ are also from the MQ client-side perspective.

For example, Kafka plugins in the Java agent could detect the transformation latency of message
As a result, SkyWalking would show message count , transformation latency, success rate powered by backend analysis capabilities in this dashboard.

The MQ operation span should have
- It is an **Exit**(on producer side) or **Entry**(on consumer side) span
- **Span's layer == MQ**
- Tag key = `mq.broker`, value = MQ address
- Tag key = `mq.queue`, value = MQ queue name
- Tag key = `mq.topic`, value = MQ queue topic
- Tag key = `transmission.latency`, value = Transmission latency from consumer to producer