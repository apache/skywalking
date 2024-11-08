## 10.2.0

#### OAP Server

* Skip processing OTLP metrics data points with flag `FLAG_NO_RECORDED_VALUE`, which causes exceptional result.
* Add self observability metrics for GraphQL query, `graphql_query_latency`.
* Reduce the count of process index and adding time range when query process index.
* Bump up Apache commons-io to 2.17.0.
* Polish eBPF so11y metrics and add error count for query metrics.
* Support query endpoint list with duration parameter(optional).
* Change the endpoint_traffic to updatable for the additional column `last_ping`.
* Add Component ID(5023) for the GoZero framework.
* Support Kong monitoring.
* Support adding additional attr[0-5] for service/endpoint level metrics.
* Support async-profiler feature for performance analysis.
* Add metrics value owner for metrics topN query result.
* Add naming control for `EndpointDependencyBuilder`.
* The index type `BanyanDB.IndexRule.IndexType#TREE` is removed. All indices are using `IndexType#INVERTED` now.
* Add max query size settings to BanyanDB.
* Fix "BanyanDBTraceQueryDAO.queryBasicTraces" doesn't support querying by "trace_id".

#### UI

* Add support for case-insensitive search in the dashboard list.
* Add content decorations to Table and Card widgets.
* Support the endpoint list widget query with duration parameter.
* Support ranges for Value Mappings.
* Add service global topN widget on `General-Root`, `Mesh-Root`, `K8S-Root` dashboard.
* Fix initialization dashboards.
* Update the Kubernetes metrics for reduce multiple metrics calculate in MQE.
* Support view data value related dashboards in TopList widgets.
* Add endpoint global topN widget on `General-Root`, `Mesh-Root`.
* Implement owner option for TopList widgets in related trace options.


#### Documentation
* Update release document to adopt newly added revision-based process.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/224?closed=1)
