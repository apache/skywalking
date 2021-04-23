7.0.0
------------------

#### Project
* SkyWalking discards the supports of JDK 1.6 and 1.7 on the java agent side. The minimal requirement of JDK is JDK8.
* Support method performance profile.
* Provide new E2E test framework.
* Remove AppVeyor from the CI, use GitHub action only.
* Provide new plugin test tool.
* Don't support SkyWalking v5 agent in-wire and out-wire protocol. v6 is required.

#### Java Agent
* Add lazy injection API in the agent core.
* Support Servlet 2.5 in the Struts plugin.
* Fix RestTemplate plugin ClassCastException in the Async call.
* Add Finagle plugin.
* Add test cases of H2 and struts.
* Add Armeria 0.98 plugin.
* Fix ElasticSearch plugin bug.
* Fix EHCache plugin bug.
* Fix a potential I/O leak.
* Support Oracle SID mode.
* Update Byte-buddy core.
* Performance tuning: replace AtomicInteger with AtomicIntegerFieldUpdater.
* Add AVRO plugin.
* Update to JDK 1.8
* Optimize the ignore plugin.
* Enhance the gRPC plugin.
* Add Kotlin Coroutine plugin.
* Support HTTP parameter collection in Tomcat and SpringMVC plugin.
* Add @Tag annotation in the application toolkit.
* Move Lettuce into the default plugin list.
* Move Webflux into the default plugin list.
* Add HttpClient 3.x plugin.

#### OAP-Backend
* Support InfluxDB as a new storage option.
* Add `selector` in the `application.yml`. Make the provider activation more flexible through System ENV.
* Support sub-topology map query.
* Support gRPC SSL.
* Support HTTP protocol for agent.
* Support Nginx LUA agent.
* Support skip the instance relationship analysis if some agents doesn't have upstream address, currently for LUA agent.
* Support metrics entity name in the storage. Optional, default OFF.
* Merge the HOUR and DAY metrics into MINUTE in the ElasticSearch storage implementation. Reduce the payload for ElasticSearch server.
* Support change detection mechanism in DCS.
* Support Daily step in the ElasticSearch storage implementation for low traffic system.
* Provide profile export tool.
* Support alarm gRPC hook.
* Fix PHP language doesn't show up on the instance page.
* Add more comments in the source codes.
* Add a new metrics type, multiple linears.
* Fix thread concurrency issue in the alarm core.

#### UI
* Support custom topology definition.


#### Document
* Add FAQ about `python2` command required in the compiling.
* Add doc about new e2e framework.
* Add doc about the new profile feature.
* Powered-by page updated.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/37?closed=1)