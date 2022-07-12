## 9.2.0

#### Project

* Upgrade Armeria to 1.16.0, Kubernetes Java client to 15.0.1.

#### OAP Server

* Add more entities for Zipkin to improve performance.
* ElasticSearch: scroll id should be updated when scrolling as it may change.
* Mesh: fix only last rule works when multiple rules are defined in metadata-service-mapping.yaml.
* Support sending alarm messages to PagerDuty.
* Support Zipkin kafka collector.
* Add `VIRTUAL` detect type to Process for Network Profiling.
* Add component ID(128) for Java Hutool plugin.
* Add Zipkin query exception handler, response error message for illegal arguments.
* Fix a NullPointerException in the endpoint analysis, which would cause missing MQ-related `LocalSpan` in the trace.
* Add `forEach`, `processRelation` function to MAL expression.
* Add `expPrefix`, `initExp` in MAL config.
* Add component ID(7015) for Python Bottle plugin.
* Remove legacy OAL `percentile` functions, `p99`, `p95`, `p90`, `p75`, `p50` func(s).

#### UI

* Fix query conditions for the browser logs.
* Implement a url parameter to activate tab index.
* Fix clear interval fail when switch autoRefresh to off.
* Optimize log tables.
* Fix log detail pop-up page doesn't work.
* Optimize table widget to hide the whole metric column when no metric is set.
* Implement the Event widget.
* Fix span detail text overlap.
* Add Python Bottle Plugin Logo.
* Implement an association between widgets(line, bar, area graphs) with time

#### Documentation

* Fix invalid links in release docs.
* Clean up doc about event metrics.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/136?closed=1)
