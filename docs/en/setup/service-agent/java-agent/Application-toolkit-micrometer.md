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

* Using `Millisecond` as the time unit.

* Adapt micrometer data convention.

|Micrometer data type|Transform to meter name|Skywalking data type| Description|
|----- |----- |----- |----- |
|Counter|Counter name|Counter|Same with counter|
|Gauges|Gauges name|Gauges|Same with gauges|
|Timer|Timer name + "_count"|Counter|Execute finished count|
| |Timer name + "_sum"|Counter|Total execute finished duration|
| |Timer name + "_max"|Gauges|Max duration of execute finished time|
| |Timer name + "_histogram"|Histogram|Histogram of execute finished duration|
|LongTaskTimer|Timer name + "_active_count"|Gauges|Executing task count|
| |Timer name + "_duration_sum"|Counter|All of executing task sum duration|
| |Timer name + "_max"|Counter|Current longest running task execute duration|
| |Timer name + "_histogram"|Histogram|Executing finished task duration histogram|
|Function Timer|Timer name + "_count"|Gauges|Execute finished timer count|
| |Timer name + "_sum"|Gauges|Execute finished timer total duration|
|Function Counter|Counter name|Gauges|Total count|
|Distribution summary|Summary name + "_count"|Counter|Total record count|
| |Summary name + "_sum"|Counter|Total record amount sum|
| |Summary name + "_max"|Gauges|Max record amount|
| |Summary name + "_histogram"|Gauges|Histogram of the amount|