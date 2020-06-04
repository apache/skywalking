* Dependency the toolkit, such as using maven or gradle
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-meter</artifactId>
      <version>${skywalking.version}</version>
   </dependency>
```

* `Counter` API represents a single monotonically increasing counter, automatic collect data and report to backend.
```java
Counter counter = Counter.create(meterName).tag("tagKey", "tagValue").build();
counter.increment(1d);
```
1. `Counter.create` Create a new counter builder with the meter name.
1. `Counter.Builder.tag(String key, String value)` Mark a tag key/value pair.
1. `Counter.Builder.build()` To build a new `Counter` and it will register to the agent, automatically collect data and report to the backend.
1. `Counter.increment(double count)` Increment count to the `Counter`, It could be a positive/negative value.

* `Gauge` API represents a single numerical value.
```java
ThreadPoolExecutor threadPool = ...;
Gauge gauge = Gauge.create(meterName, () -> threadPool.getActiveCount()).tag("tagKey", "tagValue").build();
```
1. `Gauge.create(String name, Supplier<Double> getter)` Create a new gauge builder with the meter name and supplier function, this function need to return a `double` value.
1. `Gauge.Builder.tag(String key, String value)` Mark a tag key/value pair.
1. `Gauge.Builder.build()` To build a new `Gauge` and it will register to the agent, automatically collect data and report to the backend.

* `Histogram` API represents a summary sample observations with customize buckets.
```java
Histogram histogram = Histogram.create("test").tag("tagKey", "tagValue").steps(Arrays.asList(1, 5, 10)).exceptMinValue(0).build();
histogram.addCountToStep(5, 1L);
histogram.addValue(3);
```
1. `Histogram.create(String name)` Create a new histogram builder with the meter name.
1. `Histogram.Builder.tag(String key, String value)` Mark a tag key/value pair.
1. `Histogram.Builder.steps(List<Double> steps)` Setting the histogram buckets.
1. `Histogram.Builder.exceptMinValue(double value)` Setting the histogram min value will accept, it will help the Skywalking UI to display, default is `0`.
1. `Histogram.Builder.build()` To build a new `Histogram` and it will register to the agent, automatically collect data and report to the backend.
1. `Histogram.addCountToStep(double step, long count)` Add count to appoint step. Only support an existing step.
1. `Histogram.addValue(double value)` Add value into the histogram, automatically analyze what bucket count needs to be increment. rule: count into [step1, step2).

* `Percentile` API represents a percentile calculator.
```java
Percentile percentile = Percentile.create("test").build();
percentile.record(5);
```
1. `Percentile.create(String name)` Create a new percentile builder with the meter name.
1. `Percentile.Builder.tag(String key, String value)` Mark a tag key/value pair.
1. `Percentile.Builder.build()` To build a new `Percentile` and it will register to the agent, automatically collect data and report to the backend.
1. `Percentile.addValue(double value)` Add value into the percentile.
