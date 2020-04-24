# Dynamic Configuration
SkyWalking Configurations mostly are set through `application.yml` and OS system environment variables.
But some of them are supporting dynamic settings from upstream management system.

Right now, SkyWalking supports following dynamic configurations.

| Config Key | Value Description | Value Format Example |
|:----:|:----:|:----:|
|receiver-trace.default.slowDBAccessThreshold| Thresholds of slow Database statement, override `receiver-trace/default/slowDBAccessThreshold` of `applciation.yml`. | default:200,mongodb:50|
|receiver-trace.default.uninstrumentedGateways| The uninstrumented gateways, override `gateways.yml`. | same as [`gateways.yml`](uninstrumented-gateways.md#configuration-format) |
|alarm.default.alarm-settings| The alarm settings, will override `alarm-settings.yml`. | same as [`alarm-settings.yml`](backend-alarm.md) |
|core.default.apdexThreshold| The apdex threshold settings, will override `service-apdex-threshold.yml`. | same as [`service-apdex-threshold.yml`](apdex-threshold.md) |


This feature depends on upstream service, so it is **DISABLED** by default.

```yaml
configuration:
  selector: ${SW_CONFIGURATION:none}
  none:
  apollo:
    apolloMeta: http://106.12.25.204:8080
    apolloCluster: default
    appId: skywalking
    period: 5
  # ... other implementations
```

## Dynamic Configuration Service, DCS
[Dynamic Configuration Service](../../../../oap-server/server-configuration/grpc-configuration-sync/src/main/proto/configuration-service.proto) 
is a gRPC service, which requires the upstream system implemented.
The SkyWalking OAP fetches the configuration from the implementation(any system), after you open this implementation like this.

```yaml
configuration:
  selector: ${SW_CONFIGURATION:grpc}
  grpc:
    host: 127.0.0.1
    port: 9555
```

## Dynamic Configuration Apollo Implementation

[Apollo](https://github.com/ctripcorp/apollo/) is also supported as DCC(Dynamic Configuration Center), to use it, just configured as follows:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:apollo}
  apollo:
    apolloMeta: <your apollo meta address>
    apolloCluster: default
    appId: skywalking
    period: 5
```

## Dynamic Configuration Nacos Implementation

[Nacos](https://github.com/alibaba/nacos) is also supported as DCC(Dynamic Configuration Center), to use it, please configure as follows:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:nacos}
  nacos:
    serverAddr: 127.0.0.1
    port: 8848
    group: 'skywalking'
    namespace: ''
    period : 60
    clusterName: "default"
  # ... other configurations
```


## Dynamic Configuration Zookeeper Implementation

[Zookeeper](https://github.com/apache/zookeeper) is also supported as DCC(Dynamic Configuration Center), to use it, please configure as follows:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:zookeeper}
  zookeeper:
    period : 60 # Unit seconds, sync period. Default fetch every 60 seconds.
    nameSpace: /default
    hostPort: localhost:2181
    baseSleepTimeMs: 1000 # initial amount of time to wait between retries
    maxRetries: 3 # max number of times to retry
  # ... other configurations
```

## Dynamic Configuration Etcd Implementation

[Etcd](https://github.com/etcd-io/etcd) is also supported as DCC(Dynamic Configuration Center), to use it, please configure as follows:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:etcd}
  etcd:
    period : 60 # Unit seconds, sync period. Default fetch every 60 seconds.
    group :  'skywalking'
    serverAddr: localhost:2379
    clusterName: "default"
  # ... other configurations
```

## Dynamic Configuration Consul Implementation

[Consul](https://github.com/rickfast/consul-client) is also supported as DCC(Dynamic Configuration Center), to use it, please configure as follows:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:consul}
  consul:
    # Consul host and ports, separated by comma, e.g. 1.2.3.4:8500,2.3.4.5:8500
    hostAndPorts: 127.0.0.1:8500
    # Sync period in seconds. Defaults to 60 seconds.
    period: 60
    # aclToken of connection consul (optional)
    aclToken: ${consul.aclToken}
  # ... other configurations
```



