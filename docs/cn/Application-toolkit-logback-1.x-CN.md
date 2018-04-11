* 使用 maven 和 gradle 依赖相应的工具包
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-logback-1.x</artifactId>
      <version>{project.release.version}</version>
   </dependency>
```

* 在logback.xml中的`Pattern`配制节中，设置`%tid`
```xml
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
            <layout class="org.apache.skywalking.apm.toolkit.log.logback.v1.x.TraceIdPatternLogbackLayout">
                <Pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%tid] [%thread] %-5level %logger{36} -%msg%n</Pattern>
            </layout>
        </encoder>
    </appender>
```

* 当你使用`-javaagent`参数激活sky-walking的探针, 如果当前上下文中存在traceid，logback将在输出**traceId**。如果探针没有被激活，将输出`TID: N/A`.
