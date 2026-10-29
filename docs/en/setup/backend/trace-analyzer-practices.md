# OpenTelemetry Trace Analysis Practices

The [trace analysis](trace-analyzer.md) names a peer that a CLIENT span reaches by address, `cart:7070` or
`172.19.0.19:8080`, after the service that answered, by pairing the caller and the callee of one call: the callee's
SERVER span names the caller's span as its parent. The alias it learns is keyed by the caller and the address it dialed,
`frontend->cart:7070`. The mechanism is described under [Address-named
peers](../../concepts-and-designs/tal.md#address-named-peers). This page shows how it behaves in common deployments,
and what to change when it does not draw what you expect.

## Callers dial a Kubernetes Service

`frontend` dials `cart:7070`, the Kubernetes Service in front of the `cart` pods, which report
`service.name=cart`. The first calls are drawn as `frontend → cart:7070`. Once one of them is paired, after about
four calls at the default `pairingSamplePercent` of 25, the row `frontend->cart:7070 → cart` is written, and once
it has been stored and reloaded, about 35 seconds later, the edge lands on `cart`. The calls recorded before then keep their
metrics under the `frontend → cart:7070` relation; the topology folds that node onto `cart`.

A second caller, `checkout`, dialing the same `cart:7070`, is learned on its own: its first calls are drawn to the
address too. The name `cart` is never guessed from the host, so a Service named differently from the pods'
`service.name` works the same way.

## The same application in two namespaces

With [OTLP service grouping](otlp-trace.md) by namespace, `shop::frontend` and `outlet::frontend` both dial
`cart:7070`, each reaching the `cart` of its own namespace. The rows are `shop::frontend->cart:7070 → shop::cart` and
`outlet::frontend->cart:7070 → outlet::cart`: the caller in the key keeps them apart, whatever the grouping attribute
is.

## Proxies, gateways and canaries: which setup

| In front of the callee | Do | Topology |
|---|---|---|
| A proxy or gateway that traces itself with a SERVER and a CLIENT span per request, as Envoy | give its spans `service.layer=OTLP_GENERAL` | `caller → gateway → backend`, each hop learned from its own spans, see [A proxy that emits spans](#a-proxy-that-emits-spans) and [Instrumenting a gateway](#instrumenting-a-gateway) |
| A proxy that reports one span per request, as nginx with its OpenTelemetry module | propagate the context, put the upstream on its span, and add a rule on its SERVER span | `caller → nginx → backend`, see [A proxy that reports one span per request](#a-proxy-that-reports-one-span-per-request) |
| A gateway that does not trace, routing by host, or by path to distinct services | list it in [uninstrumented gateways](uninstrumented-gateways.md) | `caller → [gateway] → service`, an edge per service, see [A load balancer or gateway that does not trace](#a-load-balancer-or-gateway-that-does-not-trace) |
| A gateway that does not trace and splits one address between versions, by blue/green, weight or header | not supported: [instrument the gateway](#instrumenting-a-gateway) | as for a gateway that traces itself |
| A canary behind a traced gateway that dials `b` and `b-canary` at their own addresses | nothing | `gateway → b` and `gateway → b-canary`, see [A canary](#a-canary) |
| A canary behind a traced gateway that dials both at one address, a router behind it splitting them | a rule on the gateway's CLIENT spans | `gateway → b` and `gateway → b-canary`, see [A canary](#a-canary) |
| A canary chosen by a header, through a router that emits no spans and is not listed, as Istio with the sidecars' tracing off | the header rule on the callers' CLIENT spans | `a → b` and `a → b-canary`, see [A canary](#a-canary) |

## A proxy that emits spans

The OpenTelemetry Demo's browser calls `frontend-proxy:8080`, an Envoy that traces itself, which forwards to
`frontend` at `172.19.0.19:8080` and keeps the `Host` header, so `frontend`'s SERVER span reports
`server.address=frontend-proxy`. Each hop is learned from its own two spans:

| Caller span | Callee span | Row |
|---|---|---|
| `frontend-web` CLIENT to `frontend-proxy:8080` | `frontend-proxy` SERVER | `frontend-web->frontend-proxy:8080 → frontend-proxy` |
| `frontend-proxy` CLIENT to `172.19.0.19:8080` | `frontend` SERVER | `frontend-proxy->172.19.0.19:8080 → frontend` |

The address `frontend` reports is never used, so the proxy keeps its own node. The proxy's spans need
`service.layer=OTLP_GENERAL` too: an unanalyzed proxy sends no pairing fact, and its callers stay address-named.

## A load balancer or gateway that does not trace

An ingress, a cloud load balancer or an nginx without tracing emits no spans, so the callee's SERVER span names the
caller's CLIENT span as its parent while the caller dialed the gateway's address. Without more, the pair would name
the gateway's address after the service behind it, and every service behind it would take the address in turn. List
the gateway in [uninstrumented gateways](uninstrumented-gateways.md): its address is never aliased, the caller's edge
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
  [uninstrumented gateways](uninstrumented-gateways.md) for the format and for changing it at runtime.

A listed ingress that routes by host, or by path to distinct services, is the ordinary case: the callers' edges end on
the gateway, and each service behind it draws its own edge from the gateway when the `Host` and the trace context are
passed through. Such a gateway left out of the file cannot work: each service answers calls its callers made to the
same gateway address, so the pairs hand that address to one service after another.

A gateway that does not trace and splits one address between versions of a service, by a blue/green switch, by
weight or by a header, is not supported. [Instrument the gateway](#instrumenting-a-gateway), so it dials each version
at its own address, see [A canary](#a-canary).

## Instrumenting a gateway

A gateway that traces itself is analyzed like any service, and each hop through it is learned from its own two spans.
Whatever the gateway, check that:

- **It exports its spans over OTLP, with `service.layer=OTLP_GENERAL` on their resource.** If the gateway cannot set
  resource attributes, insert it in the OpenTelemetry Collector it exports through, with a `resource` processor that
  uses `action: insert`, so a service that set its own layer keeps it. Without the layer its spans are stored but
  not analyzed, it sends no pairing fact, and its callers stay address-named.
- **It continues the caller's trace and passes the trace context to the backend**, as W3C `traceparent`. The pairing
  matches a SERVER span with the caller's span that is its parent; a gateway that starts a new trace leaves the hop
  from its caller unpaired, and one that does not forward the context leaves the hop to the backend unpaired.
- **It reports a SERVER span for the request it receives and a CLIENT span for each call it makes to a backend.**
  The SERVER span pairs with the caller; the CLIENT span pairs with the backend. A gateway that reports one span per
  request pairs only one of the two hops, unless a rule also sends a caller half from that span, see [A proxy that
  reports one span per request](#a-proxy-that-reports-one-span-per-request).
- **Its CLIENT span names the backend it dialed.** The shipped rule reads, in this order: `service.peer.name` (or
  `peer.service`), which overrides the address; `server.address` and `server.port`; `peer.address`, as Envoy
  reports it; then the host and port of `url.full`. That peer keys the row, so a blue and a green backend dialed at
  their own addresses are two rows, `gateway->10.0.1.5:8080` and `gateway->10.0.2.7:8080`, each naming its own
  service.

A gateway that records its upstream under another attribute, logically the same as these, needs one line in your
copy of `otlp-general.yaml`, next to the other peer sources in the CLIENT block, before the gateway check and
`prepareNetworkAlias`:

```text
if (!peer && hasAttr("<attribute>")) {
  def peer = attr("<attribute>")
}
```

Nothing else in TAL changes per gateway. A gateway listed in `gateways.yml` needs no rule change, and neither does a
traced gateway whose upstreams have distinct addresses; only a traced gateway that dials two versions at one address
does, see [A canary](#a-canary).

The OpenTelemetry Demo's `frontend-proxy`, an Envoy that traces itself, does all four, see [A proxy that emits
spans](#a-proxy-that-emits-spans). Its spans get `service.layer` from the demo's Collector.

Do not also list an instrumented gateway in `gateways.yml`: its address would never be aliased, and its callers'
edges would end on a `VIRTUAL_GATEWAY` node instead of on the gateway's own service.

## A canary

`b` has a canary, `b-canary`, which reports `service.name=b-canary`; requests carrying `x-canary: v2` go to it, the
others to `b`. How the topology tells the two apart depends on what routes the requests.

**A traced gateway that dials each version at its own address** needs nothing: `gateway->10.0.1.5:8080 → b` and
`gateway->10.0.2.7:8080 → b-canary` are two rows learned from the gateway's own spans, so the topology reads
`a → gateway`, `gateway → b` and `gateway → b-canary`, whether the split is by header, weight or blue/green. This is
the setup to prefer: [instrument the gateway](#instrumenting-a-gateway).

**A traced gateway that dials both versions at one address**, leaving the split to a router behind it, makes both
pairs write one row, and the edge flips between `b` and `b-canary`. Make the version part of the peer on the gateway's
own CLIENT spans, in the CLIENT block of your copy of `otlp-general.yaml`, after the peer is named and before
`prepareNetworkAlias`:

```text
if (peer && meta.service == "<gateway>" && attr("http.request.header.x-canary") == "v2") {
  def peer = peer + "(v2)"
}
```

**A router that emits no spans and is not a listed gateway**, such as Istio routing on the header with the sidecars'
tracing off, leaves the callers' CLIENT spans as the only ones that dialed the address. Put the version on their
peer:

```text
if (peer && !isGateway(peer) && attr("http.request.header.x-canary") == "v2") {
  def peer = peer + "(v2)"
}
```

The canary's calls are then `a->b:8080(v2) → b-canary`, the others `a->b:8080 → b`, and each edge lands on its own
service. The SERVER block, `buildNetworkAlias` and the topology fold stay as they are, since the version travels in
the peer the caller prepared. A header canary through a gateway that does not trace is the unsupported case above.

- `!isGateway(peer)` keeps a call to a gateway listed in `gateways.yml` as it is: `ingress:80(v2)` would no longer
  match the file, so the canary's calls would skip the gateway node the others end on.
- The header has to be on the CLIENT span the rule reads, which the SDKs capture only when asked, for example
  `OTEL_INSTRUMENTATION_HTTP_CLIENT_CAPTURE_REQUEST_HEADERS=x-canary` for the OpenTelemetry Java agent. The attribute
  is `http.request.header.x-canary`.
- Match the values you route on. Appending any header value as it comes makes a new peer, and a new pair to learn,
  per value.
- With a rule on the CLIENT span, a split by weight, or by anything that span does not carry, such as a header the
  router sets itself, cannot be told apart. Trace the gateway so it dials the versions at their own addresses, or
  report the canary under the same `service.name` with its own `service.instance.id` or `service.version`, so its
  calls are `b`'s and the instances tell the versions apart.
- Rules for one layer all run, so list your file in `talFiles` (`SW_TRACE_TAL_FILES`) in place of `otlp-general`,
  not beside it, or every span is counted twice.

## Service mesh sidecars that trace

When the applications and their sidecars both export spans, the trace reads `a` CLIENT, `a`'s sidecar, `b`'s sidecar,
`b` SERVER. `b`'s SERVER span names its sidecar's span as its parent, not `a`'s CLIENT span, so no pair of analyzed
spans meets and the edge stays address-named. Labelling the sidecars' spans `service.layer=OTLP_MESH` keeps them out
of the analysis but not out of the trace, so it does not help here. Trace each hop once: either the applications,
with the sidecars' tracing off and the trace context passed through, or the mesh alone, whose topology the [service
mesh](../envoy/als_setting.md) analysis draws from access logs.

## Agent-instrumented callers

A SkyWalking agent calling an OTLP service by address writes no caller-scoped row, and the OTLP service registers no
plain-address row for it, so that edge stays drawn to the address. An OTLP caller of an agent-instrumented service is
folded at query time by the plain row the agent registers.

## A proxy that reports one span per request

The pairing matches the two halves of one call: a caller half, keyed by the id of the span that sent it, and a callee
half from a SERVER span, keyed by the id of its parent. A proxy that follows the OpenTelemetry trace API reports a
SERVER span for the request it receives and a CLIENT span for its call to the upstream, since a SERVER span does not
describe an outgoing call
([SpanKind](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#spankind)),
so each of its hops pairs through its own span, see [Instrumenting a gateway](#instrumenting-a-gateway). nginx's
OpenTelemetry modules, the official `ngx_otel_module` and the contrib `otel_ngx_module`, report one SERVER span per
request. With `otel_trace_context propagate`, that span continues the caller's trace, and nginx injects the span's own
context into the call to the upstream. Both halves then come from that one span: its callee half, keyed by its parent,
pairs with the caller's CLIENT span, and a caller half sent from it, keyed by its own id, pairs with the upstream's
SERVER span, whose parent it is. The shipped rule sends the callee half; a rule that also sends the caller half,
naming the upstream, learns both hops:

| Caller half | Callee half | Row |
|---|---|---|
| `frontend` CLIENT, to `nginx:80` | `nginx` SERVER | `frontend->nginx:80 → nginx` |
| the same `nginx` SERVER span, to its upstream `10.0.3.4:8080` | `cart` SERVER | `nginx->10.0.3.4:8080 → cart` |

The topology reads `frontend → nginx → cart`. Each upstream keys its own row, so a blue/green or weight split inside
nginx is drawn as for any traced gateway.

**On nginx**, continue the trace and pass it on, follow the caller's sampling decision, put the upstream on the span,
and give the span the layer:

```nginx
otel_service_name nginx;
otel_resource_attr service.layer OTLP_GENERAL;
otel_trace $otel_parent_sampled;
otel_trace_context propagate;

# Only a request answered by the one server it tried names its upstream; see the retries below.
map $upstream_addr $otel_upstream {
    "~^(?<otel_single_upstream>[^,\s]+)$" $otel_single_upstream;
    default "";
}
otel_span_attr upstream.address $otel_upstream;
```

- `otel_span_attr` is evaluated when the span is written, after the upstream answered, so `$upstream_addr` is known.
  The contrib module takes the same value with `opentelemetry_attribute upstream.address $otel_upstream;`, and passes
  the context on with `opentelemetry_propagate;`.
- A retried request, `proxy_next_upstream`, lists every server it tried, `10.0.3.4:8080, 10.0.3.5:8080`, and each
  attempt's SERVER span names nginx's span as its parent, so the pair cannot tell which attempt the last address
  belongs to: with a blue and a green server in one upstream, it could name the green address after blue. The map
  keeps such a request's upstream empty, so it draws no call and its attempts pair with nothing.
- `$otel_parent_sampled` follows the caller's sampling decision, so a request that arrives with no trace context, as
  at an nginx that faces browsers, is not recorded. Give such an nginx its own decision, `otel_trace on;` or a ratio
  as in the module's [README](https://github.com/nginx/nginx-otel#ratio-based-tracing). Its span then starts the
  trace, sends no callee half, and still pairs with its upstream.
- With `propagate` or `inject` but without `otel_trace`, nginx records no span and passes the context on as not
  sampled, so a service behind it whose SDK samples by its parent records nothing either.
- `upstream.address` is a name of your choice, not a semantic convention. Pick one that no application sets.

**In TAL**, in the SERVER block of your copy of `otlp-general.yaml`, pick nginx's SERVER span and send a caller half
from it as well:

```text
// nginx's SERVER span, picked by the service name `otel_service_name` set. It also made the call to the upstream.
if (meta.kind == "SERVER" && meta.service == "nginx") {
  def upstream = attr("upstream.address")
  if (upstream) {
    prepareNetworkAlias upstream
    def upstream = resolveNetworkAlias(callerScopedAddress(meta.service, upstream))
    def proxied = serviceRelation(CLIENT, meta.service, meta.layer, upstream, "OTLP_GENERAL", component("http"))
    metrics { name "service_relation_client_cpm";       scope proxied; function cpm; value 1 }
    metrics { name "service_relation_client_resp_time"; scope proxied; function avg; value meta.latency }
    metrics { name "service_relation_client_call_sla";  scope proxied; function percent
              match ok }
  }
}
```

- Instead of the service name, any tag only nginx's spans carry can pick them, such as a resource attribute set in
  the nginx config:

  ```nginx
  otel_resource_attr proxy.type nginx;
  ```

  ```text
  if (meta.kind == "SERVER" && resource("proxy.type") == "nginx") {
  ```

  With [OTLP service grouping](otlp-trace.md), `meta.service` is the grouped name, such as `shop::nginx`, so compare
  that, or use the tag. On any other service's SERVER span, the block would draw a call that was never made.
- A request nginx answers itself, a redirect or a static file, has no upstream, so it draws no call.
- The rest of the SERVER block runs as for any service, so the callers' hop to nginx is learned too.
- The relation's latency is the whole request at nginx, its own time included.
- Do not also list this nginx in `gateways.yml`.

Without the rule, treat nginx as [a gateway that does not trace](#a-load-balancer-or-gateway-that-does-not-trace):
list it in `gateways.yml`, keep the callers' `Host`, and leave `service.layer` off its spans, so they stay in the
trace but out of the analysis. The topology then reads `caller → [nginx] → service`, as long as the services behind it
report a SERVER span with a parent: the trace context has to reach them, and, where they sample by their parent,
arrive sampled. With `propagate`, an nginx that is analyzed, but neither listed nor given the rule, learns its
callers' hop and draws nothing to the services behind it, whose SERVER spans pair with nothing.
