## Required of third party softwares
- JDK 6+（instruments application can run in jdk6）
- JDK8  ( SkyWalking collector and SkyWalking WebUI )
- Elasticsearch 5.x, cluster mode or not
- Zookeeper 3.4.10

## Download released version
- Go to [released page](http://skywalking.apache.org/downloads/)

## Deploy Zookeeper
Zookeeper is used for collector coordination. Only required you need more than one collector instances.

Add Zookeeper cluster info in each collector `application.yml`
```yml
cluster:
# The Zookeeper cluster for collector cluster management.
  zookeeper:
    # multiple instances should be separated by comma.
    hostPort: localhost:2181
    sessionTimeout: 100000
```

## Deploy Elasticsearch server
- Modify `elasticsearch.yml`
  - Set `cluster.name: CollectorDBCluster`
  - Set `node.name: anyname`, this name can be any, it based on Elasticsearch.
  - Add the following configurations   

```
# The ip used for listening
network.host: 0.0.0.0
thread_pool.bulk.queue_size: 1000
```
See ElasticSearch Official documents to understand how to deploy cluster.

- Start Elasticsearch

### Deploy collector servers
1. Run `tar -xvf skywalking-dist.tar.gz`
2. Config collector in cluster mode.

Cluster mode depends on Zookeeper register and application discovery capabilities. 
So, you just need to adjust the IP config items in `config/application.yml`. 
Change IP and port configs of naming, remote, agent_gRPC, agent_jetty and ui,
replace them to the real ip or hostname which you want to use for cluster.

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
    # OS real network IP(binding required), for collector nodes communicate with each other in cluster. collectorN --(gRPC) --> collectorM
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
    # Stay in `localhost` if UI starts up in default mode.
    # Change it to OS real network IP(binding required), if deploy collector in different machine.
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

3. Run `bin/collectorService.sh`

### Deploy UI servers

1. Run `tar -xvf skywalking-dist.tar.gz`
2. Config UI in cluster mode.

The config items of UI is saved in `bin/webappService.sh` (`bin\webappService.bat` for windows).

| Config                           | Description                                                                                          |
|----------------------------------|------------------------------------------------------------------------------------------------------|
| `server.port`                    | Port to listen on                                                                                    |
| `collector.ribbon.listOfServers` | Address to access collector naming service.(Consist with `naming.jetty` in `config/application.yml`). Multiple collector addresses are split by ',' |

3. Run `bin/webappService.sh`
