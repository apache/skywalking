Changes by Version
==================
Release Notes.

8.8.0
------------------

#### Project

#### Java Agent

* Support Multiple DNS period resolving mechanism

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
* Support component IDs for Go-Kratos framework.

#### UI

* Fix not found error when refresh UI.

#### Documentation

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/96?closed=1)

------------------
Find change logs of all versions [here](changes).
