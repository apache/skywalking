# logback plugin
* Dependency the toolkit, such as using maven or gradle
```xml
    <dependency>
         <groupId>org.apache.skywalking</groupId>
         <artifactId>apm-toolkit-logback-1.x</artifactId>
         <version>{project.release.version}</version>
     </dependency>
```

# Print trace ID in your logs

* set `%tid` in `Pattern` section of logback.xml
```xml
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
            <layout class="org.apache.skywalking.apm.toolkit.log.logback.v1.x.TraceIdPatternLogbackLayout">
                <Pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%tid] [%thread] %-5level %logger{36} -%msg%n</Pattern>
            </layout>
        </encoder>
    </appender>
```

* with the MDC, set `%X{tid}` in `Pattern` section of logback.xml
```xml
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
            <layout class="org.apache.skywalking.apm.toolkit.log.logback.v1.x.mdc.TraceIdMDCPatternLogbackLayout">
                <Pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{tid}] [%thread] %-5level %logger{36} -%msg%n</Pattern>
            </layout>
        </encoder>
    </appender>
```


* Support logback AsyncAppender(MDC also support), No additional configuration is required. Refer to the demo of logback.xml below. For details: [Logback AsyncAppender](https://logback.qos.ch/manual/appenders.html#AsyncAppender)
```xml
    <configuration scan="true" scanPeriod=" 5 seconds">
        <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
                <layout class="org.apache.skywalking.apm.toolkit.log.logback.v1.x.mdc.TraceIdMDCPatternLogbackLayout">
                    <Pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{tid}] [%thread] %-5level %logger{36} -%msg%n</Pattern>
                </layout>
            </encoder>
        </appender>
    
        <appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
            <discardingThreshold>0</discardingThreshold>
            <queueSize>1024</queueSize>
            <neverBlock>true</neverBlock>
            <appender-ref ref="STDOUT"/>
        </appender>
    
        <root level="INFO">
            <appender-ref ref="ASYNC"/>
        </root>
    </configuration>
```

* When you use `-javaagent` to active the sky-walking tracer, logback will output **traceId**, if it existed. If the tracer is inactive, the output will be `TID: N/A`.

# logstash logback plugin

* Dependency the toolkit, such as using maven or gradle

```xml
<dependency>
    <groupId>org.apache.skywalking</groupId>
    <artifactId>apm-toolkit-logback-1.x</artifactId>
    <version>${skywalking.version}</version>
</dependency>
```

* set `LogstashEncoder` of logback.xml

```xml
<encoder charset="UTF-8" class="net.logstash.logback.encoder.LogstashEncoder">
    <provider class="org.apache.skywalking.apm.toolkit.log.logback.v1.x.logstash.TraceIdJsonProvider">
    </provider>
</encoder>
```

* set `LoggingEventCompositeJsonEncoder` of logstash in logback-spring.xml for custom json format

1.add converter for %tid as child of <configuration> node
```xml
<!--add converter for %tid -->
    <conversionRule conversionWord="tid" converterClass="org.apache.skywalking.apm.toolkit.log.logback.v1.x.LogbackPatternConverter"/>
```
2.add json encoder for custom json format

```xml
<encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp>
                    <timeZone>UTC</timeZone>
                </timestamp>
                <pattern>
                    <pattern>
                        {
                        "level": "%level",
                        "tid": "%tid",
                        "thread": "%thread",
                        "class": "%logger{1.}:%L",
                        "message": "%message",
                        "stackTrace": "%exception{10}"
                        }
                    </pattern>
                </pattern>
            </providers>
</encoder>
```

# gRPC reporter

The gRPC reporter could forward the collected logs to SkyWalking OAP server, or [SkyWalking Satellite sidecar](https://github.com/apache/skywalking-satellite). Trace id, segment id, and span id will attach to logs automatically. There is no need to modify existing layouts.

* Add `GRPCLogClientAppender` in logback.xml

```xml
    <appender name="grpc-log" class="org.apache.skywalking.apm.toolkit.log.logback.v1.x.log.GRPCLogClientAppender"/>
```

*  Add config of the plugin or use default

```properties
plugin.toolkit.log.grpc.reporter.server_host=${SW_GRPC_LOG_SERVER_HOST:127.0.0.1}
plugin.toolkit.log.grpc.reporter.server_port=${SW_GRPC_LOG_SERVER_PORT:11800}
plugin.toolkit.log.grpc.reporter.max_message_size=${SW_GRPC_LOG_MAX_MESSAGE_SIZE:10485760}
plugin.toolkit.log.grpc.reporter.upstream_timeout=${SW_GRPC_LOG_GRPC_UPSTREAM_TIMEOUT:30}
```

## Transmitting un-formatted messages

The logback 1.x gRPC reporter supports transmitting logs as formatted or un-formatted. Transmitting formatted data is the default but can be disabled by adding the following to the agent config:

```
plugin.toolkit.log.transmit_formatted=false
```

The above will result in the `content` field being used for the log pattern with additional log tags of `argument.0`, `argument.1`, and so on representing each logged argument as well as an additional `exception` tag which is only present if a throwable is also logged.

For example, the following code:
```java
log.info("{} {} {}", 1, 2, 3);
```

Will result in:
```json
{
  "content": "{} {} {}",
  "tags": [
    {
      "key": "argument.0",
      "value": "1"
    },
    {
      "key": "argument.1",
      "value": "2"
    },
    {
      "key": "argument.2",
      "value": "3"
    }
  ]
}
```
