# TAL for OpenTelemetry Spans

A rule with `inputType: otlp`, the default, analyzes the spans that arrive over the OpenTelemetry protocol. It is
written in the [Trace Analysis Language](tal.md); this page describes what only this input reads and builds. The
files are listed in `trace-analyzer/default/talFiles` (`SW_TRACE_TAL_FILES`); see [OpenTelemetry Trace
Analysis](../setup/backend/trace-analyzer.md) for the module's settings and for what the shipped rule file produces.

## Span metadata

Every rule reads the span through two roots. `meta` holds what the receiver derived from the span and its resource:

| `meta` field | Source on an OTLP span |
|---|---|
| `service` | the service name the receiver resolved: the resource's `service.name`, or the fallbacks in [Service name](../setup/backend/otlp-trace.md#service-name), in the `group::name` form when [service grouping](../setup/backend/otlp-trace.md#service-group) applies |
| `serviceInstance` | the resource's `service.instance.id`, else its `service.instance`; empty when it carries neither |
| `endpoint` | the span's name, until a rule sets it |
| `layer` | the resource's `service.layer`, empty when absent; see [Layer](#layer) |
| `kind` | `SERVER`, `CLIENT`, `PRODUCER`, `CONSUMER`, `INTERNAL` or `UNSPECIFIED` |
| `statusCode`, `statusMessage` | `UNSET`, `OK` or `ERROR`, and the status message |
| `startTime`, `timestamp` | the span's start and end, in milliseconds; `startTime` is the minute bucket the metrics land in. A time the sender left at 0 is replaced by the other one, and both by the time the span arrived when both are 0 |
| `latency` | end minus start, in milliseconds; 0 when the end is before the start, and at most `2147483647` (`Integer.MAX_VALUE`) |
| `traceId`, `spanId`, `parentSpanId` | the ids in lowercase hex; `parentSpanId` is empty on a root span |
| `root` | whether `parentSpanId` is empty |
| `parentLocation` | where the parent span ran, from the OTLP span flags: `REMOTE` in another process, `LOCAL` in the same one, `UNKNOWN` on a root span or when the sender leaves the flags unset |
| `scopeName`, `scopeVersion` | the instrumentation scope |
| `traceStorage` | how the receiver stores the span, its [`otlpTraceStorage`](../setup/backend/otlp-trace.md): `otlp` natively, `zipkin` as a Zipkin span |

`span` is the wire object itself, `io.opentelemetry.proto.trace.v1.Span`, resolved at compile time: `span.name`,
`span.traceState`, `span.status.message`, `span.eventsCount`. Prefer `meta.kind` and `meta.statusCode` over the
proto enums in conditions.

## Layer

A span whose resource carries `service.layer` is evaluated by the rules declared for that layer, and by nothing
else. A span whose resource carries a layer name that is not registered is logged and not analyzed, and so is a span
without `service.layer`. There is no fallback to `GENERAL`, which is the agent layer. The shipped rules are declared
for `OTLP_GENERAL`, so they analyze only the spans whose resource carries `service.layer=OTLP_GENERAL`, and put their
conjectured peers under `OTLP_VIRTUAL_DATABASE`, `OTLP_VIRTUAL_CACHE`, `OTLP_VIRTUAL_MQ` and
`OTLP_VIRTUAL_GATEWAY`, see [the setup doc](../setup/backend/trace-analyzer.md#layers).

A sender that cannot be changed gets its `service.layer` from the OpenTelemetry Collector instead, with a `resource`
processor that inserts it.

A span no rule claims, one every rule aborted or one on a layer no rule is declared for, sends no pairing fact, see
[Address-named peers](#address-named-peers). An Envoy sidecar's spans, unclaimed, therefore never become the callee
of an alias or the caller of one, and spare the cluster the pairing traffic.

Rules for one layer run in file order, each with its own copy of `meta`, so a rule that overwrites `service` does not
change what the next rule reads. Each rule decides independently.

## Filter

```text
filter {
  <def / assignment / if / foreach / abort / log>*
  extractor { ... }
  persist { ... }                                     // the span itself
}
```

An `extractor` or `persist` block may sit inside a filter-level `if`, but not inside one another. `abort {}` inside
either of them stops the whole rule for this span: it adds no tag, sends no pairing fact and votes nothing. A
`metrics {}` block or a `source {}` that ran before the `abort {}` has already sent its value, and keeps it.

### Functions of the OTLP input

Beside the [functions for every input](tal.md#functions-for-every-input), an OTLP rule reaches the span's attributes
and the rows its pairing writes through these functions, all null-safe and returning `""` when absent:

| Function | Returns |
|---|---|
| `attr(key, ...)` | the first present span attribute among the keys, rendered as a string: scalars as text, arrays joined by `,`, key-value lists as JSON, bytes as base64. Several keys let one rule serve SDKs on different semantic-convention versions: `attr("db.system.name", "db.system")` |
| `hasAttr(key, ...)` | whether any of the keys is present on the span |
| `resource(key, ...)` | the same over the resource attributes: `resource("telemetry.sdk.language")` |
| `scopeAttr(key, ...)` | the same over the instrumentation scope's attributes |
| `event(name)` | the first span event with that name, or `null`: `event("exception") != null` |
| `eventAttr(name, key, ...)` | an attribute of that event: `eventAttr("exception", "exception.type")` |
| `callerScopedAddress(service, peer)` | the key of a network address alias learned for one caller and one peer, `service->peer`, see [Address-named peers](#address-named-peers) |
| `resolveNetworkAlias(address)` | the service a caller-scoped address names, see [Address-named peers](#address-named-peers); when none is learned yet, the peer part of the address, or the address unchanged when it has no `->` |
| `findServerClusterName(address)`, `findServerClusterName(address, kind)` | the cluster the core's [server clusters](../setup/backend/server-clusters.md) list the address under: the whole string, or every element of a list joined by `,` or `;`, in any order; empty when the address is not listed. With a kind, only the clusters whose `kinds` list it, or list none, match. The shipped rule asks it, without a kind, for the address of a database, a cache or a broker |

`attr`, `hasAttr`, `resource` and `scopeAttr` take any number of keys. A span's attributes are indexed once per span,
however many rules and lookups read them. The shipped rules also call `component(...)`, `urlHost(...)` and
`isGateway(...)`, which every input has; `isGateway`'s limits on this input are listed under [Address-named
peers](#address-named-peers).

### Extractor

```text
extractor {
  <def / assignment / if / foreach / abort / log>
  service | instance | endpoint | layer <expr>   // overwrite meta.*
  tag '<key>': <expr>                       // appended to the stored span as a string attribute
  prepareNetworkAlias <expr>                // CLIENT, or a one-span proxy's SERVER: the peer it dialed
  buildNetworkAlias <expr>                  // SERVER: the alias address, composed from pairing.service and pairing.peer
  metrics { ... }                           // see the language page
  source <Name> { ... }                     // a record or another source, sent at the statement, see Records
}
```

A `metrics {}` block's `timestamp` defaults to `meta.startTime`.

### Persist

`persist {}` decides the fate of the span itself. Its statements are spelled as LAL's sink statements,
`sampler { rateLimit("<id>") { rpm <n> } }`, `dropper {}`, `enforcer {}` and `if`, but they vote rather than overwrite
one another: `dropper {}` wins over `enforcer {}`, and `enforcer {}` over the samplers, in either order, where in LAL
the last to run wins. They decide the span, so they are a compile error anywhere but inside `persist {}`. A
`rateLimit` budget is shared by id across every rule and span: the first rule to use an id sets its `rpm`. An id
unused for ten minutes is forgotten, and beyond 10,000 ids the least recently used is forgotten too, which bounds the
ids a `${...}` placeholder makes; an id forgotten and used again starts with a fresh budget.

Storing the incoming span is the receiver's default and most rules only want metrics, so a rule without `persist {}`
abstains. A rule with `persist {}` votes: `dropper {}` and an unsampled `rateLimit` vote drop, `enforcer {}` votes keep
and marks the span enforced, past this rule's samplers before or after it but not past its `dropper {}`, and an empty
`persist {}` votes keep. The span is stored when every rule that voted said keep and no span listener vetoed; a drop
vote from one rule therefore beats an `enforcer {}` in another, and a rule that aborted did not vote.

Two things follow from the span being the unit:

- `rateLimit` counts spans, not traces: two spans of one trace can fall on different sides of the budget and a
  stored trace can have spans missing. A rule that needs whole traces keys its decision on something every span of
  the trace carries, or accepts partial traces.
- `enforcer {}` also bypasses the receiver's head sampling. TAL votes before `otlpTraceSampleRate` is applied, and an
  enforced span skips the trace-id check, the way a Zipkin `debug` span does. It does not skip
  `otlpTraceMaxSpansPerSecond`, which stays the operator's ceiling. With `otlpTraceStorage: zipkin` the span is stored
  as a Zipkin `debug` span instead, which the Zipkin receiver keeps past both its `sampleRate` and its
  `maxSpansPerSecond`. The bypass is per span, so at a low sample rate an enforced slow statement is stored while the
  rest of its trace may not be, and an `enforcer {}` on a broad condition turns head sampling off for everything it
  matches; it belongs behind a condition that is rare by construction, an error status or a latency threshold.

## Records

A rule writes a record as a [`source {}`](tal.md#source-existing-system-adoption) in its extractor, sent at its
statement, so the `if` and `abort {}` before it decide which spans write one. The shipped `otlp-slow-sql` rule writes
`DatabaseSlowStatement`:

- `id` is `meta.traceId + "-" + meta.spanId`, since a span id is unique only within its trace;
- `traceId`, `latency` and `timestamp` are `meta`'s, and `timeBucket` is `recordTimeBucket(meta.timestamp)`;
- `statement` is the statement cut to 2,000 characters, the length of the statement column;
- `databaseServiceId` is `buildServiceId(formatServiceName(peer), false)`, the id of the database's node in the
  topology, which the rule names as `otlp-general` names the peer of a CLIENT span, in the `OTLP_VIRTUAL_DATABASE`
  layer, which is not a normal one. The statement shows on that node's slow-statement panel.

The source is handed to the receiver at its statement, before the span's storage is decided. A failure while it is
dispatched fails the rule, as any failure does, see [Failures](#failures): the rule's `persist {}` vote is then not
counted, and the span's other rules still run.

## Address-named peers

A CLIENT span names its peer by what it dialed, `server.address` and `server.port`, and the callee names itself by
`service.name`; nothing on the OTLP wire ties the two, since the trace context carries no address. Without help, a
fleet that does not set `service.peer.name` gets `product-catalog:3550` beside `product-catalog` in the service list and
in the topology. TAL folds the address onto the service from the trace context alone, by strict span pairing: a
SERVER span's `parentSpanId` is the span id of the caller's span that made the call, so the caller's half and the
callee's half of one call name the caller, the address it dialed and the service that answered. Nothing is inferred
from a host name, a DNS label or a Kubernetes namespace.

```text
filter {
  if (meta.kind == "SERVER") {
    extractor { buildNetworkAlias callerScopedAddress(pairing.service, pairing.peer) }
  }
  if (meta.kind == "CLIENT") {
    extractor {
      def peer = attr("server.address") + ":" + attr("server.port")
      prepareNetworkAlias peer
      peer = resolveNetworkAlias(callerScopedAddress(meta.service, peer))
    }
  }
}
```

- **`prepareNetworkAlias <peer>`** sends the caller half of a call, the span's service and the peer, to the trace's
  owner. The half is keyed by the span's own id, so it pairs with the callee half of the SERVER span whose parent that
  span is. A CLIENT span sends it, and so may a SERVER span that passed its own context to the upstream, as a proxy
  reporting one span per request does: such a span sends its callee half for the hop from its caller, when it has one,
  and a caller half for the hop to its upstream, see
  [nginx](../setup/backend/otlp-trace-practices/gateway-nginx.md#with-the-opentelemetry-module). A PRODUCER or
  CONSUMER span is ignored: a CONSUMER span's parent is the PRODUCER span, and pairing them would name the broker's
  address after the consumer. Every call in the sampled share of traces sends its half,
  and every pair that meets pushes its row; the row decides whether storage writes it, at once when the mapping
  changed and once per five minutes when it did not, so an address that moves to another service is followed at its
  next sampled call. A peer that never answers with a SERVER span, an external API or an uninstrumented service,
  costs one message per sampled call, which waits out the buffer unmatched. The statement only names the peer: the
  half is sent once the span's rules have run, by the first rule that was not aborted, as the callee half is, so an
  `abort {}` anywhere in the rule withdraws it and a span sends at most one caller half.
- **`buildNetworkAlias <address>`** marks a SERVER span with a remote parent as the callee half. The expression is
  not evaluated on the span: it runs on the trace's owner when the two halves match, and reads the caller half only,
  through `pairing`, a variable that exists only in this expression and is never declared with `def`:
  `pairing.service` is the caller's `meta.service` and `pairing.peer` the value its `prepareNetworkAlias` sent. They
  are joined with literals, `+` and `callerScopedAddress`. Reading anything else, such as `meta`, an attribute or
  another field of `pairing`, or declaring `buildNetworkAlias` twice in one rule, is a compile error. The first rule
  that claims the span and asks for an alias names the fact.

  > **Warning:** keep the address `callerScopedAddress(pairing.service, pairing.peer)`, as the shipped rule does.
  > `resolveNetworkAlias(callerScopedAddress(...))` and the topology query both look the row up by
  > `service->peer`, so any other address is built but never read. An address without the caller,
  > such as `buildNetworkAlias pairing.peer`, is worse: it is the plain-address form the agent analysis writes and
  > the topology query falls back to for every caller, so it overwrites the agent's alias of that address, the last
  > write winning, and hands a proxy's address to whichever service last answered behind it.
- **`callerScopedAddress(service, peer)`** is `service->peer`, both parts formatted as the service names are. The
  alias row is keyed by it, so one address dialed by two callers can name two services, as two namespaces' frontends
  that both dial `cart:7070` reach their own carts.
- **`resolveNetworkAlias(address)`** answers the service the row names, from the core's alias cache, which reloads
  every 10 seconds; when no row is there yet it answers the peer part, so the edge stays address-named until the row
  has been stored and reloaded, about 35 seconds after the first pair.

In a cluster the facts of one trace are routed to the node that owns the trace, so the two halves meet wherever each
arrived; a share of the traces, `pairingSamplePercent`, is paired, see [the setup
doc](../setup/backend/trace-analyzer.md#topology). Facts wait 60 seconds for their other half. A row is built for
every matched pair except one whose peer is a gateway; a call to another replica of the caller is drawn as a loop; an
address longer than the alias column's 200 characters is counted and logged, not built, and so is a pair whose
callee's rule is missing on the owner or fails to compose the address. The row names no instance, and the last pair
written wins.

What the pairing costs, and what it cannot see:

- **Each caller and peer is learned on its own.** A new caller of a known service is address-named until one of its
  own calls is paired, about four calls at the default 25% sample. Metrics recorded before then stay under the
  address-named relation; the topology query folds that node into the service once the row lands, since it asks for
  the caller's own row before the plain address.
- **The callee must be analyzed.** A callee that sends no spans, has no rule claiming its SERVER spans, or drops the
  trace context leaves its callers address-named, which is the honest answer. An uninstrumented load balancer in front
  of a fleet is listed in [uninstrumented gateways](../setup/backend/uninstrumented-gateways.md) instead.
- **One tracer per hop.** When a mesh sidecar traces as well as the applications, the application's CLIENT span is the
  parent of the sidecar's span, not of the callee's SERVER span, so no pair of analyzed spans meets. Trace each hop
  once, or use the [service mesh](../setup/envoy/als_setting.md) topology for the mesh.
- **Agent callers are not folded.** An agent-instrumented caller of an OTLP service registers no caller-scoped row,
  and its address-named edge stays as the agent analysis records it.

The best practices walk through common deployments: [gateways and
proxies](../setup/backend/otlp-trace-practices/gateway-overview.md), uninstrumented gateways and canary releases
among them, and [message queues](../setup/backend/otlp-trace-practices/message-queue.md). Two namespaces of one
application are shown under [Topology](../setup/backend/trace-analyzer.md#topology).

The topology query folds an address-named node into the service an alias names, the caller's own row first and the
plain address second. It folds a node that is not a registered service, or a self-call whose alias names another
service, the shape a caller that dials its downstream by its own service name records until the downstream's alias
lands. Other registered nodes keep their own names even when the name equals some alias address, as an OTLP
`frontend` does while an agent has registered the host `frontend` for `agent::frontend`.

An address listed in the agent analyzer's [uninstrumented gateways](../setup/backend/uninstrumented-gateways.md) is
never built by a pair either. The shipped rule does not resolve such a peer: when `isGateway` says
it is one, the gateway stays a `virtual gateway` node of its own, as on the agent path, which the caller's edge ends on
and the callee's edge starts from. `isGateway` has these limits:

- It matches the address exactly as `gateways.yml` lists it, `host:port`, with a bare host tried at port 80, the port
  the file assumes for an instance that names none. Nothing is resolved or folded the way `resolveNetworkAlias` does it: a
  gateway listed as `ingress:80` is not `ingress.default.svc.cluster.local:80`, `10.0.0.5:80` or `ingress:443`, so
  every spelling and port the callers dial has to be listed.
- A peer named by `service.peer.name` is a name, not an address, and is a gateway only if that name is listed as a
  host at port 80.
- The callee's SERVER span sees the gateway's address only when the gateway passes its callers' `Host` through, or
  sets `X-Forwarded-Host` for an SDK that reads it. A gateway that rewrites the `Host` to the upstream's still ends
  its callers' edges, but no edge leaves it; see [Topology](../setup/backend/trace-analyzer.md#topology) in the
  operator guide. A root SERVER span draws no edge from the gateway either: the trace context says nobody called
  it, so its call is `User`'s, as on the agent path.
- The shipped rule draws a listed peer as a gateway for any CLIENT span except a database or messaging one, gRPC
  included. The agent path does that for HTTP exit spans only.
- It is always false when the `agent-analyzer` module is not loaded, since that module owns `gateways.yml`. A change
  made through [dynamic configuration](../setup/backend/dynamic-config.md) applies to the next span.

The source of a client edge is always the span's own `service.name`; a span emitted under a pseudo-service named after
a backend, as HotROD's `mysql` spans are, is described under [Topology](../setup/backend/trace-analyzer.md#topology) in
the operator guide.

## Retries

An exporter retries a batch after a transport timeout, so a span can arrive twice. With `dedupWindow` above 0 a span
seen again within the window is analyzed once; see [Deduplication](../setup/backend/trace-analyzer.md#deduplication).

## Failures

A rule that fails on a span, by an exception its expressions or functions raise, is skipped for that span, and the
span's other rules still run. The first failure of a rule logs a warning, `TAL rule {} failed on a span and is skipped
for it; its later failures are logged at debug`, with the exception, and the later ones log at debug.
