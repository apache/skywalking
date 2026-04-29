## 10.5.0

#### Project

* **Runtime rule hot-update for MAL and LAL.** Operators can now ship metric (MAL) and log
  (LAL) rule changes without restarting OAP. A push to a new admin endpoint persists the rule
  to the configured storage backend, and every node in the cluster converges to the new
  content within ~30 seconds. Common workflows:
  * `addOrUpdate` — create or replace a rule. Body is the raw YAML you would normally ship
    with OAP's static rule files. Returns 200 once the rule is applied locally and
    persisted; peers pick it up on their next periodic scan (≤ 30 s).
  * `inactivate` — soft-pause a rule. The OAP stops emitting metrics for that rule but the
    backend measure (and its history) is preserved, so a later `addOrUpdate` to the same
    `(catalog, name)` is lossless. The "off" intent is durable across reboots; bundled rules
    on disk are not auto-resurrected when an `inactivate` removes the runtime override.
    This is the safe way to take a rule offline.
  * `delete` — removes an `INACTIVE` row (active rules return `409 requires_inactivate_first`).
    For runtime-only rules with no bundled YAML on disk, the backend measure is dropped and
    the rule is fully gone. For rules that have a bundled YAML, `delete` is non-destructive:
    backend resources runtime claimed that bundled does not (or claims at a different shape)
    are dropped, bundled-shared at matching shape is preserved, the row is removed, and the
    bundled rule is reinstalled into a `static:` loader on the local node — peers converge
    via the periodic reconcile. `?mode=revertToBundled` is an explicit operator hint that
    fails with `400 no_bundled_twin` when no bundled YAML exists.
  * `get` / `bundled` / `list` / `dump` — read-side endpoints for fetching a single rule's
    YAML (with `ETag` support; `?source=bundled` reads the on-disk bundled YAML even when a
    runtime override is in place), listing the bundled-vs-runtime overlay per catalog,
    inspecting cluster-wide rule state as a JSON envelope (`{generatedAt, loaderStats,
    rules}` — each row carries `status`/`localState`/`loaderKind`/`bundled`/`bundledContentHash`
    so a UI can render override badges without a second roundtrip), and exporting all rules
    as a tar.gz for backup / DR.
  Hot-updates survive OAP restart: at boot OAP merges bundled rule files with persisted
  runtime rules, so the cluster never silently regresses to the bundled defaults.
  **The endpoint is disabled by default and listens on port `17128` when enabled. It has
  no built-in authentication — operators must gateway-protect it with IP allow-lists and
  never expose it to the public internet.**
* **BanyanDB schema mismatches are now visible at boot, not silent.** If BanyanDB already
  holds a resource whose shape doesn't match what the current rule declares (e.g., a rule
  was edited on disk while OAP was offline), OAP now skips that resource, logs an ERROR
  with the declared-vs-backend diff, and continues booting — previously the mismatch was
  silently accepted and samples for the affected resource were quietly dropped. To
  re-shape a mismatched metric, push the desired YAML through
  `POST /runtime/rule/addOrUpdate`.
* Bump infra-e2e to testcontainers-go v0.42.0 (apache/skywalking-infra-e2e#146), which uses Docker Compose v2 plugin natively and removes docker-compose v1 dependency.
* Remove deprecated `version` field from all docker-compose files for Compose v2 compatibility.
* **Best-effort schema-cutover fence for BanyanDB.** After firing a schema install or drop
  OAP now waits up to a bounded window (default 2s) for every BanyanDB data node to apply
  the change before resuming dispatch — the typical case gets a clean cutover where
  samples after `200 OK` use the new shape. On laggard timeout, OAP logs a warning and
  proceeds anyway so a single slow node doesn't wedge the apply.
* Bump dependencies: gRPC `1.70.0` → `1.80.0`, protobuf-java `3.25.5` → `4.33.1`, Netty
  `4.2.10.Final` → `4.2.12.Final`, Netty-tcnative `2.0.75` → `2.0.77`, pgv (protoc-gen-validate)
  `1.2.1` → `1.3.0`. Driven by the new BanyanDB schema-consistency RPCs whose generated
  validation code requires the `protobuf-java 4.x` runtime.

#### OAP Server
* Add Zipkin Virtual GenAI e2e test. Use `zipkin_json` exporter to avoid protobuf dependency conflict
  between `opentelemetry-exporter-zipkin-proto-http` (protobuf~=3.12) and `opentelemetry-proto` (protobuf>=5.0).
* Fix missing `taskId` filter and incorrect `IN` clause parameter binding in `JDBCJFRDataQueryDAO` and `JDBCPprofDataQueryDAO`.
* Remove deprecated `GroupBy.field_name` from BanyanDB `MeasureQuery` request building (Phase 1 of staged removal across repos).
* Push `taskId` filter down to the storage layer in `IAsyncProfilerTaskLogQueryDAO`, removing in-memory filtering from `AsyncProfilerQueryService`.
* Fix missing parentheses around OR conditions in `JDBCZipkinQueryDAO.getTraces()`, which caused the table filter to be bypassed for all but the first trace ID. Replaced with a proper `IN` clause.
* Fix missing `and` keyword in `JDBCEBPFProfilingTaskDAO.getTaskRecord()` SQL query, which caused a syntax error on every invocation.
* Fix storage layer bugs in profiling DAOs and add unit test coverage for JDBC query DAOs.
  Bug fixes: duplicate `TABLE_COLUMN` condition in `JDBCMetadataQueryDAO.findEndpoint()`,
  wrong merged table check in `JFRDataQueryEsDAO` (used incorrect INDEX_NAME due to copy-paste),
  and missing `isMergedTable` check in `ProfileTaskQueryEsDAO.getById()`.
  Test additions: add unit tests for 21 JDBC query DAOs verifying SQL/WHERE clause construction.
* Optimize `TraceQueryService.sortSpans` from O(N^2) to O(N) by pre-indexing spans by `segmentSpanId`, so trace detail queries scale linearly with span count.
* Support MCP (Model Context Protocol) observability for Envoy AI Gateway: MCP metrics (request CPM/latency, method breakdown, backend breakdown, initialization latency, capabilities), MCP access log sampling (errors only), `ai_route_type` searchable log tag, and MCP dashboard tabs.
* Add weighted handler support to `BatchQueue` adaptive partitioning. MAL metrics use weight 0.05 at L1 (vs 1.0 for OAL), reducing partition count and memory overhead when many MAL metric types are registered.
* Fix missing `taskId` filter in pprof task log query and its JDBC/BanyanDB/Elasticsearch implementations.
* Fix duplicate calls in `EndpointTopologyBuilder` — calls were not deduplicated unlike `ServiceTopologyBuilder`, causing duplicate entries when storage returns multiple records for the same relation.
* Use `containsOnce` and `noDuplicates` for topology dependency e2e expected files to enforce no-duplicate verification.
* Bump infra-e2e to `ef073ad` to include `noDuplicates` pipe function support.
* PromQL: support querying Zipkin metadata (service name, remote service name, span name).
* TraceQL: support more tags and variables in Grafana for querying.
* LAL: add `sourceAttribute()` function for non-persistent OTLP resource attribute access in LAL scripts.
* LAL: add `layer: auto` mode for dynamic layer assignment when `service.layer` is absent.
* Add two-phase `SpanListener` SPI mechanism for extensible trace span processing. Refactor GenAI from hardcoded `SpanForward.processGenAILogic()` to `GenAISpanListener`.
* Add OTLP/HTTP receiver support for traces, logs, and metrics (`/v1/traces`, `/v1/logs`, `/v1/metrics`). Supports both `application/x-protobuf` and `application/json` content types.
* Fix: TTL query add metadata TTL.
* Fix: PersistentWorker used wrong TTL for metrics cache if the storage is BanyanDB.
* Add iOS/iPadOS app monitoring via OpenTelemetry Swift SDK (SWIP-11). Includes the `IOS` layer, `IOSHTTPSpanListener` for outbound HTTP client metrics (supports OTel Swift `.old`/`.stable`/`.httpDup` semantic-convention modes via stable-then-legacy attribute fallback), `IOSMetricKitSpanListener` for daily MetricKit metrics (exit counts split by foreground/background, app-launch / hang-time percentile histograms with finite 30 s overflow ceiling), LAL rules for crash/hang diagnostics, Mobile menu, and iOS dashboards.
* Fix LAL `layer: auto` mode dropping logs after extractor set the layer. Codegen now propagates `layer "..."` assignments to `LogMetadata.layer` so `FilterSpec.doSink()` sees the script-decided layer.
* Fix MetricKit histogram percentile metrics being reported at 1000× their true value — the listener now marks its `SampleFamily` with `defaultHistogramBucketUnit(MILLISECONDS)` so MAL's default SECONDS→MS rescale of `le` labels is not applied.
* Add WeChat and Alipay Mini Program monitoring via the SkyAPM mini-program-monitor SDK (SWIP-12). Two new layers (`WECHAT_MINI_PROGRAM`, `ALIPAY_MINI_PROGRAM`); two new JavaScript componentIds (`WeChat-MiniProgram: 10002`, `AliPay-MiniProgram: 10003`). Service / instance / endpoint entities are produced by MAL + LAL, not trace analysis — mini-programs are client-side (exit-only) so `RPCAnalysisListener` stays unchanged (same pattern as browser and iOS). MAL rules per platform × scope under `otel-rules/miniprogram/` with explicit `.service(...)` / `.endpoint(...)` chains (empty `expSuffix` so endpoint-scope rules aren't overridden), histogram percentile via `.histogram("le", TimeUnit.MILLISECONDS)` to keep ms bucket bounds intact, and request-cpm derived from the histogram `_count` family. LAL `layer: auto` rule produces both layers via `miniprogram.platform` dispatch and emits error-count samples consumed by per-platform log-MAL rules. Per-layer menu entries and service / instance / endpoint dashboards with Trace and Log sub-tabs.
* Fix: remove `VirtualServiceAnalysisListener`'s dependency on `GenAIAnalyzerModule` if it is disabled.
* MAL: register `TimeUnit` in `MALCodegenHelper.ENUM_FQCN` so rule YAML can write `.histogram("le", TimeUnit.MILLISECONDS)` for SDKs that emit histogram bucket bounds in ms (default `SECONDS` unit applies a ×1000 rescale that would otherwise inflate stored `le` labels 1000×).
* Fix: potential unexpected current directory inclusion in Docker OAP classpath.
* MAL: add `safeDiv(divisor)` on `SampleFamily` that yields `0` when the divisor is `0` instead of `Infinity`/`NaN`. Replace `/` with `safeDiv(...)` in Envoy AI Gateway latency-average rules so `sum / count * 1000` no longer produces dropped or out-of-range samples when a counter is zero in a window.
* Fix: `envoy-ai-gateway` metrics rules, make the metrics value return `0` when the divisor is `0`.

#### UI
* Add mobile menu icon and i18n labels for the iOS layer.
* Fix metric label rendering in multi-expression dashboard widgets.
* Add i18n menu labels for WeChat Mini Program and Alipay Mini Program (en / zh / es) — sub-menus rendered as raw keys until this bump.
* Support trace V1 view in trace single page.

#### Documentation
* Update LAL documentation with `sourceAttribute()` function and `layer: auto` mode.
* Add iOS app monitoring setup documentation.
* Add WeChat / Alipay Mini Program monitoring setup documentation, plus a client-side-monitoring section in the security guide covering public-internet ingress (OTLP + `/v3/segments`) for mobile / browser / mini-program SDKs.

All issues and pull requests are [here](https://github.com/apache/skywalking/issues?q=milestone:10.5.0)
