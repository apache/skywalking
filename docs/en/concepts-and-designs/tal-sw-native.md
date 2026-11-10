# TAL for Native Segments

A rule with `inputType: sw-native` analyzes the trace segments SkyWalking agents send. It is written in the [Trace
Analysis Language](tal.md); this page describes what only this input reads and builds. The files are listed in
`trace-analyzer/default/nativeTalFiles` (`SW_TRACE_NATIVE_TAL_FILES`), in the order their rules run; see [Native Trace
Analysis](../setup/backend/native-trace-analysis.md) for the setting and for what the shipped rule files produce.

The shipped rules are the agent analysis itself: they build the same sources, make the same naming calls and write the
same log lines as the Java analysis they replaced, so every metric of `core.oal` and the other `.oal` files keeps its
number.

```yaml
rules:
  - name: native-network-alias
    layer: GENERAL          # the rule-selection key of the agent layer; native rules run on every span anyway
    inputType: sw-native
    dsl: |
      filter {
        if (!traceAnalysis() || span.spanType != "Entry" || span.skipAnalysis) {
          abort {}
        }
        // a broker's address is not the consumer's
        if (span.spanLayer == "MQ") {
          abort {}
        }
        extractor {
          foreach (ref in span.refsList) {
            if (ref.refType == "CrossProcess") {
              def address = formatServiceName(ref.networkAddressUsedAtPeer)
              if (!isGateway(address)) {
                writeNetworkAlias address
              }
            }
          }
        }
      }
```

## The segment and its spans

A rule reads the wire objects the agent sent, the ones the Java analysis read, as they are: `span` is the protobuf
`SpanObject` itself, as an OTLP rule's `span` is the OTLP `Span`, and `segment()` is its `SegmentObject`. Both are
read-only. A field is read by its name, `span.operationName`, which compiles to the getter, `getOperationName()`.

| `span.` | Value |
|---|---|
| `operationName`, `peer`, `spanId`, `parentSpanId`, `startTime`, `endTime`, `componentId`, `isError`, `skipAnalysis` | the span's fields as the agent sent them |
| `spanType` | `Entry`, `Exit`, `Local`, or `UNRECOGNIZED` for a type this OAP does not know; compared with a literal, `span.spanType == "Entry"` |
| `spanLayer` | `Unknown`, `Database`, `RPCFramework`, `Http`, `MQ`, `Cache`, `FAAS`, `GenAI`, or `UNRECOGNIZED`; `span.spanLayer == "Database"` |
| `tagsList` | the tags in wire order, each with `key` and `value`; a key can repeat |
| `logsList` | the logs in wire order, each with `time` and `dataList`, its key and value pairs in wire order |
| `refsList` | the references in wire order, each with `refType` (`CrossProcess` or `CrossThread`), `traceId`, `parentTraceSegmentId`, `parentSpanId`, `parentService`, `parentServiceInstance`, `parentEndpoint` and `networkAddressUsedAtPeer`, the address the caller dialed, which the `sw8` header carries |

`segment()` gives the segment's own fields the same way: `segment().service`, `serviceInstance`, `traceId`,
`traceSegmentId`, `isSizeLimited` and `spansList`. What the runner derives from the whole segment before any rule runs,
as the Java analysis derived it, is read with functions:

| Function | Value |
|---|---|
| `segmentStartTime()` | the fold the agent analysis made over the spans' starts in wire order: it begins at `0` and takes a span's start time when it is smaller, or whenever the start so far is still `0`, so a span starting at `0` lets the next span's start replace it |
| `segmentLatency()` | the segment's duration, from that start to the largest end time, capped at `Integer.MAX_VALUE` |
| `segmentIsError()` | whether the segment is an error, under `agent-analyzer`'s `segmentStatusAnalysisStrategy` |
| `segmentTags()` | the span tags `core.searchableTracesTags` lists, each equal key and value pair once, those longer than a stored tag allows dropped with a debug line; the list the `Segment` record stores |
| `segmentSampled()` | whether the segment is kept: the [trace sampling](../setup/backend/trace-sampling.md) decision, or an error segment under `forceSampleErrorSegment` |
| `segmentBytes()` | the segment serialized again, what the `Segment` record stores; serialized at each call |

The runner derives them once per segment that has spans, whatever `traceAnalysis` says; every span of the segment reads
the same values, and concurrent segments never share them.

The shipped `native-segment` rule stores the received segment unchanged, `segmentBytes()`, and decides only whether the
`Segment` record is written and what its index fields are; `source Segment {}` sets whatever fields a rule gives it, so
a rule file of your own that writes one is responsible for what it stores.

There is no `meta` root and there are no `service`, `instance`, `endpoint` or `layer` statements: a native rule names
its entities in the sources it builds. Native spans carry no `service.layer`, so every native rule runs on every span,
and its `layer` key is `GENERAL`. A rule computes the layer a source needs from the span, as the agent analysis does:

```text
def calleeLayer = "GENERAL" as Layer
if (span.spanLayer == "FAAS") {
  calleeLayer = "FAAS" as Layer
}
```

A span's own latency is `(span.endTime - span.startTime) as Integer`; `as Integer` on a `long` takes its `int` value,
overflow included, as the agent analysis casts it.

## How the rules run

For each segment, the runner:

1. returns at once when the segment has no spans;
2. derives the segment values above;
3. for each span in wire order:
   - on a span whose `spanId` is `0`, runs the [`first {}`](#first) block of every rule, in rule order, before any
     rule's regular run on that span, whatever the span's type;
   - on a span of an unknown type, logs `span type value was unexpected, span type name: {}` at error;
   - runs every rule's `filter`, in rule order;
4. after the last span, runs the queued [`build {}`](#build) blocks, rule by rule in rule order, and within a rule in
   the order they were queued.

Rule order is the order of `nativeTalFiles`, and within a file the order of its rules. The order decides what reaches
an order-sensitive merge first, what a failing segment has already sent, and the order of the stateful naming calls;
see [Native Trace Analysis](../setup/backend/native-trace-analysis.md#the-order-of-the-files).

A rule tests `span.spanType` to decide which spans it reads. The `[TAL] rule=...`
debug line an OTLP rule writes before each run is not written for a native rule.

### `first`

```text
first {
  <the statements of a filter>
}
```

At filter level. It runs on every span whose `spanId` is `0`, before any rule's regular run on that span. It is a run
of its own: it has its own `def` values, an `abort {}` in it ends that run only, and it shares only
[`segmentDef`](#segmentdef) values with the rule's regular run.

### `segmentDef`

```text
segmentDef <name> = <expr>
```

A variable of the rule that lives for one segment. It starts as `null` for each segment. The `segmentDef` statement
declares it and assigns it each time it runs; `<name> = <expr>` assigns it too, anywhere in the rule, on any span of
the segment; and the name reads it anywhere in the rule, before its first assignment included. Its storage belongs to
one run of the runner, never to the compiled rule, so concurrent segments do not share it, and it is cleared when the
segment ends, also when it fails. A `def` of a `segmentDef` name is a compile error.

A rule that builds once per segment guards its `build {}` with a `segmentDef`, set on the segment's first span in
wire order, whatever its type:

```text
if (buildQueued == null) {
  segmentDef buildQueued = true
  extractor { build { ... } }
}
```

### `build`

```text
build {
  <def, assignment, if, foreach, abort, log, metrics, source>
}
```

Inside `extractor {}`. The block is queued when the run reaches it, and runs once after the segment's last span.

- **What it sees.** The run's span and its function context, as they were for that run, not the last span's. The
  `def` values the run had when it reached the block, the loop variables of the `foreach` it sits in included,
  captured by value. The `segmentDef` values as they are when the block runs.
- **Its own variables.** A captured value is the block's own copy: `caller = formatServiceName(caller)` reads the copy
  and assigns it, and the run and the other blocks do not see the change. A `def` inside the block declares a variable
  of that block.
- **Outputs.** A native rule makes its outputs here and nowhere else, each at its statement: [`metrics
  {}`](tal.md#metrics), whose `timestamp` defaults to `span.startTime`, and [`source
  {}`](tal.md#source-existing-system-adoption).
- **Abort.** An `abort {}` later in the same run withdraws the blocks that run queued. An `abort {}` inside a block
  stops that block only.

A `build {}` inside another one is a compile error.

## `writeNetworkAlias`

```text
writeNetworkAlias <address>
```

Sends, at its statement, the network address alias row an entry span's cross-process reference names:

- `address` the given address;
- the represented service `formatServiceName(segment().service)`, a normal service;
- the represented instance `formatInstanceName(segment().serviceInstance)`;
- the minute of the span's start as its time bucket.

The agent propagates the address the caller dialed in the `sw8` header, so the callee alone has every fact of the
row, and nothing waits for a caller's half as the OTLP pairing does. The row is stored as the other sources are, and
[`findNetworkAlias`](#functions-of-the-native-input) answers it once the core's alias cache has reloaded it, which
takes seconds; a reload can land while a segment is still being analyzed, so a later span of the same segment may or
may not see it.

## Functions of the native input

Beside the [functions for every input](tal.md#functions-for-every-input), a native rule calls these:

| Function | Returns |
|---|---|
| **Tags** | |
| `tag(key)`, `hasTag(key)` | the value of the span's first tag with that key, empty when there is none; whether there is one |
| `tagList()` | the span's tags as `key.trim() + ":" + value.trim()` strings, in wire order, in a new list |
| `tagMap()` | the span's tags as a new map; a key is not expected to repeat, and when it does the first value is kept, as `tag` answers it |
| **The segment** | |
| `segment()`, `segmentStartTime()`, `segmentLatency()`, `segmentIsError()`, `segmentTags()`, `segmentSampled()`, `segmentBytes()` | the segment and what the runner derives from it, see [The segment and its spans](#the-segment-and-its-spans) |
| **Peers** | |
| `findNetworkAlias(address)` | the alias row stored for the address, or `null`, with its `service` and `instance` decoded from its ids; `instance` is `_blank` when the row names none. A malformed id fails the rule |
| `clusterNameOrAddress(address)` | the cluster [server clusters](../setup/backend/server-clusters.md) list the address under, or the address |
| **Settings of `agent-analyzer`** | |
| `traceAnalysis()` | `agent-analyzer.traceAnalysis` |
| `maxSlowSQLLength()` | `agent-analyzer.maxSlowSQLLength` |
| `slowDBAccessThreshold(dbType)` | the [slow statement](../setup/backend/slow-db-statement.md) threshold for the type: the type lower-cased, then `default`, then `Integer.MAX_VALUE`, read at the call, so a dynamic change applies |
| `slowCacheReadThreshold(type)`, `slowCacheWriteThreshold(type)` | the [slow cache command](../setup/backend/slow-cache-command.md) thresholds, looked up the same way |

The OTLP input has none of these but the shared ones; its `findServerClusterName` answers empty for an unlisted
address where `clusterNameOrAddress` answers the address.

## Failures

The runner catches nothing, neither around a rule nor around the dispatch of a source it sends. A rule that throws
stops the segment at that point, and the exception reaches the receiver: the gRPC handler counts and logs it, the HTTP
handler counts it and rethrows it, and the Kafka fetcher's handler lets it leave the handler.

Everything already done stays done: alias rows sent, log lines written, and changes to the endpoint grouping's state.
A throw before the build phase means no queued block runs. A throw inside a `build {}` block, including one raised
while a source is dispatched, stops the remaining blocks, and what was already sent stays sent.

## What a native rule cannot do

- Change the span or the segment it reads: they are read-only. The stored trace is what the `Segment` record a rule
  sends holds, the received segment in the shipped `native-segment` rule.
- Use the OTLP constructs: `meta`, the `service`, `instance`, `endpoint` and `layer` statements, `persist {}`, `tag`,
  `prepareNetworkAlias`, `buildNetworkAlias` and `pairing` are compile errors.
- Make an output outside `build {}`: `metrics {}` and `source {}` anywhere else are compile errors.
- Deduplicate a re-sent segment: `dedupWindow` applies to OTLP spans only.
