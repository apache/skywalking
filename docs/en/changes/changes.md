## 9.5.0

#### Project

* Fix `Duplicate class found` due to the `delombok` goal.

#### OAP Server

* Fix wrong layer of metric `user error` in DynamoDB monitoring.
* ElasticSearch storage does not check field types when OAP running in `no-init` mode.
* Support to bind TLS status as a part of component for service topology.
* Fix component ID priority bug.
* Fix component ID of topology overlap due to storage layer bugs.
* [Breaking Change] Enhance JDBC storage through merging tables and managing day-based table rolling.
* [Breaking Change] Sharding-MySQL implementations and tests get removed due to we have the day-based rolling mechanism by default
* Fix otel k8s-cluster rule add namespace dimension for MAL aggregation calculation(Deployment Status,Deployment Spec Replicas)
* Support continuous profiling feature.
* Support collect process level related metrics.
* Fix K8sRetag reads the wrong k8s service from the cache due to a possible namespace mismatch.
* [Breaking Change] Support cross-thread trace profiling. The data structure and query APIs are changed.
* Fix PromQL HTTP API `/api/v1/labels` response missing `service` label.
* Fix possible NPE when initialize `IntList`.
* Support parse PromQL expression has empty labels in the braces for metadata query.
* Support alarm metric OP `!=`.
* Support metrics query indicates whether value == 0 represents actually zero or no data.
* Fix `NPE` when query the not exist series indexes in ElasticSearch storage. 

#### UI
* Revert: cpm5d function. This feature is cancelled from backend.
* Fix: alerting link breaks on the topology.
* Refactor Topology widget to make it more hierarchical.
  1. Choose `User` as the first node.
  2. If `User` node is absent, choose the busiest node(which has the most calls of all).
  3. Do a left-to-right flow process.
  4. At the same level, list nodes from top to bottom in alphabetical order.
* Fix filter ID when ReadRecords metric associates with trace.
* Add AWS API Gateway menu.
* Change trace profiling protocol.

#### Documentation

* Add Profiling related documentations.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/169?closed=1)
