## 9.4.0

#### Project

#### OAP Server

* Add `ServerStatusService` in the core module to provide a new way to expose booting status to other modules.
* Adds Micrometer as a new component.(ID=141)
* Refactor session cache in MetricsPersistentWorker.
* Cache enhancement - don't read new metrics from database in minute dimensionality.

```
    // When
    // (1) the time bucket of the server's latest stability status is provided
    //     1.1 the OAP has booted successfully
    //     1.2 the current dimensionality is in minute.
    // (2) the metrics are from the time after the timeOfLatestStabilitySts
    // (3) the metrics don't exist in the cache
    // the kernel should NOT try to load it from the database.
    //
    // Notice, about condition (2),
    // for the specific minute of booted successfully, the metrics are expected to load from database when
    // it doesn't exist in the cache.
```

* Remove the offset of metric session timeout according to worker creation sequence.
* Correct `MetricsExtension` annotations declarations in manual entities.
* Support component IDs' priority in process relation metrics.
* Remove abandon logic in MergableBufferedData, which caused unexpected no-update.
* Fix miss set `LastUpdateTimestamp` that caused the metrics session to expire.
* Rename MAL rule `spring-sleuth.yaml` to `spring-micrometer.yaml`.
* Fix memory leak in Zipkin API.

#### UI

#### Documentation

* Remove Spring Sleuth docs, and add `Spring MicroMeter Observations Analysis` with the latest Java agent side
  enhancement.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/160?closed=1)
