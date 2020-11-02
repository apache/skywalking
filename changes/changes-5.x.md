5.1.0
------------------
#### Agent Changes
  - Fix spring inherit issue in another way
  - Fix classloader dead lock in jdk7+ - 5.x
  - Support Spring mvc 5.x
  - Support Spring webflux 5.x
  
#### Collector Changes
  - Fix too many open files.
  - Fix the buffer file cannot delete.

5.0.0-GA
------------------

#### Agent Changes
  - Add several package names ignore in agent settings. Classes in these packages would be enhanced, even plugin declared.
  - Support Undertow 2.x plugin.
  - Fix wrong class names of Motan plugin, not a feature related issue, just naming.

#### Collector Changes
  - Make buffer file handler close more safety.
  - Fix NPE in AlarmService

#### Documentation
  - Fix compiling doc link.
  - Update new live demo address.


5.0.0-RC2
------------------

#### Agent Changes
  - Support ActiveMQ 5.x
  - Support RuntimeContext used out of TracingContext.
  - Support Oracle ojdbc8 Plugin.
  - Support ElasticSearch client transport 5.2-5.6 Plugin
  - Support using agent.config with given path through system properties.
  - Add a new way to transmit the Request and Response, to avoid bugs in Hytrix scenarios.
  - Fix HTTPComponent client v4 operation name is empty.
  - Fix 2 possible NPEs in Spring plugin.
  - Fix a possible span leak in SpringMVC plugin.
  - Fix NPE in Spring callback plugin.
  
#### Collector Changes
  - Add GZip support for Zipkin receiver.
  - Add new component IDs for nodejs.
  - Fix Zipkin span receiver may miss data in request.
  - Optimize codes in heatmap calculation. Reduce unnecessary divide.
  - Fix NPE in Alarm content generation.
  - Fix the precision lost in `ServiceNameService#startTimeMillis`.
  - Fix GC count is 0.
  - Fix topology breaks when RPC client uses the async thread call.
  
#### UI Changes
  - Fix UI port can't be set by startup script in Windows.
  - Fix Topology self link error.
  - Fix stack color mismatch label color in gc time chart.
  
#### Documentation
  - Add users list.
  - Fix several document typo.
  - Sync the Chinese documents.
  - Add OpenAPM badge.
  - Add icon/font documents to NOTICE files.
  
[Issues and Pull requests](https://github.com/apache/incubator-skywalking/milestone/27?closed=1)


5.0.0-beta2
------------------

#### UI -> Collector GraphQL query protocol
  - Add order and status in trace query. 

#### Agent Changes
  - Add SOFA plugin.
  - Add witness class for Kafka plugin.
  - Add RuntimeContext in Context.
  - Fix RuntimeContext fail in Tomcat plugin. 
  - Fix incompatible for `getPropertyDescriptors` in Spring core. 
  - Fix spymemcached plugin bug.
  - Fix database URL parser bug.
  - Fix `StringIndexOutOfBoundsException` when mysql jdbc url without databaseName。
  - Fix duplicate slash in Spring MVC plugin bug.
  - Fix namespace bug.
  - Fix NPE in Okhttp plugin when connect failed.
  - FIx `MalformedURLException` in httpClientComponent plugin. 
  - Remove unused dependencies in Dubbo plugin.
  - Remove gRPC timeout to avoid out of memory leak.
  - Rewrite Async http client plugin.
  - [Incubating] Add trace custom ignore optional plugin. 

#### Collector Changes
  - Topology query optimization for more than 100 apps.
  - Error rate alarm is not triggered.
  - Tolerate unsupported segments.
  - Support Integer Array, Long Array, String Array, Double Array in streaming data model.
  - Support multiple entry span and multiple service name in one segment durtaion record.
  - Use BulkProcessor to control the linear writing of data by multiple threads.
  - Determine the log is enabled for the DEBUG level before printing message.
  - Add `static` modifier to Logger. 
  - Add AspNet component.
  - Filter inactive service in query.
  - Support to query service based on Application.
  - Fix `RemoteDataMappingIdNotFoundException`
  - Exclude component-libaries.xml file in collector-*.jar, make sure it is in `/conf` only. 
  - Separate a single TTL in minute to in minute, hour, day, month metric and trace.
  - Add order and status in trace query. 
  - Add folder lock to buffer folder.
  - Modify operationName search from `match` to `match_phrase`.
  - [Incubating] Add Zipkin span receiver. Support analysis Zipkin v1/v2 formats.
  - [Incubating] Support sharding-sphere as storage implementor.
  
#### UI Changes
  - Support login and access control.
  - Add new webapp.yml configuration file.
  - Modify webapp startup script.
  - Link to trace query from Thermodynamic graph
  - Add application selector in service view.
  - Add order and status in trace query.
  
#### Documentation
  - Add architecture design doc.
  - Reformat deploy document. 
  - Adjust Tomcat deploy document.
  - Remove all Apache licenses files in dist release packages.
  - Update user cases.
  - Update UI licenses.
  - Add incubating sections in doc.

[Issues and Pull requests](https://github.com/apache/incubator-skywalking/milestone/28?closed=1)
  
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
  - New Application UI, merge server tab(removed) into application as sub page.
  - New Topology UI.
  - New response time / throughput TopN list.
  - Add Thermodynamic chart in overview page.
  - Change all tps to cpm(calls per minutes).
  - Fix wrong osName in server view.
  - Fix wrong startTime in trace view.
  - Fix some icons internet requirements.
 
#### Documentation
   - Add TLS document.
   - Add namespace document.
   - Add direct link document.
   - Add token document.
   - Add across thread toolkit document.
   - Add a FAQ about, `Agent or collector version upgrade`.
   - Sync all English document to Chinese.
 
[Issues and Pull requests](https://github.com/apache/incubator-skywalking/milestone/24?closed=1)
 
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
 
 [Issues and Pull requests](https://github.com/apache/incubator-skywalking/milestone/17?closed=1)
