Changes by Version
==================
Release Notes.

9.0.0
------------------

#### Project

* Upgrade log4j2 to 2.17.1 for CVE-2021-44228, CVE-2021-45046, CVE-2021-45105 and CVE-2021-44832. This CVE only effects
  on JDK if JNDI is opened in default. Notice, using JVM option `-Dlog4j2.formatMsgNoLookups=true` or setting
  the `LOG4J_FORMAT_MSG_NO_LOOKUPS=”true”` environment variable also avoids CVEs.
* Upgrade maven-wrapper to 3.1.0, maven to 3.8.4 for performance improvements and ARM more native support.

#### OAP Server

* Fix potential NPE in OAL string match and a bug when right-hand-side variable includes double quotes.
* Bump up Armeria version to fix CVE.
* Polish ETCD cluster config environment variables.
* Add the analysis of metrics in Satellite MetricsService.
* Fix `Can't split endpoint id into 2 parts` bug for endpoint ID. In the TCP in service mesh observability, endpoint
  name doesn't exist in TCP traffic.
* Upgrade H2 version to 2.0.202 to fix CVE-2021-23463.
* Extend column name override mechanism working for `ValueColumnMetadata`.
* Introduce new concept `Layer` and removed `NodeType`. More details refer to [v9-version-upgrade](docs/en/FAQ/v9-version-upgrade.md)
* Add `FAAS` to SpanLayer.

#### UI

* Remove unused jars (log4j-api.jar) in classpath.
* Bump up netty version to fix CVE.

#### Documentation

* update backend-alarm.md doc, support op "=" to "==".

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/112?closed=1)

------------------
Find change logs of all versions [here](changes).
