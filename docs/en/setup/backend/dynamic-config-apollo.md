# Dynamic Configuration Apollo Implementation

[Apollo](https://github.com/ctripcorp/apollo/) is also supported as Dynamic Configuration Center (DCC). To use it, please configure as follows:

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