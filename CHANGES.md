Changes by Version
==================
Release Notes.

8.1.0
------------------
#### Project
* Support Kafka as an optional trace, JVM metrics, profiling snapshots and meter system data transport layer.
* Support Meter system, including the native metrics APIs and the Spring Sleuth adoption.
* Support JVM thread metrics.  

#### Java Agent
* [**Core**] Fix the concurrency access bug in the Concurrency ClassLoader Case. 
* [**Core**] Separate the config of the plugins from the core level.
* [**Core**] Support instrumented class cached in memory or file, to be compatible with other agents, such as Arthas.
* Add logic endpoint concept. Could analysis any span or tags flagged by the logic endpoint.
* Add Spring annotation component name for UI visualization only.
* Add support to trace `Call procedures` in MySQL plugin.
* Support GraphQL plugin.
* Support Quasar fiber plugin.
* Support InfluxDB java client plugin.
* Support brpc java plugin
* Support `ConsoleAppender` in the logback v1 plugin.
* Enhance vert.x endpoint names.
* Optimize the code to prevent mongo statements from being too long.
* Fix WebFlux plugin concurrency access bug.
* Fix ShardingSphere plugins internal conflicts.
* Fix duplicated Spring MVC endpoint.
* Fix lettuce plugin sometimes trace doesn‘t show span layer. 
* Fix `@Tag` returnedObject bug.

#### OAP-Backend
* Support Jetty Server advanced configurations.
* Support label based filter in the prometheus fetcher and OpenCensus receiver.
* Support using k8s configmap as the configuration center.
* Support OAP health check, and storage module health check.
* Support sampling rate in the dynamic configuration.
* Add `endpoint_relation_sla` and `endpoint_relation_percentile` for endpoint relationship metrics.
* Add components for Python plugins, including Kafka, Tornado, Redis, Django, PyMysql.
* Add components for Golang SDK.
* Add Nacos 1.3.1 back as an optional cluster coordinator and dynamic configuration center.
* Enhance the metrics query for ElasticSearch implementation to increase the stability.
* Reduce the length of storage entity names in the self-observability for MySQL and TiDB storage.
* Fix labels are missing in Prometheus analysis context.
* Fix column length issue in MySQL/TiDB storage.
* Fix no data in 2nd level aggregation in self-observability. 
* Fix searchService bug in ES implementation.
* Fix wrong validation of endpoint relation entity query.
* Fix the bug caused by the OAL debug flag.
* Fix endpoint dependency bug in MQ and uninstrumented proxy cases.
* Fix time bucket conversion issue in the InfluxDB storage implementation.
* Update k8s client to 8.0.0

#### UI
* Support endpoint dependency graph.
* Support x-scroll of trace/profile page
* Fix database selector issue.
* Add the bar chart in the UI templates.

#### Document
* Update the user logo wall.
* Add backend configuration vocabulary document.
* Add agent installation doc for Tomcat9 on Windows.
* Add istioctl ALS commands for the document.
* Fix TTL documentation.
* Add FAQ doc about thread instrumentation.

#### CVE
* Fix fuzzy query sql injection in the MySQL/TiDB storage. 

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/52?closed=1)

8.0.1
------------------

#### OAP-Backend
* Fix `no-init` mode is not working in ElasticSearch storage.


8.0.0
------------------

#### Project
* v3 protocol is added and implemented. All previous releases are incompatible with 8.x releases.
* Service, Instance, Endpoint register mechanism and inventory storage entities are removed.
* New GraphQL query protocol is provided, the legacy procotol is still supported(plan to remove at the end of this year).
* Support Prometheus network protocol. Metrics in Prometheus format could be transferred into SkyWalking.
* Python agent provided.
* All inventory caches have been removed.
* Apache ShardingSphere(4.1.0, 4.1.1) agent plugin provided.

#### Java Agent
* Add MariaDB plugin.
* Vert.x plugin enhancement. More cases are covered.
* Support v3 extension header.
* Fix ElasticSearch 5.x plugin TransportClient error.
* Support Correlation protocol v1.
* Fix Finagle plugin bug, in processing Noop Span.
* Make `CommandService` daemon to avoid blocking target application shutting down gracefully.
* Refactor spring cloud gateway plugin and support tracing spring cloud gateway 2.2.x 

#### OAP-Backend
* Support meter system for Prometheus adoption. In future releases, we will add native meter APIs and MicroMeter(Sleuth) system.
* Support endpoint grouping.
* Add **SuperDataSet** annotation for storage entity. 
* Add **superDatasetIndexShardsFactor** in the ElasticSearch storage, to provide more shards for @SuperDataSet annotated entites. Typically TraceSegment.
* Support alarm settings for relationship of service, instance, and endpoint level metrics.
* Support alarm settings for database(conjecture node in tracing scenario).
* Data Model could be added in the runtime, don't depend on the bootstrap sequence anymore.
* Reduce the memory cost, due to no inventory caches.
* No buffer files in tracing and service mesh cases.
* New ReadWriteSafe cache implementation. Simplify codes.
* Provide default way for metrics query, even the metrics doesn't exist.
* New GraphQL query protocol is provided. Support the metrics type query.
* Set up length rule of service, instance, and endpoint.
* Adjust the default jks for ElasticSearch to empty.
* Fix Apdex function integer overflow issue. 
* Fix profile storage issue.
* Fix TTL issue.
* Fix H2 column type bug.
* Add JRE 8-14 test for the backend. 

#### UI
* UI dashboard is 100% configurable to adopt new metrics definited in the backend.

#### Document
* Add v8 upgrade document.
* Make the coverage accurate including UT and e2e tests.
* Add miss doc about collecting parameters in the profiled traces.

#### CVE
* Fix SQL Injection vulnerability in H2/MySQL implementation.
* Upgrade Nacos to avoid the FastJson CVE in high frequency.
* Upgrade jasckson-databind to 2.9.10. 


All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/45?closed=1)

7.0.0 release
------------------
You could find all CHANGES of 7.0.0 at [here](https://github.com/apache/skywalking/blob/v7.0.0/CHANGES.md)

6.x releases
------------------
You could find all CHANGES of 6.x at [here](https://github.com/apache/skywalking/blob/6.x/CHANGES.md)

5.x releases
------------------
You could find all CHANGES of 5.x at [here](https://github.com/apache/skywalking/blob/5.x/CHANGES.md)
