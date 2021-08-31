# gRPC SSL transportation support for OAP server

For OAP communication, we are currently using gRPC, a multi-platform RPC framework that uses protocol buffers for
message serialization. The nice part about gRPC is that it promotes the use of SSL/TLS to authenticate and encrypt
exchanges. Now OAP supports enabling SSL transportation for gRPC receivers.

To enable this feature, follow the steps below.

## Creating SSL/TLS Certificates

The first step is to generate certificates and key files for encrypting communication. This is
fairly straightforward: use `openssl` from the command line.

Use this [script](../../../../tools/TLS/tls_key_generate.sh) if you are not familiar with how to generate key files.

We need the following files:
 - `server.pem`: A private RSA key to sign and authenticate the public key. It's either a PKCS#8(PEM) or PKCS#1(DER).
 - `server.crt`: Self-signed X.509 public keys for distribution.
 - `ca.crt`: A certificate authority public key for a client to validate the server's certificate.
 
## Config OAP server 

You can enable gRPC SSL by adding the following lines to `application.yml/core/default`.
```json
gRPCSslEnabled: true
gRPCSslKeyPath: /path/to/server.pem
gRPCSslCertChainPath: /path/to/server.crt
gRPCSslTrustedCAPath: /path/to/ca.crt
```

`gRPCSslKeyPath` and `gRPCSslCertChainPath` are loaded by the OAP server to encrypt communication. `gRPCSslTrustedCAPath`
helps the gRPC client to verify server certificates in cluster mode.

When new files are in place, they can be loaded dynamically, and you won't have to restart an OAP instance.

If you enable `sharding-server` to ingest data from an external source, add the following lines to `application.yml/receiver-sharing-server/default`:

```json
gRPCSslEnabled: true
gRPCSslKeyPath: /path/to/server.pem
gRPCSslCertChainPath: /path/to/server.crt
```

Since `sharding-server` only receives data from an external source, it doesn't need a CA at all.

If you port to Java agent, refer to [the Java agent repo](http://github.com/apache/skywalking-java) to config java agent and enable TLS.

## mutual TLS mode

To enable `mTLS` mode for gRPC channel requires [Sharing gRPC Server](./backend-receivers#grpchttp-server-for-receiver) enabled, as following configuration. 

```properties
receiver-sharing-server:
  selector: ${SW_RECEIVER_SHARING_SERVER:default}
  default:
    # For gRPC server
    gRPCHost: ${SW_RECEIVER_GRPC_HOST:0.0.0.0}
    gRPCPort: ${SW_RECEIVER_GRPC_PORT:11801}
    maxConcurrentCallsPerConnection: ${SW_RECEIVER_GRPC_MAX_CONCURRENT_CALL:0}
    maxMessageSize: ${SW_RECEIVER_GRPC_MAX_MESSAGE_SIZE:0}
    gRPCThreadPoolQueueSize: ${SW_RECEIVER_GRPC_POOL_QUEUE_SIZE:0}
    gRPCThreadPoolSize: ${SW_RECEIVER_GRPC_THREAD_POOL_SIZE:0}
    gRPCSslEnabled: ${SW_RECEIVER_GRPC_SSL_ENABLED:true}
    gRPCSslKeyPath: ${SW_RECEIVER_GRPC_SSL_KEY_PATH:"/path/to/server.pem"}
    gRPCSslCertChainPath: ${SW_RECEIVER_GRPC_SSL_CERT_CHAIN_PATH:"/path/to/server.crt"}
    gRPCSslTrustedCAsPath: ${SW_RECEIVER_GRPC_SSL_TRUSTED_CAS_PATH:"/path/to/ca.crt"}
    authentication: ${SW_AUTHENTICATION:""}
```

You still use this [script](../../../../tools/TLS/tls_key_generate.sh) to generate CA certificate and the private keys of server side(for OAP Server) and Client side(for Agent/Satellite).
You have to notice the private keys, including server and client-side, are from the same CA certification.
