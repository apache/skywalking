Changes by Version
==================
Release Notes.

8.5.0
------------------
#### Project
* Update frontend-maven-plugin to 1.11.0, for Download node x64 binary on Apple Silicon.
* Add E2E test for VM monitoring that metrics from Prometheus node-exporter.

#### Java Agent
* Remove invalid mysql configuration in agent.config.
* Add net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.Listener to show detail message when redefine errors occur.
* Fix ClassCastException of log4j gRPC reporter.
* Fix NPE when Kafka reporter activated.
* Enhance gRPC log appender to allow layout pattern.
* Fix apm-dubbo-2.7.x-plugin memory leak due to some Dubbo RpcExceptions.
* Fix lettuce-5.x-plugin get null host in redis sentinel mode.
* Fix ClassCastException by making CallbackAdapterInterceptor to implement EnhancedInstance interface in the spring-kafka plugin.

#### OAP-Backend
* Allow user-defined `JAVA_OPTS` in the startup script.
* Metrics combination API supports abandoning results.
* Add a new concept "Event" and its implementations to collect events.
* Add some defensive codes for NPE and bump up Kubernetes client version to expose exception stack trace.
* Update the `timestamp` field type for `LogQuery`.
* Support Zabbix protocol to receive agent metrics.
* Update the Apdex metric combine calculator.
* Enhance `MeterSystem` to allow creating metrics with same `metricName` / `function` / `scope`.
* Storage plugin supports postgresql.
* Fix kubernetes.client.opeanapi.ApiException.
* Remove filename suffix in the meter active file config.
* Introduce log analysis language (LAL).
* Fix alarm httpclient connection leak.
* Add `sum` function in meter system.
* Remove Jaeger receiver.
* Remove the experimental Zipkin span analyzer.
* Upgrade the Zipkin Elasticsearch storage from 6 to 7.
* Require Zipkin receiver must work with `zipkin-elasticsearch7` storage option.
* Fix `DatabaseSlowStatementBuilder` statement maybe null.
* Remove fields of parent entity in the relation sources. 
* Save Envoy http access logs when error occurs.

#### UI
* Update selector scroller to show in all pages.
* Implement searching logs with date.
* Add nodejs 14 compiling.
* Fix trace id by clear search conditions.
* Search endpoints with keywords.
* Fix pageSize on logs page.

#### Documentation
* Polish documentation due to we have covered all tracing, logging, and metrics fields.
* Adjust documentation about Zipkin receiver.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/76?closed=1)

------------------
Find change logs of all versions [here](changes).
