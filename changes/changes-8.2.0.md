8.2.0
------------------
#### Project
* Support Browser monitoring.
* Add e2e test for ALS solution of service mesh observability.
* Support compiling(include testing) in JDK11.
* Support build a single module.

#### Java Agent
* Support metrics plugin.
* Support slf4j logs of gRPC and Kafka(when agent uses them) into the agent log files.
* Add `PROPERTIES_REPORT_PERIOD_FACTOR` config to avoid the properties of instance cleared.
* Limit the size of traced SQL to avoid OOM.
* Support `mount` command to load a new set of plugins.
* Add plugin selector mechanism.
* Enhance the witness classes for MongoDB plugin.
* Enhance the parameter truncate mechanism of SQL plugins.
* Enhance the SpringMVC plugin in the reactive APIs.
* Enhance the SpringMVC plugin to collect HTTP headers as the span tags.
* Enhance the Kafka plugin, about `@KafkaPollAndInvoke`
* Enhance the configuration initialization core. Plugin could have its own plugins.
* Enhance Feign plugin to collect parameters.
* Enhance Dubbo plugin to collect parameters.
* Provide Thrift plugin.
* Provide XXL-job plugin.
* Provide MongoDB 4.x plugin.
* Provide Kafka client 2.1+ plugin.
* Provide WebFlux-WebClient plugin.
* Provide ignore-exception plugin.
* Provide quartz scheduler plugin.
* Provide ElasticJob 2.x plugin.
* Provide Spring @Scheduled plugin.
* Provide Spring-Kafka plugin.
* Provide HBase client plugin.
* Provide JSON log format.
* Move Spring WebFlux plugin to the optional plugin.
* Fix inconsistent logic bug in PrefixMatch
* Fix duplicate exit spans in Feign LoadBalancer mechanism.
* Fix the target service blocked by the Kafka reporter.
* Fix configurations of Kafka report don't work.
* Fix rest template concurrent conflict.
* Fix NPE in the ActiveMQ plugin.
* Fix conflict between Kafka reporter and sampling plugin.
* Fix NPE in the log formatter.
* Fix span layer missing in certain cases, in the Kafka plugin.
* Fix error format of time in serviceTraffic update.
* Upgrade bytebuddy to 1.10.14

#### OAP-Backend
* Support Nacos authentication.
* Support labeled meter in the meter receiver.
* Separate UI template into multiple files.
* Provide support for Envoy tracing. Envoy tracer depends on the Envoy community.
* Support query trace by tags.
* Support composite alarm rules.
* Support alarm messages to DingTalk.
* Support alarm messages to WeChat.
* Support alarm messages to Slack.
* Support SSL for Prometheus fetcher and self telemetry.
* Support labeled histogram in the prometheus format.
* Support the status of segment based on entry span or first span only.
* Support the error segment in the sampling mechanism.
* Support SSL certs of gRPC server.
* Support labeled metrics in the alarm rule setting.
* Support to query all labeled data, if no explicit label in the query condition.
* Add TLS parameters in the mesh analysis.
* Add health check for InfluxDB storage.
* Add `super dataset` concept for the traces/logs.
* Add separate replicas configuration for super dataset.
* Add `IN` operator in the OAL.
* Add `!=` operator in the OAL.
* Add `like` operator in the OAL.
* Add `latest` function in the prometheus analysis.
* Add more configurations in the gRPC server. 
* Optimize the trace query performance.
* Optimize the CPU usage rate calculation, at least to be 1.
* Optimize the length of slow SQL column in the MySQL storage.
* Optimize the topology query, use client side component name when no server side mapping.
* Add component IDs for Python component.
* Add component ID range for C++.
* Fix Slack notification setting NPE.
* Fix some module missing check of the module manager core.
* Fix authentication doesn't work in sharing server.
* Fix metrics batch persistent size bug.
* Fix trace sampling bug.
* Fix CLR receiver bug.
* Fix end time bug in the query process.
* Fix `Exporter INCREMENT mode` is not working.
* Fix an error when executing startup.bat when the log directory exists
* Add syncBulkActions configuration to set up the batch size of the metrics persistent.
* Meter Analysis Language.

#### UI
* Add browser dashboard.
* Add browser log query page.
* Support query trace by tags.
* Fix JVM configuration.
* Fix CLR configuration.

#### Document
* Add the document about `SW_NO_UPSTREAM_REAL_ADDRESS`.
* Update ALS setup document.
* Add Customization Config section for plugin development.


All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/56?closed=1)
