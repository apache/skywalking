# Dynamic Configuration Apollo Implementation

[Apollo](https://github.com/ctripcorp/apollo/) is also supported as a Dynamic Configuration Center (DCC). To use it, please configure it as follows:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:apollo}
  apollo:
    apolloMeta: ${SW_CONFIG_APOLLO:http://localhost:8080}
    apolloCluster: ${SW_CONFIG_APOLLO_CLUSTER:default}
    apolloEnv: ${SW_CONFIG_APOLLO_ENV:""}
    appId: ${SW_CONFIG_APOLLO_APP_ID:skywalking}
    period: ${SW_CONFIG_APOLLO_PERIOD:60}
```

## Config Storage
### Single Config
Single configs in Apollo are key/value pairs:

| Key | Value |
|-----|-----|
| configKey | configValue |

e.g. The config is:
```
{agent-analyzer.default.slowDBAccessThreshold}:{default:200,mongodb:50}
```
The config in Apollo is:

| Key | Value |
|-----|-----|
| agent-analyzer.default.slowDBAccessThreshold | default:200,mongodb:50 |
| ... | ... |


### Group Config
Group config in Apollo are key/value pairs as well, and the key is composited by configKey and subItemKey with `.`.

| Key | Value |
|-----|-----|
| configKey.subItemkey1 | subItemValue1 |
| configKey.subItemkey2 | subItemValue2 |
| ... | ... |

e.g. The config is:
```
{core.default.endpoint-name-grouping-openapi}:|{customerAPI-v1}:{value of customerAPI-v1}
                                              |{productAPI-v1}:{value of productAPI-v1}
                                              |{productAPI-v2}:{value of productAPI-v2}
```
The config in Apollo is:

| Key | Value |
|-----|-----|
| core.default.endpoint-name-grouping-openapi.customerAPI-v1 | value of customerAPI-v1 |
| core.default.endpoint-name-grouping-openapi.productAPI-v1 | value of productAPI-v1 |
| core.default.endpoint-name-grouping-openapi.productAPI-v2 | value of productAPI-v2 |