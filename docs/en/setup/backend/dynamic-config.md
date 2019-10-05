# Dynamic Configuration
SkyWalking Configurations mostly are set through `application.yml` and OS system environment variables.
But some of them are supporting dynamic settings from upstream management system.

Right now, SkyWalking supports following dynamic configurations.

| Config Key | Value Description | Value Format Example |
|:----:|:----:|:----:|
|receiver-trace.default.slowDBAccessThreshold| Thresholds of slow Database statement, override `receiver-trace/default/slowDBAccessThreshold` of `applciation.yml`. | default:200,mongodb:50|
|receiver-trace.default.uninstrumentedGateways| The uninstrumented gateways, override `gateways.yml`. | not set |
|alarm.default.alarm-settings| The alarm settings, will override `alarm-settings.yml`. | not set |


This feature depends on upstream service, so it is **OFF** as default.
```yaml
configuration:
  none:
```

## Dynamic Configuration Service, DCS
[Dynamic Configuration Service](../../../../oap-server/server-configuration/grpc-configuration-sync/src/main/proto/configuration-service.proto) 
is a gRPC service, which requires the upstream system implemented.
The SkyWalking OAP fetches the configuration from the implementation(any system), after you open this implementation like this.

```yaml
configuration:
  grpc:
    # Upstream system hostname
    host: 127.0.0.1
    # Upstream system port
    port: 9555
    #period : 60 # Unit seconds, sync period. Default fetch every 60 seconds.
    #clusterName: "default" # the name of current cluster, set the name if you want to upstream system known.  
```

## Dynamic Configuration Apollo Implementation

[Apollo](https://github.com/ctripcorp/apollo/) is also supported as DCC(Dynamic Configuration Center), to use it, just configured as follows:

```yaml
configuration:
  apollo:
    apolloMeta: <your apollo meta address>
    apolloCluster: default
    # apolloEnv: # defaults to null
    appId: skywalking
    period: 5
```

## Dynamic Configuration Nacos Implementation

[Nacos](https://github.com/alibaba/nacos) is also supported as DCC(Dynamic Configuration Center), to use it, please configure as follows:

```yaml
configuration:
  nacos:
    # Nacos Server Host
    serverAddr: 127.0.0.1
    # Nacos Server Port
    port: 8848
    # Nacos Configuration Group
    group: 'skywalking'
    # Unit seconds, sync period. Default fetch every 60 seconds.
    period : 60
    # the name of current cluster, set the name if you want to upstream system known.
    clusterName: "default"
```


## Dynamic Configuration Zookeeper Implementation

[Zookeeper](https://github.com/apache/zookeeper) is also supported as DCC(Dynamic Configuration Center), to use it, please configure as follows:

```yaml
configuration:
  zookeeper:
    period : 60 # Unit seconds, sync period. Default fetch every 60 seconds.
    nameSpace: /default
    hostPort: localhost:2181
    #Retry Policy
    baseSleepTimeMs: 1000 # initial amount of time to wait between retries
    maxRetries: 3 # max number of times to retry
```

## Dynamic Configuration Etcd Implementation

[Etcd](https://github.com/etcd-io/etcd) is also supported as DCC(Dynamic Configuration Center), to use it, please configure as follows:

```yaml
configuration:
  etcd:
    period : 60 # Unit seconds, sync period. Default fetch every 60 seconds.
    group :  'skywalking'
    serverAddr: localhost:2379
    clusterName: "default"
```

## Dynamic Configuration Consul Implementation

[Consul](https://github.com/rickfast/consul-client) is also supported as DCC(Dynamic Configuration Center), to use it, please configure as follows:

```yaml
configuration:
  consul:
    # Consul host and ports, separated by comma, e.g. 1.2.3.4:8500,2.3.4.5:8500
    hostAndPorts: 127.0.0.1:8500
    # Sync period in seconds. Defaults to 60 seconds.
    period: 60
```



