**Problem**: <br/>
The Kafka plug-in can't track how applications handle messages

**Reason**:
The Kafka consumer actively takes a message from Kafka Cluster and the agent can not see if the consuming thread is being monitored.

**Resolve**:
Add the `@Trace` annotation to the method of handle message and the method of poll message , see the [document](https://github.com/apache/incubator-skywalking/blob/master/docs/en/Application-toolkit-trace.md)