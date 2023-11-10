## 9.7.0

#### Project

* Bump Java agent to 9.1-dev in the e2e tests.
* Bump up netty to 4.1.100.
* Update Groovy 3 to 4.0.15.
* Support packaging the project in JDK21. Compiler source and target remain in JDK11.

#### OAP Server

* ElasticSearchClient: Add `deleteById` API.
* Fix Custom alarm rules are overwritten by 'resource/alarm-settings.yml'
* Support Kafka Monitoring.
* Support Pulsar server and BookKeeper server Monitoring.
* [Breaking Change] Elasticsearch storage merge all management data indices into one index `management`, 
  including `ui_template，ui_menu，continuous_profiling_policy`.
* Add a release mechanism for alarm windows when it is expired in case of OOM.
* Fix Zipkin trace receiver response: make the HTTP status code from `200` to `202`.
* Update BanyanDB Java Client to 0.5.0.
* Fix getInstances query in the BanyanDB Metadata DAO.
* BanyanDBStorageClient: Add `keepAliveProperty` API.
* Fix table exists check in the JDBC Storage Plugin.
* Enhance extensibility of HTTP Server library.
* Adjust `AlarmRecord` alarmMessage column length to 512.
* Fix `EventHookCallback` build event: build the layer from `Service's Layer`.
* Fix `AlarmCore` doAlarm: catch exception for each callback to avoid interruption.
* Optimize queryBasicTraces in TraceQueryEsDAO.
* Fix `WebhookCallback` send incorrect messages, add catch exception for each callback HTTP Post.
* Fix AlarmRule expression validation: add labeled metrics mock data for check.
* Support collect ZGC memory pool metrics.
* Add a component ID for Netty-http (ID=151).
* Add a component ID for Fiber (ID=5021).
* BanyanDBStorageClient: Add `define(Property property, PropertyStore.Strategy strategy)` API.
* Correct the file format and fix typos in the filenames for monitoring Kafka's e2e tests.
* Support extract timestamp from patterned datetime string in LAL.
* Support output key parameters in the booting logs.
* Fix cannot query zipkin traces with `annotationQuery` parameter in the JDBC related storage.
* Fix `limit` doesn't work for `findEndpoint` API in ES storage.
* Isolate MAL CounterWindow cache by metric name.

#### UI

* Add new menu for kafka monitoring.
* Fix independent widget duration.
* Fix the display height of the link tree structure.
* Replace the name by shortName on service widget.
* Refactor: update pagination style. No visualization style change.
* Apply MQE on K8s layer UI-templates.
* Fix icons display in trace tree diagram.
* Fix: update tooltip style to support multiple metrics scrolling view in a metrics graph.
* Add a new widget to show jvm memory pool detail.
* Fix: avoid querying data with empty parameters.
* Add a title and a description for trace segments.
* Add Netty icon for Netty HTTP plugin.
* Add Pulsar menu i18n files.

#### Documentation

* Separate storage docs to different files, and add an estimated timeline for BanyanDB(end of 2023). 
* Add topology configuration in UI-Grafana doc.
* Add missing metrics to the `OpenTelemetry Metrics` doc.
* Polish docs of `Concepts and Designs`.
* Fix incorrect notes of slowCacheReadThreshold.
* Update OAP setup and cluster coordinator docs to explain new booting parameters table in the logs, and how to setup
  cluster mode.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/193?closed=1)
