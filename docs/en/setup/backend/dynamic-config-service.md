# Dynamic Configuration Service, DCS
[Dynamic Configuration Service](../../../../oap-server/server-configuration/grpc-configuration-sync/src/main/proto/configuration-service.proto) 
is a gRPC service which requires implementation of the upstream system.
The SkyWalking OAP fetches the configuration from the implementation (any system), after you open the implementation like this:

```yaml
configuration:
  selector: ${SW_CONFIGURATION:grpc}
  grpc:
    host: ${SW_DCS_SERVER_HOST:""}
    port: ${SW_DCS_SERVER_PORT:80}
    clusterName: ${SW_DCS_CLUSTER_NAME:SkyWalking}
    period: ${SW_DCS_PERIOD:20}
```
