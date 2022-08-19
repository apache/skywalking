# Dynamic Configuration Nacos Implementation

[Nacos](https://github.com/alibaba/nacos) is also supported as a Dynamic Configuration Center (DCC). To use it, please configure it as follows:

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

## Config Storage
### Single Config

| Data Id | Group | Config Value |
|-----|-----|-----|
| configKey | {group} | configValue |

e.g. The config is:
```
{agent-analyzer.default.slowDBAccessThreshold}:{default:200,mongodb:50}
```
If `group = skywalking`, the config in Nacos is:

| Data Id | Group | Config Value |
|-----|-----|-----|
| agent-analyzer.default.slowDBAccessThreshold | skywalking | default:200,mongodb:50 |

### Group Config

| Data Id | Group | Config Value | Config Type |
|-----|-----|-----|-----|
| configKey | {group} | subItemkey1</br>subItemkey2</br>... | TEXT |
| subItemkey1 | {group} | subItemValue1 |
| subItemkey2 | {group} | subItemValue2 |
| ... | ... | ... |

Notice: If you add/remove a subItem, you need to add/remove the subItemKey from the group to which the subItem belongs:

| Data Id | Group | Config Value | Config Type |
|-----|-----|-----|-----|
| configKey | {group} | subItemkey1</br>subItemkey2</br>... | TEXT |

We separate subItemkeys by `\n` or `\r\n`, trim leading and trailing whitespace; if you set the config by `Nacos UI`, each subItemkey should be in a new line:
```
subItemValue1
subItemValue2
...

```
If you set the config by `API`, each subItemkey should be separated by `\n` or `\r\n`:
```
configService.publishConfig("test-module.default.testKeyGroup", "skywalking", "subItemkey1\n subItemkey2"));
```

e.g. The config is:
```
{core.default.endpoint-name-grouping-openapi}:|{customerAPI-v1}:{value of customerAPI-v1}
                                              |{productAPI-v1}:{value of productAPI-v1}
                                              |{productAPI-v2}:{value of productAPI-v2}
```
If `group = skywalking`, the config in Nacos is:

| Data Id | Group | Config Value | Config Type |
|-----|-----|-----|-----|
| core.default.endpoint-name-grouping-openapi | skywalking | customerAPI-v1</br>productAPI-v1</br>productAPI-v2 | TEXT |
| customerAPI-v1 | skywalking | value of customerAPI-v1 |
| productAPI-v1 | skywalking | value of productAPI-v1 |
| productAPI-v2 | skywalking | value of productAPI-v2 |
