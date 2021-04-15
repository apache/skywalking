Changes by Version
==================
Release Notes.

8.6.0
------------------
#### Project


#### Java Agent
* Add `trace_segment_ref_limit_per_span` configuration mechanism to avoid OOM.


#### OAP-Backend
* BugFix: filter invalid Envoy access logs whose socket address is empty.
* Fix K8s monitoring the incorrect metrics calculate. 
* Loop alarm into event system.

#### UI


#### Documentation
* Polish k8s monitoring otel-collector configuration example.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/84?closed=1)

------------------
Find change logs of all versions [here](changes).
