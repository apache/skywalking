## 8.8.0

#### Project

* Split javaagent into skywalking-java repository. https://github.com/apache/skywalking-java
* Merge `Dockerfile`s from apache/skywalking-docker into this codebase.

#### OAP Server

* Fix CVE-2021-35515, CVE-2021-35516, CVE-2021-35517, CVE-2021-36090. Upgrade org.apache.commons:commons-compress to
  1.21.
* kubernetes java client upgrade from 12.0.1 to 13.0.0
* Add `event` http receiver
* Support Metric level function `serviceRelation` in `MAL`.
* Support envoy metrics binding into the topology.
* Fix openapi-definitions folder not being read correctly.
* Trace segment wouldn't be recognized as a TopN sample service. Add through #4694 experimentally, but it caused
  performance impact.
* Remove `version` and `endTime` in the segment entity. Reduce indexing payload.
* Fix `mapper_parsing_exception` in ElasticSearch 7.14.
* Support component IDs for Go-Kratos framework.
* [Break Change] Remove endpoint name in the trace query condition. Only support `query by endpoint id`.
* Fix `ProfileSnapshotExporterTest` case on `OpenJDK Runtime Environment AdoptOpenJDK-11.0.11+9 (build 11.0.11+9)`,
  MacOS.
* [Break Change] Remove page path in the browser log query condition. Only support `query by page path id`.
* [Break Change] Remove endpoint name in the backend log query condition. Only support `query by endpoint id`.
* [Break Change] Fix typo for a column `page_path_id`(was `pate_path_id`) of storage entity `browser_error_log`.
* Add component id for Python falcon plugin.
* Add `rpcStatusCode` for `rpc.status_code` tag. The `responseCode` field is marked as deprecated and replaced
  by `httpResponseStatusCode` field.
* Remove the duplicated tags to reduce the storage payload.
* Add a new API to test log analysis language.
* Harden the security of Groovy-based DSL, MAL and LAL.
* Fix distinct in Service/Instance/Endpoint query is not working.
* Support collection type in dynamic configuration core.
* Support zookeeper grouped dynamic configurations.
* Fix NPE when OAP nodes synchronize events with each other in cluster mode.
* Support k8s configmap grouped dynamic configurations.
* Add desc sort function in H2 and ElasticSearch implementations of IBrowserLogQueryDAO
* Support configure sampling policy by `configuration module` dynamically and static configuration
  file `trace-sampling-policy-settings.yml` for service dimension on the backend side. Dynamic
  configurations `agent-analyzer.default.sampleRate` and `agent-analyzer.default.slowTraceSegmentThreshold` are replaced
  by `agent-analyzer.default.traceSamplingPolicy`. Static configurations `agent-analyzer.default.sampleRate`
  and `agent-analyzer.default.slowTraceSegmentThreshold` are replaced
  by `agent-analyzer.default.traceSamplingPolicySettingsFile`.
* Fix dynamic configuration watch implementation current value not null when the config is deleted.
* Fix `LoggingConfigWatcher` return `watch.value` would not consistent with the real configuration content.
* Fix `ZookeeperConfigWatcherRegister.readConfig()` could cause `NPE` when `data.getData()` is null.
* Support nacos grouped dynamic configurations.
* Support for filter function filtering of int type values.
* Support mTLS for gRPC channel.
* Add yaml file suffix limit when reading ui templates.
* Support consul grouped dynamic configurations.
* Fix `H2MetadataQueryDAO.searchService` doesn't support auto grouping.
* Rebuilt ElasticSearch client on top of their REST API.
* Fix ElasticSearch storage plugin doesn't work when hot reloading from `secretsManagementFile`.
* Support etcd grouped dynamic configurations.
* Unified the config word `namespace` in the project.
* Switch JRE base image for dev images.
* Support apollo grouped dynamic configurations.
* Fix `ProfileThreadSnapshotQuery.queryProfiledSegments` adopts a wrong sort function
* Support gRPC sync grouped dynamic configurations.
* Fix `H2EventQueryDAO` doesn't sort data by Event.START_TIME and uses a wrong pagination query.
* Fix `LogHandler` of `kafka-fetcher-plugin` cannot recognize namespace.
* Improve the speed of writing TiDB by batching the SQL execution.
* Fix wrong service name when IP is node IP in `k8s-mesh`.
* Support dynamic configurations for openAPI endpoint name grouping rule.
* Add component definition for `Alibaba Druid` and `HikariCP`.
* Fix `Hour` and `Day` dimensionality metrics not accurate, due to the cache read-then-clear mechanism conflicts with
  low down metrics flush period added in 8.7.0.
* Fix `Slow SQL sampling` not accurate, due to TopN works conflict with cache read-then-clear mechanism.
* The persistent cache is only read when necessary.
* Add component definition for `Alibaba Fastjson`.
* Fix entity(service/instance/endpoint) names in the MAL system(prometheus, native meter, open census, envoy metric
  service) are not controlled by core's naming-control mechanism.
* Upgrade netty version to 4.1.68.Final avoid cve-2021-37136.

#### UI

* Fix not found error when refresh UI.
* Update endpointName to endpointId in the query trace condition.
* Add Python falcon icon on the UI.
* Fix searching endpoints with keywords.
* Support clicking the service name in the chart to link to the trace or log page.
* Implement the Log Analysis Language text regexp debugger.
* Fix fetching nodes and calls with serviceIds on the topology side.
* Implement Alerts for query errors.
* Fixes graph parameter of query for topology metrics.

#### Documentation

* Add a section in `Log Collecting And Analysis` doc, introducing the new Python agent log reporter.
* Add one missing step in `otel-receiver` doc about how to activate the default receiver.
* Reorganize dynamic configuration doc.
* Add more description about meter configurations in `backend-meter` doc.
* Fix typo in `endpoint-grouping-rules` doc.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/96?closed=1)

