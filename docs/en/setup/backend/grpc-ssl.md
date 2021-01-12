# Support gRPC SSL transportation for OAP server

For OAP communication we are currently using gRPC, a multi-platform RPC framework that uses protocol buffers for
message serialization. The nice part about gRPC is that it promotes the use of SSL/TLS to authenticate and encrypt
exchanges. Now OAP supports to enable SSL transportation for gRPC receivers.

You can follow below steps to enable this feature

## Creating SSL/TLS Certificates

It seems like step one is to generate certificates and key files for encrypting communication. I thought this would be
fairly straightforward using `openssl` from the command line.

Use this [script](../../../../tools/TLS/tls_key_generate.sh) if you are not familiar with how to generate key files.

We need below files:
 - `server.pem` a private RSA key to sign and authenticate the public key. It's either a PKCS#8(PEM) or PKCS#1(DER).
 - `server.crt` self-signed X.509 public keys for distribution.
 - `ca.crt` a certificate authority public key for a client to validate the server's certificate.
 
## Config OAP server 

You can enable gRPC SSL by add following lines to `application.yml/core/default`.
```json
gRPCSslEnabled: true
gRPCSslKeyPath: /path/to/server.pem
gRPCSslCertChainPath: /path/to/server.crt
gRPCSslTrustedCAPath: /path/to/ca.crt
```

`gRPCSslKeyPath` and `gRPCSslCertChainPath` are loaded by OAP server to encrypt the communication. `gRPCSslTrustedCAPath`
helps gRPC client to verify server certificates in cluster mode.

When new files are in place, they can be load dynamically instead of restarting OAP instance.

If you enable `sharding-server` to ingest data from external, add following lines to `application.yml/receiver-sharing-server/default`:

```json
gRPCSslEnabled: true
gRPCSslKeyPath: /path/to/server.pem
gRPCSslCertChainPath: /path/to/server.crt
```

Because `sharding-server` only receives data from external, so it doesn't need CA at all.

If you port to java agent, refer to [TLS.md](../service-agent/java-agent/TLS.md) to config java agent to enable TLS.
