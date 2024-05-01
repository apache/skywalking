## 10.0.0

#### Project
* Support Java 21 runtime.
* Support oap-java21 image for Java 21 runtime.
* Upgrade `OTEL collector` version to `0.92.0` in all e2e tests.
* Switch CI macOS runner to m1.
* Upgrade PostgreSQL driver to `42.4.4` to fix CVE-2024-1597.
* Remove CLI(`swctl`) from the image.
* Remove CLI_VERSION variable from Makefile build.
* Add BanyanDB to docker-compose quickstart.
* Bump up Armeria, jackson, netty, jetcd and grpc to fix CVEs.

#### OAP Server

* Add `layer` parameter to the global topology graphQL query.
* Add `is_present` function in MQE for check if the list metrics has a value or not.
* Remove unreasonable default configurations for gRPC thread executor.
* Remove `gRPCThreadPoolQueueSize (SW_RECEIVER_GRPC_POOL_QUEUE_SIZE)` configuration.
* Allow excluding ServiceEntries in some namespaces when looking up ServiceEntries as a final resolution method of
  service metadata.
* Set up the length of source and dest IDs in relation entities of service, instance, endpoint, and process to 250(was
  200).
* Support build Service/Instance Hierarchy and query.
* Change the string field in Elasticsearch storage from **keyword** type to **text** type if it set more than `32766` length.
* [Break Change] Change the configuration field of `ui_template` and `ui_menu` in Elasticsearch storage from **keyword** type to **text**.
* Support Service Hierarchy auto matching, add auto matching layer relationships (upper -> lower) as following:
  - MESH -> MESH_DP
  - MESH -> K8S_SERVICE
  - MESH_DP -> K8S_SERVICE
  - GENERAL -> K8S_SERVICE
* Add `namespace` suffix for `K8S_SERVICE_NAME_RULE/ISTIO_SERVICE_NAME_RULE` and `metadata-service-mapping.yaml` as default.
* Allow using a dedicated port for ALS receiver.
* Fix log query by traceId in `JDBCLogQueryDAO`.
* Support handler eBPF access log protocol.
* Fix SumPerMinFunctionTest error function.
* Remove unnecessary annotations and functions from Meter Functions.
* Add `max` and `min` functions for MAL down sampling.
* Fix critical bug of uncontrolled memory cost of TopN statistics. Change topN group key from `StorageId` to `entityId + timeBucket`.
* Add Service Hierarchy auto matching layer relationships (upper -> lower) as following:
  - MYSQL -> K8S_SERVICE
  - POSTGRESQL -> K8S_SERVICE
  - SO11Y_OAP -> K8S_SERVICE
  - VIRTUAL_DATABASE -> MYSQL
  - VIRTUAL_DATABASE -> POSTGRESQL
* Add Golang as a supported language for AMQP.
* Support available layers of service in the topology.
* Add `count` aggregation function for MAL
* Add Service Hierarchy auto matching layer relationships (upper -> lower) as following:
  - NGINX -> K8S_SERVICE
  - APISIX -> K8S_SERVICE
  - GENERAL -> APISIX
* Add Golang as a supported language for RocketMQ.
* Support Apache RocketMQ server monitoring.
* Add Service Hierarchy auto matching layer relationships (upper -> lower) as following:
  - ROCKETMQ -> K8S_SERVICE
  - VIRTUAL_MQ -> ROCKETMQ
* Fix ServiceInstance `in` query.
* Mock `/api/v1/status/buildinfo` for PromQL API.
* Fix table exists check in the JDBC Storage Plugin.
* Fix day-based table rolling time range strategy in JDBC storage.
* Add `maxInboundMessageSize (SW_DCS_MAX_INBOUND_MESSAGE_SIZE)` configuration to change the max inbound message size of DCS.
* Fix Service Layer when building Events in the EventHookCallback.
* Add Golang as a supported language for Pulsar.
* Add Service Hierarchy auto matching layer relationships (upper -> lower) as following:
  - RABBITMQ -> K8S_SERVICE
  - VIRTUAL_MQ -> RABBITMQ
* Remove Column#function mechanism in the kernel.
* Make query `readMetricValue` always return the average value of the duration.
* Add Service Hierarchy auto matching layer relationships (upper -> lower) as following:
  - KAFKA -> K8S_SERVICE
  - VIRTUAL_MQ -> KAFKA
* Support ClickHouse server monitoring.
* Add Service Hierarchy auto matching layer relationships (upper -> lower) as following:
  - CLICKHOUSE -> K8S_SERVICE
  - VIRTUAL_DATABASE -> CLICKHOUSE
* Add Service Hierarchy auto matching layer relationships (upper -> lower) as following:
  - PULSAR -> K8S_SERVICE
  - VIRTUAL_MQ -> PULSAR
* Add Golang as a supported language for Kafka.
* Support displaying the port services listen to from OAP and UI during server start.
* Refactor data-generator to support generating metrics.
* Fix `AvgHistogramPercentileFunction` legacy name.
* [Break Change] Labeled Metrics support multiple labels.
  - Storage: store all label names and values instead of only the values.
  - MQE: 
    - Support querying by multiple labels(name and value) instead using `_` as the anonymous label name.
    - `aggregate_labels` function support aggregate by specific labels.
    - `relabels` function require target label and rename label name and value.
  - PromQL:
    - Support querying by multiple labels(name and value) instead using `lables` as the anonymous label name.
    - Remove general labels `labels/relabels/label` function.
    - API `/api/v1/labels` and `/api/v1/label/<label_name>/values` support return matched metrics labels.
  - OAL:
    - Deprecate `percentile` function and introduce `percentile2` function instead.
* Bump up Kafka to fix CVE.
* Fix `NullPointerException` in Istio ServiceEntry registry.
* Remove unnecessary `componentIds` as series ID in the `ServiceRelationClientSideMetrics` and `ServiceRelationServerSideMetrics` entities.
* Fix not throw error when part of expression not matched any expression node in the `MQE` and `PromQL.
* Remove `kafka-fetcher/default/createTopicIfNotExist` as the creation is automatically since [#7326](https://github.com/apache/skywalking/issues/7326) (v8.7.0).
* Fix inaccuracy nginx service metrics.
* Fix/Change Windows metrics name(Swap -> Virtual Memory)
  - `memory_swap_free` -> `memory_virtual_memory_free`
  - `memory_swap_total` -> `memory_virtual_memory_total`
  - `memory_swap_percentage` -> `memory_virtual_memory_percentage`
* Fix/Change UI init setting for Windows Swap -> Virtual Memory
* Fix `Memory Swap Usage`/`Virtual Memory Usage` display with UI init.(Linux/Windows)
* Fix inaccurate APISIX metrics.
* Fix inaccurate MongoDB Metrics.
* Support Apache ActiveMQ server monitoring. 
* Add Service Hierarchy auto matching layer relationships (upper -> lower) as following:
  - ACTIVEMQ -> K8S_SERVICE
* Calculate Nginx service HTTP Latency by MQE.
* MQE query: make metadata not return `null`.
* MQE labeled metrics Binary Operation: return empty value if the labels not match rather than report error.
* Fix inaccurate Hierarchy of RabbitMQ Server monitoring metrics.
* Fix inaccurate MySQL/MariaDB, Redis, PostgreSQL metrics.
* Support DoubleValue,IntValue,BoolValue in OTEL metrics attributes.
* [Break Change] gGRPC metrics exporter unified the metric value type and support labeled metrics.
* Add component definition(ID=152) for `c3p0`(JDBC3 Connection and Statement Pooling).
* Fix MQE `top_n` global query. 
* Fix inaccurate Pulsar and Bookkeeper metrics.

#### UI

* Fix the mismatch between the unit and calculation of the "Network Bandwidth Usage" widget in Linux-Service Dashboard.
* Add theme change animation.
* Implement the Service and Instance hierarchy topology.
* Support Tabs in the widget visible when MQE expressions.
* Support search on Marketplace.
* Fix default route.
* Fix layout on the Log widget.
* Fix Trace associates with Log widget.
* Add isDefault to the dashboard configuration.
* Add expressions to dashboard configurations on the dashboard list page.
* Update Kubernetes related UI templates for adapt data from eBPF access log. 
* Fix dashboard `K8S-Service-Root` metrics expression.
* Add dashboards for Service/Instance Hierarchy.
* Fix MQE in dashboards when using `Card widget`.
* Optimize tooltips style.
* Fix resizing window causes the trace graph to display incorrectly.
* Add the not found page(404).
* Enhance VNode logic and support multiple Trace IDs in span's ref.
* Add the layers filed and associate layers dashboards for the service topology nodes.
* Fix `Nginx-Instance` metrics to instance level.
* Update tabs of the Kubernetes service page. 
* Add Airflow menu i18n.
* Add Support for dragging in the trace panel.
* Add workflow icon.
* Metrics support multiple labels.
* Support the `SINGLE_VALUE` for table widgets.
* Remove the General metric mode and related logical code.
* Remove metrics for unreal nodes in the topology.
* Enhance the Trace widget for batch consuming spans.
* Clean the unused elements in the UI-templates.

#### Documentation

* Update the release doc to remove the announcement as the tests are through e2e rather than manually.
* Update the release notification mail a little.
* Polish docs structure. Move customization docs separately from the introduction docs.
* Add webhook/gRPC hooks settings example for `backend-alarm.md`.
* Begin the process of `SWIP - SkyWalking Improvement Proposal`.
* Add `SWIP-1 Create and detect Service Hierarchy Relationship`.
* Add `SWIP-2 Collecting and Gathering Kubernetes Monitoring Data`.
* Update the `Overview` docs to add the `Service Hierarchy Relationship` section.
* Fix incorrect words for `backend-bookkeeper-monitoring.md` and `backend-pulsar-monitoring.md`
* Document a new way to load balance OAP.
* Add `SWIP-3 Support RocketMQ monitoring`.
* Add `OpenTelemetry SkyWalking Exporter` deprecated warning doc.
* Update i18n for rocketmq monitoring.
* Fix: remove click event after unmounted.
* Fix: end loading without query results.
* Update nanoid version to 3.3.7.
* Update postcss version to 8.4.33.
* Fix kafka topic name in exporter doc.
* Fix query-protocol.md, make it consistent with the GraphQL query protocol.
* Add `SWIP-5 Support ClickHouse Monitoring`.
* Remove `OpenTelemetry Exporter` support from meter doc, as this has been flagged as unmaintained on OTEL upstream.
* Add doc of one-line quick start script for different storage types.
* Add FAQ for `Why is Clickhouse or Loki or xxx not supported as a storage option?`.
* Add `SWIP-8 Support ActiveMQ Monitoring`.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/202?closed=1)
