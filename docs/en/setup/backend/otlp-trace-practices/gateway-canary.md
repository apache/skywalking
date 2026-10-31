# Canary Releases

`b` has a canary, `b-canary`, which reports `service.name=b-canary`; requests carrying `x-canary: v2` go to it, the
others to `b`. How the topology tells the two apart depends on what routes the requests.

**A traced gateway that dials each version at its own address** needs nothing: `gateway->10.0.1.5:8080 → b` and
`gateway->10.0.2.7:8080 → b-canary` are two rows learned from the gateway's own spans, so the topology reads
`a → gateway`, `gateway → b` and `gateway → b-canary`, whether the split is by header, weight or blue/green. This is
the setup to prefer: [instrument the gateway](gateway-overview.md#instrumenting-a-gateway).

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
the peer the caller prepared. A header canary through a gateway that does not trace is not supported, see [Gateways
Without Instrumentation](gateway-without-instrumentation.md).

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
