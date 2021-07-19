# Tracing and Tracing based Metrics Analyze Plugins
The following plugins provide the distributed tracing capability, and the OAP backend would analyze the topology and 
metrics based on the tracing data.

* HTTP Server
  * [Tomcat](https://github.com/apache/tomcat) 7
  * [Tomcat](https://github.com/apache/tomcat) 8
  * [Tomcat](https://github.com/apache/tomcat) 9
  * [Spring Boot](https://github.com/spring-projects/spring-boot) Web 4.x
  * Spring MVC 3.x, 4.x 5.x with servlet 3.x
  * [Nutz Web Framework](https://github.com/nutzam/nutz)  1.x
  * [Struts2 MVC](http://struts.apache.org/)  2.3.x -> 2.5.x
  * [Resin](https://www.caucho.com/resin-4.0/) 3 (Optional¹)
  * [Resin](https://www.caucho.com/resin-4.0/) 4 (Optional¹)
  * [Jetty Server](http://www.eclipse.org/jetty/) 9
  * [Spring WebFlux](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html) 5.x (Optional¹)
  * [Undertow](http://undertow.io/)  1.3.0.Final -> 2.0.27.Final
  * [RESTEasy](https://resteasy.github.io/)  3.1.0.Final -> 3.7.0.Final
  * [Play Framework](https://www.playframework.com/) 2.6.x -> 2.8.x
  * [Light4J Microservices Framework](https://doc.networknt.com/) 1.6.x -> 2.x
  * [Netty SocketIO](https://github.com/mrniko/netty-socketio) 1.x
* HTTP Client
  * [Feign](https://github.com/OpenFeign/feign) 9.x
  * [Netflix Spring Cloud Feign](https://github.com/spring-cloud/spring-cloud-openfeign) 1.1.x -> 2.x
  * [Okhttp](https://github.com/square/okhttp) 3.x -> 4.x
  * [Apache httpcomponent HttpClient](http://hc.apache.org/) 2.0 -> 3.1, 4.2, 4.3
  * [Spring RestTemplete](https://github.com/spring-projects/spring-framework) 4.x
  * [Jetty Client](http://www.eclipse.org/jetty/) 9
  * [Apache httpcomponent AsyncClient](https://hc.apache.org/httpcomponents-asyncclient-4.1.x/) 4.x
  * [AsyncHttpClient](https://github.com/AsyncHttpClient/async-http-client) 2.x
  * JRE HttpURLConnection (Optional²)
* HTTP Gateway
  * [Spring Cloud Gateway](https://spring.io/projects/spring-cloud-gateway) 2.0.2.RELEASE -> 3.x (Optional²)
* JDBC
  * Mysql Driver 5.x, 6.x, 8.x
  * Oracle Driver (Optional¹)
  * H2 Driver 1.3.x -> 1.4.x
  * [ShardingSphere](https://github.com/apache/shardingsphere) 3.0.0, 4.0.0, 4.0.1, 4.1.0, 4.1.1, 5.0.0-beta
  * PostgreSQL Driver 8.x, 9.x, 42.x
  * Mariadb Driver 2.x, 1.8
  * [InfluxDB](https://github.com/influxdata/influxdb-java) 2.5 -> 2.17
  * [Mssql-Jtds](https://github.com/milesibastos/jTDS) 1.x
  * [Mssql-jdbc](https://github.com/microsoft/mssql-jdbc) 6.x -> 8.x
* RPC Frameworks
  * [Dubbo](https://github.com/alibaba/dubbo) 2.5.4 -> 2.6.0
  * [Dubbox](https://github.com/dangdangdotcom/dubbox) 2.8.4
  * [Apache Dubbo](https://github.com/apache/dubbo) 2.7.0
  * [Motan](https://github.com/weibocom/motan) 0.2.x -> 1.1.0
  * [gRPC](https://github.com/grpc/grpc-java) 1.x
  * [Apache ServiceComb Java Chassis](https://github.com/apache/servicecomb-java-chassis) 0.1 -> 0.5,1.x
  * [SOFARPC](https://github.com/alipay/sofa-rpc) 5.4.0
  * [Armeria](https://github.com/line/armeria) 0.63.0 -> 0.98.0
  * [Apache Avro](http://avro.apache.org) 1.7.0 - 1.8.x
  * [Finagle](https://github.com/twitter/finagle) 6.44.0 -> 20.1.0  (6.25.0 -> 6.44.0 not tested)
  * [Brpc-Java](https://github.com/baidu/brpc-java) 2.3.7 -> 2.5.3
  * [Thrift](https://github.com/apache/thrift/tree/master/lib/java) 0.10.0 -> 0.12.0
  * [Apache CXF](https://github.com/apache/cxf) 3.x
* MQ
  * [RocketMQ](https://github.com/apache/rocketmq) 4.x
  * [Kafka](http://kafka.apache.org) 0.11.0.0 -> 2.8.0
  * [Spring-Kafka](https://github.com/spring-projects/spring-kafka) Spring Kafka Consumer 1.3.x -> 2.3.x (2.0.x and 2.1.x not tested and not recommended by [the official document](https://spring.io/projects/spring-kafka))
  * [ActiveMQ](https://github.com/apache/activemq) 5.10.0 -> 5.15.4
  * [RabbitMQ](https://www.rabbitmq.com/) 5.x
  * [Pulsar](http://pulsar.apache.org) 2.2.x -> 2.4.x
  * [Aliyun ONS](https://help.aliyun.com/document_detail/114448.html) 1.x (Optional¹)
* NoSQL
  * Redis
    * [Jedis](https://github.com/xetorthio/jedis) 2.x
    * [Redisson](https://github.com/redisson/redisson) Easy Java Redis client 3.5.2+
    * [Lettuce](https://github.com/lettuce-io/lettuce-core) 5.x
  * [MongoDB Java Driver](https://github.com/mongodb/mongo-java-driver) 2.13-2.14, 3.4.0-3.12.7, 4.0.0-4.1.0
  * Memcached Client
    * [Spymemcached](https://github.com/couchbase/spymemcached) 2.x
    * [Xmemcached](https://github.com/killme2008/xmemcached) 2.x
  * [Elasticsearch](https://github.com/elastic/elasticsearch)
    * [transport-client](https://github.com/elastic/elasticsearch/tree/v5.2.0/client/transport) 5.2.x-5.6.x
    * [transport-client](https://github.com/elastic/elasticsearch/tree/v6.7.1/client/transport) 6.7.1-6.8.4
    * [transport-client](https://github.com/elastic/elasticsearch/tree/7.0/client/transport) 7.0.0-7.5.2
    * [rest-high-level-client](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/6.7/index.html) 6.7.1-6.8.4
    * [rest-high-level-client](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/7.0/java-rest-high.html) 7.0.0-7.5.2
  * [Solr](https://github.com/apache/solr/)
    * [SolrJ](https://github.com/apache/solr/tree/main/solr/solrj) 7.x
  * [Cassandra](https://github.com/apache/cassandra) 3.x
    * [cassandra-java-driver](https://github.com/datastax/java-driver) 3.7.0-3.7.2
  * HBase
    * [hbase-client](https://github.com/apache/hbase) HTable 1.0.0-2.4.2
  * Neo4j
    * [Neo4j-java](https://neo4j.com/docs/java-manual/current) 4.x
* Service Discovery
  * [Netflix Eureka](https://github.com/Netflix/eureka)
* Distributed Coordination
  * [Zookeeper](https://github.com/apache/zookeeper) 3.4.x (Optional² & Except 3.4.4)
* Spring Ecosystem
  * Spring Bean annotations(@Bean, @Service, @Component, @Repository) 3.x and 4.x (Optional²)
  * Spring Core Async SuccessCallback/FailureCallback/ListenableFutureCallback 4.x
  * Spring Transaction 4.x and 5.x (Optional²)
* [Hystrix: Latency and Fault Tolerance for Distributed Systems](https://github.com/Netflix/Hystrix) 1.4.20 -> 1.5.18
* [Sentinel: The Sentinel of Your Microservices](https://github.com/alibaba/Sentinel) 1.7.0 -> 1.8.1
* Scheduler
  * [Elastic Job](https://github.com/elasticjob/elastic-job) 2.x
  * [Apache ShardingSphere-Elasticjob](https://github.com/apache/shardingsphere-elasticjob) 3.0.0-alpha
  * [Spring @Scheduled](https://github.com/spring-projects/spring-framework) 3.1+
  * [Quartz Scheduler](https://github.com/quartz-scheduler/quartz) 2.x (Optional²)
  * [XXL Job](https://github.com/xuxueli/xxl-job) 2.x
* OpenTracing community supported
* [Canal: Alibaba mysql database binlog incremental subscription & consumer components](https://github.com/alibaba/canal) 1.0.25 -> 1.1.2
* JSON
  * [GSON](https://github.com/google/gson) 2.8.x (Optional²)
* Vert.x Ecosystem
  * Vert.x Eventbus 3.2+
  * Vert.x Web 3.x
* Thread Schedule Framework
  * [Spring @Async](https://github.com/spring-projects/spring-framework) 4.x and 5.x
  * [Quasar](https://github.com/puniverse/quasar) 0.7.x
  * JRE Callable and Runnable (Optional²)
* Cache
  * [Ehcache](https://www.ehcache.org/) 2.x
  * [GuavaCache](https://github.com/google/guava) 18.x -> 23.x (Optional²)
* Kotlin
  * [Coroutine](https://kotlinlang.org/docs/reference/coroutines-overview.html) 1.0.1 -> 1.3.x (Optional²)
* GraphQL
  * [Graphql](https://github.com/graphql-java) 8.0 -> 15.x
* Pool
  * [Apache Commons DBCP](https://github.com/apache/commons-dbcp) 2.x
* Logging Framework
  * [log4j](https://github.com/apache/log4j) 2.x
  * [log4j2](https://github.com/apache/logging-log4j2) 1.2.x 
  * [logback](https://github.com/qos-ch/logback) 1.2.x
* ORM
  * [MyBatis](https://github.com/mybatis/mybatis-3) 3.4.x -> 3.5.x

# Meter Plugins
The meter plugin provides the advanced metrics collections, which are not a part of tracing.

___
¹Due to license incompatibilities/restrictions these plugins are hosted and released in 3rd part repository, 
 go to [SkyAPM java plugin extension repository](https://github.com/SkyAPM/java-plugin-extensions) to get these.

²These plugins affect the performance or must be used under some conditions, from experiences. So only released in `/optional-plugins` or `/bootstrap-plugins`, copy to `/plugins` in order to make them work.
