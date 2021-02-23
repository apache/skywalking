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

#### UI
* Update selector scroller to show in all pages.
* Implement searching logs with date.

#### Documentation


All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/76?closed=1)

------------------
Find change logs of all versions [here](changes).
