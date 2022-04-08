## 8.9.0

#### Project

* E2E tests immigrate to e2e-v2.
* Support JDK 16 and 17.
* Add Docker images for arm64 architecture.

#### OAP Server

* Add component definition for `Jackson`.
* Fix that zipkin-receiver plugin is not packaged into dist.
* Upgrade Armeria to 1.12, upgrade OpenSearch test version to 1.1.0.
* Add component definition for `Apache-Kylin`.
* Enhance `get` generation mechanism of OAL engine, support map type of source's field.
* Add `tag`(Map) into All, Service, ServiceInstance and Endpoint sources.
* Fix `funcParamExpression` and `literalExpression` can't be used in the same aggregation function.
* Support cast statement in the OAL core engine.
* Support `(str->long)` and `(long)` for string to long cast statement.
* Support `(str->int)` and `(int)` for string to int cast statement.
* Support Long literal number in the OAL core engine.
* Support literal `string` as parameter of aggregation function.
* Add `attributeExpression` and `attributeExpressionSegment` in the OAL grammar tree to support `map` type for the
  attribute expression.
* Refactor the OAL compiler context to improve readability.
* Fix wrong generated codes of `hashCode` and `remoteHashCode` methods for numeric fields.
* Support `!= null` in OAL engine.
* Add `Message Queue Consuming Count` metric for MQ consuming service and endpoint.
* Add `Message Queue Avg Consuming Latency` metric for MQ consuming service and endpoint.
* Support `-Inf` as bucket in the meter system.
* Fix setting wrong field when combining `Event`s.
* Support search browser service.
* Add `getProfileTaskLogs` to profile query protocol.
* Set `SW_KAFKA_FETCHER_ENABLE_NATIVE_PROTO_LOG`, `SW_KAFKA_FETCHER_ENABLE_NATIVE_JSON_LOG` default `true`.
* Fix unexpected deleting due to TTL mechanism bug for H2, MySQL, TiDB and PostgreSQL.
* Add a GraphQL query to get OAP version, display OAP version in startup message and error logs.
* Fix TimeBucket missing in H2, MySQL, TiDB and PostgreSQL bug, which causes TTL doesn't work for `service_traffic`.
* Fix TimeBucket missing in ElasticSearch and provide compatible `storage2Entity` for previous versions.
* Fix ElasticSearch implementation of `queryMetricsValues` and `readLabeledMetricsValues` doesn't fill default values
  when no available data in the ElasticSearch server.
* Fix config yaml data type conversion bug when meets special character like !.
* Optimize metrics of minute dimensionality persistence. The value of metrics, which has declaration of the default
  value and current value equals the default value logically, the whole row wouldn't be pushed into database.
* Fix `max` function in OAL doesn't support negative long.
* Add `MicroBench` module to make it easier for developers to write JMH test.
* Upgrade Kubernetes Java client to 14.0.0, supports GCP token refreshing and fixes some bugs.
* Change `SO11Y` metric `envoy_als_in_count` to calculate the ALS message count.
* Support Istio `1.10.3`, `1.11.4`, `1.12.0` release.(Tested through e2e)
* Add filter mechanism in MAL core to filter metrics.
* Fix concurrency bug in MAL `increase`-related calculation.
* Fix a null pointer bug when building `SampleFamily`.
* Fix the so11y latency of persistence execution latency not correct in ElasticSearch storage.
* Add `MeterReportService` `collectBatch` method. 
* Add OpenSearch 1.2.0 to test and verify it works.
* Upgrade grpc-java to 1.42.1 and protoc to 3.17.3 to allow using native Mac osx-aarch_64 artifacts.
* Fix TopologyQuery.loadEndpointRelation bug.
* Support using IoTDB as a new storage option.
* Add customized envoy ALS protocol receiver for satellite transmit batch data.
* Remove `logback` dependencies in IoTDB plugin.
* Fix `StorageModuleElasticsearchProvider` doesn't watch on `trustStorePath`.
* Fix a wrong check about entity if GraphQL at the endpoint relation level.

#### UI

* Optimize endpoint dependency.
* Show service name by hovering nodes in the sankey chart.
* Add Apache Kylin logo.
* Add ClickHouse logo.
* Optimize the style and add tips for log conditions.
* Fix the condition for trace table.
* Optimize profile functions.
* Implement a reminder to clear cache for dashboard templates.
* Support +/- hh:mm in TimeZone setting.
* Optimize global settings.
* Fix current endpoint for endpoint dependency.
* Add version in the global settings popup.
* Optimize Log page style.
* Avoid some abnormal settings.
* Fix query condition of events.

#### Documentation

* Enhance documents about the data report and query protocols.
* Restructure documents about receivers and fetchers.
    1. Remove general receiver and fetcher docs
    2. Add more specific menu with docs to help users to find documents easier.
* Add a guidance doc about the logic endpoint. 
* Link Satellite as Load Balancer documentation and compatibility with satellite.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/101?closed=1)
