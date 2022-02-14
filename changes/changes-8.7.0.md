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
* Build and push snapshot Docker images to GitHub Container Registry, this is only for people who want to help to test
  the master branch codes, please don't use in production environments.

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
* Fix async finish repeatedly in `spring-webflux-5.x-webclient` plugin.
* Add agent plugin to support Sentinel.
* Move `ehcache-2.x` plugin as an optional plugin.
* Support `guava-cache` plugin.
* Enhance the compatibility of `mysql-8.x-plugin` plugin.
* Support Kafka SASL login module.
* Fix gateway plugin async finish repeatedly when fallback url configured.
* Chore: polish methods naming for `Spring-Kafka` plugins.
* Remove plugins for ShardingSphere legacy version.
* Update agent plugin for ElasticJob GA version
* Remove the logic of generating instance name in `KafkaServiceManagementServiceClient` class.
* Improve `okhttp` plugin performance by optimizing Class.getDeclaredField().
* Fix `GRPCLogClientAppender` no context warning.
* Fix `spring-webflux-5.x-webclient-plugin` NPE.

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
* Re-implement storage session mechanism, cached metrics are removed only according to their last access timestamp,
  rather than first time. This makes sure hot data never gets removed unexpectedly.
* Support session expired threshold configurable.
* Fix InfluxDB storage-plugin Metrics#multiGet issue.
* Replace zuul proxy with spring cloud gateway 2.x. in webapp module.
* Upgrade etcd cluster coordinator and dynamic configuration to v3.x.
* Configuration: Allow configuring server maximum request header size and ES index template order.
* Add thread state metric and class loaded info metric to JVMMetric.
* Performance: compile LAL DSL statically and run with type checked.
* Add pagination to event query protocol.
* Performance: optimize Envoy error logs persistence performance.
* Support envoy `cluster manager` metrics.
* Performance: remove the synchronous persistence mechanism from batch ElasticSearch DAO. Because the current enhanced
  persistent session mechanism, don't require the data queryable immediately after the insert and update anymore.
* Performance: share `flushInterval` setting for both metrics and record data, due
  to `synchronous persistence mechanism` removed. Record flush interval used to be hardcoded as 10s.
* Remove `syncBulkActions` in ElasticSearch storage option.
* Increase the default bulkActions(env, SW_STORAGE_ES_BULK_ACTIONS) to 5000(from 1000).
* Increase the flush interval of ElasticSearch indices to 15s(from 10s)
* Provide distinct for elements of metadata lists. Due to the more aggressive asynchronous flush, metadata lists have
  more chances including duplicate elements. Don't need this as indicate anymore.
* Reduce the flush period of hour and day level metrics, only run in 4 times of regular persistent period. This means
  default flush period of hour and day level metrics are 25s * 4.
* Performance: optimize IDs read of ElasticSearch storage options(6 and 7). Use the physical index rather than template
  alias name.
* Adjust index refresh period as INT(flushInterval * 2/3), it used to be as same as bulk flush period. At the edge case,
  in low traffic(traffic < bulkActions in the whole period), there is a possible case, 2 period bulks are included in
  one index refresh rebuild operation, which could cause version conflicts. And this case can't be fixed
  through `core/persistentPeriod` as the bulk fresh is not controlled by the persistent timer anymore.
* The `core/maxSyncOperationNum` setting(added in 8.5.0) is removed due to metrics persistence is fully asynchronous.
* The `core/syncThreads` setting(added in 8.5.0) is removed due to metrics persistence is fully asynchronous.
* Optimization: Concurrency mode of execution stage for metrics is removed(added in 8.5.0). Only concurrency of prepare
  stage is meaningful and kept.
* Fix `-meters` metrics topic isn't created with namespace issue
* Enhance persistent session timeout mechanism. Because the enhanced session could cache the metadata metrics forever,
  new timeout mechanism is designed for avoiding this specific case.
* Fix Kafka transport topics are created duplicated with and without namespace issue
* Fix the persistent session timeout mechanism bug.
* Fix possible version_conflict_engine_exception in bulk execution.
* Fix PrometheusMetricConverter may throw an `IllegalArgumentException` when convert metrics to SampleFamily
* Filtering NaN value samples when build SampleFamily
* Add Thread and ClassLoader Metrics for the self-observability and otel-oc-rules
* Simple optimization of trace sql query statement. Avoid "select *" query method
* Introduce dynamical logging to update log configuration at runtime
* Fix Kubernetes ConfigMap configuration center doesn't send delete event 
* Breaking Change: emove `qps` and add `rpm` in LAL 

#### UI

* Fix the date component for log conditions.
* Fix selector keys for duplicate options.
* Add Python celery plugin.
* Fix default config for metrics.
* Fix trace table for profile ui.
* Fix the error of server response time in the topology.
* Fix chart types for setting metrics configure.
* Fix logs pages number.
* Implement a timeline for Events in a new page.
* Fix style for event details.

#### Documentation

* Add FAQ about `Elasticsearch exception type=version_conflict_engine_exception since 8.7.0`
* Add Self Observability service discovery (k8s).
* Add sending Envoy Metrics to  OAP in envoy 1.19  example and bump up to  Envoy V3 api.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/90?closed=1)

