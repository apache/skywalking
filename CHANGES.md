 Changes by Version
 ==================
 Release Notes.
  
 5.0.0-beta
 ------------------
 
#### UI -> Collector GraphQL query protocol
  - Replace all tps to throughtput/cpm(calls per min)
  - Add `getThermodynamic` service
  - Update version to beta
 
#### Agent Changes
  - Support TLS.
  - Support namespace.
  - Support direct link.
  - Support token.
  - Add across thread toolkit.
  - Add new plugin extend machenism to override agent core implementations.
  - Fix an agent start up sequence bug.
  - Fix wrong gc count.
  - Remove system env override.
  - Add Spring AOP aspect patch to avoid aop conflicts.
 
#### Collector Changes
  - Trace query based on timeline.
  - Delete JVM aggregation in second.
  - Support TLS.
  - Support namespace.
  - Support token auth.
  - Group and aggregate requests based on reponse time and timeline, support Thermodynamic chart query
  - Support component librariy setting through yml file for better extendibility.
  - Optimize performance.
  - Support short column name in ES or other storage implementor.
  - Add a new cache module implementor, based on **Caffeine**.
  - Support system property override settings.
  - Refactor settings initialization.
  - Provide collector instrumentation agent.
  - Support .NET core component libraries.
  - Fix `divide zero` in query.
  - Fix `Data don't remove as expected` in ES implementor.
  - Add some checks in collector modulization core.
  - Add some test cases.
 
#### UI Changes
  - New trace query UI.
  - New Application UI, merge server tab(removed) into applciation as sub page.
  - New Topology UI.
  - New response time / throughput TopN list.
  - Add Thermodynamic chart in overview page.
  - Change all tps to cpm(calls per minutes).
  - Fix wrong osName in server view.
  - Fix wrong startTime in trace view.
  - Fix some icons internet requirements.
 
#### Documents
   - Add TLS document.
   - Add namespace document.
   - Add direct link document.
   - Add token document.
   - Add across thread toolkit document.
   - Add a FAQ about, `Agent or collector version upgrade`.
   - Sync all English document to Chinese.
 
  [Issues and Pull requests](https://github.com/apache/incubator-skywalking/milestone/24)
 
 5.0.0-alpha
 ------------------
 
#### Agent -> Collector protocol
 - Remove C++ keywords
 - Move **Ref** into Span from Segment
 - Add span type, when register an operation

#### UI -> Collector GraphQL query protocol
 - First version protocol
 
#### Agent Changes
 - Support gRPC 1.x plugin
 - Support kafka 0.11 and 1.x plugin
 - Support ServiceComb 0.x plugin
 - Support optional plugin mechanism.
 - Support Spring 3.x and 4.x bean annotation optional plugin
 - Support Apache httpcomponent AsyncClient 4.x plugin 
 - Provide automatic agent daily tests, and release reports [here](https://github.com/SkywalkingTest/agent-integration-test-report).
 - Refactor Postgresql, Oracle, MySQL plugin for compatible.
 - Fix jetty client 9 plugin error
 - Fix async APIs of okhttp plugin error
 - Fix log config didn't work
 - Fix a class loader error in okhttp plugin
 
#### Collector Changes
 - Support metrics analysis and aggregation for application, application instance and service in minute, hour, day and month.
 - Support new GraphQL query protocol
 - Support alarm
 - Provide a prototype instrument for collector.
 - Support node speculate in cluster and application topology. (Provider Node -> Consumer Node) -> (Provider Node -> MQ Server -> Consumer Node)
 
#### UI Changes
 - New 5.0.0 UI!!!
 
 [Issues and Pull requests](https://github.com/apache/incubator-skywalking/milestone/17)
