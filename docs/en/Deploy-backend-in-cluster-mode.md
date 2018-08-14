## Requirements
- JDK 6+（instruments application can run in jdk6）
- JDK8  ( SkyWalking collector and SkyWalking WebUI )
- Elasticsearch 5.x, cluster mode or not
- Zookeeper 3.4.10
- OS time(include time zone) of Applications under monitoring, collectors, UIs and your local machine are all correct and same.

## Download released version
- Go to [released page](http://skywalking.apache.org/downloads/)

## Deploy Zookeeper
Zookeeper is used for collector coordination. Only required if you need more than one collector instances.

Add Zookeeper cluster info in each collector `application.yml`
```yml
cluster:
# The Zookeeper cluster for collector cluster management.
  zookeeper:
    # multiple instances should be separated by comma.
    hostPort: localhost:2181
    sessionTimeout: 100000
```

## Deploy ElasticSearch server
ElasticSearch is using for storage all traces, metrics and alarms.  

- Modify `elasticsearch.yml`
  - Set `cluster.name: CollectorDBCluster`
  - Set `node.name: anyname`, this name can be any, it based on Elasticsearch.
  - Add the following configurations   

```
# The ip used for listening
network.host: 0.0.0.0
thread_pool.bulk.queue_size: 1000
```
See ElasticSearch Official documents to understand how to deploy cluster(Recommend).

- Start Elasticsearch

### Set collector
There are five types of connection for SkyWalking cluster, related to collector settings.
1. Agent to collectors by using HTTP, named as `naming`.
1. Agent to collectors by using gRPC, named as `agent_gRPC`. 
1. Collector to collector by using gRPC, named as `remote`.
1. UI to collector by using HTTP, named as `ui`. Don't need to change in most cases.
1. Optional connection: Agent to collector by using HTTP, named as `agent_jetty`.


The following segment of `application.yml` shows you the detail of each settings.

- `config/application.yml`
```
cluster:
# The Zookeeper cluster for collector cluster management.
  zookeeper:
    hostPort: localhost:2181
    sessionTimeout: 100000
naming:
# Host and port used for agent config
  jetty:
    # OS real network IP(binding required), for agent to find collector cluster. agent --(HTTP)--> collector
    host: localhost 
    port: 10800
    contextPath: /
remote:
  gRPC:
    # OS real network IP(binding required), for collector node to communicate with each other in cluster. collectorN --(gRPC) --> collectorM
    host: localhost 
    port: 11800
agent_gRPC:
  gRPC:
    # OS real network IP(binding required), for agent to uplink data(trace/metrics) to collector. agent--(gRPC)--> collector
    host: localhost
    port: 11800
agent_jetty:
  jetty:
    # OS real network IP(binding required), for agent to uplink data(trace/metrics) to collector through HTTP. agent--(HTTP)--> collector
    # SkyWalking native Java/.Net/node.js agents don't use this.
    # Open this for other implementor.
    host: localhost
    port: 12800
    contextPath: /
analysis_register:
  default:
analysis_jvm:
  default:
analysis_segment_parser:
  default:
    bufferFilePath: ../buffer/
    bufferOffsetMaxFileSize: 10M
    bufferSegmentMaxFileSize: 500M
ui:
  jetty:
    # OS real network IP(binding required), for UI to query from collector.
    host: localhost
    port: 12800
    contextPath: /
# Config Elasticsearch cluster connection info.
storage:
  elasticsearch:
    clusterName: CollectorDBCluster
    clusterTransportSniffer: true
    clusterNodes: localhost:9300
    indexShardsNumber: 2
    indexReplicasNumber: 0
    highPerformanceMode: true
    # Set an expired for metric/trace data. After the timeout has expired, the metric/trace data will be deleted automatically.
    traceDataTTL: 90 # Unit is minute
    minuteMetricDataTTL: 45 # Unit is minute
    hourMetricDataTTL: 36 # Unit is hour
    dayMetricDataTTL: 45 # Unit is day
    monthMetricDataTTL: 18 # Unit is month
configuration:
  default:
    # namespace: xxxxx
    
    # alarm threshold
    applicationApdexThreshold: 2000
    serviceErrorRateThreshold: 10.00
    serviceAverageResponseTimeThreshold: 2000
    instanceErrorRateThreshold: 10.00
    instanceAverageResponseTimeThreshold: 2000
    applicationErrorRateThreshold: 10.00
    applicationAverageResponseTimeThreshold: 2000
    
    # thermodynamic
    thermodynamicResponseTimeStep: 50
    thermodynamicCountOfResponseTimeSteps: 40
```

### Set UI

The config items of UI is saved in `webapp/webapp.yml`. 
Change `collector.ribbon.listOfServers` by following the description, with matching `naming.jetty`. 

| Config                           | Description                                                                                          |
|----------------------------------|------------------------------------------------------------------------------------------------------|
| `server.port`                    | Port to listen on. Default: 8080                                                                                 |
| `collector.ribbon.listOfServers` | Address to access collector naming service.(Consist with `naming.jetty` in `config/application.yml`). Multiple collector addresses are split by ',' |
| `collector.path`                 | Collector query uri. Default: /graphql                                                                           |
| `collector.ribbon.ReadTimeout`   | Query timeout. Default: 10 seconds                                                                               |
| `security.user.*`                | Login username/password. Default: admin/admin                                                                    |

### Start up collector node
1. Run `bin/startup.sh` to start collector and UI together. 
2. (Run if don't use 1 to start up)Run `bin/collectorService.sh` when you only want to start up collector.
3. (Run if don't use 1 to start up)Run `bin/webappService.sh` when you only want to start up UI.

