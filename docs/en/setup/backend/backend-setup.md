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
1. **Cluster**. Manages multiple backend instances in a cluster, which could provide high throughput process
capabilities. See [**Cluster Management**](backend-cluster.md) for more details.
1. **Storage**. Makes the analysis result persistent. See [**Choose storage**](backend-storage.md) for more details
1. **Query**. Provides query interfaces to UI.
1. **Receiver** and **Fetcher**. Expose the service to the agents and probes, or read telemetry data from a channel. 
See [Receiver](backend-receivers.md) and [Fetcher](backend-fetcher.md) documents for more details.

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
