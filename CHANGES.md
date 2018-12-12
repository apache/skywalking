Changes by Version
==================
Release Notes.

6.0.0-beta
------------------

#### Protocol
- Provide Trace Data Protocol v2
- Provide SkyWalking Cross Process Propagation Headers Protocol v2.

#### Java Agent
- Support Trace Data Protocol v2
- Support SkyWalking Cross Process Propagation Headers Protocol v2.
- Support SkyWalking Cross Process Propagation Headers Protocol v1 running in compatible way. Need declare open explicitly.
- Support SpringMVC 5
- Support webflux
- Support a new way to override agent.config by system env. 
- Span tag can override by explicit way.
- Fix Spring Controller Inherit issue.
- Fix ElasticSearch plugin NPE.
- Fix agent classloader dead lock in certain situation.
- Fix agent log typo.
- Fix wrong component id in resettemplete plugin.
- Fix use transform `ignore()` in wrong way.
- Fix H2 query bug.

#### Backend
- Support Trace Data Protocol v2. And Trace Data Protocol v1 is still supported.
- Support MySQL as storage.
- Support TiDB as storage.
- Support a new way to override application.yml by system env.
- Support service instance and endpoint alarm.
- Support namespace in istio receiver.
- Support service throughput(cpm), successful rate(sla), avg response time and p99/p95/p90/p75/p50 response time.
- Support backend trace sampling.
- Support Zipkin format again.
- Support init mode.
- Support namespace in Zookeeper cluster management.
- Support consul plugin in cluster module.
- OAL generate tool has been integrated into main repo, in the maven `compile` stage.
- Optimize trace paging query.
- Fix trace query don't use fuzzy query in ElasticSearch storage.
- Fix alarm can't be active in right way.
- Fix unnecessary condition in database and cache number query.
- Fix wrong namespace bug in ElasticSearch storage.
- Fix `Remote clients selector error: / by zero `.
- Fix segment TTL is not working.

#### UI
- Support service throughput(cpm), successful rate(sla), avg response time and p99/p95/p90/p75/p50 response time.
- Fix TopN endpoint link doesn't work right.
- Fix trace stack style.
- Fix CI.

#### Document
- Add more agent setting documents.
- Add more contribution documents.
- Update user wall and powered-by page.
- Add RocketBot UI project link in document.

All issues and pull requests are [here](https://github.com/apache/incubator-skywalking/milestone/31?closed=1)

6.0.0-alpha
------------------

SkyWalking 6 is totally new milestone for the project. At this point, we are not just a distributing
tracing system with analysis and visualization capabilities. We are an **Observability Analysis Platform(OAL)**.

The core and most important features in v6 are
1. Support to collect telemetry data from different sources, such as multiple language agents and service mesh.
1. Extensible stream analysis core. Make SQL and cache analysis available in core level, although haven't
provided in this release.
1. Provide **Observability Analysis Language(OAL)** to make analysis metric customization available.
1. New GraphQL query protocol. Not binding with UI now.
1. UI topology is better now.
1. New alarm core provided. In alpha, only on service related metric.

All issues and pull requests are [here](https://github.com/apache/incubator-skywalking/milestone/29?closed=1)

5.x releases
------------------
You could find all CHANGES of 5.x at [here](https://github.com/apache/incubator-skywalking/blob/5.x/CHANGES.md)
