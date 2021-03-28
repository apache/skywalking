Changes by Version
==================
Release Notes.

8.4.0
------------------
#### Project
* Incompatible with previous releases when use H2/MySQL/TiDB storage options, due to support multiple alarm rules triggered for one entity.
* Chore: adapt `create_source_release.sh` to make it runnable on Linux.
* Add `package` to `.proto` files, prevent polluting top-level namespace in some languages; The OAP server supports previous agent releases, whereas the previous OAP server (<=8.3.0) won't recognize newer agents since this version (>= 8.4.0).
* Add ElasticSearch 7.10 to test matrix and verify it works.
* Replace Apache RAT with skywalking-eyes to check license headers.
* Set up test of Envoy ALS / MetricsService under Istio 1.8.2 to verify Envoy V3 protocol
* Test: fix flaky E2E test of Kafka.

#### Java Agent
* The operation name of quartz-scheduler plugin, has been changed as the `quartz-scheduler/${className}` format.
* Fix jdk-http and okhttp-3.x plugin did not overwrite the old trace header.
* Add interceptors of method(analyze, searchScroll, clearScroll, searchTemplate and deleteByQuery) for elasticsearch-6.x-plugin.
* Fix the unexpected RunningContext recreation in the Tomcat plugin.
* Fix the potential NPE when trace_sql_parameters is enabled.
* Update `byte-buddy` to 1.10.19.
* Fix thrift plugin trace link broken when intermediate service does not mount agent
* Fix thrift plugin collects wrong args when the method without parameter.
* Fix DataCarrier's `org.apache.skywalking.apm.commons.datacarrier.buffer.Buffer` implementation isn't activated in `IF_POSSIBLE` mode.
* Fix ArrayBlockingQueueBuffer's useless `IF_POSSIBLE` mode list
* Support building gRPC TLS channel but CA file is not required.
* Add witness method mechanism in the agent plugin core.
* Add Dolphinscheduler plugin definition.
* Make sampling still works when the trace ignores plug-in activation.
* Fix mssql-plugin occur ClassCastException when call the method of return generate key.
* The operation name of dubbo and dubbo-2.7.x-plugin, has been changed as the `groupValue/className.methodName` format
* Fix bug that rocketmq-plugin set the wrong tag.
* Fix duplicated `EnhancedInstance` interface added.
* Fix thread leaks caused by the elasticsearch-6.x-plugin plugin.
* Support reading segmentId and spanId with toolkit.
* Fix RestTemplate plugin recording url tag with wrong port
* Support collecting logs and forwarding through gRPC.
* Support config `agent.sample_n_per_3_secs` can be changed in the runtime.
* Support config `agent.ignore_suffix` can be changed in the runtime.
* Support DNS periodic resolving mechanism to update backend service.
* Support config `agent.trace.ignore_path` can be changed in the runtime.
* Added support for transmitting logback 1.x and log4j 2.x formatted & un-formatted messages via gPRC

#### OAP-Backend
* Make meter receiver support MAL.
* Support influxDB connection response format option. Fix some error when use JSON as influxDB response format.
* Support Kafka MirrorMaker 2.0 to replicate topics between Kafka clusters.
* Add the rule name field to alarm record storage entity as a part of ID, to support multiple alarm rules triggered for one entity. The scope id has been removed from the ID.
* Fix MAL concurrent execution issues.
* Fix group name can't be queried in the GraphQL.
* Fix potential gRPC connection leak(not closed) for the channels among OAP instances.
* Filter OAP instances(unassigned in booting stage) of the empty IP in KubernetesCoordinator.
* Add component ID for Python aiohttp plugin requester and server.
* Fix H2 in-memory database table missing issues
* Add component ID for Python pyramid plugin server.
* Add component ID for NodeJS Axios plugin.
* Fix searchService method error in storage-influxdb-plugin.
* Add JavaScript component ID.
* Fix CVE of UninstrumentedGateways in Dynamic Configuration activation.
* Improve query performance in storage-influxdb-plugin.
* Fix the uuid field in GRPCConfigWatcherRegister is not updated.
* Support Envoy {AccessLog,Metrics}Service API V3.
* Adopt the [MAL](../docs/en/concepts-and-designs/mal.md) in Envoy metrics service analyzer.
* Fix the priority setting doesn't work of the ALS analyzers.
* Fix bug that `endpoint-name-grouping.yml` is not customizable in Dockerized case.
* Fix bug that istio version metric type on UI template mismatches the otel rule.
* Improve ReadWriteSafeCache concurrency read-write performance
* Fix bug that if use JSON as InfluxDB.ResponseFormat then NumberFormatException maybe occur.
* Fix `timeBucket` not taking effect in EqualsAndHashCode annotation of some relationship metrics.
* Fix `SharingServerConfig`'s propertie is not correct in the `application.yml`, contextPath -> restConnextPath.
* Istio control plane: remove redundant metrics and polish panel layout.
* Fix bug endpoint name grouping not work due to setting service name and endpoint name out of order.
* Fix receiver analysis error count metrics.
* Log collecting and query implementation.
* Support Alarm to feishu.
* Add the implementation of ConfigurationDiscovery on the OAP side.
* Fix bug in `parseInternalErrorCode` where some error codes are never reached.
* OAL supports multiple values when as numeric.
* Add node information from the Openensus proto to the labels of the samples, to support the identification of the source of the Metric data.
* Fix bug that the same sample name in one MAL expression caused `IllegalArgumentException` in `Analyzer.analyse`.
* Add the text analyzer for querying log in the es storage.
* Chore:  Remove duplicate codes in Envoy ALS handler.
* Remove the strict rule of OAL disable statement parameter.
* Fix a legal metric query adoption bug. Don't support global level metric query.
* Add VM MAL and ui-template configration, support Prometheus node-exporter VM metrics that pushed from OpenTelemetry-collector.
* Remove unused log query parameters.

#### UI
* Fix un-removed tags in trace query.
* Fix unexpected metrics name on single value component.
* Don't allow negative value as the refresh period.
* Fix style issue in trace table view.
* Separation Log and Dashboard selector data to avoid conflicts.
* Fix trace instance selector bug.
* Fix Unnecessary sidebar in tooltips for charts.
* Refactor dashboard query in a common script.
* Implement refreshing data for topology by updating date.
* Implement group selector in the topology.
* Fix all as default parameter for services selector.
* Add icon for Python aiohttp plugin.
* Add icon for Python pyramid plugin.
* Fix topology render all services nodes when groups changed.
* Fix rk-footer utc input's width.
* Update rk-icon and rewrite rk-header svg tags with rk-icon.
* Add icon for http type.
* Fix rk-footer utc without local storage.
* Sort group names in the topology.
* Add logo for Dolphinscheduler.
* Fix dashboard wrong instance.
* Add a legend for the topology.
* Update the condition of unhealthy cube.
* Fix: use icons to replace buttons for task list in profile.
* Fix: support `=` in the tag value in the trace query page.
* Add envoy proxy component logo.
* Chore: set up license-eye to check license headers and add missing license headers.
* Fix prop for instances-survey and endpoints-survey.
* Fix envoy icon in topology.
* Implement the service logs on UI.
* Change the flask icon to light version for a better view of topology dark theme.
* Implement viewing logs on trace page.
* Fix update props of date component.
* Fix query conditions for logs.
* Fix style of selectors to word wrap.
* Fix logs time.
* Fix search ui for logs.

#### Documentation
* Update the documents of backend fetcher and self observability about the latest configurations.
* Add documents about the group name of service.
* Update docs about the latest UI.
* Update the document of backend trace sampling with the latest configuration.
* Update kafka plugin support version to 2.6.1.
* Add FAQ about `Fix compiling on Mac M1 chip`.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/68?closed=1)
