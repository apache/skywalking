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

* `ActiveSpan.error()` Mark the current span as error status.
* `ActiveSpan.error(String errorMsg)` Mark the current span as error status with a message.
* `ActiveSpan.error(Throwable throwable)` Mark the current span as error status with a Throwable.
* `ActiveSpan.debug(String debugMsg)` Add a debug level log message in the current span.
* `ActiveSpan.info(String infoMsg)` Add an info level log message in the current span.

```java
ActiveSpan.tag("my_tag", "my_value");
ActiveSpan.error();
ActiveSpan.error("Test-Error-Reason");

ActiveSpan.error(new RuntimeException("Test-Error-Throwable"));
ActiveSpan.info("Test-Info-Msg");
ActiveSpan.debug("Test-debug-Msg");
```

