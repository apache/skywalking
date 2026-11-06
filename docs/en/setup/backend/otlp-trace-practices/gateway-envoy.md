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

Envoy reports the CLIENT span only with `spawn_upstream_span: true` in the HTTP connection manager's `tracing`, as the
demo's `frontend-proxy` sets it. The default is `false`, and Envoy then reports one span per request: the hop from its
caller pairs, the hop to the backend does not, and the backend draws no edge from Envoy. Envoy Gateway sets it on the
proxies it configures. Envoy's resource names no instance either, so its instances are listed only when it is given a
`service.instance.id`; with the `environment` resource detector, both come from `OTEL_RESOURCE_ATTRIBUTES`:

```yaml
tracing:
  spawn_upstream_span: true
  provider:
    name: envoy.tracers.opentelemetry
    typed_config:
      "@type": type.googleapis.com/envoy.config.trace.v3.OpenTelemetryConfig
      grpc_service:
        envoy_grpc: { cluster_name: oap }      # the OAP's gRPC port, 11800, or a Collector
      service_name: envoy
      resource_detectors:
        - name: envoy.tracers.opentelemetry.resource_detectors.environment
          typed_config:
            "@type": type.googleapis.com/envoy.extensions.tracers.opentelemetry.resource_detectors.v3.EnvironmentResourceDetectorConfig
```

```shell
OTEL_RESOURCE_ATTRIBUTES=service.layer=OTLP_GENERAL,service.instance.id=envoy-0
```

Do not also list an instrumented gateway such as this one in `gateways.yml`: its address would never be aliased, and
its callers' edges would end on a `virtual gateway` node instead of on the gateway's own service.
