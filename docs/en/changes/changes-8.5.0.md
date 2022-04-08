Changes by Version
==================
Release Notes.

8.5.0
------------------
#### Project
* **Incompatible Change**. Indices and templates of ElasticSearch(6/7, including zipkin-elasticsearch7) storage option have been changed.
* Update frontend-maven-plugin to 1.11.0, for Download node x64 binary on Apple Silicon.
* Add E2E test for VM monitoring that metrics from Prometheus node-exporter.
* Upgrade lombok to 1.18.16.
* Add Java agent Dockerfile to build Docker image for Java agent.

#### Java Agent
* Remove invalid mysql configuration in agent.config.
* Add net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.Listener to show detail message when redefine errors occur.
* Fix ClassCastException of log4j gRPC reporter.
* Fix NPE when Kafka reporter activated.
* Enhance gRPC log appender to allow layout pattern.
* Fix apm-dubbo-2.7.x-plugin memory leak due to some Dubbo RpcExceptions.
* Fix lettuce-5.x-plugin get null host in redis sentinel mode.
* Fix ClassCastException by making CallbackAdapterInterceptor to implement EnhancedInstance interface in the spring-kafka plugin.
* Fix NullPointerException with KafkaProducer.send(record).
* Support config `agent.span_limit_per_segment` can be changed in the runtime.
* Collect and report agent starting / shutdown events.
* Support jedis pipeline in jedis-2.x-plugin.
* Fix apm-toolkit-log4j-2.x-activation no trace Id in async log.
* Replace hbase-1.x-plugin with hbase-1.x-2.x-plugin to adapt hbase client 2.x
* Remove the close_before_method and close_after_method parameters of custom-enhance-plugin to avoid memory leaks.
* Fix bug that springmvc-annotation-4.x-plugin, witness class does not exist in some versions.
* Add Redis command parameters to 'db.statement' field on Lettuce span UI for displaying more info.
* Fix NullPointerException with `ReactiveRequestHolder.getHeaders`.
* Fix springmvc reactive api can't collect HTTP statusCode.
* Fix bug that asynchttpclient plugin does not record the response status code.
* Fix spanLayer is null in optional plugin(gateway-2.0.x-plugin gateway-2.1.x-plugin).
* Support @Trace, @Tag and @Tags work for static methods.

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
* Fix kubernetes.client.openapi.ApiException.
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
* Fix wrong `service_instance_sla` setting in the `topology-instance.yml`.
* Fix wrong metrics name setting in the `self-observability.yml`.
* Add telemetry data about metrics in, metrics scraping, mesh error and trace in metrics to zipkin receiver.
* Fix tags store of log and trace on h2/mysql/pg storage.
* Merge indices by Metrics Function and Meter Function in Elasticsearch Storage.
* Fix receiver don't need to get itself when healthCheck
* Remove group concept from AvgHistogramFunction. Heatmap(function result) doesn't support labels.
* Support metrics grouped by scope labelValue in MAL, no need global same labelValue as before.
* Add functions in MAL to filter metrics according to the metric value.
* Optimize the self monitoring grafana dashboard.
* Enhance the export service.
* Add function `retagByK8sMeta` and opt type `K8sRetagType.Pod2Service` in MAL for k8s to relate pods and services.
* Using "service.istio.io/canonical-name" to replace "app" label to resolve Envoy ALS service name.
* Support k8s monitoring.
* Make the flushing metrics operation concurrent.
* Fix ALS K8SServiceRegistry didn't remove the correct entry.
* Using "service.istio.io/canonical-name" to replace "app" label to resolve Envoy ALS service name.
* Append the root slash(/) to getIndex and getTemplate requests in ES(6 and 7) client.
* Fix `disable` statement not working. This bug exists since 8.0.0.
* Remove the useless metric in `vm.yaml`.

#### UI
* Update selector scroller to show in all pages.
* Implement searching logs with date.
* Add nodejs 14 compiling.
* Fix trace id by clear search conditions.
* Search endpoints with keywords.
* Fix pageSize on logs page.
* Update echarts version to 5.0.2.
* Fix instance dependency on the topology page.
* Fix resolved url for vue-property-decorator.
* Show instance attributes.
* Copywriting grammar fix.
* Fix log pages tags column not updated.
* Fix the problem that the footer and topology group is shaded when the topology radiation is displayed.
* When the topology radiation chart is displayed, the corresponding button should be highlighted.
* Refactor the route mapping, Dynamically import routing components, Improve first page loading performance.
* Support topology of two mutually calling services.
* Implement a type of table chart in the dashboard.
* Support event in the dashboard.
* Show instance name in the trace view.
* Fix groups of services in the topography.

#### Documentation
* Polish documentation due to we have covered all tracing, logging, and metrics fields.
* Adjust documentation about Zipkin receiver.
* Add backend-infrastructure-monitoring doc.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/76?closed=1)
