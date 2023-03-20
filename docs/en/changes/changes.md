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
#### Documentation


All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/169?closed=1)
