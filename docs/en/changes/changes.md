## 10.4.0

#### Project
* Fix E2E test metrics verify: make it failure if the metric values all null.
* Support building, testing, and publishing with Java 25.
* Add `CLAUDE.md` as AI assistant guide for the project.
* Upgrade Groovy to 5.0.3 in OAP backend.
* Bump up nodejs to v24.13.0 for the latest UI(booster-ui) compiling.

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
* `BrowserErrorLog`, OAP Server generated UUID to replace the original client side ID, because Browser scripts can't guarantee generated IDs are globally unique.
* MQE: fix multiple labeled metric query and ensure no results are returned if no label value combinations match.
* Fix `BrowserErrorLog` BanyanDB storage query order.
* `BanyanDB Client`: Property query support `Order By`.
* MQE: trim the label values condition for the labeled metrics query to enhance the readability.
* PromQL service: fix time parse issue when using RFC3339 time format for querying.
* Envoy metrics service receiver: support adapter listener metrics.

#### UI
* Fix the missing icon in new native trace view.
* Enhance the alert page to show the recovery time of resolved alerts.
* Implement a common pagination component.
* Fix validation guard for router.

#### Documentation

* Add benchmark selection into banyanDB storage documentation.
* Fix progressive TTL doc for banyanDB.
* Restructure `docs/README.md` for better navigation with high-level documentation overview.
* Move Marketplace as a top-level menu section with Overview introduction in `menu.yml`.
* Polish `marketplace.md` as the overview page for all out-of-box monitoring features.
* Add "What's Next" section to Quick Start docs guiding users to Marketplace.
* Restructure agent compatibility page with OAP 10.x focus and clearer format for legacy versions.
* Remove outdated FAQ docs (v3, v6 upgrade guides and 7.x metrics issue).
* Remove "since 7/8/9.x" version statements from documentation as features are standard in 10.x.


All issues and pull requests are [here](https://github.com/apache/skywalking/issues?q=milestone:10.4.0)

