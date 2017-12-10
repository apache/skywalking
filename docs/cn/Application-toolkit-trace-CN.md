* 使用 maven 和 gradle 依赖相应的工具包
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-trace</artifactId>
      <version>${skywalking.version}</version>
   </dependency>
```
&nbsp;&nbsp;&nbsp;[ ![Download](https://api.bintray.com/packages/wu-sheng/skywalking/org.apache.skywalking.apm-toolkit-trace/images/download.svg) ](https://bintray.com/wu-sheng/skywalking/org.apache.skywalking.apm-toolkit-trace/_latestVersion)

* 随时使用 `TraceContext.traceId()` API，在应用程序的任何地方获取traceId.
```java
import TraceContext;
...

modelAndView.addObject("traceId", TraceContext.traceId());
```
_示例代码，仅供参考_

* 对任何需要追踪的方法，使用@Trace标注，则此方法会被加入到追踪链中。
