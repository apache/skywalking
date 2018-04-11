# Support Transport Layer Security (TLS)
Transport Layer Security (TLS) is a very common security way when transport data through Internet.
In some use cases, end users report the background:

> Target(under monitoring) applications are in a region, which also named VPC,
at the same time, the SkyWalking backend is in another region (VPC).
> 
> Because of that, security requirement is very obvious.

## Requirement
Enable **direct uplink**, by following this [document](Direct-uplink.md).

Because of uplink through internet, with security concern, the naming mechanism didn't fit. 
So we didn't support TLS in naming service of HTTP service.

## Supported version
5.0.0-beta +

## Authentication Mode
Only support **no mutual auth**.
- Use this [script](../../tools/TLS/tls_key_generate.sh) if you are not familiar with how to generate key files.
- Find `ca.crt`, and use it at client side
- Find `server.crt` and `server.pem`. Use them at server side.

## Open and config TLS

### Agent config
- Place `ca.crt` into `/ca` folder in agent package. Notice, `/ca` is not created in distribution, please create it by yourself.

Agent open TLS automatically after the `/ca/ca.crt` file detected.

### Collector config
Module `agent_gRPC/gRPC` supports TLS. And only this module for now.

- Uncomment the `ssl_cert_chain_file` and `ssl_private_key_file` settings in `application.yml`
- `ssl_cert_chain_file` value is the absolute path of `server.crt`
- `ssl_private_key_file` value is the absolute path of `server.pem`

## Avoid port share
In most cases, we recommend sharing port for all gRPC services in `agent_gRPC/gRPC` and `remote/gRPC` modules.
But don't do this when you open TLS in `agent_gRPC/gRPC`, the obvious reason is you can't listen a port with and without TLS.

The solution is, change the `remote/gRPC/port`.

## How about other listening ports
Please use other security ways to make sure can't access other ports out of region (VPC), such as firewall, proxy.