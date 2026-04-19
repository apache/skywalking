# SWIP-11 Support iOS App Monitoring via OpenTelemetry

## Motivation
iOS (including iPadOS) is one of the most important client-side platforms. Monitoring iOS app performance
— HTTP request latency, crash rates, app launch time — is as important as browser monitoring, which
SkyWalking has supported since v8.x.

The [OpenTelemetry Swift SDK](https://github.com/open-telemetry/opentelemetry-swift) (v2.3.0, tracing
stable) provides auto-instrumentation for iOS apps including HTTP request tracing (URLSession), device/OS
resource attributes, and Apple MetricKit integration. All data is exported via standard OTLP.

Unlike browser monitoring which requires a custom SkyWalking protocol (`BrowserPerf.proto`) and a dedicated
receiver plugin, the OTel Swift SDK speaks standard OTLP. SkyWalking already has an OTLP receiver, so this
feature primarily requires layer detection, a MetricKit span analyzer, LAL rules for crash diagnostics, and
UI dashboards.

This SWIP also establishes a **Mobile** menu group in the UI, preparing for future Android monitoring
(via [opentelemetry-android](https://github.com/open-telemetry/opentelemetry-android)).

## Architecture Graph

```
┌──────────────────────┐                        ┌─────────────────────────────────────────────┐
│  iOS App             │     OTLP/HTTP          │  SkyWalking OAP                             │
│  + OTel Swift SDK    │  ────────────────────> │                                             │
│                      │     (port 4318)        │  ┌───────────────────────────────────────┐   │
│  Instrumentation:    │                        │  │ otel-receiver                         │   │
│  • URLSession (auto) │                        │  │                                       │   │
│  • MetricKit  (auto) │                        │  │  Trace Handler                        │   │
│                      │                        │  │  ├─ detect os.name=iOS → Layer.IOS    │   │
│  Signals:            │                        │  │  ├─ HTTP spans → SpanForward → OAL    │   │
│  1. HTTP trace spans │                        │  │  └─ MetricKit spans                   │   │
│  2. MetricKit spans  │                        │  │      → iOSMetricKitAnalyzer (new)     │   │
│  3. Diagnostic logs  │                        │  │        → extract as metrics            │   │
│                      │                        │  │                                       │   │
│  Resource attrs:     │                        │  │  Log Handler (modified)                │   │
│  os.name=iOS         │                        │  │  ├─ pass resource attrs as             │   │
│  device.model.id=... │                        │  │  │  sourceAttributes on LogMetadata    │   │
│  service.name=MyApp  │                        │  │  └─ LAL script determines layer       │   │
│  (NO service.layer)  │                        │  │     from sourceAttribute("os.name")    │   │
└──────────────────────┘                        │  └───────────────────────────────────────┘   │
                                                └─────────────────────────────────────────────┘
```

No OTel Collector is required, though one can be used for buffering.

**Key challenge:** The OTel Swift SDK does not set `service.layer` or `service.instance.id` — and
this is common for most OTLP sources. Rather than hardcoding layer inference in the handler, this
SWIP introduces a general-purpose mechanism: `sourceAttributes` on `LogMetadata` + LAL script-level
layer assignment.

## Proposed Changes

### 1. New Layer: `IOS`

Add in `Layer.java`:
```java
/**
 * iOS/iPadOS app monitoring via OpenTelemetry Swift SDK
 */
IOS(47, true),
```

Normal layer (`isNormal=true`) because the iOS app is directly instrumented.

### 2. Source Attributes on LogMetadata (General Enhancement)

OTLP resource attributes (e.g., `os.name`, `device.model.identifier`) are currently extracted by
`OpenTelemetryLogHandler` to read `service.name`, `service.layer`, `service.instance.id`, then
**discarded**. They are not passed into LogData tags and not available to LAL scripts.

This is a problem not only for iOS but for any OTLP source where `service.layer` is absent — the
LAL script has no information to determine the layer.

#### Solution: `sourceAttributes` on `LogMetadata`

Add a non-persistent `sourceAttributes` field to `LogMetadata` (Java bean, not proto):

```java
@Data
@Builder
public class LogMetadata {
    private String service;
    private String serviceInstance;
    private String endpoint;
    private String layer;
    private long timestamp;
    @Builder.Default
    private TraceContext traceContext = TraceContext.EMPTY;

    /**
     * Non-persistent attributes from the log source (e.g., OTLP resource attributes,
     * ALS node context). Available to LAL scripts via sourceAttribute() but NOT stored
     * in tagsRawData.
     */
    @Builder.Default
    private Map<String, String> sourceAttributes = Collections.emptyMap();
}
```

**Why `sourceAttributes` not `resourceAttributes`:** Different receivers have different source
contexts — OTLP has resource attributes, Envoy ALS has node info, etc. `sourceAttributes` is
generic.

**Why on `LogMetadata` not `LogData`:** `LogData` is a proto object (from `Logging.proto`). Its
`tags` field gets serialized into `tagsRawData` and persisted to storage. `LogMetadata` is a Java
bean used only as a transient carrier during LAL processing — adding fields here has no storage
impact.

#### Handler Change: `OpenTelemetryLogHandler`

Pass all resource attributes into `LogMetadata.sourceAttributes`:

```java
// Existing: extract specific fields from resource attributes
final var service = attributes.get("service.name");
final var layer = attributes.getOrDefault("service.layer", "");
final var serviceInstance = attributes.getOrDefault("service.instance.id", "");

// New: pass ALL resource attributes as sourceAttributes
final var metadata = LogMetadata.builder()
    .service(service)
    .serviceInstance(serviceInstance)
    .layer(layer)
    .timestamp(logRecord.getTimeUnixNano() / 1_000_000)
    .sourceAttributes(attributes)   // <-- all resource attrs, non-persistent
    .build();
logAnalyzerService().doAnalysis(metadata, logDataBuilder);
```

#### LAL DSL: `sourceAttribute()` Function

Add a new function to the LAL DSL that reads from `LogMetadata.sourceAttributes`:

```
sourceAttribute("os.name")        → "iOS"
sourceAttribute("os.version")     → "17.4.1"
sourceAttribute("device.model.identifier") → "iPhone15,2"
```

This is similar to `tag()` but reads from the non-persistent source context instead of LogData tags.

### 3. LAL Script-Level Layer Assignment (`layer: auto`)

Currently, `layer` in a LAL rule YAML serves as both a routing key (only rules matching the log's
layer are evaluated) and output metadata. This creates a chicken-and-egg problem: a rule that wants
to SET the layer cannot be reached if the layer is absent.

#### Solution: `layer: auto` mode

A new `layer: auto` declaration indicates the layer is **determined by the script**. Rules with
`layer: auto` match logs where `service.layer` is absent (empty/unset). The script is expected to
set the layer in the extractor:

```yaml
rules:
  - name: ios-metrickit-diagnostics
    layer: auto                   # layer determined by script; dropped if not set
    dsl: |
      filter {
        // Determine if this is an iOS log
        if (sourceAttribute("os.name") != "iOS" && sourceAttribute("os.name") != "iPadOS") {
          abort {}
        }

        extractor {
          layer IOS               # LAL script sets the layer
          // ...
        }
        sink { }
      }
```

**Drop policy:** In `auto` mode, if the script does not set the layer (either because the script
aborted or because the extractor omitted `layer`), the log is **warned and dropped** at persistence.
`layer: auto` means "I take responsibility for setting the layer" — if no layer is set, it's either
a non-matching log (abort) or a script bug (warn).

This enforces that every OTLP log source either:
1. Sets `service.layer` explicitly (like Envoy AI Gateway), OR
2. Has a matching `layer: auto` LAL rule that determines the layer from source attributes

**Backward compatibility:** Existing OTLP log sources that set `service.layer` are unaffected —
their logs have a concrete layer and are routed to layer-specific rules as before. `layer: auto`
rules only see logs with absent layer. The existing `default.yaml` rule (`layer: GENERAL`) continues
to catch logs that have `layer = GENERAL`.

### 4. Resource Attributes Available to LAL (via `sourceAttribute()`)

The OTel Swift SDK sets the following resource attributes, all available via `sourceAttribute()`:

| Resource Attribute         | Example Value          | Source                                |
|----------------------------|------------------------|---------------------------------------|
| `os.name`                  | `iOS`, `iPadOS`, `macOS` | `UIDevice.current.systemName`       |
| `os.type`                  | `darwin`               | Hardcoded                             |
| `os.version`               | `17.4.1`               | `ProcessInfo.operatingSystemVersion`  |
| `device.model.identifier`  | `iPhone15,2`           | `sysctl(HW_MACHINE)`                 |
| `service.name`             | `MyApp`                | `CFBundleName`                        |
| `service.version`          | `2.1.0 (45)`           | `CFBundleShortVersionString` + build  |
| `telemetry.sdk.language`   | `swift`                | Hardcoded                             |

### 5. OTLP Span Listener Mechanism (General Enhancement)

Currently, `SpanForward` hardcodes GenAI-specific logic (`processGenAILogic()`) inline. Adding iOS
MetricKit handling as another hardcoded case would be unmaintainable. This SWIP introduces a general
**span listener** mechanism to support extensible span-based metric extraction and trace persistence
control.

#### Current Problem

```java
// OpenTelemetryTraceHandler.java — converts ALL spans to Zipkin first
Span zipkinSpan = convertSpan(span, serviceName, resourceTags);
result.add(zipkinSpan);
// ...
// SpanForward.java — hardcoded GenAI logic after Zipkin conversion
processGenAILogic(zipkinSpan);    // GenAI metric extraction (hardcoded)
getReceiver().receive(zipkinSpan); // always persists trace
```

Problems:
1. GenAI logic is hardcoded — adding iOS/Android/etc. would keep growing
2. Listeners can't see original OTLP structure (InstrumentationScope name, resource attributes
   as separate fields) — everything is already flattened into Zipkin tags
3. Spans that should NOT be persisted (e.g., 24-hour MetricKit) still get converted to Zipkin format

#### Solution: `OTLPSpanListener` Interface — Before Zipkin Conversion

Listeners operate on the **raw OTLP span + resource attributes**, before Zipkin conversion.
This gives listeners access to:
- `InstrumentationScope` name and version (lost in Zipkin conversion)
- Resource attributes as a separate map (not flattened with span attributes)
- Original OTLP span structure

```java
/**
 * Listener for OTLP spans. Called BEFORE Zipkin conversion.
 * Implementations can:
 * 1. Extract metrics or other data from spans
 * 2. Modify resource/span attributes before Zipkin conversion
 * 3. Control whether the span should be converted and persisted as a trace
 */
public interface OTLPSpanListener {
    /**
     * Process an OTLP span.
     *
     * @param span the raw OTLP span
     * @param resourceAttributes resource-level attributes (service.name, os.name, etc.)
     * @param scopeName InstrumentationScope name (e.g., "NSURLSession", "MetricKit")
     * @param scopeVersion InstrumentationScope version
     * @return result controlling persistence and tag modifications
     */
    OTLPSpanListenerResult onSpan(
        io.opentelemetry.proto.trace.v1.Span span,
        Map<String, String> resourceAttributes,
        String scopeName,
        String scopeVersion
    );
}

public class OTLPSpanListenerResult {
    /** Whether this span should be converted to Zipkin and persisted. Default: true */
    private boolean persistTrace = true;

    /** Additional tags to inject before Zipkin conversion (e.g., estimated_cost) */
    private Map<String, String> additionalTags = Collections.emptyMap();

    /** Layer override — if set, the service is assigned this layer */
    private Layer layer = null;
}
```

#### Revised Flow in `OpenTelemetryTraceHandler`

```java
// OpenTelemetryTraceHandler.java — listeners BEFORE Zipkin conversion
for (io.opentelemetry.proto.trace.v1.Span span : scopeSpans.getSpansList()) {
    boolean shouldPersist = true;
    Map<String, String> extraTags = new HashMap<>();
    Layer layerOverride = null;

    for (OTLPSpanListener listener : spanListeners) {
        OTLPSpanListenerResult result = listener.onSpan(
            span, resourceTags, scopeName, scopeVersion);
        if (!result.isPersistTrace()) {
            shouldPersist = false;
        }
        extraTags.putAll(result.getAdditionalTags());
        if (result.getLayer() != null) {
            layerOverride = result.getLayer();
        }
    }

    if (shouldPersist) {
        // Merge extraTags into resourceTags before Zipkin conversion
        resourceTags.putAll(extraTags);
        Span zipkinSpan = convertSpan(span, serviceName, resourceTags);
        result.add(zipkinSpan);
    }
}
```

#### Registered Listeners

| Listener | Detects | Extracts | Persists? | Modifies? |
|---|---|---|---|---|
| `GenAISpanListener` | `gen_ai.system` or `gen_ai.provider.name` attribute | Token metrics, cost → Sources | Yes | Yes (adds `estimated_cost` tag) |
| `IOSMetricKitSpanListener` | `scopeName == "MetricKit"` + `span.name == "MXMetricPayload"` | Device stats → SampleFamily → shared MAL pipeline | **No** | No |

Listeners are registered via SPI (`META-INF/services/`) and loaded at handler initialization.
The existing `processGenAILogic()` is refactored into `GenAISpanListener` — no behavior change,
just better structure.

**Key design points:**
- Listeners see raw OTLP data — InstrumentationScope name, resource attributes as separate map
- Any listener can veto trace persistence — prevents Zipkin conversion entirely (no wasted work)
- Any listener can inject tags — merged before Zipkin conversion
- Multiple listeners can process the same span (e.g., a GenAI span on iOS triggers both)
- If ANY listener vetoes persistence, the span is not converted or stored

**Note:** No `IOSLayerSpanListener` is needed. The `IOS` layer is registered automatically
when the MAL `expSuffix` with `Layer.IOS` processes MetricKit metrics. The OTLP→Zipkin trace
pipeline (`SpanForward`) emits Zipkin-specific sources (not OAL sources), so there are no
OAL traffic metrics for OTLP traces.

### 6. Entity Model

| SkyWalking Entity    | Source                                                     | Example       |
|----------------------|------------------------------------------------------------|---------------|
| **Service**          | `service_name` label in MAL `expSuffix`                    | `MyApp`       |
| **Service Instance** | `service_instance_id` label in MAL `expSuffix`             | `2.1.0`       |

No endpoint entity — MetricKit metrics are service/instance scoped only.

### 7. HTTP Span Processing (Trace Path)

HTTP spans from `InstrumentationScope NSURLSession` flow through the existing OTLP → Zipkin → SpanForward
trace pipeline. They are stored as Zipkin spans and queryable via the Zipkin query API.

**Note:** The OTLP→Zipkin trace pipeline (`SpanForward`) emits Zipkin-specific sources
(`ZipkinService`, `ZipkinServiceSpan`, `ZipkinServiceRelation`), not OAL sources. There are
**no OAL traffic metrics** (e.g., `service_cpm`, `service_resp_time`) generated from OTLP traces.
HTTP trace metrics for iOS may be added in the future via MAL extraction in a SpanListener.

#### OTLP Export Feedback Loop

The URLSession auto-instrumentation captures **all** HTTP calls including the OTLP export calls
themselves. This creates an exponential feedback loop — validated in our POC: 4 real HTTP requests
generated 41,213 spurious export spans.

**Recommended mitigation** (documented in user guide): Use the SDK's `shouldInstrument` callback to
exclude the collector URL:
```swift
URLSessionInstrumentationConfiguration(
    shouldInstrument: { request in
        return request.url?.host != "<collector-host>"
    }
)
```

### 8. Metrics Overview

iOS monitoring metrics come from MetricKit — daily aggregated device statistics delivered once
per day per device via the OTel Swift SDK's MetricKit instrumentation.

### 9. MetricKit Span Listener (`IOSMetricKitSpanListener`)

Apple's MetricKit delivers pre-aggregated app statistics once per day. The OTel Swift SDK encodes
this as a single span with `startTime = 24h ago`, `endTime = now`, with all statistics as span
attributes. These are **not trace spans** — they must be intercepted and converted to metrics.

`IOSMetricKitSpanListener` implements the `SpanListener` SPI (Section 5):
- **Detection:** `scopeName == "MetricKit"` AND `span.spanName() == "MXMetricPayload"` — uses the
  raw OTLP InstrumentationScope name, available because listeners run before Zipkin conversion
- **Action:** Extract span attributes as `SampleFamily` samples with 4 labels (`service_name`,
  `service_instance_id`, `device_model`, `os_version`), push into the shared MAL pipeline via
  `OpenTelemetryMetricRequestProcessor.toMeter()` — no duplicate rule loading
- **Persistence:** Returns `shouldPersist = false` — a 24-hour span must not be stored as a trace
- **Required module:** `receiver-otel` — the listener uses the otel-receiver's MAL converters
  configured via `enabledOtelMetricsRules`

#### MetricKit Source Attributes

| Span Attribute                                             | Type   | Unit    | Description                       |
|------------------------------------------------------------|--------|---------|-----------------------------------|
| `metrickit.app_launch.time_to_first_draw_average`          | Double | seconds | Average time to first draw        |
| `metrickit.app_responsiveness.hang_time_average`           | Double | seconds | Average hang duration             |
| `metrickit.cpu.cpu_time`                                   | Double | seconds | Cumulative CPU time (24h)         |
| `metrickit.memory.peak_memory_usage`                       | Double | bytes   | Peak memory usage                 |
| `metrickit.network_transfer.wifi_download`                 | Double | bytes   | WiFi download (24h)               |
| `metrickit.network_transfer.wifi_upload`                   | Double | bytes   | WiFi upload (24h)                 |
| `metrickit.network_transfer.cellular_download`             | Double | bytes   | Cellular download (24h)           |
| `metrickit.network_transfer.cellular_upload`               | Double | bytes   | Cellular upload (24h)             |
| `metrickit.app_exit.foreground.abnormal_exit_count`        | Int    | count   | Abnormal exits (foreground crashes) — aggregated |
| `metrickit.app_exit.foreground.normal_app_exit_count`      | Int    | count   | Normal foreground exits — not aggregated         |
| `metrickit.app_exit.background.abnormal_exit_count`        | Int    | count   | Background abnormal exits — aggregated           |
| `metrickit.app_exit.background.normal_app_exit_count`      | Int    | count   | Normal background exits — not aggregated         |
| `metrickit.app_exit.background.memory_pressure_exit_count` | Int    | count   | OOM kills — aggregated                           |
| `metrickit.animation.scroll_hitch_time_ratio`              | Double | ratio   | Scroll jank ratio                 |
| `metrickit.gpu.time`                                       | Double | seconds | Cumulative GPU time               |
| `metrickit.diskio.logical_write_count`                     | Double | bytes   | Disk writes (24h)                 |
| `metrickit.metadata.device_type`                           | String | —       | Device model in MetricKit payload |
| `metrickit.metadata.os_version`                            | String | —       | OS version in MetricKit payload   |

#### Aggregation Strategy

MetricKit data is inherently daily — each device reports once per day. Multiple devices running the
same app produce multiple data points per day. The analyzer uses the span's **end time** as the data
point timestamp with **day-level time bucket** (`TimeBucket.getDayTimeBucket()`).

Different metrics require different cross-device aggregation:

| Metric Category | Aggregation | Reasoning |
|---|---|---|
| Pre-averaged values (launch time, hang time) | `longAvg` | Apple already averaged per-device; average across fleet |
| Peak values (memory) | `max` | Want the worst-case device |
| Counts (crash count, exit count) | `sum` | Total events across fleet |
| Cumulative volumes (network bytes, disk writes, CPU time) | `sum` | Total fleet resource usage |
| Ratios (scroll hitch) | `doubleAvg` | Fleet-wide average jank |

#### Span-to-Sample Conversion

The listener converts each `MXMetricPayload` span into labeled `SampleFamily` samples:

```
metrickit_app_launch_time{service_name="MyApp", service_instance_id="2.1.0", device_model="iPhone15,2", os_version="17.4.1"} 850
metrickit_hang_time{service_name="MyApp", service_instance_id="2.1.0", device_model="iPhone15,2", os_version="17.4.1"} 120
metrickit_peak_memory{service_name="MyApp", service_instance_id="2.1.0", device_model="iPhone15,2", os_version="17.4.1"} 157286400
metrickit_foreground_abnormal_exit_count{service_name="MyApp", service_instance_id="2.1.0", device_model="iPhone15,2", os_version="18.0"} 2
metrickit_wifi_download{service_name="MyApp", service_instance_id="2.1.0", device_model="iPhone15,2", os_version="17.4.1"} 52428800
```

Labels are extracted from:
- `service_name` → resource attribute `service.name`
- `service_instance_id` → resource attribute `service.version` (instance fallback)
- `device_model` → span attribute `metrickit.metadata.device_type` or resource attribute `device.model.identifier`
- `os_version` → span attribute `metrickit.metadata.os_version` or resource attribute `os.version`

#### MAL Rules

Create `oap-server/server-starter/src/main/resources/otel-rules/ios/ios-metrickit.yaml`:

```yaml
expSuffix: service(['service_name'], Layer.IOS)
metricPrefix: meter_ios

metricsRules:
  # App responsiveness — percentile across devices (P50 = median)
  - name: app_launch_time_percentile
    exp: metrickit_app_launch_time_histogram.sum(['service_name', 'service_instance_id', 'le']).histogram().histogram_percentile([50,75,90,95,99])
  - name: hang_time_percentile
    exp: metrickit_hang_time_histogram.sum(['service_name', 'service_instance_id', 'le']).histogram().histogram_percentile([50,75,90,95,99])
  - name: hang_time_sum
    exp: metrickit_hang_time.sum(['service_name', 'service_instance_id'])

  # Stability — sum across devices. Foreground/background are reported separately so
  # watchdog kills and background-task crashes don't hide behind total crash counts.
  # Normal graceful exits are not aggregated (no diagnostic signal).
  - name: foreground_abnormal_exit_count
    exp: metrickit_foreground_abnormal_exit_count.sum(['service_name', 'service_instance_id'])
  - name: background_abnormal_exit_count
    exp: metrickit_background_abnormal_exit_count.sum(['service_name', 'service_instance_id'])
  - name: background_oom_kill_count
    exp: metrickit_background_oom_kill_count.sum(['service_name', 'service_instance_id'])

  # Resource usage
  - name: peak_memory
    exp: metrickit_peak_memory.max(['service_name', 'service_instance_id'])
  - name: cpu_time
    exp: metrickit_cpu_time.sum(['service_name', 'service_instance_id'])
  - name: gpu_time
    exp: metrickit_gpu_time.sum(['service_name', 'service_instance_id'])
  - name: disk_write
    exp: metrickit_disk_write.sum(['service_name', 'service_instance_id'])

  # Network transfer — average per device
  - name: wifi_download
    exp: metrickit_wifi_download.avg(['service_name', 'service_instance_id'])
  - name: wifi_upload
    exp: metrickit_wifi_upload.avg(['service_name', 'service_instance_id'])
  - name: cellular_download
    exp: metrickit_cellular_download.avg(['service_name', 'service_instance_id'])
  - name: cellular_upload
    exp: metrickit_cellular_upload.avg(['service_name', 'service_instance_id'])

  # UI quality
  - name: scroll_hitch_ratio
    exp: metrickit_scroll_hitch_ratio.avg(['service_name', 'service_instance_id'])
```

The listener emits histogram-bucketed samples (with `le` labels) for app launch time and
hang time, enabling `histogram_percentile` to compute P50/P75/P90/P95/P99 across the device fleet.

**Bucket ceiling**: both histograms top out at a finite 30 s bucket rather than `+Inf`. MAL
parses `le="Infinity"` to `(long) Double.POSITIVE_INFINITY = Long.MAX_VALUE` and surfaces it
verbatim in percentile queries; on a dashboard that renders as ~9.2×10¹⁸, which is worse than
a visibly alarming but human-readable cap. Values above 30 s are vanishingly rare for iOS app
launch / hang observations (MetricKit itself hard-caps hangs near 30 s), so the finite sentinel
preserves percentile accuracy without breaking the UI.

#### Aggregation Example

Given 3 devices reporting on the same day for service "MyApp":

```
Device A: appLaunchTime=850ms, peakMemory=150MB, abnormalExitCount=2, wifiDownload=50MB
Device B: appLaunchTime=1200ms, peakMemory=200MB, abnormalExitCount=0, wifiDownload=80MB
Device C: appLaunchTime=900ms, peakMemory=140MB, abnormalExitCount=1, wifiDownload=30MB
```

Resulting daily metrics:

| Metric                         | Aggregation              | Result         |
|--------------------------------|--------------------------|----------------|
| `ios_app_launch_time_percentile P50` | histogram_percentile | **900 ms**   |
| `ios_app_launch_time_percentile P90` | histogram_percentile | **1200 ms**  |
| `ios_peak_memory`              | max(150, 200, 140)       | **200 MB**     |
| `ios_foreground_abnormal_exit_count` | sum(2, 0, 1)       | **3 crashes**  |
| `ios_wifi_download`            | avg(50, 80, 30)          | **53 MB**      |

### 10. MetricKit Diagnostic Log Processing (LAL)

MetricKit diagnostic payloads arrive as OTLP log records with `InstrumentationScope: MetricKit`.
The diagnostic type is identified by the `name` log record attribute.

#### LogData Input to LAL

After the changes in Sections 2–3, the LogData seen by LAL for a crash diagnostic:

```
LogMetadata {
  service:          "MyApp"
  serviceInstance:  ""                 ← empty (SDK doesn't set service.instance.id)
  layer:            ""                 ← empty (SDK doesn't set service.layer)
  sourceAttributes: {                  ← NEW: non-persistent, from OTLP resource
    "os.name":                 "iOS",
    "os.version":              "17.4.1",
    "device.model.identifier": "iPhone15,2",
    "service.name":            "MyApp",
    "service.version":         "2.1.0 (45)",
    "telemetry.sdk.language":  "swift",
    "telemetry.sdk.name":      "opentelemetry",
    "os.type":                 "darwin"
  }
}

LogData (proto) {
  tags: [                              ← only log record attributes, persisted in tagsRawData
    {key: "name",                                              value: "metrickit.diagnostic.crash"},
    {key: "exception.type",                                    value: "EXC_BAD_ACCESS"},
    {key: "exception.message",                                 value: "Could not access memory"},
    {key: "exception.stacktrace",                              value: "0   CoreFoundation ..."},
    {key: "metrickit.diagnostic.crash.exception.signal",       value: "11"},
    {key: "metrickit.diagnostic.crash.exception.signal.name",  value: "SIGSEGV"}
  ]
  body.text: ""                        ← MetricKit logs have empty body
}
```

Key distinction:
- `sourceAttributes` → readable via `sourceAttribute()` in LAL, **NOT persisted**
- `tags` → readable via `tag()` in LAL, **persisted** in `tagsRawData`
- LAL extractor `tag 'key': value` → adds to both persistent tags and searchable tags

#### Diagnostic Types

| Diagnostic Type      | `name` Attribute                           | Key Attributes                                                                                                      |
|----------------------|--------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| Crash                | `metrickit.diagnostic.crash`               | `exception.type`, `exception.message`, `exception.stacktrace`, `metrickit.diagnostic.crash.exception.signal.name`   |
| Hang                 | `metrickit.diagnostic.hang`                | `exception.stacktrace`, `metrickit.diagnostic.hang.hang_duration`                                                   |
| CPU Exception        | `metrickit.diagnostic.cpu_exception`       | `metrickit.diagnostic.cpu_exception.total_cpu_time`                                                                 |
| Disk Write Exception | `metrickit.diagnostic.disk_write_exception` | `metrickit.diagnostic.disk_write_exception.total_writes_caused`                                                    |
| App Launch (iOS 16+) | `metrickit.diagnostic.app_launch`          | `metrickit.diagnostic.app_launch.launch_duration`                                                                   |

#### LAL Rules

Create `oap-server/server-starter/src/main/resources/lal/ios-metrickit.yaml`:

```yaml
rules:
  - name: ios-metrickit-diagnostics
    layer: auto                   # layer determined by script; dropped if not set
    dsl: |
      filter {
        // Only match iOS/iPadOS logs
        if (sourceAttribute("os.name") != "iOS" && sourceAttribute("os.name") != "iPadOS") {
          abort {}
        }
        // Only match MetricKit diagnostic logs
        if (tag("name") == null || !tag("name").startsWith("metrickit.diagnostic.")) {
          abort {}
        }

        extractor {
          layer IOS
          // Set instance from service.version (SDK doesn't set service.instance.id)
          instance sourceAttribute("service.version")

          // Selectively copy useful source attributes into persistent tags
          tag 'device.model': sourceAttribute("device.model.identifier")
          tag 'os.version': sourceAttribute("os.version")

          // Copy diagnostic details from log record tags
          tag 'diagnosticType': tag("name")
          tag 'exception.type': tag("exception.type")
          tag 'exception.message': tag("exception.message")
          tag 'exception.stacktrace': tag("exception.stacktrace")
          tag 'signal.name': tag("metrickit.diagnostic.crash.exception.signal.name")
          tag 'hang.duration': tag("metrickit.diagnostic.hang.hang_duration")
        }

        sink {
          // Store all diagnostics — they are already rare (once/day batches from real devices)
        }
      }
```

### 11. UI Menu and Dashboards

#### Menu Configuration

Add to `oap-server/server-starter/src/main/resources/ui-initialized-templates/menu.yaml`:
```yaml
- title: Mobile
  icon: mobile
  description: Mobile application monitoring via OpenTelemetry SDKs.
  i18nKey: mobile
  menus:
    - title: iOS
      layer: IOS
      description: iOS/iPadOS app monitoring via OpenTelemetry Swift SDK.
      documentLink: https://skywalking.apache.org/docs/main/next/en/setup/service-agent/ios-monitoring/
      i18nKey: ios
```

#### Dashboard Templates

Create dashboards under `ui-initialized-templates/ios/`:

**ios-root.json** — Root list view of all iOS app services.

**ios-service.json** — Per-app dashboard:

| Panel Group      | Metrics                                                        | Source                       |
|------------------|----------------------------------------------------------------|------------------------------|
| HTTP Traffic     | `service_cpm`, `service_resp_time`, `service_sla`, `service_percentile` | OAL (from IOSHTTPSpanListener) |
| App Launch       | `meter_ios_app_launch_time`                                    | MetricKit MAL                |
| Stability        | `meter_ios_foreground_abnormal_exit_count`, `meter_ios_background_oom_kill_count`    | MetricKit MAL                |
| Memory           | `meter_ios_peak_memory`                                        | MetricKit MAL                |
| Network Transfer | `meter_ios_wifi_download`, `meter_ios_cellular_download`, etc. | MetricKit MAL                |
| Responsiveness   | `meter_ios_hang_time`                                          | MetricKit MAL                |

**ios-instance.json** — Per-version dashboard (instance = app version):
- HTTP traffic metrics scoped to instance (`service_instance_cpm`, `service_instance_resp_time`, `service_instance_sla`)
- MetricKit metrics per version

**ios-endpoint.json** — Per-domain dashboard (endpoint = `net.peer.name` domain):
- `endpoint_cpm`, `endpoint_resp_time`, `endpoint_sla`, `endpoint_percentile` (from OAL)

#### UI Side

A separate PR in [skywalking-booster-ui](https://github.com/apache/skywalking-booster-ui) is needed
for i18n menu entries for the "Mobile" group and "iOS" sub-item.

## Imported Dependencies libs and their licenses.
No new dependencies. All processing uses existing OTLP receiver, OAL, LAL, and meter infrastructure.

## Compatibility
- **Configuration:** New layer `IOS` and menu entry — additive, no breaking change.
- **Storage:** No new storage structures. Uses existing trace, metrics, and log storage.
- **Protocols:** No protocol changes. Uses existing OTLP receiver.
- **LogMetadata:** New `sourceAttributes` field — backward compatible. Existing receivers that don't
  populate it get an empty map. Existing LAL rules that don't call `sourceAttribute()` are unaffected.
- **LAL `layer: auto` mode:** Additive. Existing rules with specific layers (GENERAL, MESH, etc.)
  are unaffected. Only new rules can opt into `auto` mode to match logs with absent layer.
- **Drop policy for `auto` rules:** In `auto` mode, logs where the script does not set a layer are
  warned and dropped. This only affects logs routed to `auto` rules — logs with explicit layers
  are unaffected.

## General usage docs

### Prerequisites
- iOS app instrumented with [OpenTelemetry Swift SDK](https://github.com/open-telemetry/opentelemetry-swift) v1.12+
- OTLP/HTTP exporter pointing to SkyWalking OAP (port 4318) or an OTel Collector

### iOS App Setup

```swift
import OpenTelemetryApi
import OpenTelemetrySdk
import OpenTelemetryProtocolExporterHTTP
import ResourceExtension
import URLSessionInstrumentation

// Resource attributes (device, OS, app info — auto-collected)
let resources = DefaultResources().get()

// OTLP exporter
let traceExporter = OtlpHttpTraceExporter(
    endpoint: URL(string: "http://<oap-host>:4318/v1/traces")!
)
let logExporter = OtlpHttpLogExporter(
    endpoint: URL(string: "http://<oap-host>:4318/v1/logs")!
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
        return request.url?.host != "<oap-host>"
    }
)
let _ = URLSessionInstrumentation(configuration: config)

// MetricKit (real device only, not Simulator)
import MetricKit
let metricKit = MetricKitInstrumentation()
MXMetricManager.shared.add(metricKit)
```

### SkyWalking OAP Configuration

Enable the OTLP receiver and LAL rules in `application.yml`:
```yaml
receiver-otel:
  selector: ${SW_OTEL_RECEIVER:default}
  default:
    enabledHandlers: ${SW_OTEL_RECEIVER_ENABLED_HANDLERS:"otlp-traces,otlp-logs"}

log-analyzer:
  selector: ${SW_LOG_ANALYZER:default}
  default:
    lalFiles: ${SW_LOG_LAL_FILES:"ios-metrickit"}
```

### What You'll See

1. **Mobile > iOS** menu appears when iOS services are detected
2. **Trace view** shows individual HTTP requests with latency, status, URL, device model, carrier
3. **Service dashboard** shows HTTP performance (real-time) + MetricKit stats (daily)
4. **Log view** shows crash/hang diagnostic events with native stack traces, tagged with device model and OS version

### Limitations
- MetricKit data (daily stats, crash diagnostics) requires a real iOS device — not available in Simulator
- MetricKit stats are delivered approximately once per day — not real-time
- Screen/view transition tracking is not automatic — the OTel Swift SDK does not instrument
  UIViewController or SwiftUI lifecycle
- Carrier info is iOS-only (not available on macOS)
