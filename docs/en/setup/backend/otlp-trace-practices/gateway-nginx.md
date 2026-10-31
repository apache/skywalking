# nginx

## With the OpenTelemetry module

The pairing matches the two halves of one call: a caller half, keyed by the id of the span that sent it, and a callee
half from a SERVER span, keyed by the id of its parent. A proxy that follows the OpenTelemetry trace API reports a
SERVER span for the request it receives and a CLIENT span for its call to the upstream, since a SERVER span does not
describe an outgoing call
([SpanKind](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#spankind)),
so each of its hops pairs through its own span, see [Instrumenting a
gateway](gateway-overview.md#instrumenting-a-gateway). nginx's
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

  With [OTLP service grouping](../otlp-trace.md), `meta.service` is the grouped name, such as `shop::nginx`, so compare
  that, or use the tag. On any other service's SERVER span, the block would draw a call that was never made.
- A request nginx answers itself, a redirect or a static file, has no upstream, so it draws no call.
- The rest of the SERVER block runs as for any service, so the callers' hop to nginx is learned too.
- The relation's latency is the whole request at nginx, its own time included.
- Do not also list this nginx in `gateways.yml`.

## As a gateway without instrumentation

An nginx without the rule above, or without an OpenTelemetry module at all, is a [gateway without
instrumentation](gateway-without-instrumentation.md): list it in `gateways.yml`, keep the callers' `Host` with
`proxy_set_header Host $host;`, since `proxy_pass` otherwise sends the upstream's own name, and leave `service.layer`
off its spans, if it reports any, so they stay in the trace but out of the analysis. The topology then reads `caller → [nginx] → service`, as long as the services behind it
report a SERVER span with a parent: the trace context has to reach them, and, where they sample by their parent,
arrive sampled. With `propagate`, an nginx that is analyzed, but neither listed nor given the rule, learns its
callers' hop and draws nothing to the services behind it, whose SERVER spans pair with nothing.
