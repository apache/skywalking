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

* `ActiveSpan.error()` support user to make the span of traced method error
* `ActiveSpan.error(String errorMsg)` support user to log some error message
* `ActiveSpan.error(Throwable throwable)` support user to error a Throwable
* `ActiveSpan.debug(String debugMsg)` support user to log some debug message
* `ActiveSpan.info(String infoMsg)` support user to log some info message
```java
ActiveSpan.tag("my_tag", "my_value");
ActiveSpan.error();
ActiveSpan.error("Test-Error-Reason");

ActiveSpan.error(new RuntimeException("Test-Error-Throwable"));
ActiveSpan.info("Test-Info-Msg");
ActiveSpan.debug("Test-debug-Msg");
```

