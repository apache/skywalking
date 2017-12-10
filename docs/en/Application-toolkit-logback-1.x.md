* Dependency the toolkit, such as using maven or gradle
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>skywalking-toolkit-logback-1.x</artifactId>
      <version>{project.release.version}</version>
   </dependency>
```
&nbsp;&nbsp;&nbsp;[ ![Download](https://api.bintray.com/packages/wu-sheng/skywalking/org.apache.skywalking.apm-toolkit-logback-1.x/images/download.svg) ](https://bintray.com/wu-sheng/skywalking/org.apache.skywalking.apm-toolkit-logback-1.x/_latestVersion)

* set `%tid` in `Pattern` section of logback.xml
```xml
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
            <layout class="com.a.eye.skywalking.toolkit.log.logback.v1.x.TraceIdPatternLogbackLayout">
                <Pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%tid] [%thread] %-5level %logger{36} -%msg%n</Pattern>
            </layout>
        </encoder>
    </appender>
```

* When you use `-javaagent` to active the sky-waking tracer, logback will output **traceId**, if it existed. If the tracer is inactive, the output will be `TID: N/A`.
