## 10.0.0

#### Project


#### OAP Server
* Add `layer` parameter to the global topology graphQL query.
* Add `is_present` function in MQE for check if the list metrics has a value or not.
* Remove unreasonable default configurations for gRPC thread executor.
* Remove `gRPCThreadPoolQueueSize (SW_RECEIVER_GRPC_POOL_QUEUE_SIZE)`
  configuration.
* Allow excluding ServiceEntries in some namespaces when looking up
  ServiceEntries as a final resolution method of service metadata.

#### UI
* Fix the mismatch between the unit and calculation of the "Network Bandwidth Usage" widget in Linux-Service Dashboard.


#### Documentation
* Update the release doc to remove the announcement as the tests are through e2e rather than manually.
* Update the release notification mail a little.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/202?closed=1)
