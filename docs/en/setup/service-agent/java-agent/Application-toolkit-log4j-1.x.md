* Dependency the toolkit, such as using maven or gradle
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-log4j-1.x</artifactId>
      <version>{project.release.version}</version>
   </dependency>
```

* Config a layout
```properties
log4j.appender.CONSOLE.layout=TraceIdPatternLayout
```

* set `%T` in `layout.ConversionPattern` ( In 2.0-2016, you should use %x, [Why change?](https://github.com/wu-sheng/sky-walking/issues/77) )
```properties
log4j.appender.CONSOLE.layout.ConversionPattern=%d [%T] %-5p %c{1}:%L - %m%n
```

* When you use `-javaagent` to active the sky-walking tracer, log4j will output **traceId**, if it existed. If the tracer is inactive, the output will be `TID: N/A`.
