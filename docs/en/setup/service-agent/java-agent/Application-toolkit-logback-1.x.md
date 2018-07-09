* Dependency the toolkit, such as using maven or gradle
```xml
    <dependency>
         <groupId>org.apache.skywalking</groupId>
         <artifactId>apm-toolkit-logback-1.x</artifactId>
         <version>{project.release.version}</version>
     </dependency>
```

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

* When you use `-javaagent` to active the sky-walking tracer, logback will output **traceId**, if it existed. If the tracer is inactive, the output will be `TID: N/A`.
