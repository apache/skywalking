Changes by Version
==================
Release Notes.

9.0.0
------------------

#### Project

* Upgrade log4j2 to 2.15.0 for CVE-2021-44228. This CVE only effects on JDK if JDNI is opened in default. Notice, using
  JVM option `-Dlog4j2.formatMsgNoLookups=true` also avoids CVE if your JRE opened JNDI in default.

#### OAP Server

* Fix potential NPE in OAL string match and a bug when right-hand-side variable includes double quotes.
* Bump up Armeria version to fix CVE.
* Polish ETCD cluster config environment variables.

#### UI


#### Documentation

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/112?closed=1)

------------------
Find change logs of all versions [here](changes).
