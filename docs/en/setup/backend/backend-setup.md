# Backend setup
SkyWalking backend distribution package includes the following parts:

1. **bin/cmd scripts**, in `/bin` folder. Includes startup linux shell and Windows cmd scripts for Backend
   server and UI startup.

2. **Backend config**, in `/config` folder. Includes settings files of the backend, which are:
    * `application.yml`
    * `log4j.xml`
    * `alarm-settings.yml`

3. **Libraries of backend**, in `/oap-libs` folder. All the dependencies of the backend are in it.

4. **Webapp env**, in `webapp` folder. UI frontend jar file is here, with its `webapp.yml` setting file.

## Requirements and default settings

Requirement: **JDK8 to JDK12 are tested**, other versions are not tested and may or may not work.

Before you start, you should know that the quickstart aims to get you a basic configuration mostly for previews/demo, performance and long-term running are not our goals.

For production/QA/tests environments, you should head to [Backend and UI deployment documents](#deploy-backend-and-ui).

You can use `bin/startup.sh` (or cmd) to startup the backend and UI with their default settings, which are:

- Backend storage uses **H2 by default** (for an easier start)
- Backend listens `0.0.0.0/11800` for gRPC APIs and `0.0.0.0/12800` for http rest APIs.

In Java, DotNetCore, Node.js, Istio agents/probe, you should set the gRPC service address to `ip/host:11800`, with ip/host where your backend is.
- UI listens on `8080` port and request `127.0.0.1/12800` to do GraphQL query.

### Interaction

Before deploying Skywalking in your distributed environment, you should know how agents/probes, backend, UI communicates with each other:

<img src="https://skywalking.apache.org/doc-graph/communication-net.png"/>

- All native agents and probes, either language based or mesh probe, are using gRPC service (`core/default/gRPC*` in `application.yml`) to report data to the backend. Also, jetty service supported in JSON format.
- UI uses GraphQL (HTTP) query to access the backend also in Jetty service (`core/default/rest*` in `application.yml`).


## Startup script
The default startup scripts are `/bin/oapService.sh`(.bat). 
Read [start up mode](backend-start-up-mode.md) document to know other options
of starting backend.


## application.yml
SkyWalking backend startup behaviours are driven by `config/application.yml`.
Understood the setting file will help you to read this document.
The core concept behind this setting file is, SkyWalking collector is based on pure modularization design. 
End user can switch or assemble the collector features by their own requirements.

So, in `application.yml`, there are three levels.
1. **Level 1**, module name. Meaning this module is active in running mode.
1. **Level 2**, provider option list and provider selector. Available providers are listed here with a selector to indicate which one will actually take effect,
if there is only one provider listed, the `selector` is optional and can be omitted.
1. **Level 3**. settings of the provider.

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
1. **`selector`** selects one out of the all providers listed below, the unselected ones take no effect as if they were deleted.
1. **`default`** is the default implementor of core module.
1. `driver`, `url`, ... `metadataQueryMaxSize` are all setting items of the implementor.

At the same time, modules includes required and optional, the required modules provide the skeleton of backend,
even modularization supported pluggable, removing those modules are meaningless, for optional modules, some of them have
a provider implementation called `none`, meaning it only provides a shell with no actual logic, typically such as telemetry.
Setting `-` to the `selector` means this whole module will be excluded at runtime.
We highly recommend you don't try to change APIs of those modules, unless you understand SkyWalking project and its codes very well.

List the required modules here
1. **Core**. Do basic and major skeleton of all data analysis and stream dispatch.
1. **Cluster**. Manage multiple backend instances in a cluster, which could provide high throughputs process
capabilities.
1. **Storage**. Make the analysis result persistence.
1. **Query**. Provide query interfaces to UI.

For **Cluster** and **Storage** have provided multiple implementors(providers), see **Cluster management**
and **Choose storage** documents in the [link list](#advanced-feature-document-link-list).

Also, several **receiver** modules are provided.
Receiver is the module in charge of accepting incoming data requests to backend. Most(all) provide 
service by some network(RPC) protocol, such as gRPC, HTTPRestful.  
The receivers have many different module names, you could
read **Set receivers** document in the [link list](#advanced-feature-document-link-list).

## Configuration Vocabulary
All available configurations in `application.yml` could be found in [Configuration Vocabulary](configuration-vocabulary.md). 

## Advanced feature document link list
After understand the setting file structure, you could choose your interesting feature document.
We recommend you to read the feature documents in our following order.

1. [Overriding settings](backend-setting-override.md) in application.yml is supported
1. [IP and port setting](backend-ip-port.md). Introduce how IP and port set and be used.
1. [Backend init mode startup](backend-init-mode.md). How to init the environment and exit graciously.
Read this before you try to initial a new cluster.
1. [Cluster management](backend-cluster.md). Guide you to set backend server in cluster mode.
1. [Deploy in kubernetes](backend-k8s.md). Guide you to build and use SkyWalking image, and deploy in k8s.
1. [Choose storage](backend-storage.md). As we know, in default quick start, backend is running with H2
DB. But clearly, it doesn't fit the product env. In here, you could find what other choices do you have.
Choose the ones you like, we are also welcome anyone to contribute new storage implementor.
1. [Set receivers](backend-receivers.md). You could choose receivers by your requirements, most receivers
are harmless, at least our default receivers are. You would set and active all receivers provided.
1. [Open fetchers](backend-fetcher.md). You could open different fetchers to read metrics from the target applications.
These ones work like receivers, but in pulling mode, typically like Prometheus.
1. [Token authentication](backend-token-auth.md). You could add token authentication mechanisms to avoid `OAP` receiving untrusted data.  
1. Do [trace sampling](trace-sampling.md) at backend. This sample keep the metrics accurate, only don't save some of traces
in storage based on rate.
1. Follow [slow DB statement threshold](slow-db-statement.md) config document to understand that, 
how to detect the Slow database statements(including SQL statements) in your system.
1. Official [OAL scripts](../../guides/backend-oal-scripts.md). As you have known from our [OAL introduction](../../concepts-and-designs/oal.md),
most of backend analysis capabilities based on the scripts. Here is the description of official scripts,
which helps you to understand which metrics data are in process, also could be used in alarm.
1. [Alarm](backend-alarm.md). Alarm provides a time-series based check mechanism. You could set alarm 
rules targeting the analysis oal metrics objects.
1. [Advanced deployment options](advanced-deployment.md). If you want to deploy backend in very large
scale and support high payload, you may need this. 
1. [Metrics exporter](metrics-exporter.md). Use metrics data exporter to forward metrics data to 3rd party
system.
1. [Time To Live (TTL)](ttl.md). Metrics and trace are time series data, TTL settings affect the expired time of them.
1. [Dynamic Configuration](dynamic-config.md). Make configuration of OAP changed dynamic, from remote service
or 3rd party configuration management system.
1. [Uninstrumented Gateways](uninstrumented-gateways.md). Configure gateways/proxies that are not supported by SkyWalking agent plugins,
to reflect the delegation in topology graph.
1. [Apdex threshold](apdex-threshold.md). Configure the thresholds for different services if Apdex calculation is activated in the OAL.
1. [Service Grouping](service-auto-grouping.md). An automatic grouping mechanism for all services based on name.
1. [Group Parameterized Endpoints](endpoint-grouping-rules.md). Configure the grouping rules for parameterized endpoints,
to improve the meaning of the metrics.
1. [OpenTelemetry Metrics Analysis](backend-receivers.md#opentelemetry-receiver). Activate built-in configurations to convert the metrics forwarded from OpenTelemetry collector.
And learn how to write your own conversion rules.
1. [Meter Analysis](backend-meter.md). Set up the backend analysis rules, when use [SkyWalking Meter System Toolkit](../service-agent/java-agent/README.md#advanced-features) 
or meter plugins. 
1. [Spring Sleuth Metrics Analysis](spring-sleuth-setup.md). Configure the agent and backend to receiver metrics from micrometer.
1. [Log Analyzer](log-analyzer.md)

## Telemetry for backend
OAP backend cluster itself underlying is a distributed streaming process system. For helping the Ops team,
we provide the telemetry for OAP backend itself. Follow [document](backend-telemetry.md) to use it.

At the same time, we provide [Health Check](backend-health-check.md) to get a score for the health status.
> 0 means healthy, more than 0 means unhealthy 
> and less than 0 means oap doesn't startup.

## FAQs
#### When and why do we need to set Timezone?
SkyWalking provides downsampling time series metrics features. 
Query and storage at each time dimension(minute, hour, day, month metrics indexes)
related to timezone when doing time format. 

For example, metrics time will be formatted like YYYYMMDDHHmm in minute dimension metrics,
which format process is timezone related.
  
In default, SkyWalking OAP backend choose the OS default timezone.
If you want to override it, please follow Java and OS documents to do so.

#### How to query the storage directly from 3rd party tool?
SkyWalking provides browser UI, CLI and GraphQL ways to support extensions. But some users may have the idea to query data 
directly from the storage. Such as in ElasticSearch case, Kibana is a great tool to do this.

In default, due to reduce memory, network and storage space usages, SkyWalking saves based64-encoded id(s) only in the metrics entities. 
But these tools usually don't support nested query, or don't work conveniently. In this special case,
SkyWalking provide a config to add all necessary name column(s) into the final metrics entities with ID as a trade-off.

Take a look at `core/default/activeExtraModelColumns` config in the `application.yaml`, and set it as `true` to open this feature.

This feature wouldn't provide any new feature to the native SkyWalking scenarios, just for the 3rd party integration.
