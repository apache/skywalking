Changes by Version
==================
Release Notes.

9.0.0
------------------

#### Project

* Upgrade log4j2 to 2.17.0 for CVE-2021-44228, CVE-2021-45046 and CVE-2021-45105. This CVE only effects on JDK if JNDI is opened in
  default. Notice, using JVM option `-Dlog4j2.formatMsgNoLookups=true` or setting
  the `LOG4J_FORMAT_MSG_NO_LOOKUPS=”true”` environment variable also avoids CVEs.

#### OAP Server

* Fix potential NPE in OAL string match and a bug when right-hand-side variable includes double quotes.
* Bump up Armeria version to fix CVE.
* Polish ETCD cluster config environment variables.
* Add the analysis of metrics in Satellite MetricsService.

#### UI

#### Documentation

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/112?closed=1)

------------------
Find change logs of all versions [here](changes).
