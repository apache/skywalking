# Backend setup
SkyWalking's backend distribution package consists of the following parts:

1. **bin/cmd scripts**: Located in the `/bin` folder. Includes startup Linux shell and Windows cmd scripts for the backend server and UI startup.

2. **Backend config**: Located in the `/config` folder. Includes settings files of the backend, which are:
    * `application.yml`
    * `log4j.xml`
    * `alarm-settings.yml`

3. **Libraries of backend**: Located in the `/oap-libs` folder. All dependencies of the backend can be found there.

4. **Webapp env**: Located in the `webapp` folder. UI frontend jar file can be found here, together with its `webapp.yml` setting file.

## Requirements and default settings

Requirement: **Java 11/17/21**.

You should set up the database ready before starting the backend. We recommend to use BanyanDB.
If you want to use other databases, please read the [storage document](backend-storage.md).

Use the docker mode to run BanyanDB containerized. 
```shell
# The compatible version number could be found in /config/bydb.dependencies.properties
export BYDB_VERSION=xxx

docker pull apache/skywalking-banyandb:${BYDB_VERSION}

docker run -d \
  -p 17912:17912 \
  -p 17913:17913 \
  --name banyandb \
  apache/skywalking-banyandb:${BYDB_VERSION} \
  standalone
```

You can use `bin/startup.sh` (or cmd) to start up the OAP server and UI with their default settings, 
OAP listens on `0.0.0.0/11800` for gRPC APIs and `0.0.0.0/12800` for HTTP APIs.

In Java, DotNetCore, Node.js, and Istio agents/probes, you should set the gRPC service address to `ip/host:11800`, and IP/host should be where your OAP is. 

UI listens on `8080` port and request `127.0.0.1/12800` to run a GraphQL query.

### Interaction

Before deploying Skywalking in your distributed environment, you should learn about how agents/probes, the backend, and the UI communicate with each other:

<img src="https://skywalking.apache.org/doc-graph/communication-net.png"/>

- Most native agents and probes, including language-based or mesh probes, use gRPC service (`core/default/gRPC*` in `application.yml`) to report data to the backend. Also, the REST service is supported in JSON format.
- UI uses GraphQL (HTTP) query to access the backend, also in REST service (`core/default/rest*` in `application.yml`).


## Startup script
The default startup scripts are `/bin/oapService.sh`(.bat).
Read the [start up mode](backend-start-up-mode.md) document to learn other ways to start up the backend.


### Key Parameters In The Booting Logs
After the OAP booting process completed, you should be able to see all important parameters listed in the logs.

```
2023-11-06 21:10:45,988 org.apache.skywalking.oap.server.starter.OAPServerBootstrap 67 [main] INFO  [] - The key booting parameters of Apache SkyWalking OAP are listed as following.

Running Mode                               |   null                  
TTL.metrics                                |   7                     
TTL.record                                 |   3                     
Version                                    |   9.7.0-SNAPSHOT-92af797
module.agent-analyzer.provider             |   default               
module.ai-pipeline.provider                |   default               
module.alarm.provider                      |   default               
module.aws-firehose.provider               |   default               
module.cluster.provider                    |   standalone            
module.configuration-discovery.provider    |   default               
module.configuration.provider              |   none                  
module.core.provider                       |   default               
module.envoy-metric.provider               |   default               
module.event-analyzer.provider             |   default               
module.log-analyzer.provider               |   default               
module.logql.provider                      |   default               
module.promql.provider                     |   default               
module.query.provider                      |   graphql               
module.receiver-browser.provider           |   default               
module.receiver-clr.provider               |   default               
module.receiver-ebpf.provider              |   default               
module.receiver-event.provider             |   default               
module.receiver-jvm.provider               |   default               
module.receiver-log.provider               |   default               
module.receiver-meter.provider             |   default               
module.receiver-otel.provider              |   default               
module.receiver-profile.provider           |   default               
module.receiver-register.provider          |   default               
module.receiver-sharing-server.provider    |   default               
module.receiver-telegraf.provider          |   default               
module.receiver-trace.provider             |   default               
module.service-mesh.provider               |   default               
module.storage.provider                    |   h2                    
module.telemetry.provider                  |   none                  
oap.external.grpc.host                     |   0.0.0.0               
oap.external.grpc.port                     |   11800                 
oap.external.http.host                     |   0.0.0.0               
oap.external.http.port                     |   12800                  
oap.internal.comm.host                     |   0.0.0.0               
oap.internal.comm.port                     |   11800       
```

- `oap.external.grpc.host`:`oap.external.grpc.port` is for reporting telemetry data through gRPC channel, including
  native agents, OTEL.
- `oap.external.http.host`:`oap.external.http.port` is for reporting telemetry data through HTTP channel and query,
  including native GraphQL(UI), PromQL, LogQL.
- `oap.internal.comm.host`:`oap.internal.comm.port` is for OAP cluster internal communication via gRPC/HTTP2 protocol.
  The default host(`0.0.0.0`) is not suitable for the cluster mode, unless in k8s deployment. Please
  read [Cluster Doc](backend-cluster.md) to understand how to set up the SkyWalking backend in the cluster mode.
  
## application.yml
SkyWalking backend startup behaviours are driven by `config/application.yml`. Understanding the settings file will help you read this document.

The core concept behind this setting file is that the SkyWalking collector is based on a pure modular design.
End-users can switch or assemble the collector features according to their unique requirements.

In `application.yml`, there are three levels.
1. **Level 1**: Module name. This means that this module is active in running mode.
1. **Level 2**: Provider option list and provider selector. Available providers are listed here with a selector to indicate which one will actually take effect. If only one provider is listed, the `selector` is optional and can be omitted.
1. **Level 3**. Settings of the chosen provider.

Example:

```yaml
storage:
  selector: banyandb # the banyandb storage will actually be activated.
  mysql:
    properties:
      jdbcUrl: ${SW_JDBC_URL:"jdbc:mysql://localhost:3306/swtest?allowMultiQueries=true"}
      dataSource.user: ${SW_DATA_SOURCE_USER:root}
      dataSource.password: ${SW_DATA_SOURCE_PASSWORD:root@1234}
      dataSource.cachePrepStmts: ${SW_DATA_SOURCE_CACHE_PREP_STMTS:true}
      dataSource.prepStmtCacheSize: ${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_SIZE:250}
      dataSource.prepStmtCacheSqlLimit: ${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_LIMIT:2048}
      dataSource.useServerPrepStmts: ${SW_DATA_SOURCE_USE_SERVER_PREP_STMTS:true}
    metadataQueryMaxSize: ${SW_STORAGE_MYSQL_QUERY_MAX_SIZE:5000}
  banyandb:
    targets: ${SW_STORAGE_BANYANDB_TARGETS:127.0.0.1:17912}
    maxBulkSize: ${SW_STORAGE_BANYANDB_MAX_BULK_SIZE:10000}
    flushInterval: ${SW_STORAGE_BANYANDB_FLUSH_INTERVAL:15}
    flushTimeout: ${SW_STORAGE_BANYANDB_FLUSH_TIMEOUT:10}
```

1. **`storage`** is the module.
1. **`selector`** selects one out of all providers listed below. The unselected ones take no effect as if they were deleted.
1. **`default`** is the default implementor of the core module.
1. `driver`, `url`, ... `metadataQueryMaxSize` are all setting items of the implementor.

At the same time, there are two types of modules: required and optional. The required modules provide the skeleton of the backend.
Even though their modular design supports pluggability, removing those modules does not serve any purpose. For optional modules, some of them have
a provider implementation called `none`, meaning that it only provides a shell with no actual logic, typically such as telemetry.
Setting `-` to the `selector` means that this whole module will be excluded at runtime.
We advise against changing the APIs of those modules unless you understand the SkyWalking project and its codes very well.

The required modules are listed here:
1. **Core**. Provides the basic and major skeleton of all data analysis and stream dispatch.
1. **Cluster**. Manages multiple backend instances in a cluster, which could provide high throughput process
capabilities. See [**Cluster Management**](backend-cluster.md) for more details.
1. **Storage**. Makes the analysis result persistent. See [**Choose storage**](backend-storage.md) for more details
1. **Query**. Provides query interfaces to UI.
1. **Receiver** and **Fetcher**. Expose the service to the agents and probes, or read telemetry data from a channel.

## FAQs
#### Why do we need to set the timezone? And when do we do it?
SkyWalking provides downsampling time-series metrics features.
Query and store at each time dimension (minute, hour, day, month metrics indexes)
related to timezone when time formatting.

For example, metrics time will be formatted like yyyyMMddHHmm in minute dimension metrics, which is timezone-related.

By default, SkyWalking's OAP backend chooses the **OS default timezone**.
Please follow the Java and OS documents if you want to override the timezone.

