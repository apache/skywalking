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

