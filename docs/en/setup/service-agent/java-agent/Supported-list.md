* HTTP Server
  * [Tomcat](https://github.com/apache/tomcat) 7
  * [Tomcat](https://github.com/apache/tomcat) 8
  * [Tomcat](https://github.com/apache/tomcat) 9
  * [Spring Boot](https://github.com/spring-projects/spring-boot) Web 4.x
  * Spring MVC 3.x, 4.x 5.x with servlet 3.x
  * [Nutz Web Framework](https://github.com/nutzam/nutz)  1.x
  * [Struts2 MVC](http://struts.apache.org/)  2.3.x -> 2.5.x
  * [Resin](http://www.caucho.com/resin-4.0/) 3 (Optional¹)
  * [Resin](http://www.caucho.com/resin-4.0/) 4 (Optional¹)
  * [Jetty Server](http://www.eclipse.org/jetty/) 9
  * [Spring Webflux](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html) 5.x
  * [Undertow](http://undertow.io/)  2.0.0.Final -> 2.0.13.Final
  * [RESTEasy](https://resteasy.github.io/)  3.1.0.Final -> 3.7.0.Final
  * [Play Framework](https://www.playframework.com/) 2.6.x -> 2.7.x (Optional²)
  * [Light4J Microservices Framework](https://doc.networknt.com/) 1.6.x -> 2.x
* HTTP Client
  * [Feign](https://github.com/OpenFeign/feign) 9.x
  * [Netflix Spring Cloud Feign](https://github.com/spring-cloud/spring-cloud-netflix/tree/master/spring-cloud-starter-feign) 1.1.x, 1.2.x, 1.3.x
  * [Okhttp](https://github.com/square/okhttp) 3.x
  * [Apache httpcomponent HttpClient](http://hc.apache.org/) 4.2, 4.3
  * [Spring RestTemplete](https://github.com/spring-projects/spring-framework) 4.x
  * [Jetty Client](http://www.eclipse.org/jetty/) 9
  * [Apache httpcomponent AsyncClient](https://hc.apache.org/httpcomponents-asyncclient-dev/) 4.x
* HTTP Gateway
  * [Spring Cloud Gateway](https://spring.io/projects/spring-cloud-gateway) 2.1.x.RELEASE (Optional²)
* JDBC
  * Mysql Driver 5.x, 6.x, 8.x
  * Oracle Driver (Optional¹)
  * H2 Driver 1.3.x -> 1.4.x
  * [Sharding-JDBC](https://github.com/shardingjdbc/sharding-jdbc) 1.5.x
  * [ShardingSphere](https://github.com/apache/incubator-shardingsphere) 3.0.0, 4.0.0-RC1
  * PostgreSQL Driver 8.x, 9.x, 42.x
* RPC Frameworks
  * [Dubbo](https://github.com/alibaba/dubbo) 2.5.4 -> 2.6.0
  * [Dubbox](https://github.com/dangdangdotcom/dubbox) 2.8.4
  * [Apache Dubbo](https://github.com/apache/incubator-dubbo) 2.7.0
  * [Motan](https://github.com/weibocom/motan) 0.2.x -> 1.1.0
  * [gRPC](https://github.com/grpc/grpc-java) 1.x
  * [Apache ServiceComb Java Chassis](https://github.com/apache/servicecomb-java-chassis) 0.1 -> 0.5,1.0.x
  * [SOFARPC](https://github.com/alipay/sofa-rpc) 5.4.0
* MQ
  * [RocketMQ](https://github.com/apache/rocketmq) 4.x
  * [Kafka](http://kafka.apache.org) 0.11.0.0 -> 1.0
  * [ActiveMQ](https://github.com/apache/activemq) 5.x
  * [RabbitMQ](https://www.rabbitmq.com/) 5.x
* NoSQL
  * Redis
    * [Jedis](https://github.com/xetorthio/jedis) 2.x
    * [Redisson](https://github.com/redisson/redisson) Easy Java Redis client 3.5.2+
    * [Lettuce](https://github.com/lettuce-io/lettuce-core) 5.x (Optional²)
  * [MongoDB Java Driver](https://github.com/mongodb/mongo-java-driver) 2.13-2.14,3.3+
  * Memcached Client
    * [Spymemcached](https://github.com/couchbase/spymemcached) 2.x
    * [Xmemcached](https://github.com/killme2008/xmemcached) 2.x
  * [Elasticsearch](https://github.com/elastic/elasticsearch)
    * [transport-client](https://github.com/elastic/elasticsearch/tree/master/client/transport) 5.2.x-5.6.x
  * [Solr](https://github.com/apache/lucene-solr/)
    * [SolrJ](https://github.com/apache/lucene-solr/tree/master/solr/solrj) 7.x
  * [Cassandra](https://github.com/apache/cassandra) 3.x
    * [cassandra-java-driver](https://github.com/datastax/java-driver) 3.6.0-3.7.2

* Service Discovery
  * [Netflix Eureka](https://github.com/Netflix/eureka)
* Distributed Coordination
  * [Zookeeper](https://github.com/apache/zookeeper) 3.4.0+ (Optional² & Except 3.4.4)
* Spring Ecosystem
  * Spring Bean annotations(@Bean, @Service, @Component, @Repository) 3.x and 4.x (Optional²)
  * Spring Core Async SuccessCallback/FailureCallback/ListenableFutureCallback 4.x
* [Hystrix: Latency and Fault Tolerance for Distributed Systems](https://github.com/Netflix/Hystrix) 1.4.20 -> 1.5.12
* Scheduler
  * [Elastic Job](https://github.com/elasticjob/elastic-job) 2.x
* OpenTracing community supported
* [Canal: Alibaba mysql database binlog incremental subscription & consumer components](https://github.com/alibaba/canal) 1.0.25 -> 1.1.2
* JSON
  * [GSON](https://github.com/google/gson) 2.8.x (Optional²)
* Vert.x Ecosystem
  * Vert.x Eventbus 3.2+
  * Vert.x Web 3.x
* Thread Schedule Framework
  * [Spring @Async](https://github.com/spring-projects/spring-framework) 4.x and 5.x


¹Due to license incompatibilities/restrictions these plugins are hosted and released in 3rd part repository, 
 go to [SkyAPM java plugin extension repository](https://github.com/SkyAPM/java-plugin-extensions) to get these.

²These plugins affect the performance or must be used under some conditions, from experiences. So only released in `/optional-plugins`, copy to `/plugins` in order to make them work.
