# Dynamic Configuration Etcd Implementation

[Etcd](https://github.com/etcd-io/etcd) is also supported as Dynamic Configuration Center (DCC). To use it, please configure as follows:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:etcd}
  etcd:
    period: ${SW_CONFIG_ETCD_PERIOD:60} # Unit seconds, sync period. Default fetch every 60 seconds.
    endpoints: ${SW_CONFIG_ETCD_ENDPOINTS:localhost:2379}
    namespace: ${SW_CONFIG_ETCD_NAMESPACE:/skywalking}
    authentication: ${SW_CONFIG_ETCD_AUTHENTICATION:false}
    user: ${SW_CONFIG_ETCD_USER:}
    password: ${SW_CONFIG_ETCD_password:}
```

**NOTE**: Only the v3 protocol is supported since 8.7.0.