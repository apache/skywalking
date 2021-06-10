# Backend setup
SkyWalking's backend distribution package consists of the following parts:

1. **bin/cmd scripts**: Located in the `/bin` folder. Includes startup linux shell and Windows cmd scripts for the backend
   server and UI startup.

2. **Backend config**: Located in the `/config` folder. Includes settings files of the backend, which are:
    * `application.yml`
    * `log4j.xml`
    * `alarm-settings.yml`

3. **Libraries of backend**: Located in the `/oap-libs` folder. All dependencies of the backend can be found in it.

4. **Webapp env**: Located in the `webapp` folder. UI frontend jar file can be found here, together with its `webapp.yml` setting file.

## Requirements and default settings

Requirement: **JDK8 to JDK12 are tested**. Other versions are not tested and may or may not work.

Before you start, you should know that the main purpose of quickstart is to help you obtain a basic configuration for previews/demo. Performance and long-term running are not our goals.

For production/QA/tests environments, see [Backend and UI deployment documents](#deploy-backend-and-ui).

You can use `bin/startup.sh` (or cmd) to start up the backend and UI with their default settings, set out as follows:

- Backend storage uses **H2 by default** (for an easier start)
- Backend listens on `0.0.0.0/11800` for gRPC APIs and `0.0.0.0/12800` for HTTP REST APIs.

In Java, DotNetCore, Node.js, and Istio agents/probes, you should set the gRPC service address to `ip/host:11800`, and ip/host should be where your backend is.
- UI listens on `8080` port and request `127.0.0.1/12800` to run a GraphQL query.

### Interaction

Before deploying Skywalking in your distributed environment, you should learn about how agents/probes, the backend, and the UI communicate with each other:

<img src="https://skywalking.apache.org/doc-graph/communication-net.png"/>

- All native agents and probes, either language based or mesh probe, use the gRPC service (`core/default/gRPC*` in `application.yml`) to report data to the backend. Also, the Jetty service is supported in JSON format.
- UI uses GraphQL (HTTP) query to access the backend also in Jetty service (`core/default/rest*` in `application.yml`).


## Startup script
The default startup scripts are `/bin/oapService.sh`(.bat). 
Read the [start up mode](backend-start-up-mode.md) document to learn about other ways to start up the backend.


## application.yml
SkyWalking backend startup behaviours are driven by `config/application.yml`.
Understanding the setting file will help you read this document.
The core concept behind this setting file is that the SkyWalking collector is based on pure modular design. 
End users can switch or assemble the collector features according to their own requirements.

In `application.yml`, there are three levels.
1. **Level 1**: Module name. This means that this module is active in running mode.
1. **Level 2**: Provider option list and provider selector. Available providers are listed here with a selector to indicate which one will actually take effect. If there is only one provider listed, the `selector` is optional and can be omitted.
1. **Level 3**. Settings of the provider.

Example:

```yaml
storage:
  selector: mysql # the mysql storage will actually be activated, while the h2 storage takes no effect
  h2:
    driver: ${SW_STORAGE_H2_DRIVER:org.h2.jdbcx.JdbcDataSource}
    url: ${SW_STORAGE_H2_URL:jdbc:h2:mem:skywalking-oap-db}
    user: ${SW_STORAGE_H2_USER:sa}
    metadataQueryMaxSize: ${SW_STORAGE_H2_QUERY_MAX_SIZE:5000}
  mysql:
    properties:
      jdbcUrl: ${SW_JDBC_URL:"jdbc:mysql://localhost:3306/swtest"}
      dataSource.user: ${SW_DATA_SOURCE_USER:root}
      dataSource.password: ${SW_DATA_SOURCE_PASSWORD:root@1234}
      dataSource.cachePrepStmts: ${SW_DATA_SOURCE_CACHE_PREP_STMTS:true}
      dataSource.prepStmtCacheSize: ${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_SIZE:250}
      dataSource.prepStmtCacheSqlLimit: ${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_LIMIT:2048}
      dataSource.useServerPrepStmts: ${SW_DATA_SOURCE_USE_SERVER_PREP_STMTS:true}
    metadataQueryMaxSize: ${SW_STORAGE_MYSQL_QUERY_MAX_SIZE:5000}
  # other configurations
```

1. **`storage`** is the module.
1. **`selector`** selects one out of all providers listed below. The unselected ones take no effect as if they were deleted.
1. **`default`** is the default implementor of the core module.
1. `driver`, `url`, ... `metadataQueryMaxSize` are all setting items of the implementor.

At the same time, there are two types of modules: required and optional. The required modules provide the skeleton of the backend. 
Even though their modular design supports pluggability, removing those modules does not serve any purpose. For optional modules, some of them have
a provider implementation called `none`, meaning that it only provides a shell with no actual logic, typically such as telemetry.
Setting `-` to the `selector` means that this whole module will be excluded at runtime.
We advise against trying to change the APIs of those modules, unless you understand the SkyWalking project and its codes very well.

The required modules are listed here:
1. **Core**. Provides the basic and major skeleton of all data analysis and stream dispatch.
1. **Cluster**. Manages multiple backend instances in a cluster, which could provide high throughputs process
capabilities.
1. **Storage**. Makes the analysis result persistent.
1. **Query**. Provides query interfaces to UI.

**Cluster** and **Storage** have provided multiple implementors (providers). See **Cluster management**
and **Choose storage** documents in the [link list](#advanced-feature-document-link-list).

Several **receiver** modules are also provided.
Receiver is the module in charge of accepting incoming data requests to the backend. They usually provide 
services by some network (RPC) protocols, such as gRPC and HTTPRestful.  
The receivers have many different module names. You could
read the **set receivers** document in the [link list](#advanced-feature-document-link-list).

## Configuration Vocabulary
All available configurations in `application.yml` could be found in [Configuration Vocabulary](configuration-vocabulary.md). 

## Advanced feature document link list
After understanding the setting file structure, you may learn more about the advanced features.
You may read the advanced feature documents in the following order.

1. [Overriding settings](backend-setting-override.md) in application.yml are supported.
1. [IP and port setting](backend-ip-port.md). Introduces how IP and port are set and used.
1. [Backend init mode startup](backend-init-mode.md). How to initialize the environment and exit graciously.
Read this before you try to initialize a new cluster.
1. [Cluster management](backend-cluster.md). Guides you on how to set the backend server in cluster mode.
1. [Deploy in kubernetes](backend-k8s.md). Guides you on how to build and use the SkyWalking image, and deploy in k8s.
1. [Choose storage](backend-storage.md). As we know, in default quick start, the backend is running with H2
DB. But clearly, it doesn't fit the product env. Here you may find out about the other options available to you.
We also welcome anyone to contribute a new storage implementor.
1. [Set receivers](backend-receivers.md). You may choose receivers according to your requirements. Most receivers
are harmless, including our default receivers. You may set and activate all receivers provided.
1. [Open fetchers](backend-fetcher.md). You may open different fetchers to read metrics from target applications.
These ones work like receivers, except that they are in pull mode. A typical example is Prometheus.
1. [Token authentication](backend-token-auth.md). You may add token authentication mechanisms to prevent `OAP` from receiving untrusted data.  
1. Run [trace sampling](trace-sampling.md) at the backend. This sample keeps the metrics accurate, although some of the traces
in storage are not saved based on rate.
1. Follow [slow DB statement threshold](slow-db-statement.md) config document to learn about 
how to detect the Slow database statements (including SQL statements) in your system.
1. Official [OAL scripts](../../guides/backend-oal-scripts.md). As you have seen from our [OAL introduction](../../concepts-and-designs/oal.md),
most backend analysis capabilities are based on scripts. Here is a detailed description of the official scripts,
which helps you understand which metrics data are in process, and which could be used in alarm.
1. [Alarm](backend-alarm.md). Alarm provides a time-series based check mechanism. You may set alarm 
rules targeting the analysis oal metrics objects.
1. [Advanced deployment options](advanced-deployment.md). If you want to deploy backend in very large
scale and support high payload, you may need this. 
1. [Metrics exporter](metrics-exporter.md). Use metrics data exporter to forward metrics data to 3rd party
systems.
1. [Time To Live (TTL)](ttl.md). Since metrics and trace are time series data, TTL settings affect their expiration time.
1. [Dynamic Configuration](dynamic-config.md). Configure the OAP to dynamic from remote service
or 3rd party configuration management systems.
1. [Uninstrumented Gateways](uninstrumented-gateways.md). Configure gateways/proxies that are not supported by SkyWalking agent plugins to reflect the delegation in topology graph.
1. [Apdex threshold](apdex-threshold.md). Configure the thresholds for different services if Apdex calculation is activated in the OAL.
1. [Service Grouping](service-auto-grouping.md). An automatic grouping mechanism for all services based on name.
1. [Group Parameterized Endpoints](endpoint-grouping-rules.md). Configure the grouping rules for parameterized endpoints to improve the meaning of the metrics.
1. [OpenTelemetry Metrics Analysis](backend-receivers.md#opentelemetry-receiver). Activate built-in configurations to convert the metrics forwarded from OpenTelemetry collector, and learn how to write your own conversion rules.
1. [Meter Analysis](backend-meter.md). Set up the backend analysis rules when using [SkyWalking Meter System Toolkit](../service-agent/java-agent/README.md#advanced-features) 
or meter plugins. 
1. [Spring Sleuth Metrics Analysis](spring-sleuth-setup.md). Configure the agent and backend to receiver metrics from micrometer.
1. [Log Analyzer](log-analyzer.md)

## Telemetry for backend
The OAP backend cluster itself is a distributed streaming process system. To assist the Ops team,
we provide the telemetry for the OAP backend itself. Follow the [document](backend-telemetry.md) to use it.

At the same time, we provide [Health Check](backend-health-check.md) to get a score for the health status.
> 0 means healthy, and more than 0 means unhealthy. 
> less than 0 means that the OAP doesn't start up.

## FAQs
#### Why do we need to set the timezone? And when do we do it?
SkyWalking provides downsampling time series metrics features. 
Query and store at each time dimension (minute, hour, day, month metrics indexes)
related to timezone when time formatting.

For example, metrics time will be formatted like YYYYMMDDHHmm in minute dimension metrics,
which is timezone related.
  
By default, SkyWalking's OAP backend chooses the OS default timezone.
If you want to override it, please follow the Java and OS documents.

#### How to query the storage directly from a 3rd party tool?
SkyWalking provides different options based on browser UI, CLI and GraphQL to support extensions. But some users may want to query data 
directly from the storage. For example, in the case of ElasticSearch, Kibana is a great tool for doing this.

By default, in order to reduce memory, network and storage space usages, SkyWalking saves based64-encoded ID(s) only in metrics entities. 
But these tools usually don't support nested query, and are not convenient to work with. For these exceptional reasons,
SkyWalking provides a config to add all necessary name column(s) into the final metrics entities with ID as a trade-off.

Take a look at `core/default/activeExtraModelColumns` config in the `application.yaml`, and set it as `true` to enable this feature.

Note that this feature is simply for 3rd party integration and doesn't provide any new features to native SkyWalking use cases.
