## Required of third party softwares
- JDK 6+（instruments application can run in jdk6）
- JDK8  ( skywalking collector and skywalking webui )
- Elasticsearch 5.2.2 or 5.3, cluster mode or not
- Zookeeper 3.4.10

## Download released version
- Go to [released page](https://github.com/OpenSkywalking/skywalking/releases)

## Deploy Elasticsearch server
- Modify `elasticsearch.yml`
  - Set `cluster.name: CollectorDBCluster`
  - Set `node.name: anyname`, this name can be any, it based on Elasticsearch.
  - Add the following configurations to   
 
```
# The ip used for listening
network.host: 0.0.0.0
thread_pool.bulk.queue_size: 1000
```

- Start Elasticsearch

## Single Node Mode Collector
Single Node collector is easy to deploy, and provides same features as cluster mode. You can use almost all default config to run in this mode. And attention, all the default configs of single node mode, depend on running the collector, traced application, ElasticSearch and Zookeeper in the same machine. 

### Deploy collector servers
1. Run `tar -xvf skywalking-collector.tar.gz`
1. Run `bin/startup.sh`

- `config/application.yml`
```
cluster:
  # The address of Zookeeper
  zookeeper:
    hostPort: localhost:2181
    sessionTimeout: 100000
# IPs in agent_server, agent_stream, ui, collector_inside are addresses of Collector
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
    # The address of Elastic Search
    cluster_nodes: localhost:9300
    index_shards_number: 2
    index_replicas_number: 0
```

## Cluster Mode Collector
Cluster mode depends on Zookeeper register and application discovery capabilities. So, you just need to adjust the IP config items in `config/application.yml`. Change IP and port configs of agent_server, agent_stream, ui, collector_inside, replace them to the real ip or hostname which you want to use for cluster.
