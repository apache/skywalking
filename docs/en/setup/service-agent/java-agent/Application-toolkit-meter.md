* Dependency the toolkit, such as using maven or gradle
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-meter</artifactId>
      <version>${skywalking.version}</version>
   </dependency>
```

If you're using Spring sleuth, you could use [Spring Sleuth Setup](./../../backend/spring-sleuth-setup.md).

* `Counter` API represents a single monotonically increasing counter, automatic collect data and report to backend.
```java
import org.apache.skywalking.apm.toolkit.meter.MeterFactory;

Counter counter = MeterFactory.counter(meterName).tag("tagKey", "tagValue").mode(Counter.Mode.INCREMENT).build();
counter.increment(1d);
```
1. `MeterFactory.counter` Create a new counter builder with the meter name.
1. `Counter.Builder.tag(String key, String value)` Mark a tag key/value pair.
1. `Counter.Builder.mode(Counter.Mode mode)` Change the counter mode, `RATE` mode means reporting rate to the backend.
1. `Counter.Builder.build()` Build a new `Counter` which is collected and reported to the backend.
1. `Counter.increment(double count)` Increment count to the `Counter`, It could be a positive value.

* `Gauge` API represents a single numerical value.
```java
import org.apache.skywalking.apm.toolkit.meter.MeterFactory;

ThreadPoolExecutor threadPool = ...;
Gauge gauge = MeterFactory.gauge(meterName, () -> threadPool.getActiveCount()).tag("tagKey", "tagValue").build();
```
1. `MeterFactory.gauge(String name, Supplier<Double> getter)` Create a new gauge builder with the meter name and supplier function, this function need to return a `double` value.
1. `Gauge.Builder.tag(String key, String value)` Mark a tag key/value pair.
1. `Gauge.Builder.build()` Build a new `Gauge` which is collected and reported to the backend.

* `Histogram` API represents a summary sample observations with customize buckets.
```java
import org.apache.skywalking.apm.toolkit.meter.MeterFactory;

Histogram histogram = MeterFactory.histogram("test").tag("tagKey", "tagValue").steps(Arrays.asList(1, 5, 10)).minValue(0).build();
histogram.addValue(3);
```
1. `MeterFactory.histogram(String name)` Create a new histogram builder with the meter name.
1. `Histogram.Builder.tag(String key, String value)` Mark a tag key/value pair.
1. `Histogram.Builder.steps(List<Double> steps)` Set up the max values of every histogram buckets.
1. `Histogram.Builder.minValue(double value)` Set up the minimal value of this histogram, default is `0`.
1. `Histogram.Builder.build()` Build a new `Histogram` which is collected and reported to the backend.
1. `Histogram.addValue(double value)` Add value into the histogram, automatically analyze what bucket count needs to be increment. rule: count into [step1, step2).
