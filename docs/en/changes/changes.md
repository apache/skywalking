## 10.3.0

#### Project

* Bump up BanyanDB dependency version(server and java-client) to 0.9.0.

#### OAP Server

* BanyanDB: Support `hot/warm/cold` stages configuration.
* Fix query continues profiling policies error when the policy is already in the cache.
* Support `hot/warm/cold` stages TTL query in the status API and graphQL API.
* PromQL Service: traffic query support `limit` and regex match.
* Fix an edge case of HashCodeSelector(Integer#MIN_VALUE causes ArrayIndexOutOfBoundsException).
* Support Flink monitoring.
* BanyanDB: Support `@ShardingKey` for Measure tags and set to TopNAggregation group tag by default.
* BanyanDB: Support cold stage data query for metrics/traces/logs.
* Increase the idle check interval of the message queue to 200ms to reduce CPU usage under low load conditions.
* Limit max attempts of DNS resolution of Istio ServiceEntry to 3, and do not wait for first resolution result in case the DNS is not resolvable at all.
* Support analysis waypoint metrics in Envoy ALS receiver.
* Add Ztunnel component in the topology.
* [Break Change] Change `compomentId` to `componentIds` in the K8SServiceRelation Scope. 
* Adapt the mesh metrics if detect the ambient mesh in the eBPF access log receiver.
* Add JSON format support for the `/debugging/config/dump` status API.
* Enhance status APIs to support multiple `accept` header values, e.g. `Accept: application/json; charset=utf-8`.
* Storage: separate `SpanAttachedEventRecord` for SkyWalking trace and Zipkin trace.
* [Break Change]BanyanDB: Setup new Group policy.
* Bump up commons-beanutils to 1.11.0.
* Refactor: simplify the `Accept` http header process.
* [Break Change]Storage: Move `event` from metrics to recods.

#### UI

* Enhance the trace `List/Tree/Table` graph to support displaying multiple refs of spans and distinguishing different parents.
* Fix: correct the same labels for metrics.
* Refactor: use the Fetch API to instead of Axios.
* Support cold stage data for metrics, trace and log.
* Add route to status API `/debugging/config/dump` in the UI.
* Implement the Status API on Settings page.
* Bump vite from 6.2.6 to 6.3.4.

#### Documentation

* BanyanDB: Add `Data Lifecycle Stages(Hot/Warm/Cold)` documentation.
* Add `SWIP-9 Support flink monitoring`.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/230?closed=1)

