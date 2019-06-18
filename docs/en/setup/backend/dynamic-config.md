# Dynamic Configuration
SkyWalking Configurations mostly are set through `application.yml` and OS system environment variables.
But some of them are supporting dynamic settings from upstream management system.

Right now, SkyWalking supports following dynamic configurations.

| Config Key | Value Description | Value Format Example |
|:----:|:----:|:----:|
|receiver-trace.default.slowDBAccessThreshold| Thresholds of slow Database statement, override `receiver-trace/default/slowDBAccessThreshold` of `applciation.yml`. | default:200,mongodb:50|


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

[Apollo](https://github.com/ctripcorp/apollo/) is also supported in DCS, to use it, just configured as follows:

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

[Nacos](https://github.com/alibaba/nacos) is also supported in DCS, to use it, please configure as follows:

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

## 3rd party Configuration Center
We are welcome contributions to implement this module provider to support popular configuration center, 
such as Zookeeper, etcd, Consul. Submit issue to discuss.


