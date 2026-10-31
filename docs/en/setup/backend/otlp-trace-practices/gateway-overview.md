# Gateways and Proxies

The [trace analysis](../trace-analyzer.md) names a peer that a CLIENT span reaches by address, `cart:7070` or
`172.19.0.19:8080`, after the service that answered, by pairing the caller and the callee of one call: the callee's
SERVER span names the caller's span as its parent. The alias it learns is keyed by the caller and the address it dialed,
`frontend->cart:7070`. The mechanism is described under [Address-named
peers](../../../concepts-and-designs/tal.md#address-named-peers). A gateway or a proxy between the caller and the
callee changes which spans meet; this page tells which setup fits the gateway in front of your services, and the pages
next to it show how each one behaves and what to change when it does not draw what you expect.

## Which setup

| In front of the callee | Do | Topology |
|---|---|---|
| A proxy or gateway that traces itself with a SERVER and a CLIENT span per request, as Envoy | give its spans `service.layer=OTLP_GENERAL` | `caller → gateway → backend`, each hop learned from its own spans, see [Envoy](gateway-envoy.md) and [Instrumenting a gateway](#instrumenting-a-gateway) |
| A proxy that reports one span per request, as nginx with its OpenTelemetry module | propagate the context, put the upstream on its span, and add a rule on its SERVER span | `caller → nginx → backend`, see [nginx](gateway-nginx.md#with-the-opentelemetry-module) |
| A gateway that does not trace, routing by host, or by path to distinct services | list it in [uninstrumented gateways](../uninstrumented-gateways.md) | `caller → [gateway] → service`, an edge per service, see [Gateways Without Instrumentation](gateway-without-instrumentation.md) |
| A gateway that does not trace and splits one address between versions, by blue/green, weight or header | not supported: [instrument the gateway](#instrumenting-a-gateway) | as for a gateway that traces itself |
| A canary behind a traced gateway that dials `b` and `b-canary` at their own addresses | nothing | `gateway → b` and `gateway → b-canary`, see [Canary Releases](gateway-canary.md) |
| A canary behind a traced gateway that dials both at one address, a router behind it splitting them | a rule on the gateway's CLIENT spans | `gateway → b` and `gateway → b-canary`, see [Canary Releases](gateway-canary.md) |
| A canary chosen by a header, through a router that emits no spans and is not listed, as Istio with the sidecars' tracing off | the header rule on the callers' CLIENT spans | `a → b` and `a → b-canary`, see [Canary Releases](gateway-canary.md) |

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
  request pairs only one of the two hops, unless a rule also sends a caller half from that span, see
  [nginx](gateway-nginx.md#with-the-opentelemetry-module).
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
does, see [Canary Releases](gateway-canary.md).

The OpenTelemetry Demo's `frontend-proxy`, an Envoy that traces itself, does all four, see [Envoy](gateway-envoy.md).
Its spans get `service.layer` from the demo's Collector.
