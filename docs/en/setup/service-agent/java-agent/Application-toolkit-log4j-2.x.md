* Dependency the toolkit, such as using maven or gradle
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-log4j-2.x</artifactId>
      <version>{project.release.version}</version>
   </dependency>
```

# Print trace ID in your logs

* Config the `[%traceId]` pattern in your log4j2.xml
```xml
   <Appenders>
      <Console name="Console" target="SYSTEM_OUT">
         <PatternLayout pattern="%d [%traceId] %-5p %c{1}:%L - %m%n"/>
      </Console>
   </Appenders>
```

* Support log4j2 AsyncRoot , No additional configuration is required. Refer to the demo of log4j2.xml below. For details: [Log4j2 Async Loggers](https://logging.apache.org/log4j/2.x/manual/async.html)
```xml
    <Configuration>
        <Appenders>
            <Console name="Console" target="SYSTEM_OUT">
                <PatternLayout pattern="%d [%traceId] %-5p %c{1}:%L - %m%n"/>
            </Console>
        </Appenders>
        <Loggers>
            <AsyncRoot level="INFO">
                <AppenderRef ref="Console"/>
            </AsyncRoot>
        </Loggers>
    </Configuration>
```
* Support log4j2 AsyncAppender , No additional configuration is required. Refer to the demo of log4j2.xml below. 

    For details: [All Loggers Async](https://logging.apache.org/log4j/2.x/manual/async.html#AllAsync) 

    Log4j-2.9 and higher require disruptor-3.3.4.jar or higher on the classpath. Prior to Log4j-2.9, disruptor-3.0.0.jar or higher was required.
    This is simplest to configure and gives the best performance. To make all loggers asynchronous, add the disruptor jar to the classpath and 
    set the system property `log4j2.contextSelector` to `org.apache.logging.log4j.core.async.AsyncLoggerContextSelector`.
    ```xml
    <Configuration status="WARN">
      <Appenders>
        <!-- Async Loggers will auto-flush in batches, so switch off immediateFlush. -->
        <RandomAccessFile name="RandomAccessFile" fileName="async.log" immediateFlush="false" append="false">
          <PatternLayout>
            <Pattern>%d %p %c{1.} [%t] [%traceId] %m %ex%n</Pattern>
          </PatternLayout>
        </RandomAccessFile>
      </Appenders>
      <Loggers>
        <Root level="info" includeLocation="false">
          <AppenderRef ref="RandomAccessFile"/>
        </Root>
      </Loggers>
    </Configuration>
    ```
    For details: [Mixed Sync & Async](https://logging.apache.org/log4j/2.x/manual/async.html#MixedSync-Async)
    
    Log4j-2.9 and higher require disruptor-3.3.4.jar or higher on the classpath. Prior to Log4j-2.9, disruptor-3.0.0.jar or higher was required. 
    There is no need to set system property `Log4jContextSelector` to any value.
    
    ```xml
    <Configuration status="WARN">
      <Appenders>
        <!-- Async Loggers will auto-flush in batches, so switch off immediateFlush. -->
        <RandomAccessFile name="RandomAccessFile" fileName="asyncWithLocation.log"
                  immediateFlush="false" append="false">
          <PatternLayout>
            <Pattern>%d %p %class{1.} [%t] [%traceId] %location %m %ex%n</Pattern>
          </PatternLayout>
        </RandomAccessFile>
      </Appenders>
      <Loggers>
        <!-- pattern layout actually uses location, so we need to include it -->
        <AsyncLogger name="com.foo.Bar" level="trace" includeLocation="true">
          <AppenderRef ref="RandomAccessFile"/>
        </AsyncLogger>
        <Root level="info" includeLocation="true">
          <AppenderRef ref="RandomAccessFile"/>
        </Root>
      </Loggers>
    </Configuration>
    ```
* Support log4j2 AsyncAppender, For details: [Log4j2 AsyncAppender](https://logging.apache.org/log4j/2.x/manual/appenders.html)
```xml
    <Configuration>
        <Appenders>
            <Console name="Console" target="SYSTEM_OUT">
                <PatternLayout pattern="%d [%traceId] %-5p %c{1}:%L - %m%n"/>
            </Console>
            <Async name="Async">
                <AppenderRef ref="Console"/>
            </Async>
        </Appenders>
        <Loggers>
            <Root level="INFO">
                <AppenderRef ref="Async"/>
            </Root>
        </Loggers>
    </Configuration>
```
* When you use `-javaagent` to active the sky-walking tracer, log4j2 will output **traceId**, if it existed. If the tracer is inactive, the output will be `TID: N/A`.

# gRPC reporter

The gRPC report could forward the collected logs to SkyWalking OAP server, or [SkyWalking Satellite sidecar](https://github.com/apache/skywalking-satellite). Trace id, segment id, and span id will attach to logs automatically. You don't need to change the layout.

* Add `GRPCLogClientAppender` in log4j2.xml

```xml
    <GRPCLogClientAppender name="grpc-log"/>
```

*  Add config of the plugin or use default

```properties
plugin.toolkit.log.grpc.reporter.server_host=${SW_GRPC_LOG_SERVER_HOST:127.0.0.1}
plugin.toolkit.log.grpc.reporter.server_port=${SW_GRPC_LOG_SERVER_PORT:11800}
plugin.toolkit.log.grpc.reporter.max_message_size=${SW_GRPC_LOG_MAX_MESSAGE_SIZE:10485760}
plugin.toolkit.log.grpc.reporter.upstream_timeout=${SW_GRPC_LOG_GRPC_UPSTREAM_TIMEOUT:30}
```


## Transmitting un-formatted messages

The log4j 2.x gRPC reporter supports transmitting logs as formatted or un-formatted. Transmitting formatted data is the default but can be disabled by adding the following to the agent config:

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
