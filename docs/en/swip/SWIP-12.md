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
endpoint entity convention, a `SpanListener` for native trace segments, menu/dashboard
support, and a data generator for skywalking-showcase.

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
| **ServiceInstance** | `service.version`               | tens per app      | Version regression / rollout monitoring (per SWIP-11)  |
| **Endpoint**      | `miniprogram.page.path`           | dozens per app    | Which in-app page is slow / errors — matches browser-agent semantics |

**What we deliberately drop:**

| Dropped                                              | Instead used as                                    | Why                                                           |
|------------------------------------------------------|----------------------------------------------------|---------------------------------------------------------------|
| `service.instance.id` (device-level, `mp-ax91z2pf`)  | Span tag on trace segments only                    | Unbounded cardinality — millions for any real user base       |
| `server.address` (remote host for outbound requests) | Metric label on `miniprogram.request.duration` + `peer` on segments | Not a mini-program entity; topology handles it via tracing |

All three entities are needed — each answers a distinct question:
- **Service** → how is the app doing overall?
- **Instance (= version)** → did v1.3 regress vs v1.2?
- **Endpoint (= page)** → which page is slow / error-prone?

Skipping any of them loses a class of question.

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

### 4. MAL Rules (Shared File, Fork by Platform)

Create `oap-server/server-starter/src/main/resources/otel-rules/miniprogram/miniprogram.yaml`.

A single MAL file handles both platforms. The `miniprogram.platform` resource attribute
selects the output layer via conditional `expSuffix`:

```yaml
# WeChat series
expSuffix: service(['service_name'], Layer.WECHAT_MINI_PROGRAM)
metricPrefix: meter_miniprogram
filter: "{ tags -> tags['miniprogram_platform'] == 'wechat' }"

metricsRules:
  - name: app_launch_duration
    exp: miniprogram_app_launch_duration.sum(['service_name', 'service_instance_id', 'miniprogram_page_path']).avg(['service_name', 'service_instance_id', 'miniprogram_page_path'])
  - name: first_render_duration
    exp: miniprogram_first_render_duration.avg(['service_name', 'service_instance_id', 'miniprogram_page_path'])
  # first_paint.time is an epoch-ms timestamp, not a duration — averaging is meaningless,
  # so it's NOT exposed as a MAL metric. Available via raw OTLP query / trace correlation.
  - name: route_duration
    exp: miniprogram_route_duration.avg(['service_name', 'service_instance_id', 'miniprogram_page_path'])
  - name: script_duration
    exp: miniprogram_script_duration.avg(['service_name', 'service_instance_id', 'miniprogram_page_path'])
  - name: package_load_duration
    exp: miniprogram_package_load_duration.avg(['service_name', 'service_instance_id', 'miniprogram_page_path'])
  - name: request_duration_percentile
    exp: miniprogram_request_duration_histogram.sum(['service_name', 'service_instance_id', 'miniprogram_page_path', 'le']).histogram().histogram_percentile([50,75,90,95,99])
---
# Alipay series — only the metrics Alipay actually emits
expSuffix: service(['service_name'], Layer.ALIPAY_MINI_PROGRAM)
metricPrefix: meter_miniprogram
filter: "{ tags -> tags['miniprogram_platform'] == 'alipay' }"

metricsRules:
  - name: app_launch_duration
    exp: miniprogram_app_launch_duration.avg(['service_name', 'service_instance_id', 'miniprogram_page_path'])
  - name: first_render_duration
    exp: miniprogram_first_render_duration.avg(['service_name', 'service_instance_id', 'miniprogram_page_path'])
  - name: request_duration_percentile
    exp: miniprogram_request_duration_histogram.sum(['service_name', 'service_instance_id', 'miniprogram_page_path', 'le']).histogram().histogram_percentile([50,75,90,95,99])
```

Notes:
- `service_instance_id` in MAL = OTLP resource `service.version` (per §2). The OTLP
  receiver's label normalization already maps `service.version` → `service_instance_id`
  when `service.instance.id` is absent or device-ephemeral. (SDK guidance in §7: omit
  `service.instance.id` or set it to the version; device id goes on span tags only.)
- `miniprogram_page_path` is a metric label, producing **endpoint-scoped** metrics via
  OAP's label-to-endpoint mapping.

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
          instance sourceAttribute("service.version")
          endpoint tag("miniprogram.page.path")

          tag 'platform': platform
          tag 'exception.type': tag("exception.type")
          tag 'exception.stacktrace': tag("exception.stacktrace")
          // ajax-specific extras, nullable
          tag 'http.method': tag("http.request.method")
          tag 'http.status': tag("http.response.status_code")
          tag 'server.address': tag("server.address")
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
`CommonAnalysisListener.getLayer(SpanLayer)` in the agent-analyzer module, which maps
`SpanLayer.FAAS → Layer.FAAS` and everything else to `Layer.GENERAL`. Extend the same
method to also look at the span's component id, which the SDK already sets on every
outbound span:

```java
public static Layer getLayer(SpanLayer spanLayer, int componentId) {
    if (componentId == ComponentsDefine.WECHAT_MINI_PROGRAM.getId()) {
        return Layer.WECHAT_MINI_PROGRAM;
    }
    if (componentId == ComponentsDefine.ALIPAY_MINI_PROGRAM.getId()) {
        return Layer.ALIPAY_MINI_PROGRAM;
    }
    if (SpanLayer.FAAS.equals(spanLayer)) {
        return Layer.FAAS;
    }
    return Layer.GENERAL;
}
```

This requires two new component-library entries (see §7) and a small change to the
`getLayer()` call sites so they pass the componentId. No new SPI, no new listener
registration — all the work happens inside the existing
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

mini-program-monitor v0.3.0 already lands two of the three originally-proposed SDK
conventions:

| Convention                                                    | Status in v0.3.0                          |
|---------------------------------------------------------------|-------------------------------------------|
| Per-platform `componentId` on exit spans (`10002` / `10003`)  | ✅ shipped                                |
| `miniprogram.platform` span tag on every exit span            | ✅ shipped                                |
| Default `serviceInstance` to `service.version` (not device id)| ❌ pending — SDK still uses `autoInstance()` |

**Remaining ask** for a future SDK release: change the default `serviceInstance` from
the auto-generated device-style id (`mp-ax91z2pf`) to `service.version`, so OTLP metrics
and trace segments aggregate under the same instance entity. The device id should move
to a span tag (e.g., `miniprogram.device`) for per-session trace filtering. Operators
can already work around this today by passing `serviceInstance: serviceVersion` in their
own `init({...})` call; documenting this in the per-platform docs (§9 doc page) covers
the gap until the SDK default changes.

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

Create templates under `ui-initialized-templates/wechat-mini-program/` and
`ui-initialized-templates/alipay-mini-program/`. Structure mirrors the iOS dashboards
but **adds a trace widget** — because native segments are queryable in the normal trace
UI (unlike iOS's OTLP→Zipkin path).

**Per-service dashboard panels:**

| Panel Group    | Widgets                                                                                   | Notes                                       |
|----------------|-------------------------------------------------------------------------------------------|---------------------------------------------|
| App Launch     | `meter_miniprogram_app_launch_duration`                                                   | Both platforms                              |
| Page Render    | `meter_miniprogram_first_render_duration`, `first_paint_time`*                            | *WeChat only                                |
| Navigation     | `route_duration`*, `script_duration`*, `package_load_duration`*                           | *WeChat only — hidden on Alipay dashboard   |
| Request Perf   | `meter_miniprogram_request_duration_percentile` (P50/P75/P90/P95/P99)                     | Both platforms                              |
| Errors         | Error count by `exception.type`; top error endpoints                                      | Derived from LAL-processed logs             |
| **Traces**     | Native trace list filtered by layer; endpoint trace drill-down                            | **Mini-program only** — iOS dashboards lack this because iOS traces go to Zipkin |

**Per-instance (version) dashboard:** same metrics scoped to the service instance —
version regression views.

**Per-endpoint (page) dashboard:** same metrics scoped to page, plus per-page error list.

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
  image: ghcr.io/skyapm/mini-program-monitor/sim-wechat:v0.3.0
  environment:
    MODE: loop
    SCENARIO: demo
    COLLECTOR_URL: http://oap:12800
    TRACE_COLLECTOR_URL: http://oap:12800
    SERVICE: showcase-wechat-mp
    SERVICE_VERSION: v1.0.0
sim-alipay:
  image: ghcr.io/skyapm/mini-program-monitor/sim-alipay:v0.3.0
  environment:
    MODE: loop
    SCENARIO: demo
    COLLECTOR_URL: http://oap:12800
    TRACE_COLLECTOR_URL: http://oap:12800
    SERVICE: showcase-alipay-mp
    SERVICE_VERSION: v1.0.0
```

**Run modes** (env `MODE`): `loop` (forever, for demo), `timed` (for `DURATION_MS` then
exit), `once` (one of each signal then exit, for CI parity).

**Scenarios** (env `SCENARIO`): `demo` (healthy + all four error surfaces), `baseline`
(steady happy stream), `error-storm` (high error rate + 5xx), `slow-api` (heavy tail
latency).

**Payload cost in the showcase:** at default cadences, well below existing
Java/Python/Go showcase services. Pinning to a specific SHA / version (no `:latest`) is
mandated by the SDK side — the showcase manifest tracks an explicit version.

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
- **SDK version dependency:** mini-program-monitor **≥ v0.3.0** is required for the
  per-platform `componentId` and `miniprogram.platform` span tag. SDK ≤ v0.2.x emits
  `componentId = 10001` (ajax-inherited) — its segments resolve to `Layer.GENERAL` and
  do not benefit from this SWIP's layer/topology integration. OTLP metrics + logs from
  v0.2.x still flow through MAL/LAL normally because they key on `miniprogram.platform`
  resource attribute, which v0.2 already emits.
- **SDK change:** the instance-id/version convention change (§7) is SDK-internal, not a
  wire format change. Older SDK versions continue to work but produce misaligned
  instance entities between metrics and traces — documented as a known limitation for
  pre-fix SDK versions.

## General usage docs

### Prerequisites

- Mini program instrumented with [mini-program-monitor](https://github.com/SkyAPM/mini-program-monitor) **≥ v0.3.0** (per-platform componentId + `miniprogram.platform` span tag)
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
      // Workaround until SDK default changes: align serviceInstance with version so
      // OTLP metrics and SW native segments aggregate under the same instance entity.
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

```yaml
receiver-otel:
  selector: ${SW_OTEL_RECEIVER:default}
  default:
    enabledHandlers: ${SW_OTEL_RECEIVER_ENABLED_HANDLERS:"otlp-metrics,otlp-logs"}
    enabledOtelMetricsRules: ${SW_OTEL_RECEIVER_RULES:"miniprogram/miniprogram"}

log-analyzer:
  selector: ${SW_LOG_ANALYZER:default}
  default:
    lalFiles: ${SW_LOG_LAL_FILES:"miniprogram"}
```

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
- Device-level per-user aggregation is not supported — instance is version, not device.
  Individual user sessions are filterable in the trace view via the `miniprogram.device`
  span tag.
- WebSocket, memory-warning, and network-status-change signals are not instrumented by
  the current SDK.