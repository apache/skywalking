# Scratch The OAP Config Dump

SkyWalking OAP behaviors could be controlled through hundreds of configurations. It is hard to know what is the final
configuration as all the configurations could be overrided by system environments.

The core config file [application.yml](../../../oap-server/server-starter/src/main/resources/application.yml) lists all
the configurations
and their default values. However, it is still hard to know the runtime value.

Scratch is a tool to dump the final configuration. It is provided within OAP rest server, which could be accessed
through HTTP GET `http://{core restHost}:{core restPort}/debugging/config/dump`.

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
  selector: ${SW_STORAGE:h2}
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
keywords4MaskingSecretsOfConfig: ${SW_CORE_KEYWORDS_FOR_MASKING_SECRETS:user,password,token,accessKey,secretKey}
```

## Disable The Config Dump Service

By default, this service is open for helping users to debug and diagnose. If you want to disable it, you need to diable the whole
`debugging-query` module through setting `selector=-`.

```yaml
debugging-query:
  selector: ${SW_DEBUGGING_QUERY:-}
```