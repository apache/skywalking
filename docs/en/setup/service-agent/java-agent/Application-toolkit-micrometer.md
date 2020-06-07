* Dependency the toolkit, such as using maven or gradle
```xml
   <dependency>
      <groupId>org.apache.skywalking</groupId>
      <artifactId>apm-toolkit-micrometer-registry</artifactId>
      <version>${skywalking.version}</version>
   </dependency>
```

* Using `SkywalkingMeterRegistry` as the registry, it could automatic collect your meter to out meter system.
```java
SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();

// Also you could using composite registry to combine multiple meter registry, such as collect to Skywalking and prometheus
CompositeMeterRegistry compositeRegistry = new CompositeMeterRegistry();
compositeRegistry.add(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
compositeRegistry.add(new SkywalkingMeterRegistry());
```

* Using snake case as the naming convention. Such as `test.meter` will be send to `test_meter`.

* Adapt micrometer data convention.

|Micrometer data type|Transform to meter name|Skywalking data type|
|----- |----- |----- |
|Counter|Counter name|Counter|
|Gauges|Gauges name|Gauges|
|Timer|Timer name + "_count"|Counter|
| |Timer name + "_sum"|Counter|
| |Timer name + "_max|Gauges|
||LongTaskTimer|Timer name + "_active_count"|Gauges|
| |Timer name + "_duration_sum"|Counter|
|Function Timer|Timer name + "_count"|Gauges|
| |Timer name + "_sum"|Gauges|
|Function Counter|Counter name|Gauges|
|Distribution summary|Summary name + "_count"|Counter|
| |Summary name + "_sum"|Counter|
| |Summary name + "_max"|Gauges|

Also, If the meter support the histogram/percentile, such as Timer, Summary, support using currently meter name + "_histogram" / "_percentile" to collect the histogram/percentile.

