* Dependency the toolkit, such as using maven or gradle
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-log4j-2.x</artifactId>
      <version>{project.release.version}</version>
   </dependency>
```

* Config the `[%traceId]` pattern in your log4j2.xml
```xml
   <Appenders>
      <Console name="Console" target="SYSTEM_OUT">
         <PatternLayout pattern="%d [%traceId] %-5p %c{1}:%L - %m%n"/>
      </Console>
   </Appenders>
```

* Support log4j2 AsyncRoot (AsyncAppender not supported for now), No additional configuration is required. Refer to the demo of log4j2.xml below. For details: [Log4j2 Async Loggers](https://logging.apache.org/log4j/2.x/manual/async.html)
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

* When you use `-javaagent` to active the sky-walking tracer, log4j2 will output **traceId**, if it existed. If the tracer is inactive, the output will be `TID: N/A`.
