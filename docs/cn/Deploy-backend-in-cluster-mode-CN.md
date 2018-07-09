## 所需的第三方软件
- JDK6+(被监控的应用程序运行在jdk6及以上版本)
- JDK8+(SkyWalking collector和WebUI部署在jdk8及以上版本)
- Elasticsearch 5.x(集群模式或不使用)
- Zookeeper 3.4.10
- 被监控应用的宿主服务器系统时间(包含时区)与collectors,UIs部署的宿主服务器时间设置正确且相同

## 下载发布版本
- 前向[发布页面下载](http://skywalking.apache.org/downloads/)

## 部署 Zookeeper
Zookeeper用于collector协作,仅在需要多个collector实例时才需要.

在每个collector实例的`application.yml`中添加Zookeeper集群配置
```yml
cluster:
# zk用于管理collector集群协作.
  zookeeper:
    # 多个zk连接地址用逗号分隔.
    hostPort: localhost:2181
    sessionTimeout: 100000
```

## 部署Elasticsearch
- 修改`elasticsearch.yml`文件
  - 设置 `cluster.name: CollectorDBCluster`。此名称需要和collector配置文件一致。
  - 设置 `node.name: anyname`，可以设置为任意名字，如Elasticsearch为集群模式，则每个节点名称需要不同。
  - 增加如下配置

```
# ES监听的ip地址
network.host: 0.0.0.0
thread_pool.bulk.queue_size: 1000
```
请参阅ElasticSearch官方文档以了解如何部署集群（推荐）

- 启动 Elasticsearch

### 配置 collector
下面是关于collector连接配置的5种类型方式
1. `naming`     :agent使用HTTP协议连接collectors
1. `agent_gRPC` :agent使用gRPC协议连接collectors
1. `remote`     :Collector使用gRPC协议连接collector
1. `ui`         :使用HTTP协议连接collector,(大多数情况不需要修改)
1. `agent_jetty`:agent使用HTTP协议连接collectors(可选连接)


以下是 `application.yml`的详细的配置

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
    # 配置agent发现collector集群,host必须要系统真实网络ip地址. agent --(HTTP)--> collector
    host: localhost 
    port: 10800
    contextPath: /
remote:
  gRPC:
    # 配置collector节点在集群中相互通信,host必须要系统真实网络ip地址. collectorN --(gRPC) --> collectorM
    host: localhost 
    port: 11800
agent_gRPC:
  gRPC:
    # 配置agent上传(链路跟踪和指标)数据到collector,host必须要系统真实网络ip地址. agent--(gRPC)--> collector
    host: localhost
    port: 11800
agent_jetty:
  jetty:
    # 配置agent上传(链路跟踪和指标)数据到collector,host必须要系统真实网络ip地址. agent--(HTTP)--> collector
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
    # 配置UI访问collector,host必须要系统真实网络ip地址.
    host: localhost
    port: 12800
    contextPath: /
# 配置Elasticsearch 集群连接信息
storage:
  elasticsearch:
    clusterName: CollectorDBCluster
    clusterTransportSniffer: true
    clusterNodes: localhost:9300
    indexShardsNumber: 2
    indexReplicasNumber: 0
    highPerformanceMode: true
    # 设置统计指标数据的失效时间，当指标数据失效时系统将数据自动删除.
    traceDataTTL: 90 # 单位为分
    minuteMetricDataTTL: 45 # 单位为分
    hourMetricDataTTL: 36 # 单位为小时
    dayMetricDataTTL: 45 # 单位为天
    monthMetricDataTTL: 18 # 单位为月
configuration:
  default:
    # namespace: xxxxx
    # 告警阀值
    applicationApdexThreshold: 2000
    serviceErrorRateThreshold: 10.00
    serviceAverageResponseTimeThreshold: 2000
    instanceErrorRateThreshold: 10.00
    instanceAverageResponseTimeThreshold: 2000
    applicationErrorRateThreshold: 10.00
    applicationAverageResponseTimeThreshold: 2000
    
    # 热力图配置，修改配置后需要删除热力指标统计表，由系统重建
    thermodynamicResponseTimeStep: 50
    thermodynamicCountOfResponseTimeSteps: 40
```

### 配置 UI

UI的配置项保存在`webapp/webapp.yml`中.
参考下面描述,更改 `collector.ribbon.listOfServers`并且与 `naming.jetty`参数值对应.

| Config                           | Description                                                                                          |
|----------------------------------|------------------------------------------------------------------------------------------------------|
| `server.port`                    | 默认监听8080端口                                                                                 |
| `collector.ribbon.listOfServers` | collector的访问服务名称(与`config/application.yml`中`naming.jetty`配置保持相同) 且若是多个 collector 服务名称用','分隔 |
| `collector.path`                 | Collector 查询uri地址. 默认是/graphql                                                                           |
| `collector.ribbon.ReadTimeout`   | 查询超时时间,默认是10秒                                                                               |
| `security.user.*`                | 登录用户名/密码. 默认是 admin/admin                                                                    |

### 启动 collector 节点
1. 使用 `bin/startup.sh`同时启动collector和UI，若不使用1启动,需要单独启动,参考2,3
2. 单独启动collector,运行 `bin/collectorService.sh`
3. 单独启动UI,运行 `bin/webappService.sh`