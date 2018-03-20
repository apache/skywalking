 Changes by Version
 ==================
 Release Notes.
 
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
