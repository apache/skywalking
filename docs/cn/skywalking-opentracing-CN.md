* 使用 maven 和 gradle 依赖相应的工具包
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-opentracing</artifactId>
      <version>{project.release.version}</version>
   </dependency>
```

&nbsp;&nbsp;&nbsp;[ ![Download](https://api.bintray.com/packages/wu-sheng/skywalking/org.apache.skywalking.apm-toolkit-opentracing/images/download.svg) ](https://bintray.com/wu-sheng/skywalking/org.apache.skywalking.apm-toolkit-opentracing/_latestVersion)

* 使用OpenTracing的标准API和桥接器，使用手动埋点
```java
Tracer tracer = new org.apache.skywalking.apm.toolkit.opentracing.SkyWalkingTracer();
Tracer.SpanBuilder spanBuilder = tracer.buildSpan("/yourApplication/yourService");

```
