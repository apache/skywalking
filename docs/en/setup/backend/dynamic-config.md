# Dynamic Configuration
SkyWalking Configurations are mostly set through `application.yml` and OS system environment variables.

At the same time, some of them support dynamic settings from an upstream management system.

Currently, SkyWalking supports two types of dynamic configurations: Single and Group.

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
## Single Configuration
Single Configuration is a config key that corresponds to a specific config value. The logic structure is:
```
{configKey}:{configValue}
```
For example:
```
{agent-analyzer.default.slowDBAccessThreshold}:{default:200,mongodb:50}
```
Supported configurations are as follows:

| Config Key | Value Description | Value Format Example |
|:----:|:----:|:----:|
|agent-analyzer.default.slowDBAccessThreshold| Thresholds of slow Database statement. Overrides `agent-analyzer/default/slowDBAccessThreshold` of `application.yml`. | default:200,mongodb:50|
|agent-analyzer.default.uninstrumentedGateways| The uninstrumented gateways. Overrides `gateways.yml`. | Same as [`gateways.yml`](uninstrumented-gateways.md#configuration-format). |
|alarm.default.alarm-settings| The alarm settings. Overrides `alarm-settings.yml`. | Same as [`alarm-settings.yml`](backend-alarm.md). |
|core.default.apdexThreshold| The apdex threshold settings. Overrides `service-apdex-threshold.yml`. | Same as [`service-apdex-threshold.yml`](apdex-threshold.md). |
|core.default.endpoint-name-grouping| The endpoint name grouping setting. Overrides `endpoint-name-grouping.yml`. | Same as [`endpoint-name-grouping.yml`](endpoint-grouping-rules.md). |
|core.default.log4j-xml| The log4j xml configuration. Overrides `log4j2.xml`. | Same as [`log4j2.xml`](dynamical-logging.md). |
|core.default.searchableTracesTags| The searchableTracesTags configuration. Override `core/default/searchableTracesTags` in the `application.yml`. | http.method,http.status_code,rpc.status_code,db.type,db.instance,mq.queue,mq.topic,mq.broker |
|agent-analyzer.default.traceSamplingPolicy| The sampling policy for default and service dimension, override `trace-sampling-policy-settings.yml`. | same as [`trace-sampling-policy-settings.yml`](trace-sampling.md) | 
|configuration-discovery.default.agentConfigurations| The ConfigurationDiscovery settings. | See [`configuration-discovery.md`](https://skywalking.apache.org/docs/skywalking-java/next/en/setup/service-agent/java-agent/configuration-discovery/). |

## Group Configuration
Group Configuration is a config key corresponding to a group sub config item. A sub config item is a key-value pair. The logic structure is:
```
{configKey}: |{subItemkey1}:{subItemValue1}
             |{subItemkey2}:{subItemValue2}
             |{subItemkey3}:{subItemValue3}
             ...      
```
For example:
```
{core.default.endpoint-name-grouping-openapi}:|{customerAPI-v1}:{value of customerAPI-v1}
                                              |{productAPI-v1}:{value of productAPI-v1}
                                              |{productAPI-v2}:{value of productAPI-v2}
                                              
```
Supported configurations are as follows:

| Config Key | SubItem Key Description |  Value Description | Value Format Example |
|:----:|:----:|:----:|:----:|
|core.default.endpoint-name-grouping-openapi|The serviceName relevant to openAPI definition file. eg. `serviceA`. If the serviceName relevant to multiple files should add subItems for each files, and each subItem key should split serviceName and fileName with `.` eg. `serviceA.API-file1`,`serviceA.API-file2` |The openAPI definitions file contents(yaml format) for create endpoint name grouping rules.|Same as [`productAPI-v2.yaml`](endpoint-grouping-rules.md)|

## Dynamic Configuration Implementations
- [Dynamic Configuration Service, DCS](./dynamic-config-service.md)
- [Zookeeper Implementation](./dynamic-config-zookeeper.md)
- [Etcd Implementation](./dynamic-config-etcd.md)
- [Consul Implementation](./dynamic-config-consul.md)
- [Apollo Implementation](./dynamic-config-apollo.md)
- [Kubernetes Configmap Implementation](./dynamic-config-configmap.md)
- [Nacos Implementation](./dynamic-config-nacos.md)
