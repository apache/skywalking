* 使用 maven 和 gradle 依赖相应的工具包
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-log4j-1.x</artifactId>
      <version>{project.release.version}</version>
   </dependency>
```

* 配置layout
```properties
log4j.appender.CONSOLE.layout=TraceIdPatternLayout
```

* 在`layout.ConversionPattern`中设置 `%T`来展示traceid ( 在 2.0-2016版本中, 你应该设置为 %x, [为什么改变配置，请参考相关issue?](https://github.com/wu-sheng/sky-walking/issues/77) )
```properties
log4j.appender.CONSOLE.layout.ConversionPattern=%d [%T] %-5p %c{1}:%L - %m%n
```

* 当你使用`-javaagent`参数激活sky-walking的探针, 如果当前上下文中存在traceid，log4j将在输出**traceId**。如果探针没有被激活，将输出`TID: N/A`.
