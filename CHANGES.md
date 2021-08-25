Changes by Version
==================
Release Notes.

8.8.0
------------------

#### Project

* Upgrade jdk 11 in dockerfile and remove unused java_opts.
* DataCarrier changes a `#consume` API to add properties as a parameter to initialize consumer when
  use `Class<? extends IConsumer<T>> consumerClass`.

#### Java Agent

* Support Multiple DNS period resolving mechanism
* Modify `Tags.STATUS_CODE` field name to `Tags.HTTP_RESPONSE_STATUS_CODE` and type from `StringTag` to `IntegerTag`, add `Tags.RPC_RESPONSE_STATUS_CODE` field to hold rpc response code value.
* Fix kafka-reporter-plugin shade package conflict
* Add all config items to `agent.conf` file for convenient containerization use cases.
* Advanced Kafka Producer configuration enhancement.

#### OAP-Backend

* Fix CVE-2021-35515, CVE-2021-35516, CVE-2021-35517, CVE-2021-36090. Upgrade org.apache.commons:commons-compress to
  1.21.
* kubernetes java client upgrade from 12.0.1 to 13.0.0
* Add `event` http receiver
* Support Metric level function `serviceRelation` in `MAL`.
* Support envoy metrics binding into the topology.
* Fix openapi-definitions folder not being read correctly.
* Trace segment wouldn't be recognized as a TopN sample service. Add through #4694 experimentally, but it caused
  performance impact.
* Remove `version` and `endTime` in the segment entity. Reduce indexing payload.
* Fix `mapper_parsing_exception` in ElasticSearch 7.14.
* Support component IDs for Go-Kratos framework.
* [Break Change] Remove endpoint name in the trace query condition. Only support `query by endpoint id`.
* Fix `ProfileSnapshotExporterTest` case on `OpenJDK Runtime Environment AdoptOpenJDK-11.0.11+9 (build 11.0.11+9)`,
  MacOS.
* [Break Change] Remove page path in the browser log query condition. Only support `query by page path id`.
* [Break Change] Remove endpoint name in the backend log query condition. Only support `query by endpoint id`.
* [Break Change] Fix typo for a column `page_path_id`(was `pate_path_id`) of storage entity `browser_error_log`.
* Add component id for Python falcon plugin.
* Add `rpcStatusCode` for `rpc.status_code` tag. The `responseCode` field is marked as deprecated and replaced by `httpResponseStatusCode` field. 
* Remove the duplicated tags to reduce the storage payload.
* Add a new API to test log analysis language.
* Harden the security of Groovy-based DSL, MAL and LAL.
* Fix distinct in Service/Instance/Endpoint query is not working.
* Support collection type in dynamic configuration core.
* Support zookeeper grouped dynamic configurations.
* Fix NPE when OAP nodes synchronize events with each other in cluster mode.
* Support configure sampling rate by `configuration module` dynamically and static configuration file `trace-sample-rate-setting.yml` for service dimension on the backend side. Dynamic configurations `agent-analyzer.default.sampleRate` and `agent-analyzer.default.slowTraceSegmentThreshold` are replaced by `agent-analyzer.default.traceSamplingPolicy`. Static configurations `agent-analyzer.default.sampleRate` and `agent-analyzer.default.slowTraceSegmentThreshold` are replaced by `agent-analyzer.default.traceSampleRateSettingFile`.

#### UI

* Fix not found error when refresh UI.
* Update endpointName to endpointId in the query trace condition.
* Add Python falcon icon on the UI.
* Fix searching endpoints with keywords.

#### Documentation

* Add a section in `Log Collecting And Analysis` doc, introducing the new Python agent log reporter.
* Add one missing step in `otel-receiver` doc about how to activate the default receiver.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/96?closed=1)

------------------
Find change logs of all versions [here](changes).
