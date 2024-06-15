# gRPC SSL transportation support for OAP server

For OAP communication, we are currently using gRPC, a multi-platform RPC framework that uses protocol buffers for message serialization. The nice part about gRPC is that it promotes the use of SSL/TLS to authenticate and encrypt exchanges. Now OAP supports enabling SSL transportation for gRPC receivers. Since 8.8.0, OAP supports enabling mutual TLS authentication between probes and OAP servers.

To enable this feature, follow the steps below.

## Preparation


By default, the communication between OAP nodes and the communication between receiver and probe share the same gRPC server. Its configuration is in `application.yml/core/default` section.

The advanced gRPC receiver is only for communication with the probes. This configuration is in `application.yml/receiver-sharing-server/default` section.


The first step is to generate certificates and private key files for encrypting communication.

### Creating SSL/TLS Certificates

The first step is to generate certificates and key files for encrypting communication. This is fairly straightforward: use `openssl` from the command line.

Use this [script](../../../../tools/TLS/tls_key_generate.sh) if you are not familiar with how to generate key files.

We need the following files:

* `ca.crt`: A certificate authority public key for a client to validate the server's certificate.
* `server.pem`, `client.pem`: A private RSA key to sign and authenticate the public key. It's either a PKCS#8(PEM) or PKCS#1(DER).
* `server.crt`, `client.crt`: Self-signed X.509 public keys for distribution.

## TLS on OAP servers

By default, the communication between OAP nodes and the communication between receiver and probe share the same gRPC server. That means once you enable SSL for receivers and probes, the OAP nodes will enable it too.


**NOTE**: SkyWalking **does not** support enabling mTLS on `OAP server nodes communication`. That means you have to enable `receiver-sharing-server` for enabling mTLS on communication between probes and OAP servers. More details see [Enable mTLS mode on gRPC receiver](#enable-mtls-mode-on-grpc-receiver).


You can enable gRPC SSL by adding the following lines to `application.yml/core/default`.

```yaml
gRPCSslEnabled: true
gRPCSslKeyPath: /path/to/server.pem
gRPCSslCertChainPath: /path/to/server.crt
gRPCSslTrustedCAPath: /path/to/ca.crt
```

`gRPCSslKeyPath` and `gRPCSslCertChainPath` are loaded by the OAP server to encrypt communication. `gRPCSslTrustedCAPath`
helps the gRPC client to verify server certificates in cluster mode.

> There is a gRPC client and server in every OAP server node. The gRPC client communicates with OAP servers in cluster mode. They are sharing the core module configuration.

**When new files are in place, they can be loaded dynamically, and you won't have to restart an OAP instance.**


## Enable TLS on independent gRPC receiver

If you enable `receiver-sharing-server` to ingest data from an external source, add the following lines to `application.yml/receiver-sharing-server/default`:

```yaml
gRPCPort: ${SW_RECEIVER_GRPC_PORT:"changeMe"}
gRPCSslEnabled: true
gRPCSslKeyPath: /path/to/server.pem
gRPCSslCertChainPath: /path/to/server.crt
```

Since `receiver-sharing-server` only receives data from an external source, it doesn't need a CA at all. But you have to configure the CA for the clients, such as [Java agent](http://github.com/apache/skywalking-java), [Satellite](http://github.com/apache/skywalking-satellite). If you port to the Java agent, refer to [the Java agent repo](http://github.com/apache/skywalking-java) to configure the Java agent and enable TLS.

**NOTE**: change the `SW_RECEIVER_GRPC_PORT` as non-zero to enable `receiver-sharing-server`. And the port is open for the clients.

### Enable mTLS mode on gRPC receiver

Since 8.8.0, SkyWalking has supported mutual TLS authentication for transporting between clients and OAP servers. Enable `mTLS` mode for the gRPC channel requires [Sharing gRPC Server](backend-expose.md) enabled, as the following configuration.

```yaml
receiver-sharing-server:
  selector: ${SW_RECEIVER_SHARING_SERVER:default}
  default:
    # For gRPC server
    gRPCHost: ${SW_RECEIVER_GRPC_HOST:0.0.0.0}
    gRPCPort: ${SW_RECEIVER_GRPC_PORT:"changeMe"}
    maxConcurrentCallsPerConnection: ${SW_RECEIVER_GRPC_MAX_CONCURRENT_CALL:0}
    maxMessageSize: ${SW_RECEIVER_GRPC_MAX_MESSAGE_SIZE:52428800} #50MB
    gRPCThreadPoolSize: ${SW_RECEIVER_GRPC_THREAD_POOL_SIZE:0}
    gRPCSslEnabled: ${SW_RECEIVER_GRPC_SSL_ENABLED:true}
    gRPCSslKeyPath: ${SW_RECEIVER_GRPC_SSL_KEY_PATH:"/path/to/server.pem"}
    gRPCSslCertChainPath: ${SW_RECEIVER_GRPC_SSL_CERT_CHAIN_PATH:"/path/to/server.crt"}
    gRPCSslTrustedCAsPath: ${SW_RECEIVER_GRPC_SSL_TRUSTED_CAS_PATH:"/path/to/ca.crt"}
    authentication: ${SW_AUTHENTICATION:""}
```

You can still use this [script](../../../../tools/TLS/tls_key_generate.sh) to generate CA certificate and the key files of server-side(for OAP Server) and client-side(for Agent/Satellite).
You have to notice the keys, including server and client-side, are from the same CA certificate.
