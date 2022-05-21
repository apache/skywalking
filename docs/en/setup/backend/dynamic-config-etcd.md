# Dynamic Configuration Etcd Implementation

[Etcd](https://github.com/etcd-io/etcd) is also supported as a Dynamic Configuration Center (DCC). To use it, please configure it as follows:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:etcd}
  etcd:
    period: ${SW_CONFIG_ETCD_PERIOD:60} # Unit seconds, sync period. Default fetch every 60 seconds.
    endpoints: ${SW_CONFIG_ETCD_ENDPOINTS:http://localhost:2379}
    namespace: ${SW_CONFIG_ETCD_NAMESPACE:/skywalking}
    authentication: ${SW_CONFIG_ETCD_AUTHENTICATION:false}
    user: ${SW_CONFIG_ETCD_USER:}
    password: ${SW_CONFIG_ETCD_password:}
```

**NOTE**: Since 8.7.0, only the v3 protocol is supported.

## Config Storage
### Single Config
Single configs in etcd are key/value pairs:

| Key | Value |
|-----|-----|
| {namespace}/configKey | configVaule |

e.g. The config is:
```
{agent-analyzer.default.slowDBAccessThreshold}:{default:200,mongodb:50}
```
If `namespace = /skywalking` the config in etcd is:

| Key | Value |
|-----|-----|
| /skywalking/agent-analyzer.default.slowDBAccessThreshold | default:200,mongodb:50 |
| ... | ... |


### Group Config
Group config in etcd are key/value pairs as well, and the key is composited by configKey and subItemKey with `/`.

| Key | Value |
|-----|-----|
| {namespace}/configKey/subItemkey1 | subItemValue1 |
| {namespace}/configKey/subItemkey2 | subItemValue2 |
| ... | ... |

e.g. The config is:
```
{core.default.endpoint-name-grouping-openapi}:|{customerAPI-v1}:{value of customerAPI-v1}
                                              |{productAPI-v1}:{value of productAPI-v1}
                                              |{productAPI-v2}:{value of productAPI-v2}
```
If `namespace = /skywalking` the config in etcd is:

| Key | Value |
|-----|-----|
| /skywalking/core.default.endpoint-name-grouping-openapi/customerAPI-v1 | value of customerAPI-v1 |
| /skywalking/core.default.endpoint-name-grouping-openapi/productAPI-v1 | value of productAPI-v1 |
| /skywalking/core.default.endpoint-name-grouping-openapi/productAPI-v2 | value of productAPI-v2 |
