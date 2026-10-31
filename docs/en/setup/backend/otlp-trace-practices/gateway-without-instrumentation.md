# Gateways Without Instrumentation

An ingress, a cloud load balancer or an nginx without tracing emits no spans, so the callee's SERVER span names the
caller's CLIENT span as its parent while the caller dialed the gateway's address. Without more, the pair would name
the gateway's address after the service behind it, and every service behind it would take the address in turn. List
the gateway in [uninstrumented gateways](../uninstrumented-gateways.md): its address is never aliased, the caller's edge
ends on a `VIRTUAL_GATEWAY` node, and the callee's edge starts from it when the gateway passes its callers' `Host`
through.

List the address the callers dial, as their CLIENT spans report it, in `config/gateways.yml`:

```yaml
gateways:
  - name: shop-ingress
    instances:
      - host: shop-ingress.ingress.svc   # as the callers' server.address or peer.address names it
        port: 80
```

- The match is exact: a caller that dials `shop-ingress` and another that dials `shop-ingress.ingress.svc` need both
  listed. A host without a port is matched at port 80.
- The callee's edge from the gateway is drawn from the address the callee reports it was dialed at, its
  `server.address` and `server.port`, which an HTTP server takes from the `Host` header. Keep the callers' `Host` on
  the way through; for nginx, `proxy_set_header Host $host;`, since `proxy_pass` otherwise sends the upstream's own
  name. A gateway that rewrites `Host` still ends its callers' edges, but the services behind it draw no edge from
  it. A `Host` without a port, as `$host` passes it, is read as port 80, the port the file assumes, so a gateway
  listening elsewhere, such as HTTPS on 443, draws the edge to the services behind it only when the `Host` it
  passes carries the port.
- The file belongs to the agent analyzer; with the `agent-analyzer` module not loaded, no address is a gateway. See
  [uninstrumented gateways](../uninstrumented-gateways.md) for the format and for changing it at runtime.

A listed ingress that routes by host, or by path to distinct services, is the ordinary case: the callers' edges end on
the gateway, and each service behind it draws its own edge from the gateway when the `Host` and the trace context are
passed through. Such a gateway left out of the file cannot work: each service answers calls its callers made to the
same gateway address, so the pairs hand that address to one service after another.

A gateway that does not trace and splits one address between versions of a service, by a blue/green switch, by
weight or by a header, is not supported. [Instrument the gateway](gateway-overview.md#instrumenting-a-gateway), so
it dials each version at its own address, see [Canary Releases](gateway-canary.md).
