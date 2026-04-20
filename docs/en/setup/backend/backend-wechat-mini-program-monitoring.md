# WeChat Mini Program Monitoring

SkyWalking supports WeChat Mini Program monitoring via the
[SkyAPM mini-program-monitor SDK](https://github.com/SkyAPM/mini-program-monitor).
The SDK emits three data streams over standard protocols:

1. **OTLP logs** — JS errors, promise rejections, AJAX failures, and `pageNotFound` events
2. **OTLP metrics** — app launch, first render, route, script, and sub-package load durations;
   a `first_paint.time` epoch-ms timestamp; and a delta histogram of outbound request durations
3. **SkyWalking native trace segments** (opt-in) — one `SegmentObject` per sampled outbound
   `wx.request`, with an `sw8` header injected on the wire so downstream backend services join
   the same trace

No new receiver is required — the SDK speaks OTLP/HTTP and the SkyWalking native v3 protocol.

## Prerequisites

- mini-program-monitor **≥ v0.4.0** is the recommended baseline. v0.3.x still works but with
  the legacy per-device `serviceInstance` behavior (see [Compatibility](#compatibility)).
- SkyWalking OAP with the changes from SWIP-12 — OTLP HTTP receiver enabled (default on core
  REST port 12800), the two new component ids registered in `component-libraries.yml`, and the
  four MAL rules under `otel-rules/miniprogram/` enabled.

## OAP Configuration

Append `miniprogram/*` to `enabledOtelMetricsRules` and `miniprogram` to `lalFiles` and
`malFiles` in `application.yml` (preserve the existing defaults — don't replace them). These
entries are included in the shipped defaults, so this section is only relevant if you have
overridden the defaults via `SW_OTEL_RECEIVER_ENABLED_OTEL_METRICS_RULES`, `SW_LOG_LAL_FILES`,
or `SW_LOG_MAL_FILES`:

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
    malFiles: ${SW_LOG_MAL_FILES:"<existing defaults>,miniprogram"}
```

`miniprogram/*` picks up all four MAL files under
`otel-rules/miniprogram/` (per-platform × per-scope, see [Metrics](#metrics)). Native trace
segments (`/v3/segments`) need no additional config — the existing trace receiver handles them
and assigns the `WECHAT_MINI_PROGRAM` layer automatically based on the SDK's
`WeChat-MiniProgram` componentId (10002).

## Mini Program Setup

```js
// app.js
const { init } = require('mini-program-monitor');
App({
  onLaunch() {
    init({
      service: 'my-mini-program',
      serviceVersion: 'v1.2.0',
      // Recommended: set serviceInstance to a version-scoped value (mirroring
      // service.version or a release tag). Leaving it unset means OTLP metrics + logs
      // do not produce an instance entity at all (native segments produce a literal '-');
      // per-version / per-release dashboards need this field populated.
      serviceInstance: 'v1.2.0',
      collector: 'https://<oap-or-collector-host>',
      platform: 'wechat',                  // optional — auto-detected from runtime
      enable: { tracing: true },           // opt-in: SkyWalking native segments
    });
  },
});
```

> **Security:** mini-program SDKs send telemetry from end-user devices over the public
> internet. See the [Security Notice](../../security/README.md) for deployment guidelines.

## Metrics

All MAL metrics are prefixed `meter_wechat_mp_*` (service-scope) or
`meter_wechat_mp_instance_*` (instance-scope). Endpoint-scoped variants, where present, use
the chained `.endpoint(...)` override inside the service-scope file and surface as
`meter_wechat_mp_endpoint_*`.

| Metric                           | Scope                | Source                                                |
|----------------------------------|----------------------|-------------------------------------------------------|
| `app_launch_duration`            | service / instance / endpoint | `wx.getPerformance()` launch entry                    |
| `first_render_duration`          | service / instance / endpoint | PerformanceObserver `firstRender` entry               |
| `route_duration`                 | service / instance   | PerformanceObserver `navigation` / `route` entry      |
| `script_duration`                | service / instance   | PerformanceObserver `script` entry                    |
| `package_load_duration`          | service / instance   | PerformanceObserver `loadPackage` entry               |
| `request_duration_percentile`    | service / instance / endpoint | `miniprogram.request.duration` histogram (P50–P99)    |
| `error_count`                    | service              | LAL-derived counter — log-MAL rule aggregates per exception type |

`first_paint.time` is emitted by the SDK as a wall-clock epoch-ms timestamp (not a duration).
It is passed through as a raw gauge sample but not aggregated by MAL — surface it only on
per-page trace / log views where the absolute timestamp has meaning.

Mini-program native trace segments are client-side (exit-only) — the same shape as browser
JS-agent traces. They flow through OAP's standard `RPCAnalysisListener` pipeline and produce
`ServiceRelation` / `ServiceInstanceRelation` edges to the backend services the mini-program
calls (so topology shows the outbound dependency), but do **not** produce
`service_cpm` / `service_resp_time` / `service_sla` / `endpoint_cpm` / `endpoint_resp_time`
under the mini-program layer — those come from inbound (entry-span) analysis, which
mini-programs don't have. The mini-program service / instance / endpoint entities are
created by MAL (OTLP metrics) and LAL (OTLP logs) instead. All request-load / latency
metrics on the dashboard come from the `miniprogram.request.duration` histogram's
`_count` + bucket families.

## Error Logs

The LAL rule `lal/miniprogram.yaml` uses `layer: auto` mode. It dispatches on the
`miniprogram.platform` resource attribute — one rule file produces both the `WECHAT_MINI_PROGRAM`
and `ALIPAY_MINI_PROGRAM` layers. For each error log, the extractor also emits a
`miniprogram_error_count` counter sample, which the log-MAL rule in `log-mal-rules/miniprogram.yaml`
aggregates into the per-layer `error_count` metric.

Error categories (`exception.type` tag):

| Category           | Source                                                           |
|--------------------|------------------------------------------------------------------|
| `js`               | `wx.onError` — unhandled JS errors                               |
| `promise`          | `wx.onUnhandledRejection` — unhandled promise rejections         |
| `ajax`             | `wx.request` failures (network + non-2xx)                        |
| `pageNotFound`     | `wx.onPageNotFound` — page not found (WeChat only)               |

## Entities

| SkyWalking Entity | Source                            | Cardinality       | Rationale                                              |
|-------------------|-----------------------------------|-------------------|--------------------------------------------------------|
| Service           | `service.name`                    | 1 per app         | Fleet-wide app health                                  |
| ServiceInstance   | `service.instance.id` (pattern: set to `service.version`) | tens per app | Version regression / rollout monitoring |
| Endpoint          | `miniprogram.page.path`           | dozens per app    | Which in-app page is slow / error-prone                |

Per-device `service.instance.id` is intentionally not used as an aggregation dimension —
unbounded cardinality means millions of entities on any real user base. The SDK (≥ v0.4.0)
no longer auto-generates a device id; operators set `serviceInstance` to a version-scoped value.

## Compatibility

- **SDK ≤ v0.2.x** emits `componentId = 10001` (ajax-inherited). Its segments resolve to
  `Layer.GENERAL` and do not benefit from this integration's layer / topology work. OTLP
  metrics + logs still flow through MAL / LAL because they key on the `miniprogram.platform`
  resource attribute, which v0.2 already emits.
- **SDK ≤ v0.3.x** auto-generates `service.instance.id = mp-{random}` per session, creating
  one OAP instance entity per device — usually undesirable. Operators on v0.3.x can avoid this
  by passing `init({ serviceInstance: serviceVersion })` explicitly.
- **SDK ≥ v0.4.0** leaves `service.instance.id` unset by default. The three signal pipelines
  then handle absence differently: native segments produce a literal `-` instance entity; OTLP
  logs and metrics create no instance entity at all. Per-instance dashboards are meaningful
  only when the operator sets `serviceInstance`.
- The recommended pattern (SDK docs + e2e CI) is to set `serviceInstance` to a version-scoped
  value (mirroring `service.version` or a release tag). Then all three signal pipelines
  aggregate under the same OAP instance entity.

## Dashboards

- **WeChat Mini Program** in the **Mobile** menu group — service list landing page.
- **Per-service** dashboard — launch / render / route / script / package-load durations,
  request latency percentile, outbound traffic (load, avg latency, success rate), error count,
  plus tabs for Instance, Endpoint, Trace, and Log drill-down.
- **Per-instance (version)** dashboard — same metric set scoped to a release.
- **Per-endpoint (page)** dashboard — per-page perf, outbound traffic, request latency
  percentile.

## Limitations

- WebSocket, memory-warning, and network-status-change signals are not instrumented by the
  current SDK.
- Device-level per-user aggregation is not supported by design — `serviceInstance` is intended
  to be a version-scoped identifier.
- `first_paint.time` is an epoch-ms timestamp, not a duration. It is not aggregated by MAL.
