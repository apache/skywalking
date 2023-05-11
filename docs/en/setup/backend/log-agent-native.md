# Collecting Logs by Agents

Some of SkyWalking native agents support collecting logs and sending them to OAP server without local files and/or file
agents, which are listed in [here](filelog-native.md).

## Java agent's toolkits

Java agent provides toolkits for
[log4j](https://skywalking.apache.org/docs/skywalking-java/next/en/setup/service-agent/java-agent/application-toolkit-log4j-1.x/),
[log4j2](https://skywalking.apache.org/docs/skywalking-java/next/en/setup/service-agent/java-agent/application-toolkit-log4j-2.x/),
and
[logback](https://skywalking.apache.org/docs/skywalking-java/next/en/setup/service-agent/java-agent/application-toolkit-logback-1.x/)
to report logs through gRPC with automatically injected trace context.

[SkyWalking Satellite sidecar](https://github.com/apache/skywalking-satellite) is a recommended proxy/side that
forwards logs (including the use of Kafka MQ to transport logs). When using this,
open [kafka-fetcher](kafka-fetcher.md#kafka-fetcher)
and enable configs `enableNativeProtoLog`.

Java agent provides toolkits for
[log4j](https://skywalking.apache.org/docs/skywalking-java/next/en/setup/service-agent/java-agent/application-toolkit-log4j-1.x/#print-skywalking-context-in-your-logs),
[log4j2](https://skywalking.apache.org/docs/skywalking-java/next/en/setup/service-agent/java-agent/application-toolkit-log4j-2.x/#print-skywalking-context-in-your-logs),
and
[logback](https://skywalking.apache.org/docs/skywalking-java/next/en/setup/service-agent/java-agent/application-toolkit-logback-1.x/#print-skywalking-context-in-your-logs)
to report logs through files with automatically injected trace context.

Log framework config examples:

- [log4j1.x fileAppender](../../../../test/e2e-v2/java-test-service/e2e-service-provider/src/main/resources/log4j.properties)
- [log4j2.x fileAppender](../../../../test/e2e-v2/java-test-service/e2e-service-provider/src/main/resources/log4j2.xml)
- [logback fileAppender](../../../../test/e2e-v2/java-test-service/e2e-service-provider/src/main/resources/logback.xml)

## Python agent log reporter

[SkyWalking Python Agent](https://github.com/apache/skywalking-python) implements a log reporter for the [logging
module](https://docs.python.org/3/library/logging.html) with functionalities aligning with the Java toolkits.

To explore how to enable the reporting features for your use cases, please refer to the
[Log Reporter Doc](https://skywalking.apache.org/docs/skywalking-python/next/en/setup/advanced/logreporter/) for a
detailed guide.
