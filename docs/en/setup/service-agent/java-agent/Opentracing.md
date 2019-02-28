* Dependency the toolkit, such as using maven or gradle
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-opentracing</artifactId>
      <version>{project.release.version}</version>
   </dependency>
```


* Use our OpenTracing tracer implementation
```java
Tracer tracer = new SkywalkingTracer();
Tracer.SpanBuilder spanBuilder = tracer.buildSpan("/yourApplication/yourService");

```
