Changes by Version
==================
Release Notes.

8.4.0
------------------
#### Project
* Incompatible with previous releases when use H2/MySQL/TiDB storage options, due to support multiple alarm rules triggered for one entity.
* Chore: adapt `create_source_release.sh` to make it runnable on Linux.

#### Java Agent
* The operation name of quartz-scheduler plugin, has been changed as the `quartz-scheduler/${className}` format.
* Fix jdk-http and okhttp-3.x plugin did not overwrite the old trace header.
* Support collecting logs of log4j, log4j2, and logback in the tracing context with a new `logger-plugin`.
* Fix the unexpected RunningContext recreation in the Tomcat plugin.

#### OAP-Backend
* Make meter receiver support MAL.
* Support influxDB connection response format option. Fix some error when use JSON as influxDB response format.
* Support Kafka MirrorMaker 2.0 to replicate topics between Kafka clusters.
* Add the rule name field to alarm record storage entity as a part of ID, to support multiple alarm rules triggered for one entity. The scope id has been removed from the ID.
* Fix MAL concurrent execution issues.
* Fix group name can't be query in the GraphQL.
* Fix potential gRPC connection leak(not closed) for the channels among OAP instances.
* Filter OAP instances(unassigned in booting stage) of the empty IP in KubernetesCoordinator.
* Add component ID for Python aiohttp plugin requester and server.

#### UI
* Fix un-removed tags in trace query.
* Fix unexpected metrics name on single value component.
* Don't allow negative value as the refresh period.
* Fix style issue in trace table view.
* Separation Log and Dashboard selector data to avoid conflicts.
* Fix trace instance selector bug.
* Fix Unnecessary sidebar in tooltips for charts.
* Refactor dashboard query in a common script.
* Implement refreshing data for topology by updating date.
* Implement group selector in the topology.

#### Documentation
* Update the documents of backend fetcher and self observability about the latest configurations.
* Add documents about the group name of service.
* Update docs about the latest UI.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/68?closed=1)

------------------
Find change logs of all versions [here](changes).
