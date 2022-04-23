# Dynamic Configuration Zookeeper Implementation
[Zookeeper](https://github.com/apache/zookeeper) is also supported as a Dynamic Configuration Center (DCC). To use it, please configure it as follows:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:zookeeper}
  zookeeper:
    period: ${SW_CONFIG_ZK_PERIOD:60} # Unit seconds, sync period. Default fetch every 60 seconds.
    namespace: ${SW_CONFIG_ZK_NAMESPACE:/default}
    hostPort: ${SW_CONFIG_ZK_HOST_PORT:localhost:2181}
    # Retry Policy
    baseSleepTimeMs: ${SW_CONFIG_ZK_BASE_SLEEP_TIME_MS:1000} # initial amount of time to wait between retries
    maxRetries: ${SW_CONFIG_ZK_MAX_RETRIES:3} # max number of times to retry
```

The **namespace** is the ZooKeeper path. The config key and value are the properties of the `namespace` folder.

## Config Storage
### Single Config
```
znode.path = {namespace}/configKey
configValue = znode.data
```
e.g. The config is: 
```
{agent-analyzer.default.slowDBAccessThreshold}:{default:200,mongodb:50}
```
If `namespace = /default` the config in zookeeper is:
```
znode.path = /default/agent-analyzer.default.slowDBAccessThreshold
znode.data = default:200,mongodb:50
```

### Group Config
```
znode.path = {namespace}/configKey
znode.child1.path = {znode.path}/subItemkey1
znode.child2.path = {znode.path}/subItemkey2
...
subItemValue1 = znode.child1.data
subItemValue2 = znode.child2.data
...
```
e.g. The config is:
```
{core.default.endpoint-name-grouping-openapi}:|{customerAPI-v1}:{value of customerAPI-v1}
                                              |{productAPI-v1}:{value of productAPI-v1}
                                              |{productAPI-v2}:{value of productAPI-v2}
```
If `namespace = /default` the config in zookeeper is:
```
znode.path = /default/core.default.endpoint-name-grouping-openapi
znode.customerAPI-v1.path = /default/core.default.endpoint-name-grouping-openapi/customerAPI-v1
znode.productAPI-v1.path = /default/core.default.endpoint-name-grouping-openapi/productAPI-v1
znode.productAPI-v2.path = /default/core.default.endpoint-name-grouping-openapi/productAPI-v2

znode.customerAPI-v1.data = value of customerAPI-v1
znode.productAPI-v1.data = value of productAPI-v1
znode.productAPI-v2.data = value of productAPI-v2

```