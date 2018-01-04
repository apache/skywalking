* 使用 maven 和 gradle 依赖相应的工具包
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-log4j-2.x</artifactId>
      <version>{project.release.version}</version>
   </dependency>
```

* 在log4j2.xml中的pattern 配置节，配置`[%traceId]`
```xml
   <Appenders>
      <Console name="Console" target="SYSTEM_OUT">
         <PatternLayout pattern="%d [%traceId] %-5p %c{1}:%L - %m%n"/>
      </Console>
   </Appenders>
```
* 当你使用`-javaagent`参数激活sky-walking的探针, 如果当前上下文中存在traceid，log4j2将在输出**traceId**。如果探针没有被激活，将输出`TID: N/A`.
