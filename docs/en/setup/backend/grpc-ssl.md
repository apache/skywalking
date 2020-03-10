# Support gRPC SSL transportation for OAP server

For OAP communication we are currently using gRPC, a multi-platform RPC framework that uses protocol buffers for
message serialization. The nice part about gRPC is that it promotes the use of SSL/TLS to authenticate and encrypt
exchanges. Now OAP supports to enable SSL transportation for gRPC receivers.

You can follow below steps to enable this feature

## Creating SSL/TLS Certificates

It seems like step one is to generate certificates and key files for encrypting communication. I thought this would be
fairly straightforward using `openssl` from the command line, However, it may be simpler to use
[certstrap](https://github.com/square/certstrap), a simple certificate manager written in Go by the folks at Square.
The app avoids dealing with `openssl`, but has a very simple workflow: create a certificate authority, sign certificates
with it.

After signing the certificates of OAP server, we should convert private key to a PKCS8 format before placing it into the host.

```
$ openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in server.key -out server-key.pem
```

## Config OAP server 

You can enable gRPC SSL by add following lines to `application.yml/core/default`.
```json
gRPCSslEnabled: true
gRPCSslKeyPath: /path/to/server-key.pem
gRPCSslCertChainPath: /path/to/server.crt
```

If you port to java agent, refer to [TLS.md](../service-agent/java-agent/TLS.md) to config java agent to enable TLS.
