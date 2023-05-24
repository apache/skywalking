# Dynamic Configuration Consul Implementation

[Consul](https://github.com/rickfast/consul-client) is also supported as a Dynamic Configuration Center (DCC). To use it, please configure it as follows:

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

## Config Storage
### Single Config
Single configs in Consul are key/value pairs:

| Key | Value |
|-----|-----|
| configKey | configValue |

e.g. The config is:
```
{agent-analyzer.default.slowDBAccessThreshold}:{default:200,mongodb:50}
```
The config in Consul is:

| Key | Value |
|-----|-----|
| agent-analyzer.default.slowDBAccessThreshold | default:200,mongodb:50 |
| ... | ... |


### Group Config
Group config in Consul are key/value pairs as well, but  according to the level keys organized by `/`.

| Key | Value |
|-----|-----|
| configKey/subItemkey1 | subItemValue1 |
| configKey/subItemkey2 | subItemValue2 |
| ... | ... |

If we use Consul UI, we can see keys organized like a folder:
```
configKey
    -- subItemkey1
    -- subItemkey2
...
```
e.g. The config is:
```
{core.default.endpoint-name-grouping-openapi}:|{customerAPI-v1}:{value of customerAPI-v1}
                                              |{productAPI-v1}:{value of productAPI-v1}
                                              |{productAPI-v2}:{value of productAPI-v2}
```
The config in Consul is:

| Key | Value |
|-----|-----|
| core.default.endpoint-name-grouping-openapi/customerAPI-v1 | value of customerAPI-v1 |
| core.default.endpoint-name-grouping-openapi/productAPI-v1 | value of productAPI-v1 |
| core.default.endpoint-name-grouping-openapi/productAPI-v2 | value of productAPI-v2 |
