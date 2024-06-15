# Setup External Communication Channels

SkyWalking has default activated gRPC/HTTP servers in the core module, which serve for both internal communication
and external data report or query.

In some advanced scenarios, such as security requirements, specific gRPC/HTTP servers should be exposed for external
requests.

```yaml
receiver-sharing-server:
  selector: ${SW_RECEIVER_SHARING_SERVER:default}
  default:
    # For REST server
    restHost: ${SW_RECEIVER_SHARING_REST_HOST:0.0.0.0}
    restPort: ${SW_RECEIVER_SHARING_REST_PORT:0}
    restContextPath: ${SW_RECEIVER_SHARING_REST_CONTEXT_PATH:/}
    restMaxThreads: ${SW_RECEIVER_SHARING_REST_MAX_THREADS:200}
    restIdleTimeOut: ${SW_RECEIVER_SHARING_REST_IDLE_TIMEOUT:30000}
    restAcceptQueueSize: ${SW_RECEIVER_SHARING_REST_QUEUE_SIZE:0}
    httpMaxRequestHeaderSize: ${SW_RECEIVER_SHARING_HTTP_MAX_REQUEST_HEADER_SIZE:8192}
    # For gRPC server
    gRPCHost: ${SW_RECEIVER_GRPC_HOST:0.0.0.0}
    gRPCPort: ${SW_RECEIVER_GRPC_PORT:0}
    maxConcurrentCallsPerConnection: ${SW_RECEIVER_GRPC_MAX_CONCURRENT_CALL:0}
    maxMessageSize: ${SW_RECEIVER_GRPC_MAX_MESSAGE_SIZE:52428800} #50MB
    gRPCThreadPoolSize: ${SW_RECEIVER_GRPC_THREAD_POOL_SIZE:0}
    gRPCSslEnabled: ${SW_RECEIVER_GRPC_SSL_ENABLED:false}
    gRPCSslKeyPath: ${SW_RECEIVER_GRPC_SSL_KEY_PATH:""}
    gRPCSslCertChainPath: ${SW_RECEIVER_GRPC_SSL_CERT_CHAIN_PATH:""}
    gRPCSslTrustedCAsPath: ${SW_RECEIVER_GRPC_SSL_TRUSTED_CAS_PATH:""}
    authentication: ${SW_AUTHENTICATION:""}
```

Set `restPort`(HTTP) and `gRPCPort`(gRPC) to a legal port(greater than 0), would initialize new gRPC/HTTP servers for
external requests with other relative settings. In this case, `core/gRPC` and `core/rest` could be served for cluster
internal communication only.
