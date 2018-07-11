# 用途说明
单机模式collector不支持集群部署,它使用本地H2数据库。主要用于：预览、功能测试、演示和低压力系统。

如果在生产或者真实环境中(非本地demo) 部署SkyWalking，至少应该切换到Elasticsearch作为存储.

**在5.0.0-alpha版本中，暂不提供H2实现, 所以在启动之前,必须先部署ElasticSearch**

## 所需的第三方软件
- JDK6+(被监控的应用程序运行在jdk6及以上版本)
- JDK8+(SkyWalking collector和WebUI部署在jdk8及以上版本)
- Elasticsearch 5.x(集群模式或不使用)

## 下载发布版本
- 前向[发布页面下载](http://skywalking.apache.org/downloads/)

## 快速入门
如果端口8080,10800,11800,12800不被占用，你可以直接解压tar / unzip,然后启动。

- 部署 ElasticSearch.
- linux环境中 `tar -xvf skywalking-dist.tar.gz`解压,windows环境中选择zip包.
- 运行 `bin/startup.sh`,windows环境运行 `bin/startup.bat`

你可以使用`config / application.yml`的默认值

- **注意：在5.0.0-alpha 版本中,startup.sh将会启动collector和UI两个进程，UI通过127.0.0.1:10800访问本地collector，无需额外配置。**

## 使用Elastic Search代替H2存储
- 即使在单机模式下，`collector`也可以配置ElasticSearch作为运行存储介质, 若想使用,去掉  `application.yml`配置文件中的`storage` 相关配置节的注释，并修改配置,默认的配置是`collector`和 `Elasticsearch` 运行在同一台机器上,并不开启集群模式。

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