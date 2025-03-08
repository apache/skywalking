## 10.2.0

#### Project
* Add [`doc_values`](https://www.elastic.co/guide/en/elasticsearch/reference/current/doc-values.html) for fields
  that need to be sorted or aggregated in Elasticsearch, and disable all others.
  * This change would not impact the existing deployment and its feature for our official release users.
  * **Warning** If there are custom query plugins for our Elasticsearch indices, this change could break them as
    sort queries and aggregation queries which used the unexpected fields are being blocked.
* [Breaking Change] Rename `debugging-query` module to `status-query` module. Relative exposed APIs are **UNCHANGED**. 
* [Breaking Change] All jars of the `skywalking-oap-server` are no longer published through maven central. We will only 
  publish the source tar and binary tar to the website download page, and docker images to docker hub.
  * **Warning** If you are using the `skywalking-oap-server` as a dependency in your project, you need to download the 
    source tar from the website and publish them to your private maven repository.
* [Breaking Change] Remove H2 as storage option permanently. BanyanDB 0.8(OAP 10.2 required) is easy, stable and 
  production-ready. Don't need H2 as default storage anymore.
* [Breaking Change] Bump up BanyanDB server version to 0.8.0. This version is not compatible with the previous 
  versions. Please upgrade the BanyanDB server to 0.8.0 before upgrading OAP to 10.2.0.
* Bump up nodejs to v22.14.0 for the latest UI(booster-ui) compiling. 

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
* Polish mesh data dispatcher: don't generate Instance/Endpoint metrics if they are empty.
* Adapt the new metadata standardization in Istio 1.24.
* Bump up netty to 4.1.115, grpc to 1.68.1, boringssl to 2.0.69.
* BanyanDB: Support update the Group settings when OAP starting.
* BanyanDB: Introduce index mode and refactor banyandb group settings.
* BanyanDB: Introduce the new Progressive TTL feature.
* BanyanDB: Support update the Schema when OAP starting.
* BanyanDB: Speed up OAP booting while initializing BanyanDB.
* BanyanDB: Support `@EnableSort` on the column to enable sorting for `IndexRule` and set the default to false.
* Support `Get Effective TTL Configurations` API.
* Fix `ServerStatusService.statusWatchers` concurrent modification.
* Add protection for dynamic config change propagate chain.
* Add Ruby component IDs(HttpClient=2, Redis=7, Memcached=20, Elasticsearch=47, Ruby=12000, Sinatra=12001).
* Add component ID(160) for Caffeine.
* Alarm: Support store and query the metrics snapshot when the alarm is triggered.
* Alarm: Remove unused `Alarm Trend` query.
* Fix missing remote endpoint IP address in span query of zipkin query module.
* Fix `hierarchy-definition.yml` config file packaged into start.jar wrongly.
* Add `bydb.dependencies.properties` config file to define server dependency versions.
* Fix `AvgHistogramPercentileFunction` doesn't have proper field definition for `ranks`.
* BanyanDB: Support the new Property data module.
* MQE: Support `top_n_of` function for merging multiple metrics topn query.
* Support `labelAvg` function in the OAL engine.
* Added `maxLabelCount` parameter in the `labelCount` function of OAL to limit the number of labels can be counted.
* Adapt the new Browser API(`/browser/perfData/webVitals`, `/browser/perfData/webInteractions`, `/browser/perfData/resources`) protocol.
* Add Circuit Breaking mechanism.
* BanyanDB: Add support for compatibility checks based on the BanyanDB server's API version.
* MQE: Support `&&(and)`, `||(or)` bool operators.
* OAP self observability: Add JVM heap and direct memory used metrics.
* OAP self observability: Add watermark circuit break/recover metrics.
* AI Pipeline: Support query baseline metrics names and predict metrics value.
* Add `Get Node List in the Cluster` API.
* Add type descriptor when converting Envoy logs to JSON for persistence, to avoid conversion error.
* Bseline: Support query baseline with MQE and use in the Alarm Rule.
* Bump up netty to 4.11.118 to fix CVE-2025-24970.
* Add `Get Alarm Runtime Status` API.
* Add `lock` when query the Alarm metrics window values.
* Add a fail-safe mechanism to prevent traffic metrics inconsistent between in-memory and database server.
* Add more clear logs when oap-cluster-internal data(metrics/traffic) format is inconsistent.
* Optimize metrics cache loading when trace latency greater than cache timeout. 
* Allow calling `lang.groovy.GString` in DSL.
* BanyanDB: fix alarm query result without sort. 
* Add a component ID for Virtual thread executor

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
* Hide entrances to unrelated dashboards in topn list.
* Split topology metric query to avoid exceeding the maximum query complexity.
* Fix view metrics related trace and metrics query.
* Add support collapse span.
* Refactor copy util with Web API.
* Releases an existing object URL.
* Optimize Trace Profiling widget.
* Implement Async Profiling widget.
* Fix inaccurate data query issue on endpoint topology page.
* Update browser dashboard for the new metrics.
* Visualize `Snapshot` on `Alerting` page.
* OAP self observability dashboard: Add JVM heap and direct memory used metrics.
* OAP self observability dashboard: Add watermark circuit break/recover metrics.
* Implement the legend selector in metrics charts.
* Fix repetitive names in router.
* Bump up dependencies.
* Fixes tooltips cannot completely display metrics information.

#### Documentation
* Update release document to adopt newly added revision-based process.
* Improve BanyanDB documentation.
* Improve component-libraries documentation.
* Improve configuration-vocabulary documentation.
* Add `Get Effective TTL Configurations` API documentation.
* Add Status APIs docs.
* Simplified the release process with removing maven central publish relative processes.
* Add Circuit Breaking mechanism doc.
* Add `Get Node List in the Cluster` API doc.
* Remove `meter.md` doc, because `mal.md` has covered all the content.
* Merge `browser-http-api-protocol.md` doc into `browser-protocol.md`.


All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/224?closed=1)

