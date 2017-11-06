## 所需的第三方软件
- 被监控程序要求JDK6+
- sky-walking server和webui要求JDK8+
- Elasticsearch 5.3
- Zookeeper 3.4.10

## 下载发布版本
- 前向[发布页面](https://github.com/OpenSkywalking/skywalking/releases)

## 部署Elasticsearch
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

### 部署collector
1. 解压安装包`tar -xvf skywalking-collector.tar.gz`，windows用户可以选择zip包
1. 运行`bin/startup.sh`启动。windows用户为.bat文件。

- `config/application.yml`
```
cluster:
# Zookeeper地址配置
  zookeeper:
    hostPort: localhost:2181
    sessionTimeout: 100000
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
storage:
  elasticsearch:
    cluster_name: CollectorDBCluster
    cluster_transport_sniffer: true
    # Elastic Search地址信息
    cluster_nodes: localhost:9300
    index_shards_number: 2
    index_replicas_number: 0
```

## Collector集群模式启动
集群模式主要依赖Zookeeper的注册和应用发现能力。所以，你只需要调整 `config/application.yml`中，agent_server, agent_stream, ui, collector_inside这些配置项的ip信息，使用真实的IP地址或者hostname，Collector就会使用集群模式运行。
其次，将elasticsearch的注释取消，并修改集群的节点地址信息。
