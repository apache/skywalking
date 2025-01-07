# Dump Effective Initial Configurations

SkyWalking OAP behaviors could be controlled through hundreds of configurations. It is hard to know what is the final
configuration as all the configurations could be override by system environments.

The core config file [application.yml](../../../oap-server/server-starter/src/main/resources/application.yml) lists all the configurations
and their default values. However, it is still hard to know the runtime value.

Dump Effective Initial Configurations API is designed to help users to understand the effective configurations, no matter
they are initialized in the `application.yml`, or override through system environments.
- URL, `http://{core restHost}:{core restPort}/debugging/config/dump`
- HTTP GET method.

```shell
> curl http://127.0.0.1:12800/debugging/config/dump
cluster.provider=standalone
core.provider=default
core.default.prepareThreads=2
core.default.restHost=0.0.0.0
core.default.searchableLogsTags=level,http.status_code
core.default.role=Mixed
core.default.persistentPeriod=25
core.default.syncPeriodHttpUriRecognitionPattern=10
core.default.restIdleTimeOut=30000
core.default.dataKeeperExecutePeriod=5
core.default.topNReportPeriod=10
core.default.gRPCSslTrustedCAPath=
core.default.downsampling=[Hour, Day]
core.default.serviceNameMaxLength=70
core.default.gRPCSslEnabled=false
core.default.restPort=12800
core.default.serviceCacheRefreshInterval=10
...
```

All booting configurations with their runtime values are listed, including the selected provider for each module.

## Protect The Secrets

Some of the configurations contain sensitive values, such as username, password, token, etc. These values would be
masked
in the dump result. For example, the `storage.elasticsearch.password` in the following configurations,

```yaml
storage:
  selector: ${SW_STORAGE:elasticsearch}
  elasticsearch:
    password: ${SW_ES_PASSWORD:""}
```

It would be masked and shown as `********` in the dump result.

```shell
> curl http://127.0.0.1:12800/debugging/config/dump
...
storage.elasticsearch.password=********
...
```

By default, we mask the config keys through the following configurations.

```yaml
# Include the list of keywords to filter configurations including secrets. Separate keywords by a comma.
keywords4MaskingSecretsOfConfig: ${SW_DEBUGGING_QUERY_KEYWORDS_FOR_MASKING_SECRETS:user,password,token,accessKey,secretKey,authentication}
```