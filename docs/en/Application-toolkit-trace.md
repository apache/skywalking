* Dependency the toolkit, such as using maven or gradle
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-trace</artifactId>
      <version>${skywalking.version}</version>
   </dependency>
```
&nbsp;&nbsp;&nbsp;[ ![Download](https://api.bintray.com/packages/wu-sheng/skywalking/org.apache.skywalking.apm-toolkit-trace/images/download.svg) ](https://bintray.com/wu-sheng/skywalking/org.apache.skywalking.apm-toolkit-trace/_latestVersion)

* Use `TraceContext.traceId()` API to obtain traceId.
```java
import TraceContext;
...

modelAndView.addObject("traceId", TraceContext.traceId());
```
_Sample codes only_

* Add `@Trace` to any method you want to trace. After that, you can see the span in the Stack.
