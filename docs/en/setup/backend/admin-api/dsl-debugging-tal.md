# DSL Debug API — TAL

> Status: **shipped**. Operator reference for the TAL slice of the DSL Debug API. Index of related pages:
> [DSL Debug API overview](dsl-debugging.md). The language itself: [Trace Analysis Language](../../../concepts-and-designs/tal.md).

## What it captures

A TAL session attaches to one rule of a TAL rule file. A record follows the object the analyzer passes through the
rule, so its shape depends on the rule's input:

| Rule input | One record is | `context.kind` | `spans[]` holds |
|---|---|---|---|
| `otlp` | one OTLP span through the rule | `span` | the one span |
| `sw-native` | one segment through the rule | `segment` | every span of the segment, in wire order; a span the rule never ran on, after it failed on an earlier span, has no `run` |

Every record is a span list: each entry is the span as it arrived, with the rule's run on it. The wire shape:

```text
nodes[]
  records[]
    startedAtMs
    captureTime                  — when the node captured it, `<millis>.<six-digit nanos>`, which pages the read
    dsl                          — the rule's verbatim DSL
    rule                         — { ruleName, file, inputType, layer, sourceLine, segmentDefs, dslFirstLine }
    context                      — the object the runner passed, see below
    spans[]
      span                       — the span as it arrived: SpanObject (native), Span (OTLP) as JSON
      meta                       — OTLP only: the span's metadata as the receiver read it
      run
        continueOn               — false when the span's run aborted, or the rule failed on it
        reason                   — `abort {} at line N`, or the failure, which wins over an abort
        steps[]                  — the statements the run executed, in order
        outputs[]                — what left the rule on this span
        state                    — the run's state at its end
```

The `context` of a native record holds what a rule can read beyond the span: `segment`, the `SegmentObject` without
its spans (`traceId`, `traceSegmentId`, `service`, `serviceInstance`, `isSizeLimited`, `spanCount`), and `derived`, the
values the analyzer computes once per segment before any rule runs, named by the function a rule reads each with
(`segmentStartTime`, `segmentLatency`, `segmentIsError`, `segmentSampled`, `segmentTags`, and `segmentBytes` as the
size only). An OTLP record's `context` holds the span's `resource` and instrumentation `scope` attributes.

A native run on span 0 starts with its `first {}` steps, at the `first` stage. A `build {}` block runs after the
segment's last span; its steps and outputs are listed on the span whose run queued it, at the `build` stage, and the
span's `state` is what the segment's values are after it. `first {}` and each `build {}` run on their own, so an
`abort {}` in one ends that block only: it shows as its step, and `continueOn` stays as the span's run left it.

### Steps

Each step names its `stage`, the block it sits in (`first`, `filter`, `extractor`, `persist`, or `build`),
its `kind`, its `sourceLine` and its verbatim `sourceText`, and carries only what the statement produced. A statement
with a body (a block, `foreach`, `rateLimit`, a queued `build {}`) shows its first line as `sourceText`, as its body's
statements have steps of their own. A `failure` step, which no statement wrote, carries `kind` and `value` only.

| `kind` | Statement | Carries |
|---|---|---|
| `if` | an `if` or `else if` condition | `operands`, each operand the condition evaluated, in order, as `{ text, value }`: the operand as the rule writes it and its value; a literal is not listed, nor an operand that `&&` or `\|\|` skipped. Then `result` |
| `def`, `assign`, `segmentDef` | a variable set | `value` |
| `foreach` | one iteration | `value`, the element |
| `service`, `instance`, `endpoint`, `layer` | the span's identity set | `value`, as the run now holds it |
| `tag` | one tag added | `value`, `{ key: value }` |
| `prepareNetworkAlias` | a caller half asked for | `value`, the peer the statement named; the run keeps the first that is not empty, and an empty one asks for no half |
| `buildNetworkAlias` | a callee half asked for | — |
| `enforcer`, `dropper`, `rateLimit` | a persist vote | `value`, the vote the run now holds |
| `block` | a block entered: `first {`, `extractor {`, `persist {`, `sampler {`, a queued `build {` running | — |
| `build` | a `build {}` block queued | `value`, the block's index |
| `source`, `metrics`, `writeNetworkAlias` | an output written | `output`, the index into `outputs[]` |
| `pairing` | the network-alias half the run asked for, decided after the run (OTLP) | `output`, the index into `outputs[]` |
| `abort` | `abort {}` | — |
| `log` | a `log` statement | — |
| `failure` | the rule threw | `value`, the exception |

A statement whose branch did not run leaves no step, so `steps[]` is the path the run took. `sourceLine` is the line
in the rule file, counted from the record's `rule.dslFirstLine`, the file line the rule's DSL starts at, so a client
places each step on the file without matching texts; when the node cannot read the file, the record has no
`dslFirstLine` and a step carries `dslLine`, the line within the rule's DSL.

### Outputs: what a run pushes forward

`outputs[]` is the data the run generated and pushed out of the rule, each with the `sourceLine` of the block or
statement that generated it; a `pairing` output names the first statement that asked for its half. Every output but
`persist` also has a step naming it by its index (`output`), in both granularities, so the step list shows where in
the rule each piece of data left.

| `kind` | Generated by | Pushed to | Carries |
|---|---|---|---|
| `source` | `source <Scope> {}` | the source receiver, which dispatches it by its scope to OAL or to the dispatcher a module declares: at the statement for OTLP, in the segment's build phase for native | `scope`, and `fields`, the source as sent |
| `metrics` | `metrics {}` | the meter system, as one value of the metric for its entity, in the minute of its timestamp | `metric`, `entity`, `observation`, `timestamp`; or `skipped`, with the reason the block sent nothing (its entity's layer is not registered, or the block failed) |
| `writeNetworkAlias` | `writeNetworkAlias` (native) | the network address alias, as the alias of the address | `address` |
| `persist` | `persist {}` (OTLP) | the span's storage: the votes of every rule of the span merge into whether it is stored; the vote of a run that aborted or failed is not merged, so such a run has no `persist` output, while its steps and `state` keep the vote | `vote`: `keep`, `drop`, `sampledOut` or `enforced` |
| `pairing` | `prepareNetworkAlias`, `buildNetworkAlias` (OTLP) | the OAP node that owns the trace, as the caller or the callee half of a call | `role` (`caller` or `callee`), `outcome`, the `peer` of a caller half, `offeredBy`, and `half`: the half this run built, its span's `traceId`, `spanId` and `parentSpanId`, the `service` as the run left it (after a `service` statement), and a caller half's `peer` (a callee half meets the caller half whose span is its parent); with `offeredBy`, the span sent that earlier rule's half instead, see [Network-alias pairing](#network-alias-pairing) |

A `pairing` output is decided once every rule of the span has run, so its step comes after the run's other steps.

### State

For OTLP, `state` holds `meta`, the identity the run left on the span (`service`, `serviceInstance`, `endpoint`,
`layer`), the `tags` it added, and its `persist` vote. For native, it holds `segmentDefs`, the rule's segment
variables after the run, which every later run of the rule on the segment reads.

### Network-alias pairing

A run that asks for a network-alias half gets a `pairing` output once every rule of its span has run. A span offers
at most one caller half and one callee half, each that of the first rule that asked; a later rule's output names that
rule as `offeredBy`. Its `outcome` is what became of the half the span offered: `sent` (handed to the node that owns
the trace; when that node is slow to take them and the send buffer is full, the half is still dropped, which the OAP
log counts), or why not: `notSampled` (the trace falls outside `pairingSamplePercent`), `notServerSpan`, `rootSpan`,
`parentLocal` (a callee half needs a SERVER span with a remote parent), `notCallerKind` (a caller half needs a CLIENT
or SERVER span), or `failed` (offering the halves threw, as building a match on the trace's owner can). A
`prepareNetworkAlias` whose peer is empty asks for no half, so its run has no `pairing` output.

A session records what the rule did with the span it received, and the halves are where that ends. Whether a half
later meets its pair, and the alias that match builds, belong to the pairing on the trace's owner, which runs outside
the rule's run, so a session records neither; the [Trace Pairing Debugging API](trace-pairing-debugging.md) shows
them. A run that reads an alias, with `resolveNetworkAlias` (OTLP) or `findNetworkAlias` (native), shows what it read
as that statement's step.

## Enabling

The same switches as the other DSLs, see [Common ground](dsl-debugging.md#common-ground). The probes are compiled
into a rule when it loads at boot, so `SW_DSL_DEBUGGING_INJECTION_ENABLED=false` leaves every TAL rule without them,
and a session install answers `503 injection_disabled`.

## Picking the rule key

| Param | Value |
|---|---|
| `catalog` | `tal` |
| `name` | the rule file's name without its extension, as the listing below gives it: `otlp-general`, `native-rpc`, … |
| `ruleName` | the rule's `name` in that file |

The rules a node loaded are listed by the read-only endpoints below, which do not need the probes. TAL rules load at
boot and are not managed by the runtime-rule API, so the listing is what the node runs until it restarts.

| Verb | Path | Purpose |
|---|---|---|
| GET | `/runtime/tal/files` | Every loaded file with its rules: the `talFiles` first, then the `nativeTalFiles` in the order their rules run. Each rule carries its `layer`, `inputType`, `line`, native `order`, and whether it is `debuggable`: not when the node runs without the probes, or when two rules of the file share its name, in different layers, since a session names a rule by its file and name. |
| GET | `/runtime/tal/files/{name}` | The file's content as the node loaded it (text); `name` is the listed name, or the file's own name with its extension. |

A session is rule-oriented: it sees the spans its rule runs on. An OTLP span reaches the rules of the layer its
`service.layer` names, so a span without that attribute, with an unregistered layer, or on a layer with no rules
runs no rule and appears in no session.

## Granularity

`granularity=block` (the default) shows what each run generated and pushed forward, and the blocks it went through to
get there: the `block` steps, the `build` step of a `build {}` block queued, the step of every output (`source`,
`metrics`, `writeNetworkAlias`, `pairing`), and any `abort` or `failure`. `granularity=statement` adds every statement
and every `if` with its operands and result. `outputs[]` and `state` are the same in both.

## End-to-end example

```bash
# 1. Open a session on the native segment rule
curl -s -X POST 'http://localhost:17128/dsl-debugging/session?catalog=tal&name=native-segment&ruleName=native-segment&clientId=me&granularity=statement'
# → {"sessionId":"…","peers":[…]}

# 2. Send traffic from a SkyWalking agent, then poll; pass the response's nextCapturedSince back to read only what
#    was captured since
curl -s 'http://localhost:17128/dsl-debugging/session/<sessionId>'
curl -s 'http://localhost:17128/dsl-debugging/session/<sessionId>?capturedSince=<nextCapturedSince>'

# 3. Stop
curl -s -X POST 'http://localhost:17128/dsl-debugging/session/<sessionId>/stop'
```

An OTLP rule works the same way, with `name=otlp-general&ruleName=otlp-general`.

## Failure modes

The [common failure modes](dsl-debugging.md#failure-modes) apply. `404 rule_not_found` also means no reachable node
compiled the rule's probes in, as when every node that loads the rule booted with injection off.

## Limits

As for every DSL, `recordCap` defaults to and is at most 100, and `retentionMillis` defaults to 5 minutes and is at
most 1 hour. `recordCap` counts records, so its maximum of 100 means different amounts of data for the two inputs:

- A native rule keeps at most 100 **segments**, each with all its spans and the rule's run on every span.
- An OTLP rule keeps at most 100 **records**, each one OTLP span.

A native session therefore holds more memory than an OTLP one at the same `recordCap`. That is still bounded by the
cap and the retention window, and is acceptable for a debug session; lower `recordCap` for a rule whose segments carry
many spans.

## See also

- [DSL Debug API overview](dsl-debugging.md)
- [Trace Analysis Language](../../../concepts-and-designs/tal.md), [TAL for native segments](../../../concepts-and-designs/tal-sw-native.md), [TAL for OpenTelemetry spans](../../../concepts-and-designs/tal-otlp.md)
