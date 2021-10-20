Changes by Version
==================
Release Notes.

8.9.0
------------------
#### Project

* Replace e2e cases to e2e-v2:
  - Simple: JDK, Auth, SSL, mTLS
  - Lua Nginx
  - SelfObservability
  - Gateway
  - Meter
  - Nodejs
  - PHP
  - VM: Prometheus Node Exporter, Zabbix
  - go2sky
  - log
  - Python
  - Storage
  - Cluster
  - Event
  - Profile

#### OAP Server

* Add component definition for `Jackson`.
* Fix that zipkin-receiver plugin is not packaged into dist.
* Upgrade Armeria to 1.12, upgrade OpenSearch test version to 1.1.0.
* Add component definition for `Apache-Kylin`.
* Enhance `get` generation mechanism of OAL engine, support map type of source's field.
* Add `tag`(Map) into All, Service, ServiceInstance and Endpoint sources.
* Fix `funcParamExpression` and `literalExpression` can't be used in the same aggregation function.
* Support cast statement in the OAL core engine.
* Support `(str->long)` and `(long)` for string to long cast statement.
* Support `(str->int)` and `(int)` for string to int cast statement.
* Support Long literal number in the OAL core engine.
* Support literal `string` as parameter of aggregation function.
* Add `attributeExpression` and `attributeExpressionSegment` in the OAL grammar tree to support `map` type for the
  attribute expression.
* Refactor the OAL compiler context to improve readability.
* Fix wrong generated codes of `hashCode` and `remoteHashCode` methods for numeric fields.
* Support `!= null` in OAL engine.
* Add `Message Queue Consuming Count` metric for MQ consuming service and endpoint.
* Add `Message Queue Avg Consuming Latency` metric for MQ consuming service and endpoint.
* Support `-Inf` as bucket in the meter system.
* Fix setting wrong field when combining `Event`s.

#### UI

* Optimize endpoint dependency.
* Show service name by hovering nodes in the sankey chart.
* Add Apache Kylin logo.
* Add ClickHouse logo.
* Optimize the style and add tips for log conditions.
* Fix the condition for trace table.
* Optimize profile functions.
* Implement a reminder to clear cache for dashboard templates.

#### Documentation

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/101?closed=1)

------------------
Find change logs of all versions [here](changes).
