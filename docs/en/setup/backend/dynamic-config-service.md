# Dynamic Configuration Service, DCS
[Dynamic Configuration Service](../../../../oap-server/server-configuration/grpc-configuration-sync/src/main/proto/configuration-service.proto) 
is a gRPC service which requires implementation of the upstream system.
The SkyWalking OAP fetches the configuration from the implementation (any system) after you open the implementation like this:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:grpc}
  grpc:
    host: ${SW_DCS_SERVER_HOST:""}
    port: ${SW_DCS_SERVER_PORT:80}
    clusterName: ${SW_DCS_CLUSTER_NAME:SkyWalking}
    period: ${SW_DCS_PERIOD:20}
```

## Config Server Response
`uuid`: To identify whether the config data changed, if `uuid` is the same, it is not required to respond to the config data.
### Single Config
Implement: 
```
rpc call (ConfigurationRequest) returns (ConfigurationResponse) { }
```

e.g. The config is:
```
{agent-analyzer.default.slowDBAccessThreshold}:{default:200,mongodb:50}
```
The response `configTable` is:
```
configTable {
  name: "agent-analyzer.default.slowDBAccessThreshold"
  value: "default:200,mongodb:50"
}
```

### Group Config
Implement:
```
rpc callGroup (ConfigurationRequest) returns (GroupConfigurationResponse) {}
```
Respond config data `GroupConfigItems groupConfigTable`

e.g. The config is:
```
{core.default.endpoint-name-grouping-openapi}:|{customerAPI-v1}:{value of customerAPI-v1}
                                              |{productAPI-v1}:{value of productAPI-v1}
                                              |{productAPI-v2}:{value of productAPI-v2}
```
The response `groupConfigTable` is:

```
groupConfigTable {
  groupName: "core.default.endpoint-name-grouping-openapi"
  items {
    name: "customerAPI-v1"
    value: "value of customerAPI-v1"
  }
  items {
    name: "productAPI-v1"
    value: "value of productAPI-v1"
  }
  items {
    name: "productAPI-v2"
    value: "value of productAPI-v2"
  }  
}
```
