## 10.3.0

#### Project

* Bump up BanyanDB dependency(server and java-client) to 0.9.0.

#### OAP Server

* BanyanDB: Support `hot/warm/cold` stages configuration.
* Fix query continues profiling policies error when the policy is already in the cache.
* Support `hot/warm/cold` stages TTL query in the status API.
* PromQL Service: traffic query support `limit` and regex match.
* Fix an edge case of HashCodeSelector(Integer#MIN_VALUE causes ArrayIndexOutOfBoundsException).

#### UI

* Enhance the trace `List/Tree/Table` graph to support displaying multiple refs of spans and distinguishing different parents.

#### Documentation

* BanyanDB: Add `Data Lifecycle Stages(Hot/Warm/Cold)` documentation.
* Add `SWIP-9 Support flink monitoring`.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/230?closed=1)

