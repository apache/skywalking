Changes by Version
==================
Release Notes.

9.0.0
------------------

#### Project

* Upgrade log4j2 to 2.17.1 for CVE-2021-44228, CVE-2021-45046, CVE-2021-45105 and CVE-2021-44832. This CVE only effects
  on JDK if JNDI is opened in default. Notice, using JVM option `-Dlog4j2.formatMsgNoLookups=true` or setting
  the `LOG4J_FORMAT_MSG_NO_LOOKUPS=”true”` environment variable also avoids CVEs.
* Upgrade maven-wrapper to 3.1.0, maven to 3.8.4 for performance improvements and ARM more native support.
* Exclude unnecessary libs when building under JDK 9+.
* Migrate base Docker image to eclipse-temurin as adoptopenjdk is deprecated.
* Add E2E test under Java 17.
* Upgrade protoc to 3.19.2.
* Add Istio 1.13.1 to E2E test matrix for verification.
* Upgrade Apache parent pom version to 25.
* Use the plugin version defined by the Apache maven parent.
  * Upgrade maven-dependency-plugin to 3.2.0.
  * Upgrade  maven-assembly-plugin to 3.3.0.
  * Upgrade  maven-failsafe-plugin to 2.22.2.
  * Upgrade  maven-surefire-plugin to 2.22.2.
  * Upgrade  maven-jar-plugin to 3.2.2.
  * Upgrade  maven-enforcer-plugin to 3.0.0.
  * Upgrade  maven-compiler-plugin to 3.10.0.
  * Upgrade  maven-resources-plugin to 3.2.0.
  * Upgrade  maven-source-plugin to 3.2.1.
* Update codeStyle.xml to fix incompatibility on M1's IntelliJ IDEA 2021.3.2.

#### OAP Server

* Fix potential NPE in OAL string match and a bug when right-hand-side variable includes double quotes.
* Bump up Armeria version to fix CVE.
* Polish ETCD cluster config environment variables.
* Add the analysis of metrics in Satellite MetricsService.
* Fix `Can't split endpoint id into 2 parts` bug for endpoint ID. In the TCP in service mesh observability, endpoint
  name doesn't exist in TCP traffic.
* Upgrade H2 version to 2.0.206 to fix CVE-2021-23463 and GHSA-h376-j262-vhq6.
* Extend column name override mechanism working for `ValueColumnMetadata`.
* Introduce new concept `Layer` and removed `NodeType`. More details refer to [v9-version-upgrade](https://skywalking.apache.org/docs/main/latest/en/faq/v9-version-upgrade/).
* Fix query sort metrics failure in H2 Storage.
* Bump up grpc to 1.43.2 and protobuf to 3.19.2 to fix CVE-2021-22569.
* Add source layer and dest layer to relation.
* Follow protocol grammar fix `GCPhrase -> GCPhase`.
* Set layer to mesh relation.
* Add `FAAS` to SpanLayer.
* Adjust e2e case for V9 core.
* Support ZGC GC time and count metric collecting.
* Sync proto buffers files from upstream Envoy (Related to https://github.com/envoyproxy/envoy/pull/18955).
* Bump up GraphQL related dependencies to latest versions.
* Add `normal` to V9 service meta query.
* Support `scope=ALL` catalog for metrics.
* Bump up H2 to 2.1.210 to fix CVE-2022-23221.
* E2E: Add `normal` field to Service.
* Add FreeSql component ID(3017) of dotnet agent.
* E2E: verify OAP cluster model data aggregation.
* Fix `SelfRemoteClient` self observing metrics.
* Add env variables `SW_CLUSTER_INTERNAL_COM_HOST` and `SW_CLUSTER_INTERNAL_COM_PORT` for cluster selectors `zookeeper`,`consul`,`etcd` and `nacos`.
* Doc update: `configuration-vocabulary`,`backend-cluster` about env variables `SW_CLUSTER_INTERNAL_COM_HOST` and `SW_CLUSTER_INTERNAL_COM_PORT`.
* Add Python MysqlClient component ID(7013) with mapping information.
* Support Java thread pool metrics analysis.
* Fix IoTDB Storage Option insert null index value.
* Set the default value of SW_STORAGE_IOTDB_SESSIONPOOL_SIZE to 8.
* Bump up iotdb-session to 0.12.4.
* Bump up PostgreSQL driver to fix CVE.
* Add Guava EventBus component ID(123) of Java agent.
* Add OpenFunction component ID(5013).
* Expose configuration `responseTimeout` of ES client.
* Support datasource metric analysis.
* [Break Change] Keep the endpoint avg resp time meter name the same with others scope. (This may break 3rd party integration and existing alarm rule settings)
* Add Python FastApi component ID(7014).
* Support all metrics from MAL engine in alarm core, including Prometheus, OC receiver, meter receiver.
* Allow updating non-metrics templates when structure changed.
* Set default connection timeout of ElasticSearch to 3000 milliseconds.
* Support ElasticSearch 8 and add it into E2E tests.
* Disable indexing for field `alarm_record.tags_raw_data` of binary type in ElasticSearch storage.
* Fix Zipkin receiver wrong condition for decoding `gzip`. 
* Add a new sampler (`possibility`) in LAL.
* Unify module name `receiver_zipkin` to `receiver-zipkin`, remove `receiver_jaeger` from `application.yaml`. 
* Introduce the entity of Process type.
* Set the length of event#parameters to 2000.
* Limit the length of Event#parameters.

#### UI

* Remove unused jars (log4j-api.jar) in classpath.
* Bump up netty version to fix CVE.
* Add Database Connection pool metric.

#### Documentation

* Update backend-alarm.md doc, support op "=" to "==".
* Update backend-meter.md doc .
* Add <STAM: Enhancing Topology Auto Detection For A Highly Distributed and Large-Scale Application System> paper.
* Add Academy menu for recommending articles.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/112?closed=1)

------------------
Find change logs of all versions [here](changes).
