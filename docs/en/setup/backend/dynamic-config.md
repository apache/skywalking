# Dynamic Configuration
SkyWalking Configurations mostly are set through `application.yml` and OS system environment variables.
At the same time, some of them are supporting dynamic settings from upstream management system.

Right now, SkyWalking supports following dynamic configurations.

| Config Key | Value Description | Value Format Example |
|:----:|:----:|:----:|
|agent-analyzer.default.slowDBAccessThreshold| Thresholds of slow Database statement, override `receiver-trace/default/slowDBAccessThreshold` of `application.yml`. | default:200,mongodb:50|
|agent-analyzer.default.uninstrumentedGateways| The uninstrumented gateways, override `gateways.yml`. | same as [`gateways.yml`](uninstrumented-gateways.md#configuration-format) |
|alarm.default.alarm-settings| The alarm settings, will override `alarm-settings.yml`. | same as [`alarm-settings.yml`](backend-alarm.md) |
|core.default.apdexThreshold| The apdex threshold settings, will override `service-apdex-threshold.yml`. | same as [`service-apdex-threshold.yml`](apdex-threshold.md) |
|core.default.endpoint-name-grouping| The endpoint name grouping setting, will override `endpoint-name-grouping.yml`. | same as [`endpoint-name-grouping.yml`](endpoint-grouping-rules.md) |
|agent-analyzer.default.sampleRate| Trace sampling , override `receiver-trace/default/sampleRate` of `application.yml`. | 10000 |
|agent-analyzer.default.slowTraceSegmentThreshold| Setting this threshold about the latency would make the slow trace segments sampled if they cost more time, even the sampling mechanism activated. The default value is `-1`, which means would not sample slow traces. Unit, millisecond. override `receiver-trace/default/slowTraceSegmentThreshold` of `application.yml`. | -1 |

This feature depends on upstream service, so it is **DISABLED** by default.

```yaml
configuration:
  selector: ${SW_CONFIGURATION:none}
  none:
  grpc:
    host: ${SW_DCS_SERVER_HOST:""}
    port: ${SW_DCS_SERVER_PORT:80}
    clusterName: ${SW_DCS_CLUSTER_NAME:SkyWalking}
    period: ${SW_DCS_PERIOD:20}
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
    host: ${SW_DCS_SERVER_HOST:""}
    port: ${SW_DCS_SERVER_PORT:80}
    clusterName: ${SW_DCS_CLUSTER_NAME:SkyWalking}
    period: ${SW_DCS_PERIOD:20}
```

## Dynamic Configuration Zookeeper Implementation
[Zookeeper](https://github.com/apache/zookeeper) is also supported as DCC(Dynamic Configuration Center), to use it, please configure as follows:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:zookeeper}
  zookeeper:
    period: ${SW_CONFIG_ZK_PERIOD:60} # Unit seconds, sync period. Default fetch every 60 seconds.
    nameSpace: ${SW_CONFIG_ZK_NAMESPACE:/default}
    hostPort: ${SW_CONFIG_ZK_HOST_PORT:localhost:2181}
    # Retry Policy
    baseSleepTimeMs: ${SW_CONFIG_ZK_BASE_SLEEP_TIME_MS:1000} # initial amount of time to wait between retries
    maxRetries: ${SW_CONFIG_ZK_MAX_RETRIES:3} # max number of times to retry
```

The **nameSpace** is the ZooKeeper path. The config key and value are the properties of the `namespace` folder.

## Dynamic Configuration Etcd Implementation

[Etcd](https://github.com/etcd-io/etcd) is also supported as DCC(Dynamic Configuration Center), to use it, please configure as follows:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:etcd}
  etcd:
    period: ${SW_CONFIG_ETCD_PERIOD:60} # Unit seconds, sync period. Default fetch every 60 seconds.
    group: ${SW_CONFIG_ETCD_GROUP:skywalking}
    serverAddr: ${SW_CONFIG_ETCD_SERVER_ADDR:localhost:2379}
    clusterName: ${SW_CONFIG_ETCD_CLUSTER_NAME:default}
```

## Dynamic Configuration Consul Implementation

[Consul](https://github.com/rickfast/consul-client) is also supported as DCC(Dynamic Configuration Center), to use it, please configure as follows:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:consul}
  consul:
    # Consul host and ports, separated by comma, e.g. 1.2.3.4:8500,2.3.4.5:8500
    hostAndPorts: ${SW_CONFIG_CONSUL_HOST_AND_PORTS:1.2.3.4:8500}
    # Sync period in seconds. Defaults to 60 seconds.
    period: ${SW_CONFIG_CONSUL_PERIOD:1}
    # Consul aclToken
    aclToken: ${SW_CONFIG_CONSUL_ACL_TOKEN:""}
```

## Dynamic Configuration Apollo Implementation

[Apollo](https://github.com/ctripcorp/apollo/) is also supported as DCC(Dynamic Configuration Center), to use it, just configured as follows:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:apollo}
  apollo:
    apolloMeta: ${SW_CONFIG_APOLLO:http://106.12.25.204:8080}
    apolloCluster: ${SW_CONFIG_APOLLO_CLUSTER:default}
    apolloEnv: ${SW_CONFIG_APOLLO_ENV:""}
    appId: ${SW_CONFIG_APOLLO_APP_ID:skywalking}
    period: ${SW_CONFIG_APOLLO_PERIOD:5}
```

## Dynamic Configuration Kuberbetes Configmap Implementation

[configmap](https://kubernetes.io/docs/concepts/configuration/configmap/) is also supported as DCC(Dynamic Configuration Center), to use it, just configured as follows:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:k8s-configmap}
  # [example] (../../../../oap-server/server-configuration/configuration-k8s-configmap/src/test/resources/skywalking-dynamic-configmap.example.yaml)
  k8s-configmap:
      # Sync period in seconds. Defaults to 60 seconds.
      period: ${SW_CONFIG_CONFIGMAP_PERIOD:60}
      # Which namespace is confiigmap deployed in.
      namespace: ${SW_CLUSTER_K8S_NAMESPACE:default}
      # Labelselector is used to locate specific configmap
      labelSelector: ${SW_CLUSTER_K8S_LABEL:app=collector,release=skywalking}
```
## Dynamic Configuration Nacos Implementation

[Nacos](https://github.com/alibaba/nacos) is also supported as DCC(Dynamic Configuration Center), to use it, please configure as follows:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:nacos}
  nacos:
    # Nacos Server Host
    serverAddr: ${SW_CONFIG_NACOS_SERVER_ADDR:127.0.0.1}
    # Nacos Server Port
    port: ${SW_CONFIG_NACOS_SERVER_PORT:8848}
    # Nacos Configuration Group
    group: ${SW_CONFIG_NACOS_SERVER_GROUP:skywalking}
    # Nacos Configuration namespace
    namespace: ${SW_CONFIG_NACOS_SERVER_NAMESPACE:}
    # Unit seconds, sync period. Default fetch every 60 seconds.
    period: ${SW_CONFIG_NACOS_PERIOD:60}
    # the name of current cluster, set the name if you want to upstream system known.
    clusterName: ${SW_CONFIG_NACOS_CLUSTER_NAME:default}
```
