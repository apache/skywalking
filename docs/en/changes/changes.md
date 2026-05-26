## 11.0.0

#### Project

* **New `queryAlarms` GraphQL query — entity / layer / rule filters for alarms.** Adds
  a comprehensive alarm query API alongside the legacy `getAlarm`. The new
  `queryAlarms(condition: AlarmQueryCondition!): Alarms` accepts a single input type
  bundling every filter the alarm record stores: `entities: [Entity!]` (reuses the
  MQE `Entity` shape — pin to specific services / instances / endpoints / processes
  or their relations, matched against alarm `id0` OR `id1`); `layer: String`
  (filter by the alarmed entity's layer — single match, since alarm rows
  persist one layer); `ruleNames: [String!]` (filter by which alarm rule
  fired); plus `keyword`, `tags`, `duration`, `paging`. Legacy `getAlarm`
  is marked `@deprecated` but still routes to the same DAO — no client breakage.
  Backend additions: a new `layer` column on `AlarmRecord` populated at alarm-mint
  time via `MetadataQueryService.getService(serviceId).getLayers()`; the existing
  `id0`/`id1` columns flipped from `storageOnly = true` to indexed so the entity
  filter pushes down to storage. `IAlarmQueryDAO.queryAlarms(condition, limit, from)`
  is a new abstract method — 3rd-party storage backends fail at compile if they miss
  the override (SWIP-14 pattern). All three bundled backends implement it:
  BanyanDB / Elasticsearch / JDBC. **Operator semantics:**
  (1) **Relation entities are exact-match.** Passing `{scope: ServiceRelation,
  serviceName: A, destServiceName: B}` matches only the alarm where
  `id0=serviceId(A) AND id1=serviceId(B)`, not any alarm that touches A or B
  on either side. Wider "anything involving A" queries should pass the
  individual non-relation entity instead (`{scope: Service, serviceName: A}` —
  which expands to `id0=A OR id1=A`).
  (2) **Single layer per alarm row.** The persisted column stores ONE layer
  (the first entry of the entity's resolved layer list — source-first for
  relations). A service in `[GENERAL, K8S_SERVICE]` whose metadata
  resolves to `GENERAL` first is filed under `GENERAL`; querying
  `layer: "K8S_SERVICE"` will miss it.
  **Operator migration note:** existing pre-upgrade alarm rows continue to
  be filterable by the legacy `getAlarm` fields; the new entity / layer /
  rule filters in `queryAlarms` apply only to alarms written after the
  upgrade (existing storage indices don't transition `index: false` → `true`
  in place; new daily-rolled indices pick up the indexed columns). Schema
  additions are non-blocking — bootstrap silently skips column-attribute
  changes on existing indices.
* **🚨 Breaking change: `apm-webapp` and the `skywalking-booster-ui` submodule are
  removed.** This OAP distribution no longer ships a bundled web UI. The legacy Armeria
  reverse proxy in `apm-webapp/` (the binary that powered the `skywalking/ui` Docker
  image) and the `skywalking-ui` git submodule (which tracked `apache/skywalking-booster-ui`)
  are both deleted along with the `docker.ui` Maven target, the `skywalking/ui` Docker
  image build, the `apm-dist/` webapp packaging, and every CI workflow path that built
  or pushed the UI image. The official UI is now
  [**Horizon UI**](https://github.com/apache/skywalking-horizon-ui), a SkyWalking
  sub-project that **releases independently** of the OAP backend on its own
  schedule, with container images published to
  [`ghcr.io/apache/skywalking-horizon-ui`](https://github.com/apache/skywalking-horizon-ui/pkgs/container/skywalking-horizon-ui).
  There is no 1:1 mapping between OAP versions and Horizon UI versions —
  operators pin the UI image tag in their deployment and upgrade the two
  on separate cadences.
  Horizon UI consumes the OAP's public GraphQL/REST surface (default `12800`) and the
  admin host (default `17128`). The on-disk dashboard seed files in
  `oap-server/server-starter/src/main/resources/ui-initialized-templates/` are deleted;
  `UITemplateInitializer` / `UIMenuInitializer` are removed from `CoreModuleProvider.notifyAfterCompleted()`,
  and Horizon UI ships its own dashboard library and its own sidebar menu.
  UI templates are now created and updated through the new
  `/ui-management/templates/*` REST surface on admin-server (see below).
  All UI-related GraphQL mutations and queries (`UIConfigurationManagement`:
  `addTemplate`, `changeTemplate`, `disableTemplate`, `getAllTemplates`,
  `getDashboardConfiguration`, `getMenuItems`) are retired from the
  public GraphQL schema, along with the `SW_ENABLE_UPDATE_UI_TEMPLATE` flag.
  The OAP backend also no longer stores or serves the sidebar menu —
  `UIMenuManagementService`, `UIMenuManagementDAO`, `UIMenu`, `MenuItem`,
  and the storage impls are all removed; Horizon UI owns the menu
  client-side and uses `listServices(layer:...)` for dynamic "layer has
  services" gating. Upgrade path: replace `skywalking/ui:<tag>` with
  `ghcr.io/apache/skywalking-horizon-ui:<tag>` in your deployment, expose
  port `17128` from the OAP container, and migrate any scripts that
  called the legacy GraphQL UI mutations to the REST endpoints under
  [UI Management API](../setup/backend/admin-api/ui-management.md). All
  status / debug endpoints (`/status/*`, `/debugging/*`) also move to
  admin-only — the public REST dual-bind for status is retired in the
  same release.
* **New `ui-management` admin module — REST surface for dashboard templates.**
  Hosts five operations on admin-server (port `17128`):
  `GET /ui-management/templates`, `GET /ui-management/templates/{id}`,
  `POST /ui-management/templates`, `PUT /ui-management/templates`,
  `POST /ui-management/templates/{id}/disable`.
  Forwards to the existing `UITemplateManagementService` (no storage DAO
  changes). Enabled by default (`SW_UI_MANAGEMENT=default`, on a
  default-on admin host). Replaces the retired GraphQL `UIConfigurationManagement`
  template resolver. The sidebar menu is intentionally NOT served — see
  the breaking-change entry above. Operator reference:
  [UI Management API](../setup/backend/admin-api/ui-management.md).
* **All admin feature modules default-on.** `admin-server`, `status`, `inspect`,
  `ui-management`, `dsl-debugging`, and `receiver-runtime-rule` all default to enabled.
  Operators who don't want a particular feature set its `SW_*` env var to empty. This
  closes a usability gap from 10.4.0 where the runtime-rule / dsl-debugging surfaces
  required explicit opt-in even though the admin host was already on.
* **Status API moved to admin-host.** Status / debug routes (`/status/*`, `/debugging/*`)
  now register on the admin-server REST host (default `17128`); they no longer mirror
  on `core.restPort` (default `12800`). Aligns status with every other admin feature
  module (inspect, dsl-debugging, runtime-rule, ui-management). Horizon UI consumes
  status from the admin host. URIs and payloads are unchanged; only the host moved.
  **One exception:** `/status/config/ttl` is also bound on the public REST host
  (12800) so ecosystem tools that discover TTL bounds via REST before issuing
  /graphql don't need to learn the admin port.
* **New `admin-server` module — shared host for admin / on-demand write APIs.** Runs on
  **two ports**: an HTTP REST surface (default `17128`) for operator-facing endpoints,
  and an **admin-internal gRPC bus** (default `17129`) for peer-to-peer cluster RPCs
  (runtime-rule Suspend / Resume / Forward; DSL debug install / collect / stop /
  stopByClientId). The admin-internal bus is a dedicated transport separate from the
  public agent / cluster gRPC port (`core.gRPCPort`, default `11800`) so privileged
  admin RPCs stay out of the agent network's blast radius — operators bind `gRPCHost`
  to a private peer-to-peer interface only. Both the runtime-rule plugin and the new
  DSL Debug API (below) mount onto this shared host. **Enabled by default** so the
  status feature module is reachable out of the box; the host binds to
  `0.0.0.0:17128` and has no built-in authentication and **must** be
  gateway-protected with IP allow-lists, never exposed to the public internet (see the
  [Admin API security notice](../setup/backend/admin-api/readme.md)). Set
  `SW_ADMIN_SERVER=` (empty) to disable entirely. The runtime-rule
  config block loses its `restHost`/`restPort`/`restContextPath`/`restIdleTimeOut`/
  `restAcceptQueueSize`/`httpMaxRequestHeaderSize` keys (and the matching
  `SW_RECEIVER_RUNTIME_RULE_REST_*` env vars); host-level knobs move under the new
  `admin-server` block (`SW_ADMIN_SERVER_HOST` / `SW_ADMIN_SERVER_PORT` /
  `SW_ADMIN_SERVER_GRPC_HOST` / `SW_ADMIN_SERVER_GRPC_PORT` /
  `SW_ADMIN_SERVER_INTERNAL_COMM_TIMEOUT` etc.).
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
    For runtime-only rules with no bundled YAML on disk, the row is dropped; the backend
    measure (if any) is left in place as an inert artefact, matching bundled-rule deletion
    semantics (removing a YAML from `otel-rules/` on disk doesn't drop its measure either).
    For rules that have a bundled YAML twin, plain `delete` returns `409
    requires_revert_to_bundled` — letting bundled silently take over the
    `(catalog, name)` is a meaningful state change that requires an explicit operator
    decision. Re-issue with `?mode=revertToBundled` to fall back to bundled: that path runs
    the schema-change pipeline (rehydrates the runtime DSL locally, then applies the
    bundled YAML through the standard apply pipeline so the runtime→bundled delta drops
    runtime-only metrics, registers bundled-only metrics, and reuses bundled-shared metrics
    at matching shape) before removing the row. Returns `400 no_bundled_twin` when
    `?mode=revertToBundled` is used without a bundled YAML on disk.
  * `get` / `bundled` / `list` / `dump` — read-side endpoints for fetching a single rule's
    YAML (with `ETag` support; `?source=bundled` reads the on-disk bundled YAML even when a
    runtime override is in place), listing the bundled-vs-runtime overlay per catalog,
    inspecting cluster-wide rule state as a JSON envelope (`{generatedAt, loaderStats,
    rules}` — each row carries `status`/`localState`/`loaderKind`/`bundled`/`bundledContentHash`
    so a UI can render override badges without a second roundtrip), and exporting all rules
    as a tar.gz for backup / DR.
  Hot-updates survive OAP restart: at boot OAP merges bundled rule files with persisted
  runtime rules, so the cluster never silently regresses to the bundled defaults.
  All admin writes for a runtime-rule cluster serialize on a single "main" OAP
  (deterministic sorted-first peer, no leader election) — non-main nodes that receive
  an HTTP write transparently forward it to the main over the admin-internal gRPC bus,
  so an L7 load balancer in front of the admin port can route any operator request to
  any OAP. Cluster convergence on the periodic refresh tick is configurable via
  `receiver-runtime-rule.refreshRulesPeriod` (default `30` s). **The endpoint is
  disabled by default and listens on port `17128` (HTTP) when enabled. It has no
  built-in authentication — operators must gateway-protect it with IP allow-lists and
  never expose it to the public internet.** Routes mount on the new `admin-server`
  HTTP host, which is on by default; enable the runtime-rule feature with
  `SW_RECEIVER_RUNTIME_RULE=default`.
* **Live debugger for MAL / LAL / OAL** — implements [SWIP-13 Live Debugger for MAL / LAL / OAL](../swip/SWIP-13.md).
  Sample-based runtime debugger that captures per-stage inputs/outputs as the three DSLs
  process live ingest. Idle-path cost is one volatile-bool read per probe call site that
  JIT eliminates after warm-up; active sessions fan out to every cluster peer over the
  admin-internal gRPC bus so each peer captures its own slice. The fan-out is LB-safe:
  any node can serve any verb (POST mints `sessionId` on the receiving node, broadcasts
  install to peers, returns `404 rule_not_found` only when no node owns the rule), so an
  L7 load balancer in front of the admin port routes operator requests freely. Mounts on
  the shared `admin-server` host (`/dsl-debugging/*` for session control plane,
  `/runtime/oal/*` for the OAL rule picker). Disabled by default; enable with
  `SW_DSL_DEBUGGING=default` (admin-server itself is on by default).
  `injectionEnabled` is a
  boot-time codegen switch defaulting to `true` — once the module is enabled, probes
  fire and sessions record samples; set `false` only if the REST surface is wanted but
  no codegen-side probe overhead is acceptable. Per-session limits enforce hard caps
  (`recordCap` ≤ 10000, `retentionMillis` ≤ 1 hour) — out-of-range requests return
  `400 invalid_limits`. LAL sessions accept a per-session `granularity=block|statement`
  flag — block mode captures the parser/extractor/sink stages; statement mode
  additionally records one `line` entry per individual extractor statement, carrying
  the source-line number and verbatim DSL text so the UI can highlight which statement
  fired. MAL captures render the file-level filter's surviving SampleFamily map
  (`{"families": N, "items": [...]}`), so multi-metric expressions show cross-family
  filter narrowing in the captured payload. Capture payloads include raw log bodies and
  parsed maps — treat the admin port as authenticated infrastructure per the
  [Admin API security notice](../setup/backend/admin-api/readme.md). Per-DSL operator
  references: [MAL](../setup/backend/admin-api/dsl-debugging-mal.md),
  [OAL](../setup/backend/admin-api/dsl-debugging-oal.md),
  [LAL](../setup/backend/admin-api/dsl-debugging-lal.md).
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
* **Inspect API on admin-server.** Two new admin-only HTTP endpoints for
  browsing the live metric catalog and the entities currently emitting
  values for a given metric. `GET /inspect/metrics` lists every registered
  metric with its type / scope / catalog / value-column name / supported
  downsamplings (pure metadata, no I/O). `GET /inspect/entities` runs the
  storage backend's entity scan for a metric over a time range + step
  (capped at 300 rows) and returns each entity decoded into an MQE-ready
  payload — the response includes a `mqeEntity` block the operator pastes
  verbatim into the public GraphQL `execExpression` mutation, plus the
  source service's layer(s) (multi-layer services emit one row per layer).
  Restricted to `REGULAR_VALUE` / `LABELED_VALUE` metrics and to non-Process
  scopes; `HEATMAP` / `SAMPLED_RECORD` / `Process` / `ProcessRelation`
  return 400. Adds `IMetricsQueryDAO.listEntityIdsInRange` as an abstract
  method on the interface — any 3rd party storage backend must explicitly
  override or the build fails. **Enabled by default** (both `SW_INSPECT` and
  `SW_ADMIN_SERVER` are on by default); set `SW_INSPECT=` empty to disable.
  Operator reference: [Inspect API](../setup/backend/admin-api/inspect.md).
* **Status feature module relocation, finalized.** The legacy
  `status-query-plugin` was replaced by a new `status` feature module
  under `server-admin/`; the route set (`/status/cluster/nodes`,
  `/status/alarm/*`, `/status/config/ttl`, `/debugging/config/dump`,
  `/debugging/query/*`) keeps URIs and payloads unchanged. The selector
  renames from the QUERY-plugin form (`SW_QUERY=…,status-query-plugin`)
  to a top-level `SW_STATUS=default` (on by default); custom
  `application.yml` overrides referencing `status-query` need to repoint
  to `status`. Routes are admin-host only — see the "Status API is
  admin-host only" entry above for the public REST retirement.

#### OAP Server
* Runtime MAL/LAL hot-update rules can declare `layerDefinitions:` to introduce new
  layers. Ordinals are operator-pinned in the `100_000+` tier; the layer is
  refcount-tracked and unregistered when the last declaring rule is removed. See
  [runtime-rule-hot-update.md#dynamic-layers](../concepts-and-designs/runtime-rule-hot-update.md)
  for the conflict rules and limitations.
* Fix: remove the redundant tags from the `envoy-ai-gateway.yaml` LAL configuration.
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
* Custom `Layer`s can be declared without modifying the OAP source — via an operator-managed `layer-extensions.yml`, inline `layerDefinitions:` block in a MAL or LAL rule file, or a plugin extension. UI dashboard templates for new layers are auto-discovered from the `ui-initialized-templates/` directory. Recommended ordinal range for external layers is `>= 1000`; conflicting names or ordinals are reported at boot.
* LAL: support full arithmetic (`+`, `-`, `*`, `/`) on numeric operands and fix the original bug where `(tag("x") as Integer) + (tag("y") as Integer)` was treated as string concatenation — expressions like `input_tokens + output_tokens < 10000` produced the concatenated string `"2589115"` rather than the integer sum `2704`, so token-threshold conditions never triggered `abort {}`. Operand types are now inferred from explicit casts (`as Integer` / `as Long` / `as Float` / `as Double`), typed proto fields, or numeric literal shape (with `L` / `F` / `D` suffix support, e.g. `1000L`). The compiler honours JLS-style binary numeric promotion and emits Java arithmetic in the declared primitive type — `(x as Integer) + (y as Integer)` compiles to `int + int` (not widened to `long`). `+` with any String operand falls back to string concatenation; `-` / `*` / `/` against non-numeric operands produces a compile-time error. The `as Double` and `as Float` casts are accepted in `typeCast` clauses, including in `def` declarations. Numeric comparisons honour declared casts on both sides (no more universal `h.toLong()` wrapper).
* Fix: `avgHistogramPercentile` / `sumHistogramPercentile` meter functions reported the smallest finite bucket boundary (e.g. `10` for OTel `gen_ai_server_request_duration` whose `le` is rewritten from `0.01s` → `10ms`) for every rank when no samples were observed in any bucket. The percentile loop's `count >= roof` check matched on the first sorted bucket because both sides were `0`. `calculate()` now short-circuits to `0` for every rank when the windowed total is `0`.
* Fix: MAL `expPrefix` now applies to every metric source in `exp`, not just the leading one. Previously the prefix was spliced after the first `.`, so secondary metrics inside arguments (e.g. the divisor in `a.sum(['s']).safeDiv(b.sum(['s']))`) silently skipped the prefix — a rule like envoy-ai-gateway's `request_latency_avg` (`sum / count`) would tag-rewrite only the dividend. The injection is now AST-aware: every bare-IDENTIFIER metric source is wrapped, while downsampling-type constants (`SUM`, `AVG`, `LATEST`, `SUM_PER_MIN`, `MAX`, `MIN`) are skipped.
* Add `@Stream(allowBootReshape = true)` opt-in for additive boot-time reshape of BanyanDB streams / measures. Code-defined stream classes (e.g. `AlarmRecord`) can now annotate their schema as eligible for in-place additive update at OAP boot — a new `@Column` is appended to the live tag-family / fields via `client.update` instead of being silently rejected with `SKIPPED_SHAPE_MISMATCH` (which previously forced operators to drop the measure / stream and lose historical rows). Additive includes both new tags / fields **and** relocating an existing tag between families when a `@Column`'s `storageOnly` flag flips (e.g. `id1` moving from `storage-only` → `searchable` when it becomes indexed). The opt-in is per-stream and gated by an `isPurelyAdditive` shape diff: tag type changes, tag drops, kind flips (tag↔field), entity / interval / sharding-key changes, and field re-typing still skip with `SKIPPED_SHAPE_MISMATCH`, so identity-breaking edits remain explicit operator actions. Only the init / standalone OAP performs the reshape; non-init peers continue through the existing poll-and-wait loop so a single node drives DDL. When a `check*` records `SKIPPED_SHAPE_MISMATCH` the dependent `IndexRule` / `IndexRuleBinding` reconciliation is also skipped — preventing the previous gap where the binding silently updated to a tag list that diverged from the live tag-family layout. `AlarmRecord` is opted in. Default remains `false` for all other models — boot-time reshape stays off unless the annotation is explicitly set. **Operator caveat:** BanyanDB does not physically migrate existing rows when a tag's family changes; pre-existing data stays in its original on-disk location while new writes go to the declared family — expect a backfill window for queries that route through new IndexRules on relocated tags.
* Mask keywords `trustStorePass`, `keyStorePass` by default.

#### UI
* Add mobile menu icon and i18n labels for the iOS layer.
* Fix metric label rendering in multi-expression dashboard widgets.
* Add i18n menu labels for WeChat Mini Program and Alipay Mini Program (en / zh / es) — sub-menus rendered as raw keys until this bump.
* Support trace V1 view in trace single page.

#### Documentation
* Update LAL documentation with `sourceAttribute()` function and `layer: auto` mode.
* Add iOS app monitoring setup documentation.
* Add WeChat / Alipay Mini Program monitoring setup documentation, plus a client-side-monitoring section in the security guide covering public-internet ingress (OTLP + `/v3/segments`) for mobile / browser / mini-program SDKs.
* Improve downsampling documentation

All issues and pull requests are [here](https://github.com/apache/skywalking/issues?q=milestone:11.0.0)
