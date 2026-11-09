# Uninstrumented Gateways/Proxies

The uninstrumented gateways are not instrumented by the SkyWalking agent plugin when they start,
but they can be configured in `gateways.yml` file or via [Dynamic Configuration](dynamic-config.md). The reason why they can't register
to backend automatically is that there are no suitable agent plugins. For example, there are no agent plugins for Nginx, HAProxy, etc.
So to visualize the real topology, we provide a way to configure the gateways/proxies manually.

## Configuration Format

The configuration content includes gateway names and their instances:

```yml
gateways:
 - name: proxy0 # the name is not used for now
   instances:
     - host: 127.0.0.1 # the host/IP of this gateway instance
       port: 9099 # the port of this gateway instance defaults to 80
```

**Note**: The `host` of the instance must be the one that is actually used on the client-side. For example,
if instance `proxyA` has 2 IPs, say `192.168.1.110` and `192.168.1.111`, both of which delegate the target service,
and the client connects to `192.168.1.110`, then configuring `192.168.1.111` as the `host` won't work properly.

## Where the list applies

Both trace analyses ask the list through the TAL function
[`isGateway(address)`](../../concepts-and-designs/tal.md#functions-for-every-input):

- The [native trace analysis](native-trace-analysis.md): for the address a reference says the caller dialed, so
  the gateway is the caller of the call into the service, and no network address alias is written for its address;
  and for the peer an HTTP exit span dialed when no alias names it, so the call ends on the gateway.
- The [OpenTelemetry trace analysis](trace-analyzer.md#topology), for the peer a client span dialed and the address a
  server span was dialed at, see [Address-named peers](../../concepts-and-designs/tal-otlp.md#address-named-peers).

An address matches as the file lists it, `host:port`. A bare host, with no port, also matches the instance listed at
port 80, the port an instance that names none gets: an HTTP client that omits the default port records its peer as the
bare host, so a gateway listed once matches both spellings. Port 443 is not inferred: a bare host is retried only at
port 80, so a gateway configured at port 443 matches only an address that includes `:443`. Nothing else is resolved, so
every other spelling and port the callers dial has to be listed.
With the `agent-analyzer` module not loaded, no address is a gateway.

With debug logging on for `UninstrumentedGatewaysConfig`, each lookup writes one line: a bare host writes two, for the
address and for it at port 80, and an empty address is answered `false` without a lookup or a line.
