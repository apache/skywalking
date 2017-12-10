* Dependency the toolkit, such as using maven or gradle
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-opentracing</artifactId>
      <version>{project.release.version}</version>
   </dependency>
```

&nbsp;&nbsp;&nbsp;[ ![Download](https://api.bintray.com/packages/wu-sheng/skywalking/org.apache.skywalking.apm-toolkit-opentracing/images/download.svg) ](https://bintray.com/wu-sheng/skywalking/org.apache.skywalking.apm-toolkit-opentracing/_latestVersion)

* Use our OpenTracing tracer implementation
```java
Tracer tracer = new SkywalkingTracer();
Tracer.SpanBuilder spanBuilder = tracer.buildSpan("/yourApplication/yourService");

```
