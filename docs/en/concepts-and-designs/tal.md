# Trace Analysis Language

Trace Analysis Language (TAL) in SkyWalking is a Domain-Specific Language (DSL) to analyze the spans that arrive over
the OpenTelemetry protocol. A TAL rule reads one span at a time and decides what it is, which service, instance and
endpoint it belongs to, which metrics it feeds, which record it writes, and whether the span itself is stored. The
metrics go through the meter system, so they are stored, downsampled, queried and alerted on like every meter
metric.

The TAL config files are in YAML format, and are located under directory `tal`. You can set
`trace-analyzer/default/talFiles` in the `application.yml` file or set environment variable `SW_TRACE_TAL_FILES` to
activate specific TAL config files. The files are read when the OAP starts. See
[OpenTelemetry Trace Analysis](../setup/backend/trace-analyzer.md) for the module's settings and for what the shipped
rule file produces.

Anyone who has written a [LAL](lal.md) rule can read a TAL rule: the grammar is LAL's, with the log-specific parts
removed and span-specific parts added. The differences are listed at the end of this page.

## Rule file

```yaml
# tal/<file>.yaml
metricPrefix: otlp     # every metric declared in this file is stored as <metricPrefix>_<name>
layerDefinitions:      # optional, the same contract as LAL and MAL: registered before the rules compile
  - name: IOT_FLEET
    ordinal: 1000
    normal: true
rules:
  - name: otlp-general # unique within its layer
    layer: OTLP_GENERAL # a registered layer name
    inputType: otlp    # optional; the only input this release ships, and the default
    outputType: SlowSQL # optional; the record the rule writes, see Output Type
    dsl: |
      filter { ... }
```

A rule names exactly one `inputType`. The OAP refuses to start on a rule whose `layer` is not registered, naming the
rule and the layer; declare the layer under `layerDefinitions` or use a built-in one.

## Span metadata

Every rule reads the span through two roots. `meta` holds what the receiver derived from the span and its resource,
and it means the same thing whatever the input:

| `meta` field | Source on an OTLP span |
|---|---|
| `service` | the service name the receiver resolved: the resource's `service.name`, or the fallbacks in [Service name](../setup/backend/otlp-trace.md#service-name), in the `group::name` form when [service grouping](../setup/backend/otlp-trace.md#service-group) applies |
| `serviceInstance` | the resource's `service.instance.id` |
| `endpoint` | the span's name, until a rule sets it |
| `layer` | the resource's `service.layer`, empty when absent; see [Layer](#layer) |
| `kind` | `SERVER`, `CLIENT`, `PRODUCER`, `CONSUMER`, `INTERNAL` or `UNSPECIFIED` |
| `statusCode`, `statusMessage` | `UNSET`, `OK` or `ERROR`, and the status message |
| `startTime`, `timestamp` | the span's start and end, in milliseconds; `startTime` is the minute bucket the metrics land in |
| `latency` | end minus start, in milliseconds |
| `traceId`, `spanId`, `parentSpanId` | the ids in lowercase hex; `parentSpanId` is empty on a root span |
| `root` | whether `parentSpanId` is empty |
| `parentLocation` | where the parent span ran, from the OTLP span flags: `REMOTE` in another process, `LOCAL` in the same one, `UNKNOWN` on a root span or when the sender leaves the flags unset |
| `scopeName`, `scopeVersion` | the instrumentation scope |

`span` is the wire object itself, `io.opentelemetry.proto.trace.v1.Span`, resolved at compile time: `span.name`,
`span.traceState`, `span.status.message`, `span.eventsCount`. Prefer `meta.kind` and `meta.statusCode` over the
proto enums in conditions.

## Layer

A span whose resource carries `service.layer` is evaluated by the rules declared for that layer, and by nothing
else. A span whose resource carries a layer name that is not registered is logged and not analyzed, and so is a span
without `service.layer`. There is no fallback to `GENERAL`, which is the agent layer. The shipped rules are declared
for `OTLP_GENERAL`, so they analyze only the spans whose resource carries `service.layer=OTLP_GENERAL`, and put their
conjectured peers under `OTLP_VIRTUAL_DATABASE`, `OTLP_VIRTUAL_CACHE` and `OTLP_VIRTUAL_MQ`, see [the setup
doc](../setup/backend/trace-analyzer.md#layers).

A sender that cannot be changed gets its `service.layer` from the OpenTelemetry Collector instead, with a `resource`
processor that inserts it.

A span no rule claims, one every rule aborted or one on a layer no rule is declared for, sends no pairing fact, see
[Address-named peers](#address-named-peers). An Envoy sidecar's spans, unclaimed, therefore never become the callee
of an alias or the caller of one, and spare the cluster the pairing traffic.

Rules for one layer run in file order, each with its own copy of `meta`, so a rule that overwrites `service` for its
record does not change what the next rule reads. Each rule decides independently.

## Filter

```text
filter {
  <if / def / abort>*
  extractor { ... }
  sink { ... }                                        // the record declared by outputType
  persist { ... }                                     // the span itself
}
```

### Global functions

Attributes are reached through functions, all null-safe and returning `""` when absent:

| Function | Returns |
|---|---|
| `attr(key, ...)` | the first present span attribute among the keys, rendered as a string: scalars as text, arrays joined by `,`, key-value lists as JSON, bytes as base64. Several keys let one rule serve SDKs on different semantic-convention versions: `attr("db.system.name", "db.system")` |
| `hasAttr(key, ...)` | whether any of the keys is present on the span |
| `resource(key, ...)` | the same over the resource attributes: `resource("telemetry.sdk.language")` |
| `scopeAttr(key, ...)` | the same over the instrumentation scope's attributes |
| `event(name)` | the first span event with that name, or `null`: `event("exception") != null` |
| `eventAttr(name, key, ...)` | an attribute of that event: `eventAttr("exception", "exception.type")` |
| `component(name)` | the component id from `component-libraries.yml`, matched exactly and then ignoring case, so `attr("rpc.system.name", "rpc.system")` giving `grpc` finds `GRPC` and `postgresql` finds `PostgreSQL`; `0`, the `Unknown` component, when nothing matches |
| `callerScopedAddress(service, peer)` | the key of a network address alias learned for one caller and one peer, `service->peer`, see [Address-named peers](#address-named-peers) |
| `resolveNetworkAlias(address)` | the service a caller-scoped address names, see [Address-named peers](#address-named-peers); when none is learned yet, the peer part of the address, or the address unchanged when it has no `->` |
| `isGateway(address)` | whether the address is listed in the agent analyzer's [uninstrumented gateways](../setup/backend/uninstrumented-gateways.md), matched exactly, a bare host at port 80; always false when the `agent-analyzer` module is not loaded. Its limits are listed under [Address-named peers](#address-named-peers) |
| `urlHost(url)` | the authority of a URL, `host` or `host:port`, without scheme, user info, path, query or fragment; empty when the value has none, which a value without a `scheme://` prefix, such as a bare path, does not. For a client that names its target only as `url.full` or `http.url` |
| `toJson(expr)`, `toJsonArray(expr)` | as in LAL |

`attr`, `hasAttr`, `resource` and `scopeAttr` take any number of keys. A method can follow a function that returns
a string, `attr("x").trim()`.

A span's attributes are indexed once per span, however many rules and lookups read them.

### Expressions

An expression starts with `span`, `meta`, a `def` variable, a function or a literal; any other first name, such as a
misspelled `mta.kind`, is a compile error. A string literal is plain text in `"` or `'`: a backslash escapes only
the quote, `'` and itself, so `"a\"b"` is the three characters `a"b` and `"C:\temp"` keeps its backslash, and there is
no `\n` or `\t`. Numbers are Java's, `1`, `10L`, `1.5`; and `true`, `false` and `null` are literals. A numeric field
of `meta`, such as `meta.latency`, is a number, so `meta.latency + 1` adds.

A comparison is decided by the types of its two sides when the rule compiles. There is no implicit parse and no
implicit `toString`; `as T` is the only conversion:

| Left and right | Allowed | Compared as |
|---|---|---|
| number and number | every operator | numbers, so `110 == 110.0` is true and an `int` compares with a `long` |
| boolean and boolean, String and String, two values of one type | `==`, `!=` | equal values |
| a value that can be absent and `null` | `==`, `!=` | a null check |
| a value whose type is known only when the rule runs, such as `event(...)`, and a string literal | `==`, `!=` | equal values; an event never equals a string, so compare `event(...)` with `null` |
| anything else, such as `attr("x") > 400` or `meta.latency == "5"` | | a compile error naming both types |

`attr(...)` returns a String, so `attr("http.response.status_code") as Integer >= 400` orders it as a number. An
`as T` that cannot convert its value, `"abc" as Integer` or an absent attribute, makes that comparison false
whatever the operator, so `!(attr("code") as Integer >= 400)` is true for a value that is not a number.

A `def` is one variable for the whole rule, declared by its first `def` and assigned by the later ones, so each value
it is given has to fit the type its first value had. A filter-level `def` is in scope inside the `extractor`, and a
`def` first given a value inside a branch is `null` after it on the paths that did not give it one.

### Extractor

```text
extractor {
  def <name> = <expr> [as <Type>]           // any value, including an entity from the metrics block
  service | instance | endpoint | layer <expr>   // overwrite meta.*; also the record's standard fields
  <field> <expr> [as <Type>]                // an output field of the outputType, see Output Type
  tag '<key>': <expr>                       // appended to the stored span as a string attribute
  prepareNetworkAlias <expr>                // CLIENT, or a one-span proxy's SERVER: the peer it dialed
  buildNetworkAlias <expr>                  // SERVER: the alias address, composed from pairing.service and pairing.peer
  metrics { ... }                           // see below
  if (...) { ... } else if (...) { ... } else { ... }
  abort {}                                  // stop this rule for this span; it writes nothing and votes nothing
}
```

`abort {}` is allowed anywhere: at filter level and inside `extractor`, `sink` and `persist`, and it stops the whole
rule, the statements after the block it sits in included. A statement after it in the same block could never run
and is a compile error. An `extractor`, `sink` or `persist` block may sit
inside a filter-level `if`, but not inside one another.

### `metrics`

A `metrics {}` block declares one metric and emits one value for this span. The name, the function and the entity
constructor are literals, so every metric a file declares is known when it compiles and its storage is created
then.

```text
metrics {
  name "<metric>"                    // literal; stored as <metricPrefix>_<metric>
  scope <entity>                     // an entity constructor, or a `def` holding one
  function <function>                // literal, see the table
  value <expr> [as <Type>]           // the observation; a number, or a string that parses as one. Not used by percent
  [match <condition>]                // percent only, required: the span counts as a match when true
  [status <condition>]               // apdex only, required: the span counts as successful when true
  [labels k: v, ...]                 // required by the *Labeled functions, forbidden otherwise
  [labelLimit n]                     // *Labeled functions only; the label sets one row keeps per minute, 50 by default
  [buckets [n, n, ...]]              // percentile only, required; whole numbers, ascending, in the unit of `value`
  [ranks [50, 75, 90, 95, 99]]       // percentile only; whole numbers in 1..100, ascending; this is the default
  [timestamp <expr> [as <Type>]]     // milliseconds; default meta.startTime, the span's start, as the agent path buckets a call
}
```

A numeric cast on `value` or `timestamp` converts before the number is taken, so `value attr("amount") as Double`
reads `"1.7"` as 1 rather than failing to parse it. Two blocks may feed one metric, from two branches of an `if`
for instance, when they agree on its function, scope, buckets and ranks; any difference is a compile error. The same
holds across rules and files, two files sharing a `metricPrefix` included: blocks of one stored name that disagree
fail the rule that loads second, and so the boot. A label
value is stored with `|` and `,` replaced by `_`, since those two characters separate the stored labels.

Every label set of a labeled metric is an entry in one stored row per entity per minute, so its labels should take a
few values, as a status code or a method does, and never a URL, an id or a statement. A row keeps at most
`labelLimit` label sets, 50 by default as OAL's `labelCount` keeps; once it is full, a new label set is not counted,
while the label sets it already holds keep counting.

The entity constructors, usable inline in `scope` or in a `def`. Every name passes through the core module's naming
rules, as a meter entity's does:

| Constructor | Entity |
|---|---|
| `service(name, layer)` | a service in the layer |
| `instance(service, instance, layer)` | a service instance |
| `endpoint(service, endpoint, layer)` | an endpoint |
| `serviceRelation(CLIENT \| SERVER, source, sourceLayer, dest, destLayer, componentId)` | a call between two services, each in its own layer; `componentId` is an `int`, normally `component(...)` |
| `serviceInstanceRelation(CLIENT \| SERVER, sourceService, sourceInstance, sourceLayer, destService, destInstance, destLayer, componentId)` | a call between two instances |

A `layer` argument is a string expression. A name that is not registered skips the block, with one warning per
name; a `value` that does not parse as a number, or any other failure inside a block, skips that block alone, with one warning per metric, and the rule's later blocks still run. The value is read and the metric built before the
block registers its entity, so a skipped block, one whose metric the meter system could not create included, writes
no traffic. An entity held in a `def` has one
shape: reassigning it with a different constructor is a compile error.

The functions are the meter system's, named for what they do to the values of one minute:

| `function` | Value | Meaning per minute per entity |
|---|---|---|
| `cpm` | number | the total of the values divided by the bucket's length in minutes, so hour and day rows read as a rate; `value 1` is calls per minute. The same number `core.oal`'s `cpm` computes; `sumPerMin` is accepted as an alias |
| `sum` | number | the raw total; at hour and day level the totals add rather than average |
| `avg` | number | the mean; `value meta.latency` is mean latency |
| `max`, `min`, `latest` | number | |
| `percent` | `match` condition | `matches * 10000 / total`, the `core.oal` `percent` scale; `match meta.statusCode != "ERROR"` is the success rate |
| `apdex` | `value` and `status` | the `core.oal` `apdex`: thresholds from `service-apdex-threshold.yml` looked up by the entity's service name as stored, after the service name length limit, satisfied under `T`, tolerated under `4T`; a relation scope is a compile error |
| `sumLabeled`, `avgLabeled`, `maxLabeled`, `minLabeled`, `latestLabeled`, `sumPerMinLabeled` | number, by `labels` | one series per label set; `labels status: attr("http.response.status_code", "http.status_code")` splits a count by status code |
| `percentile` | number, by `buckets` | the observation counted in the first bucket whose bound is not below it, so each bound is its bucket's upper edge, as MAL keys OTel's `le`; a value above the last bound counts in an overflow bucket keyed `9223372036854775807` (`Long.MAX_VALUE`), the key MAL gives OTel's `+Inf` bucket. The buckets are reduced to the `ranks` when the minute is persisted; a rank reads as the upper edge of the bucket it falls in, and a rank that falls in the overflow bucket reads `9223372036854775807` |

With `cpm`, `percent` and `apdex` a TAL metric named like an agent metric holds the same number in the same unit, so
an alarm rule or a dashboard widget written for `service_sla` ports to `otlp_service_sla` by renaming the metric.

### Sink

`sink {}` persists the record the rule builds. A rule with an `outputType` needs one, and a `sink {}` in a rule with no
`outputType` is a compile error. The record is written only when a `sink {}` ran, so `if (c) { sink {} }` writes it
only for the spans where `c` holds. Its statements are spelled as LAL's, `sampler { rateLimit("<id>") { rpm <n> } }`,
`dropper {}`, `enforcer {}` and `if`, but they vote rather than overwrite one another, see
[Differences from LAL](#differences-from-lal). They decide a record or a span, so they are a compile error anywhere but
inside `sink {}` or `persist {}`. A `rateLimit` budget is shared by id across every rule
and span: the first rule to use an id sets its `rpm`. An id unused for ten minutes is forgotten, and beyond 10,000
ids the least recently used is forgotten too, which bounds the ids a `${...}` placeholder makes; an id forgotten and
used again starts with a fresh budget. `enforcer {}` keeps the record whatever the rule's samplers say, before it or
after it; `dropper {}` drops it whatever an `enforcer {}` says, before it or after it.

### Persist

`persist {}` decides the fate of the span itself, with the same statements as `sink {}`. Storing the incoming span
is the receiver's default and most rules only want metrics, so a rule without `persist {}` abstains. A rule with
`persist {}` votes: `dropper {}` and an unsampled `rateLimit` vote drop, `enforcer {}` votes keep and marks the span
enforced, past this rule's samplers before or after it but not past its `dropper {}`, and an empty `persist {}` votes
keep. The span is stored when every rule that voted said keep and no span
listener vetoed; a drop vote from one rule therefore beats an `enforcer {}` in another, and a rule that aborted did
not vote.

Two things follow from the span being the unit:

- `rateLimit` counts spans, not traces: two spans of one trace can fall on different sides of the budget and a
  stored trace can have spans missing. A rule that needs whole traces keys its decision on something every span of
  the trace carries, or accepts partial traces.
- `enforcer {}` also bypasses the receiver's head sampling. TAL votes before `otlpTraceSampleRate` is applied, and an
  enforced span skips the trace-id check, the way a Zipkin `debug` span does. It does not skip
  `otlpTraceMaxSpansPerSecond`, which stays the operator's ceiling. With `otlpTraceStorage: zipkin` the span is stored
  as a Zipkin `debug` span instead, which the Zipkin receiver keeps past both its `sampleRate` and its
  `maxSpansPerSecond`. The bypass is per span, so at a low sample rate an
  enforced slow statement is stored while the rest of its trace may not be, and an `enforcer {}` on a broad condition
  turns head sampling off for everything it matches; it belongs behind a condition that is rare by construction, an
  error status or a latency threshold.

## Output Type

A rule that declares `outputType` builds one record per span and writes it after the rule has run, when a `sink {}` ran
and kept it, see [Sink](#sink). Every extractor statement whose name is not a keyword becomes a setter call on the
record, validated at compile time; the standard fields `service`, `instance`, `endpoint` and `layer` are taken from
`meta`, so the `service` statement names the record's service.

- A `TALOutputBuilder` name, `SlowSQL` for the `DatabaseSlowStatement` record. Its fields are `id`, `statement` and
  `latency`; `traceId`, `timestamp` and `layer` come from `meta`. The record shows on the database's slow-statement
  panel when the record's service is named and layered as the database node in the topology.
- The fully qualified class name of a `TALOutputBuilder` implementation. Any other class, a plain `Source`
  included, is refused when the rule loads.

## Address-named peers

A CLIENT span names its peer by what it dialed, `server.address` and `server.port`, and the callee names itself by
`service.name`; nothing on the OTLP wire ties the two, since the trace context carries no address. Without help, a
fleet that does not set `service.peer.name` gets `product-catalog:3550` beside `product-catalog` in the service list and
in the topology. TAL folds the address onto the service from the trace context alone, by strict span pairing: a
SERVER span's `parentSpanId` is the span id of the CLIENT span that called it, so the two spans of one call name the
caller, the address it dialed and the service that answered. Nothing is inferred from a host name, a DNS label or a
Kubernetes namespace.

```text
filter {
  if (meta.kind == "SERVER") {
    extractor { buildNetworkAlias callerScopedAddress(pairing.service, pairing.peer) }
  }
  if (meta.kind == "CLIENT") {
    extractor {
      def peer = attr("server.address") + ":" + attr("server.port")
      prepareNetworkAlias peer
      def peer = resolveNetworkAlias(callerScopedAddress(meta.service, peer))
    }
  }
}
```

- **`prepareNetworkAlias <peer>`** sends a CLIENT span's half of the call, its service and the peer, to the trace's
  owner. A SERVER span sends one too when its rule names the upstream it proxied to, for a proxy that reports one
  span per request, see [OpenTelemetry Trace Analysis
  Practices](../setup/backend/trace-analyzer-practices.md#a-proxy-that-reports-one-span-per-request). A PRODUCER or
  CONSUMER span is ignored: a CONSUMER span's parent is the PRODUCER span, and pairing them would name the broker's
  address after the consumer. Every call in the sampled share of traces sends its half,
  and every pair that meets pushes its row; the row decides whether storage writes it, at once when the mapping
  changed and once per five minutes when it did not, so an address that moves to another service is followed at its
  next sampled call. A peer that never answers with a SERVER span, an external API or an uninstrumented service,
  costs one message per sampled call, which waits out the buffer unmatched. The statement only names the peer: the
  half is sent once the span's rules have run, by the first rule that was not aborted, as the callee half is, so an
  `abort {}` anywhere in the rule withdraws it and a span sends at most one.
- **`buildNetworkAlias <address>`** marks a SERVER span with a remote parent as the callee half. The expression is
  not evaluated on the span: it runs on the trace's owner when the two halves match, and reads the caller half only,
  through `pairing`, a variable that exists only in this expression and is never declared with `def`:
  `pairing.service` is the caller's `meta.service` and `pairing.peer` the value its `prepareNetworkAlias` sent. They
  are joined with literals, `+` and `callerScopedAddress`. Reading anything else, such as `meta`, an attribute or
  another field of `pairing`, or declaring `buildNetworkAlias` twice in one rule, is a compile error. The first rule
  that claims the span and asks for an alias names the fact.

  > **Warning:** keep the address `callerScopedAddress(pairing.service, pairing.peer)`, as the shipped rule does.
  > `prepareNetworkAlias`'s check, `resolveNetworkAlias(callerScopedAddress(...))` and the topology query all look
  > the row up by `service->peer`, so any other address is built but never read. An address without the caller,
  > such as `buildNetworkAlias pairing.peer`, is worse: it is the plain-address form the agent analyzer writes and
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
every matched pair except one whose peer is a gateway; a call to another replica of the caller is drawn as a loop; an address longer than the alias
column's 200 characters is counted and logged, not built, and so is a pair whose callee's rule is missing on the
owner or fails to compose the address. The row names no instance, and the last pair written wins.

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
  and its address-named edge stays as the agent analyzer records it.

[OpenTelemetry Trace Analysis Practices](../setup/backend/trace-analyzer-practices.md) walks through common
deployments: proxies, uninstrumented gateways, two namespaces of one application and a header-routed canary.

The topology query folds an address-named node into the service an alias names, the caller's own row first and the
plain address second. It folds a node that is not a registered service, or a self-call whose alias names another
service, the shape a caller that dials its downstream by its own service name records until the downstream's alias
lands. Other registered nodes keep their own names even when the name equals some alias address, as an OTLP
`frontend` does while an agent has registered the host `frontend` for `agent::frontend`.

An address listed in the agent analyzer's [uninstrumented gateways](../setup/backend/uninstrumented-gateways.md) is
never built by a pair either. The shipped rule does not resolve such a peer: when `isGateway` says
it is one, the gateway stays a `VIRTUAL_GATEWAY` node of its own, as on the agent path, which the caller's edge ends on
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

The source of a client edge is always the span's own `service.name`; a span emitted under a pseudo-service named after a backend, as HotROD's `mysql` spans are, is described under [Topology](../setup/backend/trace-analyzer.md#topology) in the operator guide.

## Differences from LAL

- No parser blocks. `text {}`, `json {}` and `yaml {}` give a log body structure; a span is already structured.
- No `traceId`, `segmentId`, `spanId` or `timestamp` statements. The identifiers are facts of the span, readable as
  `meta.traceId` and friends.
- `abort {}` is allowed anywhere.
- `tag` injects into the stored span, not into a record.
- `prepareNetworkAlias`, `buildNetworkAlias`, `callerScopedAddress` and `resolveNetworkAlias` exist, see above.
- `metrics {}` declares a metric and emits one value; it does not hand a sample to another engine.
- `persist {}` is new and decides the span's own fate. `sink {}` needs an `outputType`.
- The statements in `sink {}` and `persist {}` vote: `dropper {}` wins over `enforcer {}`, and `enforcer {}` over the
  samplers, in either order. In LAL every one of them overwrites the decision, so the last to run wins.
- The record is written once, after the rule has run, when any `sink {}` ran and the votes keep it. A LAL rule writes
  its output when its `sink {}` block ends.
- Statements may be separated by `;` as well as by newlines, so a `metrics {}` block fits on one line.
- `!` binds tighter than `&&` and `||`, as in Java: `!peer && hasAttr("x")` reads as `(!peer) && hasAttr("x")`.
- More words are keywords: `persist`, `prepareNetworkAlias`, `buildNetworkAlias`, `scope`, `function`, `match`, `status`, `labelLimit`, `buckets`,
  `ranks`, `span`, `meta`, `CLIENT` and `SERVER`. They still work as field names and label keys, `labels status: ...`, but not as a
  `def` name, so `def status = ...` is a syntax error.
