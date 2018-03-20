## Required of third party softwares
- JDK 6+（instruments application can run in jdk6）
- JDK8  ( SkyWalking collector and SkyWalking WebUI )
- Elasticsearch 5.x, cluster mode or not
- Zookeeper 3.4.10

## Download released version
- Go to [released page](https://github.com/apache/incubator-skywalking/releases)

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

### Deploy collector servers
1. Run `tar -xvf skywalking-dist.tar.gz`
2. Config collector in cluster mode.

Cluster mode depends on Zookeeper register and application discovery capabilities. So, you just need to adjust the IP config items in `config/application.yml`. Change IP and port configs of naming, remote, agent_gRPC, agent_jetty and ui,
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
        host: localhost
        port: 10800
        context_path: /
remote:
  gRPC:
    host: localhost
    port: 11800
agent_gRPC:
  gRPC:
    host: localhost
    port: 11800
agent_jetty:
  jetty:
    host: localhost
    port: 12800
    context_path: /
analysis_register:
  default:
analysis_jvm:
  default:
analysis_segment_parser:
  default:
    buffer_file_path: ../buffer/
    buffer_offset_max_file_size: 10M
    buffer_segment_max_file_size: 500M
ui:
  jetty:
    host: localhost
    port: 12800
    context_path: /
# Config Elasticsearch cluster connection info.
storage:
  elasticsearch:
    cluster_name: CollectorDBCluster
    cluster_transport_sniffer: true
    cluster_nodes: localhost:9300
    index_shards_number: 2
    index_replicas_number: 0
    ttl: 7
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
