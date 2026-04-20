# SWIP-12 Support WeChat & Alipay Mini Program Monitoring

## Motivation

WeChat (微信) and Alipay (支付宝) Mini Programs are among the most widely used client-side
platforms in China — many businesses ship a mini-program before (or instead of) a native
mobile app. Observability for mini-programs is as important as iOS/Android monitoring.

The [SkyAPM `mini-program-monitor`](https://github.com/SkyAPM/mini-program-monitor) SDK
provides a single JavaScript client that covers both WeChat and Alipay runtimes. It emits:

- **OTLP logs** — JS errors, promise rejections, `pageNotFound` (WeChat), HTTP failures
- **OTLP metrics** — app launch / first-render / route / script / sub-package perf
  gauges (durations) + a `first_paint.time` epoch-ms timestamp gauge + a
  request-duration delta histogram (per-flush)
- **SkyWalking native trace segments** (opt-in) — one `SegmentObject` per sampled
  outgoing request, posted to `/v3/segments` with an `sw8` header injected on the wire
  so downstream services join the same trace. As of SDK v0.3.0, every exit span carries
  per-platform `componentId` (`10002` WeChat, `10003` Alipay) and a
  `miniprogram.platform` tag

Because the SDK speaks **standard OTLP + SkyWalking native**, no new receiver is required.
This SWIP focuses on: two new layers, platform-aware MAL/LAL routing, service/instance/
endpoint entity convention, componentId-driven layer mapping for native trace segments,
menu/dashboard support, and a data generator for skywalking-showcase.

This SWIP builds on LAL `layer: auto` / `sourceAttribute()` (SWIP-11) and the existing
`SegmentParserListenerManager` trace-analyzer pipeline — no new general-purpose
infrastructure is needed.

## Architecture Graph

```
┌───────────────────────────┐                         ┌────────────────────────────────────────────┐
│  WeChat / Alipay          │   OTLP/HTTP             │  SkyWalking OAP                            │
│  Mini Program             │  ─────────────────────> │                                            │
│  + mini-program-monitor   │    POST /v1/logs        │  ┌──────────────────────────────────────┐  │
│    SDK                    │    POST /v1/metrics     │  │ receiver-otel                        │  │
│                           │                         │  │                                      │  │
│  Instrumentation:         │   SW native             │  │  Log Handler                         │  │
│  • wx.onError /           │  ─────────────────────> │  │   → LAL rule (layer: auto)           │  │
│    my.onError             │    POST /v3/segments    │  │     fork by miniprogram.platform     │  │
│  • wx.request /           │                         │  │     → WECHAT_MINI_PROGRAM layer      │  │
│    my.request             │                         │  │     → ALIPAY_MINI_PROGRAM layer      │  │
│  • wx.getPerformance()    │                         │  │                                      │  │
│    (WeChat)               │                         │  │  Metric Handler                      │  │
│  • App/Page lifecycle     │                         │  │   → MAL rules (miniprogram/*.yaml)   │  │
│    (Alipay fallback)      │                         │  │     fork by miniprogram.platform     │  │
│                           │                         │  │                                      │  │
│  Resource attrs:          │                         │  │  Trace Handler (native)              │  │
│  • service.name           │                         │  │   → SegmentObject                    │  │
│  • service.version        │                         │  │     componentId 10002 / 10003 →      │  │
│  • miniprogram.platform   │                         │  │     WECHAT / ALIPAY_MINI_PROGRAM     │  │
│    = wechat | alipay      │                         │  │     layer (CommonAnalysisListener)   │  │
│  Span componentId:        │                         │  └──────────────────────────────────────┘  │
│  • 10002 (WeChat) /       │                         │                                            │
│    10003 (AliPay)         │                         │                                            │
└───────────────────────────┘                         └────────────────────────────────────────────┘
```

## Proposed Changes

### 1. Two New Layers

Add in `Layer.java`:
```java
/**
 * WeChat Mini Program monitoring via mini-program-monitor SDK
 */
WECHAT_MINI_PROGRAM(48, true),

/**
 * Alipay Mini Program monitoring via mini-program-monitor SDK
 */
ALIPAY_MINI_PROGRAM(49, true),
```

Both are normal layers (`isNormal=true`) — the mini-program is the observed service itself.

**Why two layers, not one:** the two platforms expose different sets of metrics (see §3),
and users want to compare across WeChat apps or across Alipay apps separately. Using a
tag would force every query and dashboard widget to filter by platform — a layer per
platform is cleaner and mirrors how the SkyWalking UI organizes other platforms.

### 2. Service / Instance / Endpoint Mapping

The SDK's OTLP resource block provides three identifiers, but only two are usable as
aggregation dimensions:

| SkyWalking Entity | Source                            | Cardinality       | Rationale                                              |
|-------------------|-----------------------------------|-------------------|--------------------------------------------------------|
| **Service**       | `service.name`                    | 1 per app         | Fleet-wide app health                                  |
| **ServiceInstance** | `service.instance.id` (recommended pattern: operator sets it to `service.version`) | tens per app | Version regression / rollout monitoring. **Coherence depends on operator following the recommended pattern** — see "Instance coherence" below. |
| **Endpoint**      | `miniprogram.page.path`           | dozens per app    | Which in-app page is slow / errors — matches browser-agent semantics |

**What we deliberately drop:**

| Dropped                                              | Instead used as                                    | Why                                                           |
|------------------------------------------------------|----------------------------------------------------|---------------------------------------------------------------|
| Per-device `service.instance.id`                     | Not aggregated as an entity                        | Unbounded cardinality — millions for any real user base. SDK ≥ v0.4.0 no longer auto-generates a device id; operators set `serviceInstance` to a version-scoped value (see §8). |
| `server.address` (remote host for outbound requests) | Metric label on `miniprogram.request.duration` + `peer` on segments | Not a mini-program entity; topology handles it via tracing |

All three entities are needed — each answers a distinct question:
- **Service** → how is the app doing overall?
- **Instance (= version)** → did v1.3 regress vs v1.2?
- **Endpoint (= page)** → which page is slow / error-prone?

Skipping any of them loses a class of question.

#### Instance coherence across signals

The three signal pipelines key off different attributes by default:

| Signal | Source attribute used as instance |
|---|---|
| OTLP metrics | OTLP resource `service.instance.id` (omitted by SDK if `serviceInstance` unset) |
| Native trace segments | `serviceInstance` field on the segment (substituted with literal `-` if unset) |
| OTLP logs (via LAL) | `sourceAttribute("service.instance.id")` (the recommended LAL extractor — see §5) |

For all three to land on the same OAP instance entity, the operator **must** set
`init({ serviceInstance: <some-string> })` — recommended value is `service.version` so
the same string appears as both `service.instance.id` (OTLP) and segment
`serviceInstance`.

When `serviceInstance` is unset, the three pipelines do **not** uniformly fall back to
the same placeholder — they each handle absence differently:

| Pipeline | Behavior on absent `serviceInstance` |
|---|---|
| **Native trace segment** | SDK substitutes the literal `-` at the wire (mini-program-monitor `request.ts:147`); OAP records the instance entity literally as `-`. |
| **OTLP log → LAL** | `TrafficSinkListener:83` short-circuits when `metadata.serviceInstance` is empty; **no instance traffic is generated**. |
| **OTLP metric → MAL** | `SampleFamily.dim()` (`SampleFamily.java:715`) collapses missing labels to the empty string — the instance dimension is empty, no instance entity is built. |

So the unset case is not "all three aligned under `-`" — segments get a `-` entity,
logs and metrics get no instance entity at all. Operators who care about per-instance
dashboards must set `serviceInstance`. This is documented as the recommended pattern in
the SDK (`README.md` / `SIGNALS.md`) and pinned in the SDK's e2e CI.

The earlier draft of this SWIP set the LAL `instance` to `sourceAttribute("service.version")`,
which would make logs disagree with metrics + traces whenever `serviceInstance !=
serviceVersion`. §5 below sources from `service.instance.id` directly so when the
operator follows the recommended pattern, all three signal types share the same
instance entity.

### 3. Metric Coverage Per Platform

Not every signal is supported on both runtimes. MAL rules emit the same metric names
for both layers where supported; WeChat-only metrics produce data only under the
`WECHAT_MINI_PROGRAM` layer.

| Metric (MAL output, `meter_miniprogram_*`)  | WeChat | Alipay | Source                                                                   |
|---------------------------------------------|--------|--------|--------------------------------------------------------------------------|
| `app_launch_duration`                       | ✓      | ✓      | `wx.getPerformance()` (WeChat) / `App.onLaunch→onShow` fallback (Alipay) |
| `first_render_duration`                     | ✓      | ✓      | PerformanceObserver `firstRender` (WeChat) / `onLoad→onReady` (Alipay)   |
| `first_paint.time` *(passthrough — see note)* | ✓    | —      | PerformanceObserver `firstPaint`. **Wall-clock epoch ms timestamp**, not a duration. Not aggregated by MAL — surfaced only on individual page traces / log queries. |
| `route_duration`                            | ✓      | —      | PerformanceObserver `navigation/route`                                   |
| `script_duration`                           | ✓      | —      | PerformanceObserver `script`                                             |
| `package_load_duration`                     | ✓      | —      | PerformanceObserver `loadPackage`                                        |
| `request_duration_percentile` (P50–P99)     | ✓      | ✓      | `miniprogram.request.duration` histogram (per-flush DELTA)               |
| `error_count`                               | ✓      | ✓      | `js`, `promise`, `ajax` error logs (via LAL)                             |
| `page_not_found_count`                      | ✓      | —      | `pageNotFound` error log (no `my.onPageNotFound` hook on Alipay)         |

**Precision caveat for Alipay perf metrics:** on WeChat, perf values come from the native
`PerformanceObserver` entries. On Alipay, the SDK falls back to lifecycle hooks
(`App.onLaunch→onShow`, `Page.onLoad→onReady`) because the Alipay base library does not
expose `PerformanceObserver` entries for the same events. These are approximations of
"time-to-interactive" rather than authoritative renderer timings. Dashboards should not
compare WeChat and Alipay perf values directly; this is documented in the per-platform
doc pages.

### 3a. OAL / Topology Metrics Emerge From the Layer Mapping

The SDK posts Exit spans (`spanType=Exit`, `spanLayer=Http`) to `/v3/segments` via the
SkyWalking native protocol. After §6's `identifyServiceLayer` mapping assigns
`Layer.WECHAT_MINI_PROGRAM` / `Layer.ALIPAY_MINI_PROGRAM`, the native trace pipeline's
`RPCAnalysisListener` emits `Service`, `ServiceInstance`, `Endpoint` sources — plus
`ServiceRelation` / `ServiceInstanceRelation` / `EndpointRelation` for the
mini-program → backend call edge — all tagged with the mini-program layer.

`core.oal` then produces, for free, under each mini-program layer:

| Metric family | Scope | Source |
|---|---|---|
| `service_cpm`, `service_resp_time`, `service_sla`, `service_percentile`, `service_apdex` | Service | OAL on `Service` source |
| `service_instance_cpm`, `service_instance_resp_time`, `service_instance_sla`, `service_instance_percentile` | ServiceInstance | OAL on `ServiceInstance` source |
| `endpoint_cpm`, `endpoint_resp_time`, `endpoint_sla`, `endpoint_percentile` | Endpoint (page path) | OAL on `Endpoint` source |
| Topology edges to backend services | ServiceRelation / EndpointRelation | `sw8` propagation + backend receives the trace |

These are **in addition to** the MAL-produced `meter_wechat_mp_*` /
`meter_alipay_mp_*` metrics from §4. The service dashboard panels should mix both —
outbound request latency from OAL (`service_resp_time`, `service_percentile`) keyed on
observed response time, plus the SDK's own per-page perf gauges from MAL.

Topology note: mini-programs are leaf sources — they issue outbound requests but never
receive inbound traffic. So each mini-program service has outbound edges (to backend
APIs carrying `sw8`) but no upstream. This is correct by construction for client-side
platforms.

### 3b. Error-Count Metric — Log-MAL Rule

`error_count` in §3's metric table is derived from LAL-processed error logs. The
extraction itself is a separate file in the log-MAL rules directory (not the MAL `otel-rules/`
directory — log-MAL rules convert persisted logs into metric samples):

`oap-server/server-starter/src/main/resources/log-mal-rules/miniprogram.yaml`:

```yaml
expSuffix: service(['service_name'], Layer.WECHAT_MINI_PROGRAM)
metricPrefix: meter_wechat_mp
metricsRules:
  - name: error_count
    exp: miniprogram_error_count.sum(['service_name', 'exception_type'])
---
expSuffix: service(['service_name'], Layer.ALIPAY_MINI_PROGRAM)
metricPrefix: meter_alipay_mp
metricsRules:
  - name: error_count
    exp: miniprogram_error_count.sum(['service_name', 'exception_type'])
```

The sample `miniprogram_error_count` is emitted by a `metrics {}` block in the LAL
rule (§5) — one sample per error log processed, labelled with
`exception_type`, `miniprogram_platform`, `service_name`. The platform attribute lets
the per-layer filter route to the correct `expSuffix`.

Register the log-MAL file alongside the MAL rule file in `application.yml`:

```yaml
log-analyzer:
  default:
    malFiles: ${SW_LOG_MAL_FILES:"<existing defaults>,miniprogram"}
```

### 4. MAL Rules — Per-Platform × Per-Scope, Mirroring the iOS Layout

Following the iOS pattern (`otel-rules/ios/ios-metrickit.yaml` for service-scoped +
`ios-metrickit-instance.yaml` for instance-scoped — service-scoped meters there have no
`service_instance_id` dim so the "overall app health" view is genuinely
fleet-aggregated), this SWIP creates **four files**:

```
oap-server/server-starter/src/main/resources/otel-rules/miniprogram/
├── wechat-mini-program.yaml          # service-scoped
├── wechat-mini-program-instance.yaml # instance-scoped (per release/version)
├── alipay-mini-program.yaml          # service-scoped
└── alipay-mini-program-instance.yaml # instance-scoped
```

Each file has a single `expSuffix` (one Layer, one entity scope) and a `filter` block
that gates on `miniprogram.platform` so traffic from the wrong platform is dropped at
the rule level.

#### `wechat-mini-program.yaml` — service-scoped

```yaml
expSuffix: service(['service_name'], Layer.WECHAT_MINI_PROGRAM)
metricPrefix: meter_wechat_mp
filter: "{ tags -> tags['miniprogram_platform'] == 'wechat' }"

metricsRules:
  - name: app_launch_duration
    exp: miniprogram_app_launch_duration.avg(['service_name'])
  - name: first_render_duration
    exp: miniprogram_first_render_duration.avg(['service_name'])
  # first_paint.time is an epoch-ms timestamp, not a duration — not aggregated.
  - name: route_duration
    exp: miniprogram_route_duration.avg(['service_name'])
  - name: script_duration
    exp: miniprogram_script_duration.avg(['service_name'])
  - name: package_load_duration
    exp: miniprogram_package_load_duration.avg(['service_name'])
  - name: request_duration_percentile
    exp: miniprogram_request_duration_histogram.sum(['service_name', 'le']).histogram().histogram_percentile([50,75,90,95,99])

  # Endpoint-scoped per-page (chained .endpoint(...) overrides expSuffix)
  - name: endpoint_app_launch_duration
    exp: miniprogram_app_launch_duration.avg(['service_name', 'miniprogram_page_path']).endpoint(['service_name'], ['miniprogram_page_path'], Layer.WECHAT_MINI_PROGRAM)
  - name: endpoint_first_render_duration
    exp: miniprogram_first_render_duration.avg(['service_name', 'miniprogram_page_path']).endpoint(['service_name'], ['miniprogram_page_path'], Layer.WECHAT_MINI_PROGRAM)
  - name: endpoint_request_duration_percentile
    exp: miniprogram_request_duration_histogram.sum(['service_name', 'miniprogram_page_path', 'le']).histogram().histogram_percentile([50,75,90,95,99]).endpoint(['service_name'], ['miniprogram_page_path'], Layer.WECHAT_MINI_PROGRAM)
```

#### `wechat-mini-program-instance.yaml` — instance-scoped (per version)

```yaml
expSuffix: instance(['service_name'], ['service_instance_id'], Layer.WECHAT_MINI_PROGRAM)
metricPrefix: meter_wechat_mp_instance
filter: "{ tags -> tags['miniprogram_platform'] == 'wechat' }"

metricsRules:
  - name: app_launch_duration
    exp: miniprogram_app_launch_duration.avg(['service_name', 'service_instance_id'])
  - name: first_render_duration
    exp: miniprogram_first_render_duration.avg(['service_name', 'service_instance_id'])
  - name: route_duration
    exp: miniprogram_route_duration.avg(['service_name', 'service_instance_id'])
  - name: script_duration
    exp: miniprogram_script_duration.avg(['service_name', 'service_instance_id'])
  - name: package_load_duration
    exp: miniprogram_package_load_duration.avg(['service_name', 'service_instance_id'])
  - name: request_duration_percentile
    exp: miniprogram_request_duration_histogram.sum(['service_name', 'service_instance_id', 'le']).histogram().histogram_percentile([50,75,90,95,99])
```

#### `alipay-mini-program.yaml` / `alipay-mini-program-instance.yaml`

Mirror the WeChat files exactly, differing only in:
- `filter`: `tags['miniprogram_platform'] == 'alipay'`
- `expSuffix` Layer: `Layer.ALIPAY_MINI_PROGRAM`
- `metricPrefix`: `meter_alipay_mp` / `meter_alipay_mp_instance`
- **Drop the WeChat-only metrics** Alipay doesn't emit
  (`route_duration`, `script_duration`, `package_load_duration`)

#### Notes

- **Service-scoped rules sum/avg by `service_name` only** — no `service_instance_id`
  fragmentation. This produces the genuine fleet-aggregated view for the "overall app
  health" dashboard panels. iOS's `ios-metrickit.yaml` is the precedent.
- **Instance-scoped rules go in their own file** with `expSuffix: instance(...)`. This
  is what backs per-release / version-regression dashboards.
- **`service_instance_id` source:** SDK ≥ v0.4.0 emits OTLP `service.instance.id` only
  when the operator passes `init({ serviceInstance: ... })`. When unset, the attribute
  is omitted, `SampleFamily.dim()` collapses the `service_instance_id` label to an
  empty string, and `Analyzer.java:345` (`if (!Strings.isNullOrEmpty(entity.getInstanceName()))`)
  short-circuits — **no instance traffic is emitted**, and the per-instance MAL rules
  produce no metrics. The instance dashboard is therefore meaningful only when operators
  follow the recommended `serviceInstance: serviceVersion` pattern. MAL itself can add
  a fail-safe (`tag {tags -> tags.service_instance_id = tags.service_instance_id ?: tags.service_name}`)
  to keep per-instance metrics populated when the operator doesn't set `serviceInstance`,
  but standard practice is to rely on the SDK side.
- **The `.endpoint(...)` chain on service-scoped files** — same expression-level
  override pattern as APISIX (`apisix.yaml:91-102`) and RocketMQ. One rule emits to
  service scope (default from `expSuffix`), the next emits to endpoint scope by
  chaining `.endpoint(...)` at the end.

#### Histogram unit — `miniprogram.request.duration` is in milliseconds

The SDK emits the `miniprogram.request.duration` histogram with `le` bucket bounds
in **milliseconds** (`[10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000]`). MAL's
`SampleFamily.histogram()` default assumes seconds and rescales `le` by
`defaultHistogramBucketUnit.toMillis(1)` (×1000). Without compensation, percentiles
come out 1000× too large — the exact bug that bit MetricKit in SWIP-11 (fixed via
`IOSMetricKitSpanListener` marking its `SampleFamily` with
`defaultHistogramBucketUnit(MILLISECONDS)`).

Mini-program metrics don't go through a custom listener — they arrive via the standard
OTLP metric receiver. Two options at implementation time:

1. **Preferred:** OTLP metric receiver honors the OTLP `Metric.unit` field (`"ms"` for
   this histogram) when building `SampleFamily` — a general-purpose improvement that
   benefits any OTLP ms-native histogram. Scope this as a separate enhancement.
2. **Fallback:** a minimal `OTLPMetricPreprocessor` (or per-metric override in the MAL
   rule file header) that marks matching samples with
   `defaultHistogramBucketUnit(MILLISECONDS)` before MAL sees them.

Either approach must be in place before the request-latency panels are trusted.

#### Finite sentinel for the `+Inf` overflow bucket

The SDK's 11-bucket histogram has an implicit `+Inf` overflow for observations above
10 s. MAL stores `le="Infinity"` as `Long.MAX_VALUE` (≈9.2 × 10¹⁸), which the UI
renders as a visual garbage number when a percentile lands there. SWIP-11 capped
MetricKit hang/launch histograms at 30 s. For mini-program request duration, any
observation > 10 s is already an outlier; the SDK histogram should either be updated to
include a finite overflow bound (e.g. 30 s) or the OAP-side preprocessor should
substitute a finite ceiling before percentile computation. Track as an SDK/OAP
coordination item.

### 5. LAL Rules (Error Logs, `layer: auto` Fork by Platform)

Create `oap-server/server-starter/src/main/resources/lal/miniprogram.yaml`.

Uses the `layer: auto` + `sourceAttribute()` mechanism from SWIP-11:

```yaml
rules:
  - name: miniprogram-errors
    layer: auto
    dsl: |
      filter {
        def platform = sourceAttribute("miniprogram.platform");
        if (platform != "wechat" && platform != "alipay") { abort {} }
        if (tag("exception.type") == null) { abort {} }

        extractor {
          layer platform == "wechat" ? "WECHAT_MINI_PROGRAM" : "ALIPAY_MINI_PROGRAM"
          // Instance source matches what OTLP metrics use, so logs aggregate under
          // the same OAP instance entity when operator follows the recommended
          // serviceInstance == serviceVersion pattern. SDK ≥ v0.4.0 emits
          // service.instance.id only when init({serviceInstance: ...}) is set.
          // If absent, sourceAttribute() returns null/empty → TrafficSinkListener
          // skips the instance traffic, matching MAL's empty-dim behavior.
          instance sourceAttribute("service.instance.id")
          endpoint tag("miniprogram.page.path")

          tag 'platform': platform
          tag 'exception.type': tag("exception.type")
          tag 'exception.stacktrace': tag("exception.stacktrace")
          // ajax-specific extras, nullable
          tag 'http.method': tag("http.request.method")
          tag 'http.status': tag("http.response.status_code")
          tag 'server.address': tag("server.address")

          // Emit a counter sample for every error log. Picked up by the log-MAL
          // rule in §3b, which aggregates per (service, exception_type) for the
          // per-layer error_count metric.
          metrics {
            miniprogram_error_count {
              tag('service_name', log.service)
              tag('exception_type', tag("exception.type"))
              tag('miniprogram_platform', platform)
              value 1
            }
          }
        }

        sink { }
      }
```

The rule sets the layer script-side based on `miniprogram.platform`, so **one rule file
produces two layers**. Error counts per service / instance / endpoint / exception.type
can be derived via existing OAL log-metric machinery.

### 6. Trace Segment Handling — Component-Driven Layer Mapping

The SDK posts `SegmentObject` directly to `/v3/segments` (SkyWalking native protocol).
These segments are parsed by the normal trace pipeline — no new SPI is needed.

Service-layer assignment already lives in
`CommonAnalysisListener.identifyServiceLayer(SpanLayer)` (a `protected` instance method
on the abstract base shared by `RPCAnalysisListener` and
`EndpointDepFromCrossThreadAnalysisListener` in the agent-analyzer module —
`SegmentAnalysisListener` does **not** extend `CommonAnalysisListener`, it has its own
service-meta path). Today it
maps `SpanLayer.FAAS → Layer.FAAS` and everything else to `Layer.GENERAL`. Extend it to
also accept the span's `componentId` (which the SDK already sets on every outbound span)
and dispatch to the mini-program layers.

The component name → id mapping lives in `component-libraries.yml` and is exposed only
at runtime via `IComponentLibraryCatalogService`
(`ComponentLibraryCatalogService.java:84-104`) — there are no auto-generated Java
constants. So the listener's abstract base resolves the two ids once at construction
time and caches them as `int` fields:

```java
abstract class CommonAnalysisListener {
    private final int wechatMiniProgramComponentId;
    private final int alipayMiniProgramComponentId;

    protected CommonAnalysisListener(IComponentLibraryCatalogService catalog) {
        this.wechatMiniProgramComponentId = catalog.getComponentId("WeChat-MiniProgram");
        this.alipayMiniProgramComponentId = catalog.getComponentId("AliPay-MiniProgram");
    }

    protected Layer identifyServiceLayer(SpanLayer spanLayer, int componentId) {
        if (componentId == wechatMiniProgramComponentId) {
            return Layer.WECHAT_MINI_PROGRAM;
        }
        if (componentId == alipayMiniProgramComponentId) {
            return Layer.ALIPAY_MINI_PROGRAM;
        }
        if (SpanLayer.FAAS.equals(spanLayer)) {
            return Layer.FAAS;
        }
        return Layer.GENERAL;
    }
}
```

`IComponentLibraryCatalogService` is already a `CoreModule` service, so wiring it into
the listener factories is one constructor parameter. Each of the 5 existing call sites
in `RPCAnalysisListener` (×4) and `EndpointDepFromCrossThreadAnalysisListener` (×1)
adds `span.getComponentId()` to its current `identifyServiceLayer(span.getSpanLayer())`
call.

No new SPI, no new listener registration — all the work happens inside the existing
`SegmentParserListenerManager` pipeline.

**Persistence:** default `true` — unlike iOS MetricKit spans (which represent 24-hour
windows and must be suppressed), mini-program segments are real outgoing HTTP spans
that belong in the trace UI.

### 7. Component Library Entries

Add to `oap-server/server-starter/src/main/resources/component-libraries.yml`, in the
JavaScript block `[10000, 11000)`:

```yaml
WeChat-MiniProgram:
  id: 10002
  languages: JavaScript
AliPay-MiniProgram:
  id: 10003
  languages: JavaScript
```

**Status:** as of mini-program-monitor v0.3.0, the SDK already emits these component
ids on every exit span. Without the OAP-side registration, current OAP releases render
them as "N/A" in topology even though the tag data is captured. Adding the two entries
to `component-libraries.yml` is the unblock for proper topology rendering and is what
makes the layer mapping in §6 effective.

### 8. SDK-Side Convention

All three originally-proposed SDK conventions are resolved as of mini-program-monitor
**v0.4.0** (released):

| Convention                                              | Status                                                                                   |
|---------------------------------------------------------|------------------------------------------------------------------------------------------|
| Per-platform `componentId` on exit spans (`10002` / `10003`) | ✅ shipped in v0.3.0                                                                |
| `miniprogram.platform` span tag on every exit span      | ✅ shipped in v0.3.0                                                                     |
| Drop auto-generated per-device `serviceInstance`        | ✅ shipped in v0.4.0 — `serviceInstance` defaults to unset; OTLP omits `service.instance.id`, native segments substitute `-` (protocol-mandatory field) |
| Recommend version-scoped `serviceInstance`              | ✅ documented in SDK README / SIGNALS / SAMPLES + e2e CI pins it to `service.version`    |

The originally-imagined "default to `service.version`" was rejected upstream in favor
of a cleaner shape: the SDK has no opinion on what `serviceInstance` should be, but its
docs explicitly recommend a version-scoped value (mirroring `service.version` or a
release tag). When operators leave it unset, OTLP simply omits `service.instance.id` —
spec-allowed (it's RECOMMENDED, not REQUIRED) — and OAP aggregates at the service level.

This also means **no need for a `miniprogram.device` span tag fallback** — the device-id
problem is gone at the source. Operators that genuinely need per-session granularity
can still pass `init({ serviceInstance: '<their-id>' })` themselves.

### 9. UI Menu and Dashboards

#### Menu

Extend the existing `Mobile` menu group (added in SWIP-11) in
`oap-server/server-starter/src/main/resources/ui-initialized-templates/menu.yaml`:

```yaml
- title: Mobile
  icon: mobile
  menus:
    - title: iOS
      layer: IOS
      ...
    - title: WeChat Mini Program
      layer: WECHAT_MINI_PROGRAM
      description: WeChat Mini Program monitoring via mini-program-monitor SDK.
      documentLink: https://skywalking.apache.org/docs/main/next/en/setup/backend/backend-wechat-mini-program-monitoring/
      i18nKey: wechat_mini_program
    - title: Alipay Mini Program
      layer: ALIPAY_MINI_PROGRAM
      description: Alipay Mini Program monitoring via mini-program-monitor SDK.
      documentLink: https://skywalking.apache.org/docs/main/next/en/setup/backend/backend-alipay-mini-program-monitoring/
      i18nKey: alipay_mini_program
```

#### Dashboards

`UITemplateInitializer` only loads folders listed in its hard-coded
`UI_TEMPLATE_FOLDER` array, and the folder name is `Layer.X.name().toLowerCase()`
(`UITemplateInitializer.java:45-88,101-103`). Two changes are required:

1. **Append the new layers to the allowlist** in
   `oap-server/server-core/src/main/java/.../UITemplateInitializer.java`:
   ```java
   public static String[] UI_TEMPLATE_FOLDER = new String[] {
       // ... existing entries ...
       Layer.IOS.name(),
       Layer.WECHAT_MINI_PROGRAM.name(),
       Layer.ALIPAY_MINI_PROGRAM.name(),
       "custom"
   };
   ```
2. **Create the folders with underscored names** (matching `Layer.name().toLowerCase()`),
   and **include a layer-root template** for each platform — `Layer.vue:41-44` requires
   a dashboard with `isRoot: true` to render the menu landing page (see existing
   `ios/ios-root.json` for the precedent). Without the root template, clicking the menu
   item shows an empty "no dashboard" view:
   ```
   oap-server/server-starter/src/main/resources/ui-initialized-templates/
   ├── wechat_mini_program/
   │   ├── wechat_mini_program-root.json      # isRoot: true — service-list landing page
   │   ├── wechat_mini_program-service.json
   │   ├── wechat_mini_program-instance.json
   │   └── wechat_mini_program-endpoint.json
   └── alipay_mini_program/
       ├── alipay_mini_program-root.json      # isRoot: true — service-list landing page
       ├── alipay_mini_program-service.json
       ├── alipay_mini_program-instance.json
       └── alipay_mini_program-endpoint.json
   ```
   Hyphenated folder names (e.g. `wechat-mini-program/`) are silently skipped because
   they don't match `Layer.WECHAT_MINI_PROGRAM.name().toLowerCase()`.

Structure mirrors the iOS dashboards but **adds a trace widget** — because native
segments are queryable in the normal trace UI (unlike iOS's OTLP→Zipkin path).

Metric names below use per-platform prefixes from §4 (`meter_wechat_mp` /
`meter_wechat_mp_instance` / `meter_alipay_mp` / `meter_alipay_mp_instance`). The
WeChat dashboard pulls from `meter_wechat_mp_*`; the Alipay dashboard pulls from
`meter_alipay_mp_*`.

**Per-service dashboard panels (WeChat):**

| Panel Group    | Widgets                                                                                                                | Notes                                                                            |
|----------------|------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| App Launch     | `meter_wechat_mp_app_launch_duration`                                                                                  |                                                                                  |
| Page Render    | `meter_wechat_mp_first_render_duration`                                                                                | `first_paint.time` is an epoch-ms timestamp, not aggregated by MAL — see §3      |
| Navigation     | `meter_wechat_mp_route_duration`, `meter_wechat_mp_script_duration`, `meter_wechat_mp_package_load_duration`           | WeChat-only metrics — these panels are absent from the Alipay dashboard          |
| Request Perf   | `meter_wechat_mp_request_duration_percentile` (P50/P75/P90/P95/P99)                                                    |                                                                                  |
| Errors         | Error count by `exception.type`; top error endpoints                                                                   | Derived from LAL-processed logs                                                  |
| **Traces**     | Native trace list for the in-scope service (service list is layer-filtered upstream); endpoint trace drill-down        | **Mini-program only** — iOS dashboards lack this because iOS traces go to Zipkin |

**Per-service dashboard panels (Alipay):** same shape as WeChat, but only includes the
metrics Alipay actually emits (`app_launch_duration`, `first_render_duration`,
`request_duration_percentile`, errors, traces). The Navigation row and the Page Render
row's WeChat-only `first_paint` mention are absent.

**Per-instance (version) dashboard:** same metric set scoped to the service instance —
backed by `meter_wechat_mp_instance_*` / `meter_alipay_mp_instance_*` (§4).

**Per-endpoint (page) dashboard:** uses the chained-`.endpoint(...)` per-page metrics
from §4 (`endpoint_app_launch_duration`, `endpoint_first_render_duration`,
`endpoint_request_duration_percentile`), plus per-page error list.

#### UI Side

A separate PR in [skywalking-booster-ui](https://github.com/apache/skywalking-booster-ui)
is needed for i18n entries for the two new sub-menus.

### 10. Data Generator for skywalking-showcase

mini-program-monitor v0.3.0 ships a **simulator ecosystem** as a first-class deliverable,
not a separate harness — and it already publishes multi-arch (`linux/amd64`, `linux/arm64`)
images per commit:

- `ghcr.io/skyapm/mini-program-monitor/sim-wechat:<sha-or-version>`
- `ghcr.io/skyapm/mini-program-monitor/sim-alipay:<sha-or-version>`

[skywalking-showcase](https://github.com/apache/skywalking-showcase) consumes these
images directly — no new image to build, no driver scripts to maintain in the showcase
repo. Just two service entries pointing at OAP, e.g.:

```yaml
sim-wechat:
  image: ghcr.io/skyapm/mini-program-monitor/sim-wechat:v0.4.0
  environment:
    MODE: loop
    SCENARIO: demo
    COLLECTOR_URL: http://oap:12800
    TRACE_COLLECTOR_URL: http://oap:12800
    SERVICE: showcase-wechat-mp
    SERVICE_VERSION: v1.0.0
    SERVICE_INSTANCE: v1.0.0           # version-scoped, mirrors SDK recommendation
sim-alipay:
  image: ghcr.io/skyapm/mini-program-monitor/sim-alipay:v0.4.0
  environment:
    MODE: loop
    SCENARIO: demo
    COLLECTOR_URL: http://oap:12800
    TRACE_COLLECTOR_URL: http://oap:12800
    SERVICE: showcase-alipay-mp
    SERVICE_VERSION: v1.0.0
    SERVICE_INSTANCE: v1.0.0
```

**Run modes** (env `MODE`): `loop` (forever, for demo), `timed` (for `DURATION_MS` then
exit), `once` (one of each signal then exit, for CI parity).

**Scenarios** (env `SCENARIO`): `demo` (healthy + all four error surfaces), `baseline`
(steady happy stream), `error-storm` (high error rate + 5xx), `slow-api` (heavy tail
latency).

**Payload cost in the showcase:** at default cadences, well below existing
Java/Python/Go showcase services. Pinning to a specific SHA / version (no `:latest`) is
mandated by the SDK side — the showcase manifest tracks an explicit version.

### 11. OAP-Side E2E Test Case

Separately from the showcase demo generator (§10), an OAP-side e2e test is required
for CI coverage. Add under `test/e2e-v2/cases/`:

```
test/e2e-v2/cases/miniprogram/wechat/e2e.yaml         # wechat sim image as the workload
test/e2e-v2/cases/miniprogram/wechat/expected/        # swctl-format expected outputs
test/e2e-v2/cases/miniprogram/alipay/e2e.yaml         # alipay sim image as the workload
test/e2e-v2/cases/miniprogram/alipay/expected/        # swctl-format expected outputs
```

Each case drives the `ghcr.io/skyapm/mini-program-monitor/sim-{wechat,alipay}:v0.4.0`
image in `MODE=once` (one-shot signal emission, then exit) against an OAP container
wired with the new MAL / LAL / log-MAL rules. Verify steps cover:
- service listed under the correct layer (`swctl service list --layer WECHAT_MINI_PROGRAM`)
- per-platform MAL metrics non-empty (`meter_wechat_mp_app_launch_duration`, …)
- `meter_wechat_mp_error_count` non-zero on the `error-storm` scenario
- endpoint list populated (page paths)
- native trace segments queryable

Register the two cases in `.github/workflows/skywalking.yaml` e2e matrix.

Additionally, every change to `application.yml` defaults made by this SWIP (new
`miniprogram/*` entry in `enabledOtelMetricsRules`, new `miniprogram` entry in
`lalFiles` and `malFiles`) **must** be mirrored in
`test/e2e-v2/cases/storage/expected/config-dump.yml`. The storage e2e diffs
`/debugging/config/dump` output against this file and fails on any default drift.

### 12. Security Notice

Mini-program SDKs run on end-user devices and post telemetry to OAP's OTLP + native-segment
endpoints **from the public internet**, without agent-side authentication. Same
exposure profile as iOS (SWIP-11) and browser-agent. Add a client-side-monitoring
paragraph to `docs/en/security/README.md` covering:

- The recommendation to front OAP with a rate-limiter or WAF for public-facing
  endpoints (`/v1/logs`, `/v1/metrics`, `/v3/segments`).
- The abuse surface (malformed payloads, high-volume senders, fake `sw8` headers)
  and mitigation pointers.
- Explicit mention that per-service authentication for client-side SDKs is out of
  scope for v1; operators who need it should terminate at a gateway.

### 13. Implementation Deliverables Checklist

The design sections above stop at the rule-file / code-file level. The actual PR(s)
implementing this SWIP must also ship:

| Deliverable | Location |
|---|---|
| User-facing doc — WeChat | `docs/en/setup/backend/backend-wechat-mini-program-monitoring.md` |
| User-facing doc — Alipay | `docs/en/setup/backend/backend-alipay-mini-program-monitoring.md` |
| Docs navigation | Two new entries in `docs/menu.yml` under the existing "Mobile" section alongside iOS |
| Changelog | Entry in `docs/en/changes/changes.md` under `#### OAP Server` (feature) and `#### Documentation` (the two guides) |
| SWIP readme | Move this SWIP from "Proposed" to "Accepted" in `docs/en/swip/readme.md` at merge time |
| UI i18n | Separate PR in `apache/skywalking-booster-ui` for i18n keys `wechat_mini_program` / `alipay_mini_program` (§9) |

## Imported Dependencies libs and their licenses

No new OAP-side dependencies. All processing uses existing OTLP receiver, native trace
receiver, OAL, LAL, and MAL infrastructure.

The mini-program-monitor SDK itself (Apache-2.0) is an **external** dependency of the
user's mini-program project, not of OAP. The showcase data generator images bundle the
SDK's compiled JS, same license.

## Compatibility

- **Configuration:** two new layers + a new menu section + new MAL/LAL rule files —
  additive, opt-in.
- **Storage:** no new storage structures. Uses existing trace / metric / log storage.
- **Protocols:** no protocol changes. Uses existing OTLP and SkyWalking native receivers.
- **Layer mapping:** the change to `CommonAnalysisListener.getLayer()` is additive —
  it only redirects traffic carrying the two new component ids; all other segments
  continue to resolve to `Layer.GENERAL` / `Layer.FAAS` as today.
- **Component library:** `10002` and `10003` are newly reserved ids in the JavaScript
  range `[10000, 11000)`; no collision with existing entries.
- **SDK version recommendation:** mini-program-monitor **≥ v0.4.0** is the recommended
  baseline. v0.3.0 also works but with the legacy instance-id behavior below.
  - SDK ≤ v0.2.x emits `componentId = 10001` (ajax-inherited) — its segments resolve
    to `Layer.GENERAL` and do not benefit from this SWIP's layer / topology integration.
    OTLP metrics + logs still flow through MAL / LAL because they key on the
    `miniprogram.platform` resource attribute, which v0.2 already emits.
- **Instance entity behavior across SDK versions:**
  - SDK ≤ v0.3.x auto-generated `service.instance.id = mp-{random}` per session,
    creating one OAP instance entity per device — usually undesirable. Operators on
    v0.3.x can avoid this by passing `init({ serviceInstance: serviceVersion })`
    explicitly.
  - SDK ≥ v0.4.0 leaves `service.instance.id` unset by default. The three signal
    pipelines then handle absence differently (see §2 "Instance coherence" table):
    native segments produce a literal `-` instance entity; OTLP logs and metrics
    create no instance entity at all. Per-instance dashboards are meaningful only
    when the operator sets `serviceInstance`.
  - Recommended operator pattern (SDK docs + e2e CI): set `serviceInstance` to a
    version-scoped value (mirroring `service.version` or a release tag). Then all
    three signal pipelines aggregate under the same OAP instance entity.
  - Dashboards built against pre-v0.4 traffic see a long tail of `mp-*` instance ids;
    after upgrade with no `serviceInstance` set, only the segment-side `-` entity
    remains. Set `serviceInstance` to keep populated per-version dashboards.
- **`server.address` sentinel change in SDK v0.4.0:** when the request URL has no
  parseable `https?://host` prefix, OTLP now omits `server.address` (was `"unknown"`)
  and segments substitute `-` for `peer`. MAL queries that group / filter on
  `server.address == "unknown"` need to union the old sentinel with the new behavior
  for data spanning the v0.3 → v0.4 upgrade boundary.

## General usage docs

### Prerequisites

- Mini program instrumented with [mini-program-monitor](https://github.com/SkyAPM/mini-program-monitor) **≥ v0.4.0** recommended (clean `serviceInstance` defaults). v0.3.0 still works with manual `serviceInstance: serviceVersion` workaround.
- SkyWalking OAP with the changes from this SWIP — OTLP HTTP receiver enabled (default on core REST port 12800), and the two new component ids registered

### Mini Program Setup

```js
// WeChat (app.js)
const { init } = require('mini-program-monitor');
App({
  onLaunch() {
    init({
      service: 'my-mini-program',
      serviceVersion: 'v1.2.0',
      // SDK ≥ v0.4.0 recommendation: set serviceInstance to a version-scoped value
      // (mirroring service.version or a release tag). Leaving it unset means OTLP
      // metrics + logs do not produce an instance entity at all (segments produce a
      // literal `-`); per-version / per-release dashboards need this.
      serviceInstance: 'v1.2.0',
      collector: 'https://<oap-host>',
      platform: 'wechat',                  // optional — auto-detected
      enable: { tracing: true },           // opt-in: SkyWalking native segments
    });
  },
});
```

```js
// Alipay (app.js) — same API, different platform attribute
init({
  service: 'my-mini-program',
  serviceVersion: 'v1.2.0',
  serviceInstance: 'v1.2.0',
  collector: 'https://<oap-host>',
  platform: 'alipay',
  enable: { tracing: true },
});
```

### SkyWalking OAP Configuration

Append the mini-program glob to `enabledOtelMetricsRules` and the LAL file to
`lalFiles` in `application.yml` (preserve the existing defaults — don't replace them):

```yaml
receiver-otel:
  selector: ${SW_OTEL_RECEIVER:default}
  default:
    enabledHandlers: ${SW_OTEL_RECEIVER_ENABLED_HANDLERS:"otlp-traces,otlp-metrics,otlp-logs"}
    enabledOtelMetricsRules: ${SW_OTEL_RECEIVER_ENABLED_OTEL_METRICS_RULES:"<existing defaults>,miniprogram/*"}

log-analyzer:
  selector: ${SW_LOG_ANALYZER:default}
  default:
    lalFiles: ${SW_LOG_LAL_FILES:"<existing defaults>,miniprogram"}
```

`miniprogram/*` picks up all four MAL files under `otel-rules/miniprogram/`. The
existing defaults (distributed with OAP) are a long list including `apisix`,
`ios/*`, `kafka/*`, and the `default` LAL rule — these must be kept; otherwise
non–mini-program workloads lose their MAL / LAL wiring.

Native trace segments (`/v3/segments`) need no additional config — handled by the
existing trace receiver. Layer is assigned automatically from the span's componentId.

### What You'll See

1. **Mobile > WeChat Mini Program** and **Mobile > Alipay Mini Program** menu items
2. **Service list** per platform layer — one row per mini-program
3. **Service dashboard** — launch time, render/paint timings, request percentiles,
   error counts, **trace list**
4. **Instance (version) dashboard** — same metrics scoped to a version, for rollout
   and regression monitoring
5. **Endpoint (page) dashboard** — per-page perf + error list
6. **Trace view** — individual outgoing requests when `enable.tracing = true`, with
   `sw8` propagation joining the mini-program's trace with downstream backend services

### Limitations

- **Alipay perf metrics are lifecycle-based approximations**, not native renderer
  timings. Do not compare WeChat and Alipay perf numbers head-to-head.
- WeChat-only metrics (`first_paint_time`, `route_duration`, `script_duration`,
  `package_load_duration`, `pageNotFound` error) are absent from Alipay dashboards.
- Device-level per-user aggregation is not supported by design — `serviceInstance`
  is intended to be a version-scoped identifier, not per-device. SDK v0.4.0 dropped
  the per-device auto-generator entirely; operators who genuinely need per-session
  granularity can pass any string they want via `init({ serviceInstance: '…' })`,
  but be aware OAP aggregates one instance entity per distinct value.
- WebSocket, memory-warning, and network-status-change signals are not instrumented by
  the current SDK.