# Trace Tail Sampling

Trace tail sampling discards traces **after** they have been stored, during BanyanDB's own
compaction. Because the whole trace is on disk by the time the decision is made, the decision
can consider all of it — total duration, whether any span failed, which tags appear anywhere in
it — and it reclaims space that has already been written.

This is BanyanDB-only: it runs inside the data node, not in OAP. For the ingest-side mechanism
that samples segments as they arrive, see [Trace Sampling](../setup/backend/trace-sampling.md).
The two are independent gates, so enabling both multiplies the drop rate.

For the full list of configuration keys, see the
[BanyanDB storage guide](../setup/backend/storages/banyandb.md). The samplers' source, and the
authoritative reference for their behaviour, is
[`plugins/README.md`](https://github.com/apache/skywalking-banyandb/blob/main/plugins/README.md)
in the BanyanDB repository.

## Requirements

Tail sampling is **disabled by default**, and enabling it in `bydb.yml` is not enough on its
own. It only takes effect where all of the following hold:

1. The data node runs the **plugin-capable** BanyanDB image (the `-plugins` tag). Go plugins
   need a CGO-enabled, dynamically linked host, so the default image cannot load them.
2. The node is started with `-trace-pipeline-native-plugin-enabled=true` and
   `-trace-pipeline-trusted-plugin-dir` pointing at a directory.
3. The sampler `.so` is present in that directory, usually by mounting the
   `-plugins-carrier` image at deploy time.
4. `storage.banyandb.<group>.pipeline.enabled` is `true` in `bydb.yml`.

Anything short of that is safe rather than broken:

- A node without plugin support **ignores** the pipeline config entirely — no log, no effect.
- A node that has support but cannot load the `.so` logs an error, keeps its previous sampler
  set and merges **unfiltered**. Nothing is dropped because a plugin is missing.

## How a trace is judged

Two first-party samplers ship with BanyanDB, one per trace schema: `sw-trace-sampler.so` for
SkyWalking's native segments and `zipkin-trace-sampler.so` for Zipkin spans. Both run the same
rules and differ only in which columns hold the inputs.

Rules are **OR-ed** and evaluated in order; the first match keeps the trace and the rest are
skipped:

1. **Duration** — the trace's end-to-end envelope reaches `durationThresholdMs`.
2. **Errors** — `keepErrors` is set and the trace carries an error.
3. **Tag rules** — any `keepTagRules` entry matches a searchable tag.
4. **Healthy sample** — a deterministic fraction of whatever is left.

A trace matching none of them is deleted at merge.

Order affects cost, never the verdict. The duration test reads two numeric columns, while the
tag rules require decoding the flattened tag array, so a trace kept on duration is roughly ten
times cheaper to judge than one that falls through to the tag rules.

### Duration is an envelope, not a per-span test

```
envelope = max(start + duration) − min(start)     over every row of the trace
keep     if envelope ≥ durationThresholdMs
```

This catches traces that are slow only through *sequential* work: three chained 400 ms calls
have no single span above 400 ms, but an envelope of 1.2 s, so a 1000 ms threshold keeps that
trace. A per-span maximum would miss it.

It is deliberately **not** BanyanDB's intrinsic `MaxTS − MinTS`. Those are both derived from the
per-row timestamp column, so they measure the spread of span *start* times — which ignores how
long the final span ran, and is `0` for a single-span trace.

### The healthy sample

Whatever reaches rule 4 is, by definition, fast and error-free. `healthySampleRate` keeps a
fraction of it so the group retains a baseline of ordinary traffic rather than only outliers:

```
keep if fnv1a(trace_id) mapped into [0,1) < healthySampleRate
```

`0.1` keeps 10%, `0` keeps none, `1.0` keeps all. Two properties follow from hashing the trace
ID rather than drawing a random number:

- **It is stable.** A trace is judged more than once — at every merge it takes part in, and
  again at finalization if that event is enabled. A random draw would give it a fresh chance
  each time and its survival odds would decay with every pass. Hashing means the retained
  fraction is a fixed subset.
- **It is not stratified.** The kept fraction is an arbitrary slice of trace-ID space, not
  balanced across services, endpoints or time. "Always keep everything from the payment
  service" has to be a `keepTagRules` entry; the sample rate cannot express it.

The rate is statistical, not a quota: over a million traces the realised share lands within a
fraction of a percent, but over ten traces it may be none of them.

### Tag rules match searchable tags only

OAP does not store searchable tags as individual columns. It flattens every one of them into a
single string array per trace — `tags` for segments, `query` for Zipkin — holding `key=value`
entries. Every `keepTagRules` entry is matched against that array.

A rule naming a first-class column (`service_id`, `local_endpoint_service_name`, …) therefore
could never match, and the samplers **reject** such a rule at startup rather than let it
silently never fire. So "keep everything from the payment service" has to key off a tag the
instrumentation actually emits, not the service column. If a searchable tag happens to share a
name with a column, match it through the array itself: `{tagKey: query, regex: "^duration="}`.

### What each schema stores where

| Input | `sw-trace-sampler` | `zipkin-trace-sampler` |
|---|---|---|
| Searchable tags | `tags` | `query` |
| Error signal | `is_error` column | `error` key inside `query` |
| Per-row start | `start_time` | `timestamp_millis` |
| Per-row duration | `latency` (milliseconds) | `duration` (**micro**seconds) |

Despite its name, `timestamp_millis` is a BanyanDB timestamp column, so both schemas supply the
start time in nanoseconds. Only the duration units differ, and the plugin normalizes them, so
`durationThresholdMs` is milliseconds on both.

## When the decision is made

`enabledEvents` selects which events run the chain:

- `PIPELINE_EVENT_MERGE` — during Hot-phase compaction. This is the default: an empty value
  falls back to it for backward compatibility.
- `PIPELINE_EVENT_FINALIZE` — a background sweep once a segment has settled. It must be named
  explicitly; an empty value never enables it.

`MERGE` alone is best-effort. A trace is only judged if it happens to take part in a compaction,
and compaction is driven by part count, so a trace in a shard that never reaches the merge
threshold is never evaluated and survives to TTL. `FINALIZE` is the backstop: it force-merges
un-finalized parts through the same chain so coverage does not depend on compaction luck, at the
cost of extra background work.

### Grace windows

Neither event judges a trace immediately. `mergeGraceSeconds` and `finalizeGraceSeconds` are
maturity windows measured against the trace's own data timestamps, so a trace whose remaining
spans are still arriving is not destroyed half-written.

Only a **positive** value overrides the data node's default. `-1` means "not set here", leaving
the node's own default (30s merge, 5m finalize) in force. `0` does **not** mean "no grace": the
node treats any non-positive value as unset, so a zero grace can only be set with the node's own
`-trace-pipeline-merge-grace-default` flag.

`mergeGraceSeconds` ships as an explicit 30 minutes rather than inheriting the node's 30s,
because a trace is usually read soon after it is written and the shorter window would let the
sampler delete traces while someone is still looking at them.

## Failing open

Every path is biased towards keeping data:

- A predicate that cannot be evaluated keeps the trace. An absent duration or error column
  means "can't tell", not "not slow" / "no error" — those columns are part of the schema, so
  their absence means the block was written under a different one, usually the wrong plugin
  attached to the group.
- A plugin that panics, errors, times out, or returns a mask of the wrong length is bypassed
  and the merge runs unfiltered.
- A plugin that fails to load leaves the previous good sampler set in place.

Two cases are deliberately *not* treated as "can't tell", because they are ordinary data: a
trace carrying no searchable tags simply matches no tag rule, and an error column that is
present but not truthy really does mean "not an error".

## Operating it

Watch these on the data node's own metrics endpoint:

| Metric | Meaning |
|---|---|
| `banyandb_trace_pipeline_sampler_active_count{group}` | samplers currently loaded for the group; `0` means nothing is filtering |
| `banyandb_trace_pipeline_sampler_load_failed{group,name,reason}` | a plugin was rejected — non-zero means the config is not doing what it says |
| `banyandb_trace_pipeline_sampler_register_total{group,result}` | registration outcomes, including `rejected` |

The whole sampling catalog is also collected by the OAP itself when the BanyanDB self-observability
setup is in place — the same three above become `meter_banyandb_trace_sampling_active_samplers`,
`_plugin_load_failures` and `_register_rate`, alongside the drop ratio, per-plugin `Decide` latency,
the fail-open guard counters and the first-party samplers' own decision metrics. That is the better
place to operate from, because it shows what the plugins *proposed* next to what storage *committed*.
See [Trace tail sampling scope](dashboards-banyandb.md#trace-tail-sampling-scope--sampler-plugins-meter_banyandb_trace_sampling_).

**Expect a CPU cost on merge.** BanyanDB can normally copy a single-block trace through a merge
as raw bytes without decoding it. A sampler that projects any tag column — which every useful
configuration does — disables that fast path, so blocks are decoded in full during merges that
run the filter. The reward is that dropped traces are never re-written, reclaiming the space
rather than leaving tombstones.

## Limitations

- **`keepErrors` on Zipkin reads a tag convention, not an authoritative field.** The Zipkin
  schema has no `is_error` column, so the sampler looks for Zipkin's conventional `error` tag
  among the flattened `query` entries — OAP writes each span tag there both as the bare key
  (`error`) and as `key=value` (`error=<message>`), and a match on either keeps the trace.
  Instrumentation that signals failure only through `http.status_code` 5xx or `otel.status_code`
  writes no `error` tag at all, so those traces need an explicit `keepTagRules` entry.
- **Even that is lost when the error value exceeds 256 characters.** OAP skips *both* forms —
  the bare key and `key=value` — when either exceeds `Tag.TAG_LENGTH`, so an `error` tag carrying
  a long exception message reaches `query` in neither form and `keepErrors` cannot see it. The
  loudest errors are the ones that go missing. Catch those with a `keepTagRules` entry on a
  short-valued tag, such as an `http.status_code` regex.
