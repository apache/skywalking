# Envoy

An Envoy that traces itself reports a SERVER span for the request it receives and a CLIENT span for its call to the
backend, so it is analyzed like any service, as [Instrumenting a
gateway](gateway-overview.md#instrumenting-a-gateway) describes. The OpenTelemetry Demo's browser calls `frontend-proxy:8080`, an Envoy that traces itself, which forwards to
`frontend` at `172.19.0.19:8080` and keeps the `Host` header, so `frontend`'s SERVER span reports
`server.address=frontend-proxy`. Each hop is learned from its own two spans:

| Caller span | Callee span | Row |
|---|---|---|
| `frontend-web` CLIENT to `frontend-proxy:8080` | `frontend-proxy` SERVER | `frontend-web->frontend-proxy:8080 → frontend-proxy` |
| `frontend-proxy` CLIENT to `172.19.0.19:8080` | `frontend` SERVER | `frontend-proxy->172.19.0.19:8080 → frontend` |

The address `frontend` reports is never used, so the proxy keeps its own node. The proxy's spans need
`service.layer=OTLP_GENERAL` too: an unanalyzed proxy sends no pairing fact, and its callers stay address-named.

Do not also list an instrumented gateway such as this one in `gateways.yml`: its address would never be aliased, and
its callers' edges would end on a `VIRTUAL_GATEWAY` node instead of on the gateway's own service.
