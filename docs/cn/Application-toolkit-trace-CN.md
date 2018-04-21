* 使用 maven 和 gradle 依赖相应的工具包
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-trace</artifactId>
      <version>${skywalking.version}</version>
   </dependency>
```

* 随时使用 `TraceContext.traceId()` API，在应用程序的任何地方获取traceId.
```java
import TraceContext;
...

modelAndView.addObject("traceId", TraceContext.traceId());
```
_示例代码，仅供参考_

* 对任何需要追踪的方法，使用 `@Trace` 标注，则此方法会被加入到追踪链中。
* 在被追踪的方法中自定义 tag.
```java
ActiveSpan.tag("my_tag", "my_value");
```
