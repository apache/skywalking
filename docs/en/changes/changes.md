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

#### UI

* Fix the mismatch between the unit and calculation of the "Network Bandwidth Usage" widget in Linux-Service Dashboard.

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

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/202?closed=1)
