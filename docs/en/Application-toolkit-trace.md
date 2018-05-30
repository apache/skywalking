* Dependency the toolkit, such as using maven or gradle
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-trace</artifactId>
      <version>${skywalking.version}</version>
   </dependency>
```

* Use `TraceContext.traceId()` API to obtain traceId.
```java
import TraceContext;
...

modelAndView.addObject("traceId", TraceContext.traceId());
```
_Sample codes only_

* Add `@Trace` to any method you want to trace. After that, you can see the span in the Stack.
* Add custom tag in  the context of traced method .
```java
ActiveSpan.tag("my_tag", "my_value");
```
