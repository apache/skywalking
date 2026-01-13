## 10.4.0

#### Project
* Fix E2E test metrics verify: make it failure if the metric values all null.

#### OAP Server

* KubernetesCoordinator: make self instance return real pod IP address instead of `127.0.0.1`.
* Enhance the alarm kernel with recovered status notification capability
* Fix BrowserWebVitalsPerfData `clsTime` to `cls` and make it double type.
* Init `log-mal-rules` at module provider start stage to avoid re-init for every LAL.
* Fail fast if SampleFamily is empty after MAL filter expression.
* Fix range matrix and scalar binary operation in PromQL.
* Add `LatestLabeledFunction` for meter.
* MAL Labeled metrics support additional attributes.
* Bump up netty to 4.2.9.Final.
* Add support for OpenSearch/ElasticSearch client certificate authentication.
* Fix BanyanDB logs paging query.
* Replace BanyanDB Java client with native implementation.
* Remove `bydb.dependencies.properties` and set the compatible BanyanDB API version number in `${SW_STORAGE_BANYANDB_COMPATIBLE_SERVER_API_VERSIONS}`.
* Fix trace profiling query time range condition.
* Fix BanyanDB time range overflow in profile thread snapshot query.
* `BrowserErrorLog`, OAP Server generated UUID to replace the original client side ID, because Browser scripts can't guarantee generated IDs are globally unique.>>>>>>> master

#### UI
* Fix the missing icon in new native trace view.
* Enhance the alert page to show the recovery time of resolved alerts.
* Implement a common pagination component.

#### Documentation

* Add benchmark selection into banyanDB storage documentation.
* Fix progressive TTL doc for banyanDB.


All issues and pull requests are [here](https://github.com/apache/skywalking/issues?q=milestone:10.4.0)

