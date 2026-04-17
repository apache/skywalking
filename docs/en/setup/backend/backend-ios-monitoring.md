# iOS App Monitoring

SkyWalking supports iOS/iPadOS app monitoring via the [OpenTelemetry Swift SDK](https://github.com/open-telemetry/opentelemetry-swift).
Three data streams are captured:

1. **Outbound HTTP traffic** — per-request client-side metrics for HTTP requests from the iOS app to remote servers (latency, error rate, per-domain breakdown)
2. **MetricKit daily stats** — app launch time percentile, hang time percentile/sum, crash count, OOM kills, memory, network transfer (once per day per device)
3. **MetricKit diagnostic logs** — crash and hang reports with native stack traces

All data is sent via standard OTLP/HTTP to SkyWalking OAP. No OTel Collector is required,
though one can be used for buffering.

## OAP Configuration

Enable the OTLP receiver with trace and log handlers, the iOS MAL rules, and the LAL rules
in `application.yml`:

```yaml
receiver-otel:
  selector: default
  default:
    enabledHandlers: otlp-traces,otlp-logs
    enabledOtelMetricsRules: ios/ios-metrickit,ios/ios-metrickit-instance

log-analyzer:
  selector: default
  default:
    lalFiles: ios-metrickit
```

OTLP/HTTP endpoints are available on the core REST port (default 12800)
at `/v1/traces`, `/v1/logs`, and `/v1/metrics`.

## iOS App Setup

```swift
import OpenTelemetryApi
import OpenTelemetrySdk
import OpenTelemetryProtocolExporterHTTP
import ResourceExtension
import URLSessionInstrumentation

// Resource attributes (device, OS, app info — auto-collected)
let resources = DefaultResources().get()

// OTLP exporter — point to OAP's REST port (default 12800)
// In production, use an OTel Collector or API gateway in front of OAP
let oapEndpoint = "https://<oap-or-collector-host>/v1"
let traceExporter = OtlpHttpTraceExporter(
    endpoint: URL(string: "\(oapEndpoint)/traces")!
)
let logExporter = OtlpHttpLogExporter(
    endpoint: URL(string: "\(oapEndpoint)/logs")!
)

// TracerProvider
let tracerProvider = TracerProviderBuilder()
    .add(spanProcessor: BatchSpanProcessor(spanExporter: traceExporter))
    .with(resource: resources)
    .build()
OpenTelemetry.registerTracerProvider(tracerProvider: tracerProvider)

// LoggerProvider (for MetricKit diagnostics)
let loggerProvider = LoggerProviderBuilder()
    .with(resource: resources)
    .with(processors: [SimpleLogRecordProcessor(logRecordExporter: logExporter)])
    .build()
OpenTelemetry.registerLoggerProvider(loggerProvider: loggerProvider)

// Auto-instrument URLSession (exclude collector URL to avoid feedback loop)
let config = URLSessionInstrumentationConfiguration(
    shouldInstrument: { request in
        return request.url?.host != "<oap-or-collector-host>"
    }
)
let _ = URLSessionInstrumentation(configuration: config)
```

> **Important:** The `shouldInstrument` callback is critical. Without it, the URLSession
> auto-instrumentation captures OTLP export calls themselves, creating an exponential
> feedback loop.

> **Security:** iOS apps send telemetry from the public internet. See
> [Security Notice](../../security/README.md) for deployment guidelines.

## Outbound HTTP Traffic

The `IOSHTTPSpanListener` detects iOS HTTP spans (resource attribute `os.name` is `iOS` or
`iPadOS`, span scope `NSURLSession`, kind `CLIENT`) and emits OAL sources (`Service`,
`ServiceInstance`, `Endpoint`) with `Layer.IOS`. This produces **client-side outbound traffic
metrics** — these measure HTTP requests **from the iOS app to remote servers**, not server-side
processing.

The listener reads HTTP attributes with stable-semconv fallback, so all three OpenTelemetry
Swift URLSession instrumentation modes work:

| Attribute   | Stable key (preferred)        | Legacy key (fallback) |
|-------------|-------------------------------|-----------------------|
| Host        | `server.address`              | `net.peer.name`       |
| Method      | `http.request.method`         | `http.method`         |
| Status code | `http.response.status_code`   | `http.status_code`    |

- **Service** = the iOS app (from `service.name`)
- **Instance** = the app version (from `service.version`)
- **Endpoint** = the remote domain being called (e.g., `api.example.com`)

The existing `core.oal` rules automatically produce standard outbound traffic metrics under the IOS layer:
`service_cpm`, `service_resp_time`, `service_sla`, `service_percentile`,
`service_instance_cpm`, `service_instance_resp_time`,
`endpoint_cpm`, `endpoint_resp_time`, `endpoint_sla`, `endpoint_percentile`.

## MetricKit Metrics

The `IOSMetricKitSpanListener` intercepts MetricKit payload spans (scope `MetricKit`,
span name `MXMetricPayload`) and extracts daily device statistics. It pushes `SampleFamily`
samples into the shared MAL pipeline via `OpenTelemetryMetricRequestProcessor.toMeter()`.
These spans are **not persisted as traces** (24-hour duration is not meaningful in trace view).

Available metrics (prefixed with `meter_ios_`):

| Metric                             | Aggregation          | Description                                                                                           |
|------------------------------------|----------------------|-------------------------------------------------------------------------------------------------------|
| `app_launch_time_percentile`       | histogram_percentile | App launch time (time to first draw) P50-P99. Bucket ceiling 30 s (finite overflow sentinel).         |
| `hang_time_percentile`             | histogram_percentile | UI hang (freeze) duration P50-P99. Bucket ceiling 30 s (finite overflow sentinel).                    |
| `hang_time_sum`                    | sum                  | Total hang time across devices                                                                        |
| `foreground_abnormal_exit_count`   | sum                  | Abnormal (crash) exits while app was in foreground                                                    |
| `background_abnormal_exit_count`   | sum                  | Abnormal exits while app was in background (watchdog kills, background-task crashes)                  |
| `background_oom_kill_count`        | sum                  | Out-of-memory kills (background memory-pressure exits)                                                |
| `peak_memory`                      | max                  | Peak memory usage (worst-case device)                                                                 |
| `cpu_time`                         | sum                  | Cumulative CPU time                                                                                   |
| `gpu_time`                         | sum                  | Cumulative GPU time                                                                                   |
| `disk_write`                       | sum                  | Disk write volume                                                                                     |
| `wifi_download`                    | avg                  | Average per-device WiFi download                                                                      |
| `wifi_upload`                      | avg                  | Average per-device WiFi upload                                                                        |
| `cellular_download`                | avg                  | Average per-device cellular download                                                                  |
| `cellular_upload`                  | avg                  | Average per-device cellular upload                                                                    |
| `scroll_hitch_ratio`               | avg                  | Average scroll jank ratio                                                                             |

Normal-exit counts (`metrickit.app_exit.foreground.normal_app_exit_count`,
`metrickit.app_exit.background.normal_app_exit_count`) are intentionally **not** exposed — graceful
exits carry no diagnostic signal and would only crowd dashboards.

## MetricKit Diagnostic Logs

Crash and hang diagnostics from MetricKit arrive as OTLP log records and are processed by
the `ios-metrickit` LAL rule. The rule uses `layer: auto` mode with `sourceAttribute("os.name")`
to detect iOS logs and set the `IOS` layer.

Diagnostic types:
- `metrickit.diagnostic.crash` — crash reports with exception type, signal, and stack trace
- `metrickit.diagnostic.hang` — hang reports with duration and stack trace
- `metrickit.diagnostic.cpu_exception` — CPU usage exceptions
- `metrickit.diagnostic.disk_write_exception` — disk write exceptions
- `metrickit.diagnostic.app_launch` — slow app launch diagnostics (iOS 16+)

## Limitations

- MetricKit data requires a **real iOS device** — not available in Simulator
- MetricKit stats are delivered approximately once per day — not real-time
- Screen/view transition tracking is not automatic — the OTel Swift SDK does not instrument
  UIViewController or SwiftUI lifecycle
