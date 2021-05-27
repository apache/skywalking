Changes by Version
==================
Release Notes.

8.6.0
------------------
#### Project
* Add OpenSearch as storage option.
* Upgrade Kubernetes Java client dependency to 11.0.
* Fix plugin test script error in macOS.
* Make the number of core worker in meter converter thread pool configurable.

#### Java Agent
* Add `trace_segment_ref_limit_per_span` configuration mechanism to avoid OOM.
* Improve `GlobalIdGenerator` performance.
* Add an agent plugin to support elasticsearch7.
* Add `jsonrpc4j` agent plugin.
* new options to support multi skywalking cluster use same kafka cluster(plugin.kafka.namespace)
* resolve agent has no retries if connect kafka cluster failed when bootstrap
* Add Seata in the component definition. Seata plugin hosts on Seata project.
* Extended Kafka plugin to properly trace consumers that have topic partitions directly assigned.
* Support Kafka consumer 2.8.0.
* Support print SkyWalking context to logs.
* Add `MessageListener` enhancement in pulsar plugin.
* fix a bug that spring-mvc set an error endpoint name if the controller class annotation implements an interface.
* Add an optional agent plugin to support mybatis.
* Add `spring-cloud-gateway-3.x` optional plugin.
* Add `okhttp-4.x` plugin.
* Fix NPE when thrift field is nested in plugin `thrift`
* Fix possible NullPointerException in agent's ES plugin.
* Fix the conversion problem of float type in ConfigInitializer.
* Fixed part of the dynamic configuration of ConfigurationDiscoveryService that does not take effect under certain circumstances.
* Introduce method interceptor API v2
* Fix ClassCast issue for RequestHolder/ResponseHolder.
* fixed `jdk-threading-plugin` memory leak.
* Optimize multiple field reflection opeartion in Fiegn plugin.

#### OAP-Backend
* BugFix: filter invalid Envoy access logs whose socket address is empty.
* Fix K8s monitoring the incorrect metrics calculate. 
* Loop alarm into event system.
* Support alarm tags.
* Support WeLink as a channel of alarm notification.
* Fix: Some defensive codes didn't work in `PercentileFunction combine`.
* CVE: fix Jetty vulnerability. https://nvd.nist.gov/vuln/detail/CVE-2019-17638
* Fix: MAL function would miss samples name after creating new samples.
* perf: use iterator.remove() to remove modulesWithoutProvider
* Support analyzing Envoy TCP access logs and persist error TCP logs.
* Fix: Envoy error logs are not persisted when no metrics are generated
* Fix: Memory leakage of low version etcd client. [fix-issue](https://github.com/jurmous/etcd4j/pull/185)
* Allow multiple definitions as fallback in metadata-service-mapping.yaml file and `k8sServiceNameRule`.
* Fix: NPE when configmap has no data.
* Fix: Dynamic Configuration key `slowTraceSegmentThreshold` not work
* Fix: `!=` is not supported in oal when parameters are numbers.
* Include events of the entity(s) in the alarm.
* Support `native-json` format log in kafka-fetcher-plugin.
* Fix counter misuse in the alarm core. Alarm can't be triggered in time.

#### UI
* Add logo for kong plugin.
* Add apisix logo.
* Refactor js to ts for browser logs and style change.
* When creating service groups in the topology, it is better if the service names are sorted.
* Add tooltip for dashboard component.
* Fix style of endpoint dependency.
* Support search and visualize alarms with tags.
* Fix configurations on dashboard.
* Support to configure the maximum number of displayed items.
* After changing the durationTime, the topology shows the originally selected group or service.
* remove the no use maxItemNum for labeled-value metric, etc.
* Add Azure Functions logo.
* Support search Endpoint use keyword params in trace view.

#### Documentation
* Polish k8s monitoring otel-collector configuration example.
* Print SkyWalking context to logs configuration example.
* Update doc about metrics v2 APIs.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/84?closed=1)

------------------
Find change logs of all versions [here](changes).
