## 9.6.0

#### Project

* Bump up Guava to 32.0.1 to avoid the lib listed as vulnerable due to CVE-2020-8908. This API is never used.
* Maven artifact `skywalking-log-recevier-plugin` is renamed to `skywalking-log-receiver-plugin`.

#### OAP Server

* Add Neo4j component ID(112) language: Python.
* Add Istio ServiceEntry registry to resolve unknown IPs in ALS.
* Wrap `deleteProperty` API to the BanyanDBStorageClient.
* [Breaking change] Remove `matchedCounter` from `HttpUriRecognitionService#feedRawData`.
* Remove patterns from `HttpUriRecognitionService#feedRawData` and add max 10 candidates of raw URIs for each pattern.
* Add component ID for WebSphere.
* Fix AI Pipeline uri caching NullPointer and IllegalArgument Exceptions.
* Fix `NPE` in metrics query when the metric is not exist.
* Remove E2E tests for Istio < 1.15, ElasticSearch < 7.16.3, they might still work but are not supported as planed.
* Scroll all results in ElasticSearch storage and refactor scrolling logics, including Service, Instance, Endpoint,
  Process, etc.
* Improve Kubernetes coordinator to remove `Terminating` OAP Pods in cluster.
* Support `SW_CORE_SYNC_PERIOD_HTTP_URI_RECOGNITION_PATTERN` and `SW_CORE_TRAINING_PERIOD_HTTP_URI_RECOGNITION_PATTERN`
  to control the period of training and sync HTTP URI recognition patterns. And shorten the default period to 10s for
  sync and 60s for training.
* Fix ElasticSearch scroller bug.
* Add component ID for Aerospike(ID=149).
* Packages with name `recevier` are renamed to `receiver`.
* `BanyanDBMetricsDAO` handles `storeIDTag` in `multiGet` for `BanyanDBModelExtension`.
* Fix endpoint grouping-related logic and enhance the performance of PatternTree retrieval. 
* Fix metric session cache saving after batch insert when using `mysql-connector-java`.
* Support dynamic UI menu query.
* Add comment for `docker/.env` to explain the usage.
* Fix wrong environment variable name `SW_OTEL_RECEIVER_ENABLED_OTEL_RULES` to right `SW_OTEL_RECEIVER_ENABLED_OTEL_METRICS_RULES`.
* Fix instance query in JDBC implementation.

#### UI

* Fix metric name `browser_app_error_rate` in `Browser-Root` dashboard.
* Fix display name of `endpoint_cpm` for endpoint list in `General-Service` dashboard.

#### Documentation

* Add Go agent into the server agent documentation.
* Add data unit description in the configuration of continuous profiling policy.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/181?closed=1)
