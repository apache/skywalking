## 10.0.0

#### Project

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
* Support Service Hierarchy auto matching.
* Add `namespace` suffix for `K8S_SERVICE_NAME_RULE/ISTIO_SERVICE_NAME_RULE` and `metadata-service-mapping.yaml` as default.
* Allow using a dedicated port for ALS receiver.
* Fix log query by traceId in `JDBCLogQueryDAO`.
* Support handler eBPF access log protocol.
* Fix SumPerMinFunctionTest error function.
* Remove unnecessary annotations and functions from Meter Functions.
* Add `max` and `min` functions for MAL down sampling.
* Fix critical bug of uncontrolled memory cost of TopN statistics. Change topN group key from `StorageId` to `entityId + timeBucket`.

#### UI

* Fix the mismatch between the unit and calculation of the "Network Bandwidth Usage" widget in Linux-Service Dashboard.
* Add theme change animation.
* Implement the Service and Instance hierarchy topology.
* Support Tabs in the widget visiable when MQE expressions.
* Support search on Marketplace.
* Fix default route.
* Fix layout on the Log widget.
* Fix Trace associates with Log widget.
* Add isDefault to the dashboard configuration.
* Add expressions to dashboard configurations on the dashboard list page.
* Update Kubernetes related UI templates for adapt data from eBPF access log. 
* Fix dashboard `K8S-Service-Root` metrics expression.
* Add dashboards for Service/Instance Hierarchy.

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

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/202?closed=1)
