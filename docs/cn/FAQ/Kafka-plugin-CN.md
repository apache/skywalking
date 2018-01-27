**现象** 
Kafka插件无法追踪应用是怎么处理消息

**原因**：Kafka消费端主动从Kafka Cluster获取消息，并且探针无法得知
消费线程是否被监控.

**解决方法**: 需要手动探针才能解决，获取消息和处理消息的方法追加@Trace，详情见[文档](https://github.com/apache/incubator-skywalking/blob/master/docs/en/Application-toolkit-trace.md)
