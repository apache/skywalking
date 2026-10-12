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
  - name: otlp-service # unique within its layer
    layer: auto        # a registered layer name, or `auto`
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
| `service` | the resource's `service.name`; the receiver's fallbacks are described in [OpenTelemetry Trace Format](../setup/backend/otlp-trace.md#service-name) |
| `serviceInstance` | the resource's `service.instance.id` |
| `endpoint` | the span's name, until a rule sets it |
| `layer` | the resource's `service.layer`, empty when absent; see [Layer](#layer) |
| `kind` | `SERVER`, `CLIENT`, `PRODUCER`, `CONSUMER`, `INTERNAL` or `UNSPECIFIED` |
| `statusCode`, `statusMessage` | `UNSET`, `OK` or `ERROR`, and the status message |
| `startTime`, `timestamp` | the span's start and end, in milliseconds; `timestamp` is the minute bucket the metrics land in |
| `latency` | end minus start, in milliseconds |
| `traceId`, `spanId`, `parentSpanId` | the ids in lowercase hex; `parentSpanId` is empty on a root span |
| `root` | whether `parentSpanId` is empty |
| `scopeName`, `scopeVersion` | the instrumentation scope |

`span` is the wire object itself, `io.opentelemetry.proto.trace.v1.Span`, resolved at compile time: `span.name`,
`span.traceState`, `span.status.message`, `span.eventsCount`. Prefer `meta.kind` and `meta.statusCode` over the
proto enums in conditions.

## Layer

A span whose resource carries `service.layer` is evaluated by the rules declared for that layer, and by nothing
else. A span whose resource carries a layer name that is not registered is logged and not analyzed. A span without
`service.layer` is evaluated by the `layer: auto` rules; an auto rule that does not `abort {}` has claimed the span
and must set `layer` in its extractor. There is no fallback to `GENERAL`, which is the agent layer: the shipped rule
puts OpenTelemetry services under `OTLP_SERVICE` and their conjectured peers under `OTLP_VIRTUAL_DATABASE`,
`OTLP_VIRTUAL_CACHE` and `OTLP_VIRTUAL_MQ`, see [the setup doc](../setup/backend/trace-analyzer.md#layers).

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
| `event(name)` | the first span event with that name, or `null`: `event("exception")?.timeUnixNano` |
| `eventAttr(name, key, ...)` | an attribute of that event: `eventAttr("exception", "exception.type")` |
| `component(name)` | the component id from `component-libraries.yml`, matched exactly and then ignoring case, so `attr("rpc.system.name", "rpc.system")` giving `grpc` finds `GRPC` and `postgresql` finds `PostgreSQL`; `0`, the `Unknown` component, when nothing matches |
| `resolveAlias(address)` | the service an address names, see [Address-named peers](#address-named-peers); the address unchanged when none is known yet |
| `urlHost(url)` | the authority of a URL, `host` or `host:port`, without scheme, user info, path, query or fragment; empty when the value has none. For a client that names its target only as `url.full` or `http.url` |
| `toJson(expr)`, `toJsonArray(expr)` | as in LAL |

A span's attributes are indexed once per span, however many rules and lookups read them.

### Extractor

```text
extractor {
  def <name> = <expr> [as <Type>]           // any value, including an entity from the metrics block
  service | instance | endpoint | layer <expr>   // overwrite meta.*; also the record's standard fields
  <field> <expr> [as <Type>]                // an output field of the outputType, see Output Type
  tag '<key>': <expr>                       // appended to the stored span as a string attribute
  alias <expr>                              // the address this span was dialed at, see Address-named peers
  metrics { ... }                           // see below
  if (...) { ... } else if (...) { ... } else { ... }
  abort {}                                  // stop this rule for this span; it writes nothing and votes nothing
}
```

`abort {}` is allowed anywhere: at filter level and inside `extractor`, `sink` and `persist`. In a `layer: auto`
rule it means "not mine".

### `metrics`

A `metrics {}` block declares one metric and emits one value for this span. The name, the function and the entity
constructor are literals, so every metric a file declares is known when it compiles and its storage is created
then.

```text
metrics {
  name "<metric>"                    // literal; stored as <metricPrefix>_<metric>
  scope <entity>                     // an entity constructor, or a `def` holding one
  function <function>                // literal, see the table
  value <expr>                       // the observation; a number, or a string that parses as one. Not used by percent
  [match <condition>]                // percent only, required: the span counts as a match when true
  [status <condition>]               // apdex only, required: the span counts as successful when true
  [labels k: v, ...]                 // required by the *Labeled functions, forbidden otherwise
  [buckets [n, n, ...]]              // required by histogram and percentile; ascending, in the unit of `value`
  [ranks [50, 75, 90, 95, 99]]       // percentile only; this is the default
  [timestamp <expr>]                 // milliseconds; default meta.timestamp, the span's end
}
```

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
name; a `value` that does not parse as a number skips the block the same way. An entity held in a `def` has one
shape: reassigning it with a different constructor is a compile error.

The functions are the meter system's, named for what they do to the values of one minute:

| `function` | Value | Meaning per minute per entity |
|---|---|---|
| `cpm` | number | the total of the values divided by the bucket's length in minutes, so hour and day rows read as a rate; `value 1` is calls per minute. The same number `core.oal`'s `cpm` computes; `sumPerMin` is accepted as an alias |
| `sum` | number | the raw total; at hour and day level the totals add rather than average |
| `avg` | number | the mean; `value meta.latency` is mean latency |
| `max`, `min`, `latest` | number | |
| `percent` | `match` condition | `matches * 10000 / total`, the `core.oal` `percent` scale; `match meta.statusCode != "ERROR"` is the success rate |
| `apdex` | `value` and `status` | the `core.oal` `apdex`: thresholds from `service-apdex-threshold.yml` looked up by the entity's service name, satisfied under `T`, tolerated under `4T` |
| `sumLabeled`, `avgLabeled`, `maxLabeled`, `minLabeled`, `latestLabeled`, `sumPerMinLabeled` | number, by `labels` | one series per label set; `labels status: attr("http.response.status_code", "http.status_code")` splits a count by status code |
| `histogram` | number, by `buckets` | the observation counted in the first bucket whose bound is not below it; a value above the last bound lands in the last bucket |
| `percentile` | number, by `buckets` | the same buckets, reduced to the `ranks` at query time |

With `cpm`, `percent` and `apdex` a TAL metric named like an agent metric holds the same number in the same unit, so
an alarm rule or a dashboard widget written for `service_sla` ports to `otlp_service_sla` by renaming the metric.

### Sink

`sink {}` persists the record the rule builds and needs an `outputType`; without a sink no record is written, and a
`sink {}` in a rule with no `outputType` is a compile error. Its statements are LAL's: `sampler { rateLimit("<id>")
{ rpm <n> } }`, shared by id across rules and spans, `dropper {}`, `enforcer {}` and `if`.

### Persist

`persist {}` decides the fate of the span itself, with the same statements as `sink {}`. Storing the incoming span
is the receiver's default and most rules only want metrics, so a rule without `persist {}` abstains. A rule with
`persist {}` votes: `dropper {}` and an unsampled `rateLimit` vote drop, `enforcer {}` votes keep and marks the span
enforced, an empty `persist {}` votes keep. The span is stored when every rule that voted said keep and no span
listener vetoed; a drop vote from one rule therefore beats an `enforcer {}` in another, and a rule that aborted did
not vote.

Two things follow from the span being the unit:

- `rateLimit` counts spans, not traces: two spans of one trace can fall on different sides of the budget and a
  stored trace can have spans missing. A rule that needs whole traces keys its decision on something every span of
  the trace carries, or accepts partial traces.
- `enforcer {}` also bypasses the receiver's head sampling. TAL votes before `otlpTraceSampleRate` is applied, and an
  enforced span skips the trace-id check, the way a Zipkin `debug` span does. It does not skip
  `otlpTraceMaxSpansPerSecond`, which stays the operator's ceiling. The bypass is per span, so at a low sample rate an
  enforced slow statement is stored while the rest of its trace may not be, and an `enforcer {}` on a broad condition
  turns head sampling off for everything it matches; it belongs behind a condition that is rare by construction, an
  error status or a latency threshold.

## Output Type

A rule that declares `outputType` builds one record per span and writes it in `sink {}`, the way a LAL rule does.
Every extractor statement whose name is not a keyword becomes a setter call on the record, validated at compile
time; the standard fields `service`, `instance`, `endpoint` and `layer` are taken from `meta`, so the `service`
statement names the record's service.

- A `TALOutputBuilder` name, `SlowSQL` for the `DatabaseSlowStatement` record. Its fields are `id`, `statement` and
  `latency`; `traceId`, `timestamp` and `layer` come from `meta`. The record shows on the database's slow-statement
  panel when the record's service is named and layered as the database node in the topology.
- The fully qualified class name of a `TALOutputBuilder` implementation. Any other class, a plain `Source`
  included, is refused when the rule loads.

## Address-named peers

A CLIENT span names its peer by what it dialed, `server.address` and `server.port`, and the callee names itself by
`service.name`; nothing on the OTLP wire ties the two, since the trace context carries no address. Without help, a
fleet that does not set `service.peer.name` gets `product-catalog:3550` beside `product-catalog` in the service list and
in the topology. Three mechanisms fold the address onto the service, and the shipped rule uses all three:

- **`alias <address>`** in a SERVER span's rule registers the address the span was dialed at as a network address
  alias of `meta.service`, the row the agent path derives from its propagation header. An HTTP server's
  `server.address` and `server.port` are the `Host` the caller sent, so the caller's `resolveAlias` finds it. The
  alias rows travel through storage and the core's alias cache, which reloads every 10 seconds, so the first calls
  to a freshly seen address are still address-named, and the topology query folds those.
- **`resolveAlias(address)`** on the CLIENT side answers, in this order: a host, or its first DNS label, that is the
  name of a service this OAP node has seen spans from; the service an alias row names; the service whose SERVER span
  this CLIENT span is the parent of, when the pairing below has seen it; else the address unchanged.
- **Pairing** resolves the rest from the trace context: a SERVER span's `parentSpanId` is its caller's span id,
  which needs no attribute convention on either side. Every SERVER span with a parent, and every CLIENT span
  whose peer could not be named, becomes a fact; messaging spans take no part, since a CONSUMER span's parent is
  the PRODUCER span and pairing them would alias the broker's address to the consumer. In a cluster the facts of one trace are
  routed to the node that owns the trace, so the two halves meet wherever each arrived. A match learns the alias and
  writes the row for every node. Facts wait 60 seconds for their other half; a callee that never reports, an
  uninstrumented service or a dropped trace context, leaves its caller address-named, which is the honest answer.

Two guards keep an alias honest. An address whose host names another service is never registered: a proxy that
keeps the `Host` header hands `frontend-proxy:8080` to the `frontend` behind it, and that address stays the proxy's.
And `resolveAlias` asks the known-service registry before the cache, so a row that did get through cannot redirect
an edge away from a service that exists under that name.

The source of a client edge is always the span's own `service.name`; a span emitted under a pseudo-service named after a backend, as HotROD's `mysql` spans are, is described under [Topology](../setup/backend/trace-analyzer.md#topology) in the operator guide.

## Differences from LAL

- No parser blocks. `text {}`, `json {}` and `yaml {}` give a log body structure; a span is already structured.
- No `traceId`, `segmentId`, `spanId` or `timestamp` statements. The identifiers are facts of the span, readable as
  `meta.traceId` and friends.
- `abort {}` is allowed anywhere, and a `layer: auto` rule uses it to decline a span.
- `tag` injects into the stored span, not into a record.
- `alias` and `resolveAlias` exist, see above.
- `metrics {}` declares a metric and emits one value; it does not hand a sample to another engine.
- `persist {}` is new and decides the span's own fate; `sink {}` keeps LAL's meaning and needs an `outputType`.
- Statements may be separated by `;` as well as by newlines, so a `metrics {}` block fits on one line.
- `!` binds tighter than `&&` and `||`, as in Java: `!peer && hasAttr("x")` reads as `(!peer) && hasAttr("x")`.
