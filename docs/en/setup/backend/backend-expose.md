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

## Query server

The native query APIs are served on the core REST server (`core/restPort`, default `12800`) by default:

- GraphQL, `POST /graphql`.
- The AI agent conversation view and files, `GET /ai-agent/conversations/*`.
- The TTL settings, `GET /status/config/ttl`.

When the HTTP receivers are left on the core REST server too, agents and query clients reach the same port.
The `query-server` module moves the query APIs to a server of their own, so they can be bound to another
interface, firewalled, and given TLS apart from the ports agents report to.

```yaml
query-server:
  selector: ${SW_QUERY_SERVER:default}
  default:
    restHost: ${SW_QUERY_SERVER_REST_HOST:0.0.0.0}
    restPort: ${SW_QUERY_SERVER_REST_PORT:0}
    restContextPath: ${SW_QUERY_SERVER_REST_CONTEXT_PATH:/}
    restIdleTimeOut: ${SW_QUERY_SERVER_REST_IDLE_TIMEOUT:30000}
    restAcceptQueueSize: ${SW_QUERY_SERVER_REST_QUEUE_SIZE:0}
    httpMaxRequestHeaderSize: ${SW_QUERY_SERVER_HTTP_MAX_REQUEST_HEADER_SIZE:8192}
    restSSLEnabled: ${SW_QUERY_SERVER_REST_SSL_ENABLED:false}
    restSSLKeyPath: ${SW_QUERY_SERVER_REST_SSL_KEY_PATH:""}
    restSSLCertChainPath: ${SW_QUERY_SERVER_REST_SSL_CERT_CHAIN_PATH:""}
```

With `restPort` set to a legal port (greater than 0), the query APIs move to that port, and the core REST
server no longer answers them (`/status/config/ttl` also stays on the admin server). It keeps serving the health check (`/healthcheck`) and the HTTP
receivers that the receiver sharing server does not move. Point every query client at the new port: the UI
(`queryUrl` of Horizon UI), `swctl --base-url`, and any other GraphQL client. The admin APIs stay on the
admin server (default `17128`), and the Zipkin, PromQL, LogQL, and TraceQL APIs keep their own ports.
