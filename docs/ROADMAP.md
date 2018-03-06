# Roadmap
The SkyWalking Roadmap includes three lists of features.
1. The core features, means the SkyWalking PPMC and Committer team plan to implement in the near future.(6 months max)
1. The community features, means the SkyWalking Team agrees the features are required, and welcome to accept the contributions.
1. Long term features, means high value features, but can't be implemented recently.

All the features of three lists are open for discussion.

## Core features
Core features are separated into 3 groups: agent, collector and UI.

### Agent
- Support OpenTracing-Java 0.31.0 or 1.0
- Apache HttpClientComponent 3.x plugin.
- Apache Tomcat6 plugin.
- RabbitMQ plugin. All released version in the near 2 years should be supported at least.
- MongoDB Java Driver 3.0 -> 3.2.x plugin.
- Continue DB/Cache access span merged automatically.

### Collector
- Multi-Tenancy
- Config alarm thresholds.
- Config data TTL.
- Support config data downstream to agent. Need adjust TDP(Trace Data Protocol)
- Support server metrics of other languages, such as C#. Need adjust TDP(Trace Data Protocol)
- Alarm notification. Default through mail.
- Baseline calculation, to make the alarm more accuracy.
- Performance optimization (Stream metric aggregate and query for UI)
- Automated testing (Stream metric aggregate and query for UI)
- Remote module add kafka provider
- Cluster module add redis provider

### UI
- Merged into Collector as a module.
- Support i18n.
- More interactive UI for the DIG data from any domain. 
- Plugin system for the 3rd extention of the UI.
- Support dashboard on large screen.

## Community features
Community features are reported from SkyWalking community or the core team hopes community contributors provide the implementations.

- C# agent.
- Golang SDK as OpenCensus Reporter.
- Support Sharding-JDBC as Collector storage implementor.
- Support connect ES under auth, in Collector.

## Long term features
- More language supported: PHP, Nodejs Server, Python. OpenCensus reporter implementor or Auto-instrument agent.
- Configure delivery/downstream from collector to agent.
- [Java]Server profile
- [Java]Specific service profile

## Project release plan
1. Mar. 2018, 5.0.0-alpha
1. Apr. 2018, 5.0.0-beta
1. May. 2018, 5.0.0-RC
1. Jun. 2018, 5.0.0(GA)
