Changes by Version
==================
Release Notes.

8.8.0
------------------

#### Project

#### Java Agent

* Support Multiple DNS period resolving mechanism
* Modify `Tags.STATUS_CODE` field name to `Tags.HTTP_RESPONSE_STATUS_CODE` and type from `StringTag` to `IntegerTag`, add `Tags.RPC_RESPONSE_STATUS_CODE` field to hold rpc response code value.

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
* Add `rpcStatusCode` for `rpc.status_code` tag. The `responseCode` field is marked as deprecated and replaced by `httpResponseStatusCode` field. 

#### UI

* Fix not found error when refresh UI.

#### Documentation

* Add a section in `Log Collecting And Analysis` doc, introducing the new Python agent log reporter.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/96?closed=1)

------------------
Find change logs of all versions [here](changes).
