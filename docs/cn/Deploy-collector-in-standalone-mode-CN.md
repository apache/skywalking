## 用途说明
单机模式使用本地H2数据库，不支持集群部署。主要用于：预览、功能测试、演示和低压力系统。

## 所需的第三方软件
- JDK8+

## 下载发布版本
- 前向[发布页面](https://github.com/OpenSkywalking/skywalking/releases)

## Quick Start
Collector单机模拟启动简单，提供和集群模式相同的功能，单机模式下除端口被占用的情况下，直接启动即可。

### 部署collector
1. 解压安装包`tar -xvf skywalking-collector.tar.gz`，windows用户可以选择zip包
1. 运行`bin/startup.sh`启动。windows用户为.bat文件。

- `config/application.yml`
```
# 单机模式下无需配置集群相关信息
#cluster:
#  zookeeper:
#    hostPort: localhost:2181
#    sessionTimeout: 100000
# agent_server, agent_stream, ui, collector_inside中配置的IP都是Collector所使用的IP地址
agent_server:
  jetty:
    host: localhost
    # The port used
    port: 10800
    context_path: /
agent_stream:
  grpc:
    host: localhost
    port: 11800
  jetty:
    host: localhost
    port: 12800
    context_path: /
ui:
  jetty:
    host: localhost
    port: 12800
    context_path: /
collector_inside:
  grpc:
    host: localhost
    port: 11800

#storage:
#  elasticsearch:
#    cluster_name: CollectorDBCluster
#    cluster_transport_sniffer: true
#    Elastic Search地址信息
#    cluster_nodes: localhost:9300
#    index_shards_number: 2
#    index_replicas_number: 0
```

## 使用Elastic Search代替H2存储
由于H2数据库性能的局限性，我们在单机模式下，也支持使用ElasticSearch 5.3作为存储。你需要安装对应的ElasticSearch 5.3，并取消
- 在单机模式下除了支持内置的H2数据库运行，也支持其他的存储（当前已支持的ElasticSearch 5.3），安装storage注释，修改配置信息即可。取消Storage相关配置节的注释。
```yaml
#storage:
#  elasticsearch:
#    cluster_name: CollectorDBCluster
#    cluster_transport_sniffer: true
#    cluster_nodes: localhost:9300
#    index_shards_number: 2
#    index_replicas_number: 0
```

### 部署Elasticsearch
- 修改`elasticsearch.yml`文件
  - 设置 `cluster.name: CollectorDBCluster`。此名称需要和collector配置文件一致。
  - 设置 `node.name: anyname`, 可以设置为任意名字，如Elasticsearch为集群模式，则每个节点名称需要不同。
  - 增加如下配置

```
# ES监听的ip地址
network.host: 0.0.0.0
thread_pool.bulk.queue_size: 1000
```

- 启动Elasticsearch
