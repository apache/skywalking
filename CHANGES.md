Changes by Version
==================
Release Notes.

8.5.0
------------------
#### Project
* Update frontend-maven-plugin to 1.11.0, for Download node x64 binary on Apple Silicon.

#### Java Agent
* Remove invalid mysql configuration in agent.config.
* Add net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.Listener to show detail message when redefine errors occur


#### OAP-Backend
* Allow user-defined `JAVA_OPTS` in the startup script.
* Metrics combination API supports abandoning results.
* Add a new concept "Event" and its implementations to collect events.
* Add some defensive codes for NPE and bump up Kubernetes client version to expose exception stack trace.
* Update the `timestamp` field type for `LogQuery`.
* Support Zabbix protocol to receive agent metrics.
* Update the Apdex metric combine calculator.

#### UI
* Update selector scroller to show in all pages.
* Implement searching logs with date.

#### Documentation


All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/76?closed=1)

------------------
Find change logs of all versions [here](changes).
