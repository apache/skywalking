Changes by Version
==================
Release Notes.

8.7.0
------------------

#### Project

* Extract dependency management to a bom.
* Add JDK 16 to test matrix.
* DataCarrier consumer add a new event notification, call `nothingToConsume` method if the queue has no element to
  consume.

#### Java Agent

* Supports modifying span attributes in async mode.
* Agent supports the collection of JVM arguments and jar dependency information.
* [Temporary] Support authentication for log report channel. This feature and grpc channel is going to be removed after
  Satellite 0.2.0 release.
* Remove deprecated gRPC method, `io.grpc.ManagedChannelBuilder#nameResolverFactory`.
  See [gRPC-java 7133](https://github.com/grpc/grpc-java/issues/7133) for more details.
* Add `Neo4j-4.x` plugin.
* Correct `profile.duration` to `profile.max_duration` in the default `agent.config` file.
* Fix the response time of gRPC.
* Support parameter collection for SqlServer.
* Add `ShardingSphere-5.0.0-beta` plugin.
* Fix some method exception error.

#### OAP-Backend

* Disable Spring sleuth meter analyzer by default.
* Only count 5xx as error in Envoy ALS receiver.
* Upgrade apollo core caused by CVE-2020-15170.
* Upgrade kubernetes client caused by CVE-2020-28052.
* Upgrade Elasticsearch 7 client caused by CVE-2020-7014.
* Upgrade jackson related libs caused by CVE-2018-11307, CVE-2018-14718 ~ CVE-2018-14721, CVE-2018-19360 ~
  CVE-2018-19362, CVE-2019-14379, CVE-2019-14540, CVE-2019-14892, CVE-2019-14893, CVE-2019-16335, CVE-2019-16942,
  CVE-2019-16943, CVE-2019-17267, CVE-2019-17531, CVE-2019-20330, CVE-2020-8840, CVE-2020-9546, CVE-2020-9547,
  CVE-2020-9548, CVE-2018-12022, CVE-2018-12023, CVE-2019-12086, CVE-2019-14439, CVE-2020-10672, CVE-2020-10673,
  CVE-2020-10968, CVE-2020-10969, CVE-2020-11111, CVE-2020-11112, CVE-2020-11113, CVE-2020-11619, CVE-2020-11620,
  CVE-2020-14060, CVE-2020-14061, CVE-2020-14062, CVE-2020-14195, CVE-2020-24616, CVE-2020-24750, CVE-2020-25649,
  CVE-2020-35490, CVE-2020-35491, CVE-2020-35728 and CVE-2020-36179 ~ CVE-2020-36190.
* Exclude log4j 1.x caused by CVE-2019-17571.
* Upgrade log4j 2.x caused by CVE-2020-9488.
* Upgrade nacos libs caused by CVE-2021-29441 and CVE-2021-29442.
* Upgrade netty caused by CVE-2019-20444, CVE-2019-20445, CVE-2019-16869, CVE-2020-11612, CVE-2021-21290, CVE-2021-21295
  and CVE-2021-21409.
* Upgrade consul client caused by CVE-2018-1000844, CVE-2018-1000850.
* Upgrade zookeeper caused by CVE-2019-0201, zookeeper cluster coordinator plugin now requires zookeeper server 3.5+.
* Upgrade snake yaml caused by CVE-2017-18640.
* Upgrade embed tomcat caused by CVE-2020-13935.
* Upgrade commons-lang3 to avoid potential NPE in some JDK versions.
* OAL supports generating metrics from events.
* Support endpoint name grouping by OpenAPI definitions.
* Concurrent create PrepareRequest when persist Metrics
* Fix CounterWindow increase computing issue.
* Performance: optimize Envoy ALS analyzer performance in high traffic load scenario (reduce ~1cpu in ~10k RPS).
* Performance: trim useless metadata fields in Envoy ALS metadata to improve performance.
* Fix: slowDBAccessThreshold dynamic config error when not configured.
* Performance: cache regex pattern and result, optimize string concatenation in Envy ALS analyzer.
* Performance: cache metrics id and entity id in `Metrics` and `ISource`.
* Performance: enhance persistent session mechanism, about differentiating cache timeout for different dimensionality
  metrics. The timeout of the cache for minute and hour level metrics has been prolonged to ~5 min.
* Performance: Add L1 aggregation flush period, which reduce the CPU load and help young GC.
* Support connectTimeout and socketTimeout settings for ElasticSearch6 and ElasticSearch7 storages.

#### UI

* Fix the date component for log conditions.
* Fix selector keys for duplicate options.
* Add Python celery plugin.
* Fix default config for metrics.
* Fix trace table for profile ui.

#### Documentation

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/90?closed=1)

------------------
Find change logs of all versions [here](changes).
