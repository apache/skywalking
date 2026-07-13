## 11.0.0

#### Project

* Extend the `GET /inspect/entities` admin API to inspect a metric persisted by **any** OAP, even one this node does not define locally. When the metric is unknown to the local registry, the caller supplies `valueColumn` + `valueType` and the storage backend resolves the physical index/table/group from its own running config (no DB schema/table-metadata read): ES uses the merged `metrics-all` index + `metric_table` discriminator, JDBC probes the node's function tables by the `table_name` discriminator, and BanyanDB synthesizes a read-only measure schema. Scope is no longer required — the `entity_id` is decoded structurally (service / 2nd-level / relations) with a generic `name` leaf. Locally-defined metrics keep the exact field names, scope, and `mqeEntity` as before.
* Add the `POST /inspect/values` admin API — read the value series of a metric persisted by **another** OAP (one this node does not define locally) by supplying its `{valueColumn, valueType}`. The real MQE engine runs over a request-scoped `InspectQueryContext` overlay (provide-if-absent — the local catalog always wins) that makes the foreign metric look registered to every read path: `ValueColumnMetadata` resolves its value column / type / scope, and the storage location registries resolve where it lives (`MetadataRegistry` synthesizes a BanyanDB measure schema, `IndexController` resolves the ES `metrics-all` index, `TableHelper` probes the JDBC function tables), so the read returns the native MQE `ExpressionResult` with no per-DAO special-casing. Admin-only (a forced read this OAP cannot validate); not mirrored onto the public REST / GraphQL surface. See the [Inspect API](../setup/backend/admin-api/inspect.md).
* Remove the always-on alarm-to-event conversion (`EventHookCallback`). A triggered alarm is no longer synthesized into the events pipeline as an `Alarm`/`AlarmRecovery` event; events now originate only from real event sources (agents, SkyWalking CLI, Kubernetes Event Exporter). Alarms remain available through the alarm store (`getAlarm`/`queryAlarms`) and the configured alarm hooks. This drops a documented "Known Event" and removes 1-2 synthetic event records per alarm fire.
* **TLS for all OAP HTTP/REST servers, with cert hot-reload.** Adds the
  `restSSLEnabled` / `restSSLKeyPath` / `restSSLCertChainPath` config structure to every
  OAP HTTP server — core REST, sharing-server, admin, PromQL, LogQL, TraceQL and Zipkin
  query/receiver — each with its own dedicated environment variables (`SW_CORE_REST_SSL_*`,
  `SW_RECEIVER_SHARING_REST_SSL_*`, `SW_ADMIN_SERVER_REST_SSL_*`, `SW_PROMQL_REST_SSL_*`,
  `SW_LOGQL_REST_SSL_*`, `SW_TRACEQL_REST_SSL_*`, `SW_QUERY_ZIPKIN_REST_SSL_*`,
  `SW_RECEIVER_ZIPKIN_REST_SSL_*`). The shared Armeria `HTTPServer` reloads the key pair
  from disk on rotation (via `TlsProvider.ofScheduled`) so refreshed certificates are
  picked up without restarting the OAP, matching the existing gRPC SSL hot-reload
  behavior. HTTP TLS is server-side only (no mTLS).
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
  schedule, with released container images on Docker Hub at
  [`apache/skywalking-ui`](https://hub.docker.com/r/apache/skywalking-ui)
  (tags `latest` / `horizon-<version>`; per-commit development images live on
  `ghcr.io/apache/skywalking-horizon-ui`).
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
  services" gating. Upgrade path: replace `skywalking/ui:<tag>` with the
  Horizon UI image `apache/skywalking-ui:latest` (or a `horizon-<version>`
  tag — pick a version per Horizon UI's OAP-compatibility notes, OAP `11.0+`
  is supported) in your deployment, expose
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
* Add Node.js runtime metrics via the Node.js agent **`MeterReportService`** pipeline (`meter_instance_nodejs_*`, 1s collect/report). OAP analyzes raw meters through `nodejs-runtime.yaml`. Node.js E2E asserts six `meter_instance_nodejs_*` metrics (`test/e2e-v2/cases/nodejs/e2e.yaml`).
* Add PHP runtime PHM meter analyzer (`php-runtime.yaml`) for SkyWalking PHP agent process
  metrics (CPU, memory, virtual memory, thread count, open file descriptors sampled from
  `/proc` on Linux). Registers six `meter_instance_php_*` metrics on the General Service
  layer; `php-runtime` is included in the default `meterAnalyzerActiveFiles`.
* Batch the BanyanDB schema fence per runtime-rule apply. A runtime-rule file changes dozens of rules at once, but the post-DDL fence (`SchemaWatcher.awaitRevisionApplied`) ran once per metric/downsampling, so a large file did `K×M` sequential ≤2s fences — on a laggy cluster that overran the apply's REST budget. The main-node apply path now uses `StorageManipulationOpt.withSchemaChangeDeferredFence()`: the installer records each resource's `mod_revision` without fencing and registers a single flush that the apply runs once on the file's max revision, collapsing the whole file to one barrier. The flush is one-shot — a reconciler tick reuses one opt across every rule file, so after a file flushes, the closure and accumulated revision reset and each file fences on its own DDL only. Drops still fence inline on the dropped resource's own delete revision — or, when that delete recorded no tombstone (`mod_revision == 0`), on a key-based deletion barrier (`AwaitSchemaDeleted`) — never on the shared opt's cumulative revision, so a tombstone-less delete in a multi-file tick is still confirmed removed. On the operator REST apply the single create/update fence runs on a configurable, generous budget (default 180s) in the background **before** the rule row is persisted and dispatch resumes — it gates the persist + local commit + peer resume so the durable commit point is only reached once the schema is confirmed cluster-wide, and writes never resume against an un-propagated schema (see the apply-status entry below); the reconciler tick keeps the short inline 2s fence (a background reconcile must not wait minutes per file). Peer / `withoutSchemaChange` applies are unaffected (no fence).
* Add a runtime-rule apply-status query. The cluster main now tracks each structural apply through a phase machine (`SchemaApplyCoordinator`: pending → DDL → fencing → rolling-out → applied, with `degraded` for a committed-but-unconfirmed apply — the cluster schema fence did not confirm within the timeout, in which case the lagging data-node ids are surfaced as `fenceLaggards` and dispatch is resumed anyway, or the local commit-tail threw — and `failed` carrying the specific reason). The schema fence runs on a configurable, generous budget (`receiver-runtime-rule.deferredFenceTimeoutSeconds`, default 180s) and **gates everything durable or visible**: because an un-propagated write is silently dropped at the data node, the order after a successful DDL is suspend → DDL → **fence → persist → commit → resume**. The rule row (the durable commit point) is written only AFTER the fence confirms, so "durable" implies "schema propagated cluster-wide" — a main crash before persist leaves no row (peers/crash-recovery stay safely on the old content; the orphaned measure is inert), and any durable row is guaranteed fence-confirmed, so convergence never resumes dispatch against an unpropagated schema. The fence + persist + resume run in the background so they never block the HTTP response — `POST /addOrUpdate` returns its `applyId` immediately at `fencing` (accepted, not yet durable; dispatch for that rule still paused — a clean gap, not dropped writes), and the operator polls `GET /runtime/rule/status` to watch `fencing → rolling-out → applied` (or `degraded`/`failed`); on a genuine laggard, dispatch resumes after the budget so one stuck node can't park the metric forever. A `GetApplyStatus` admin-internal gRPC served by the main backs the query — by `applyId`, or by `catalog`+`name` (+ optional `contentHash`, the durable identity) once the handle is gone after a page refresh. When the live status is gone (apply-id evicted, main restarted, or the main is unreachable), the query degrades to the durable rule row: a matching `ACTIVE` row reports `applied` derived from the content hash (a durable row is, by the fence-then-persist order, already propagation-confirmed). Non-main nodes route the read to the deterministic main; status is in-memory by design, with the content hash reconstructing truth after a restart.
* Push runtime-rule convergence to peers on commit. After a successful structural apply — and on the `commit_deferred` path, where the DB row is durable but this node's commit-tail threw — the main broadcasts a `NotifyApplied` admin-internal RPC so peers reconcile against the just-persisted DB row immediately, instead of waiting up to one refresh tick (~30s) to notice it. The fan-out runs off the REST response thread (fire-and-forget on a daemon executor) so an unreachable peer's per-call deadline never adds to the operator's apply latency. On the peer side the notify-triggered reconcile is coalesced: a burst of notifies (a multi-rule file, or several applies) collapses to a single queued full reconcile rather than one redundant `dao.getAll()` scan per notify. The notify is best-effort and idempotent (the peer runs its normal per-file-locked reconcile; a lost notify is harmless — the peer still self-converges on its next tick), so it tightens the cluster-convergence window without adding a hard dependency on the main being reachable.
* Fix BanyanDB peer nodes permanently flooding `<metric> is not registered`, and a follow-on case where a peer kept translating writes with a stale schema shape after a runtime-rule reshape, when a node held a live persist worker but its local `MetadataRegistry` schema cache was missing or stale for that model — a `withoutSchemaChange` peer apply or a runtime-rule bundled fall-over rebuilt the dispatch worker but skipped the local-cache populate, and the registry was insert-only (never evicting) while the 30s reconcile only covers runtime-rule rows, so nothing re-derived it. The peer / local-cache-only install path now (re)derives and overwrites the local schema entry from the declared model with zero server RPC — honoring the `inspectBackend=false` contract so the cache can never lag the worker, including across a reshape — and a model removal now evicts its cache entry so a dropped or reshaped model leaves no stale translation behind; the persist DAOs keep an RPC-free re-derivation as a read-side backstop, and the no-init defer poll loop retries a transient backend probe error instead of escaping and crash-looping the pod.
* Support LAL `json {}` parsing JSON content delivered in a plain-text log body. The parser reads the native protocol's JSON body first; when that is empty, it tries the text body as JSON — e.g. the OTLP log receiver maps every OTLP string body to a text body, even JSON-shaped ones, so previously-aborting `json {}` rules on OTLP-fed layers now work without any receiver or protocol change. On a successful parse from a text body, the matching rule persists the log as a JSON body with content type `JSON`; the normalization is scoped to that rule's context — other rules analyzing the same log still see the original text body.
* Surface the drop reason in LAL live-debugging. When a LAL rule stops a log at a parse step (a `json {}` / `yaml {}` parse failure, a `text { regexp }` non-match, or a non-log-body input), the recorder now captures a human-readable `reason` (e.g. the parse exception) onto the DSL-debug `Sample`, exposed through the `dsl-debugging` REST session response and the cluster forward proto. Previously a live-debug watcher could only see `continueOn=false` — that a step stopped, never why — and had to read the OAP server log. `Sample.reason` is shared across all DSL debuggers but populated by LAL today.
* Fix a v2 MAL `CounterWindow` key collision: `rate()` / `increase()` / `irate()` keyed each counter's sliding window on the rule's output metric name (the same for every input metric of a rule) instead of the counter's own name, so two or more counters that reduce to the same label set after `.sum(...)` shared one window and computed rates against each other's values — fabricating non-zero rates from unchanged counters (e.g. the BanyanDB liaison gRPC error rate read a steady non-zero off three frozen error counters). The window is now keyed by the counter's own metric name.
* Fix the v2 MAL Elvis operator `?:` to honor Groovy-falsy semantics. It compiled to `Optional.ofNullable(primary).orElse(fallback)`, applying the fallback only when the primary is `null`, so an empty-string primary kept `""` instead — e.g. a BanyanDB liaison `ServiceInstance` stored `node_type=""` rather than `n/a`, because `.sum([...,'node_type'])` fills an absent group-by label with `""`. The fallback now applies for falsy primaries such as null, false, numeric zero, and empty strings/containers.
* SWIP-15: rebuild BanyanDB self-observability around the cluster / container / group model (requires BanyanDB 0.11+). A BanyanDB cluster is modeled as one `Service`, each container as a `ServiceInstance` (role/tier as attributes), and each storage group as an `Endpoint`. The `otel-rules/banyandb/` rules are category-separated by role (`node_*` / `liaison_*` / `data_*` / `lifecycle_*`) and by data type (`measure_*` / `stream_*` / `trace_*` / `property_*`), mirroring the upstream FODC-proxy Grafana boards, and include queue batch/message granularity (apache/skywalking-banyandb#1169). Adds a `SERVICE_INSTANCE_RELATION` MAL scope and `serviceInstanceRelation(...)` builder powering a new intra-cluster pod-to-pod deployment topology (`banyandb-instance-relation.yaml`). The stale single-node `host_name` model is removed.
* Runtime MAL/LAL hot-update rules can declare `layerDefinitions:` to introduce new
  layers. Ordinals are operator-pinned in the `100_000+` tier; the layer is
  refcount-tracked and unregistered when the last declaring rule is removed. See
  [runtime-rule-hot-update.md#dynamic-layers](../concepts-and-designs/runtime-rule-hot-update.md)
  for the conflict rules and limitations.
* Fix: runtime-rule (MAL/LAL hot-update) schema changes now work in `no-init` mode — the deployment mode every production cluster runs. Previously a runtime `addOrUpdate` that introduced a new metric blocked forever in the storage installer's init-node poll loop (`ModelInstaller.whenCreating`) on a `no-init` OAP, because the gate keyed off `RunningMode` rather than the operation's intent; the `/delete?mode=revertToBundled` recreate and BanyanDB in-place shape updates were dead the same way. The poll loop is now gated on a new `StorageManipulationOpt.Flags.deferDDLToInitNode` bit set only on the static boot-time `schemaCreateIfAbsent()` opt (DRYed into `ModelInstaller.deferDDLToInitNode(opt)` and reused by the BanyanDB shape-check / group-DDL gates), so the runtime-rule opts (`withSchemaChange` / `verifySchemaOnly` / `withoutSchemaChange`) are driven by their flags and by cluster main-ness — `no-init` and `default` no longer differ for DSL DDL; `init` mode stays the dedicated initializer. `DSLManager.tickStorageOpt` is collapsed accordingly (main → `withSchemaChange`, peer → `verifySchemaOnly` at boot / `withoutSchemaChange` on tick).
* Fix: runtime-rule cross-node writes no longer fail with `HTTP 400 forward_self_loop` on a multi-replica Kubernetes cluster. Every OAP replica shared the cluster `selfNodeId` `0.0.0.0_11800` (derived from the `0.0.0.0` agent gRPC bind host via `TelemetryRelatedContext`), so the main's self-loop guard rejected a legitimate peer-to-peer Forward as if it had looped back. The runtime-rule node identity now prefers the unique per-pod `SKYWALKING_COLLECTOR_UID` (the pod UID injected by the helm chart / swck operator from `metadata.uid`), resolved in `start()` before any apply, and falls back to the telemetry id for non-k8s deployments. Adds a kind-based no-init cluster e2e (`test/e2e-v2/cases/runtime-rule/cluster`, deployed via skywalking-helm with `oap.replicas=2`) that drives the apply / STRUCTURAL / inactivate / delete lifecycle and the cross-node Forward path, replacing the prior docker-compose default-mode cluster case.
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
* Add Apache Airflow monitoring via native OpenTelemetry metrics (SWIP-7). New `AIRFLOW` layer with Service (cluster) and Instance (host) dimensions, MAL rules under `otel-rules/airflow/` (**27** metrics), [setup documentation](../setup/backend/backend-airflow-monitoring.md), mock OTLP e2e (`cases/airflow/mock/e2e.yaml`: 2 entity + 27 metric checks, 29 total), and real Celery-cluster integration smoke (`cases/airflow/cluster/e2e.yaml`: 2 entity + 14 metric checks, 16 total). See `test/e2e-v2/cases/airflow/README.md`. Horizon UI dashboards ship separately in `apache/skywalking-horizon-ui` under the Workflow Scheduler menu group.
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
* Bump up dependencies to clear CVE alerts on shipped OAP jars: log4j `2.25.3` → `2.25.4`, jackson `2.18.5` → `2.18.6`, kafka-clients `3.4.0` → `3.9.2`, postgresql `42.4.4` → `42.7.11`, commons-compress `1.21` → `1.26.2`.
* Bump up more dependencies to clear CVE alerts on shipped OAP jars: netty `4.2.12.Final` → `4.2.15.Final`, jackson `2.18.6` → `2.18.8`, commons-codec `1.11` → `1.13`. Also realign `jackson-databind` `2.16.0` → `2.18.8` so the whole jackson family is managed at a single version (it had been left behind the other jackson artifacts).
* Bump Apache Curator `4.3.0` → `5.9.0` and Apache ZooKeeper `3.5.7` → `3.9.5` together to clear CVE-2023-44981 (the bundled ZooKeeper jar carried it; OAP is a ZooKeeper client only, so the server-side bug was never reachable, but the jar tripped Dependabot). The cluster-zookeeper and configuration-zookeeper plugins use only stable Curator APIs, so no source changes were required. Operator-facing change: the supported ZooKeeper server version is now 3.6+ (Curator 5.x uses ZooKeeper persistent watches, added in server 3.6.0); older servers (3.5.x, 3.4.x) are no longer supported.
* Migrate the Consul cluster and configuration client from the abandoned `com.orbitz.consul:consul-client` `1.5.3` to the maintained fork `org.kiwiproject:consul-client` `0.9.0` to clear the okhttp CVE the old client carried (CVE-2021-0341; the old client pinned okhttp `3.14.9`, fixed in okhttp `4.9.2+`), so the BOM now pins okhttp to `4.12.0`. The fork's `0.9.x` line is the last one built for JDK 11 (which SkyWalking still targets); `1.0.0+` is compiled to JDK 17 bytecode, so the migration stays on `0.9.0`. The cluster-consul and configuration-consul plugins use only stable Consul client APIs, so the change is a package rename (`com.orbitz.consul` → `org.kiwiproject.consul`); okhttp is pulled only by the Consul plugins (the fabric8 Kubernetes client excludes its okhttp transport), so no other module is affected.
* Bump test-scope assertj-core `3.20.2` → `3.27.7` to clear CVE-2026-24400 (XXE in `isXmlEqualTo`, not used by any test).
* Clear three security alerts: bump the Airflow e2e mock's pinned `protobuf` `4.25.8` → `5.29.6` (with `opentelemetry-proto` `1.24.0` → `1.28.0`, whose `protobuf<5.0` cap was the blocker, and `grpcio` `1.62.2` → `1.63.2`, required because `opentelemetry-proto` `1.28.0`'s gRPC stubs call `unary_unary(_registered_method=...)`) to clear CVE-2026-0994 — a CI-only test fixture, never shipped; and widen the cumulative `count` accumulator from `int` to `long` in `SumHistogramPercentileFunction` / `AvgHistogramPercentileFunction` to clear the CodeQL `implicit-cast-in-compound-assignment` alerts (`count += value` silently narrowed a `long` bucket-count sum back to `int`, while `total` was already `long`).
* Fix: continuous profiling policy validation now rejects a threshold / count of `0` to match the error messages and rover's `value >= threshold` trigger semantics (a `0` threshold would always trigger). CPU percent and HTTP error rate are tightened from `[0-100]` to `(0-100]`.
* Fix wrong BanyanDB resource options in record data.
* Align the default BanyanDB stage `segmentInterval` values so each coarser stage is an integer multiple of the finer one (`records` cold `3` → `4`, `metricsMinute` cold `5` → `6`, `metricsHour` warm `7` → `10` and cold `15` → `20`), keeping hot → warm → cold lifecycle migration on the cheap whole-segment fast path.
* Fix: `layer-extensions.yml` is now excluded from the `skywalking-oap` jar and shipped to the distribution `config/` directory, so an operator-edited `config/layer-extensions.yml` is no longer shadowed by the empty template bundled in the jar. Because the OAP launch script puts `oap-libs/*.jar` ahead of `config/` on the classpath, `ResourceUtils.read("layer-extensions.yml")` previously always resolved the jar-bundled `layers: []` and silently ignored the operator's file — custom layers declared there never registered. The file now follows the same exclude-from-jar + copy-to-`config/` packaging as every other operator-editable config (`application.yml`, `alarm-settings.yml`, etc.).
* Fix: the v2 MAL compiler now resolves custom layers referenced as `Layer.NAME` in an expression. A custom layer declared through a `layerDefinitions:` block (or `layer-extensions.yml` / the `LayerExtension` SPI) has no generated `Layer.*` static field, so `service(['svc'], Layer.IOT_FLEET)` previously failed code generation because `Layer` has no `IOT_FLEET` field. The compiler now lowers every `Layer.NAME` static-field reference to a runtime `Layer.nameOf("NAME")` registry lookup, so a custom layer can be referenced exactly like a built-in one (`Layer.GENERAL`). For a built-in layer this is equivalent, because `Layer.nameOf("GENERAL")` returns the same instance as the `Layer.GENERAL` field. The lowering is scoped to `Layer` only; the other MAL enum types (`DetectPoint`, `DownsamplingType`, etc.) are real Java enums and keep their direct static-field reference.
* Fix Envoy ALS rendering for the LAL live-debugger and the persisted log `content`: an Istio metadata-exchange peer in `common_properties.filter_state_objects` (legacy Wasm `wasm.*_peer` = `Any{BytesValue}` wrapping a FlatBuffer, or modern `*_peer` = `Any{Struct}`) is now decoded into the readable peer metadata (pod / namespace / labels) instead of an opaque `jsonformat-failed` envelope or base64. The serialization is hardened so a single un-printable field can no longer blank the whole entry — the `LalPayloadDebugDump` printer carries a well-known-type `TypeRegistry` and sanitizes every value `JsonFormat` would reject (an unresolvable, no-slash, or corrupt-bytes `Any` degrades to an `@unresolved` placeholder; a non-finite `Value` double `NaN`/`Infinity` is rendered as a string), keeping the rest of the entry readable. Because the LAL output builder's `bindInput` runs eagerly before the debug capture, this also stops an unregistered `filter_state_objects` type from throwing and aborting the whole rule (dropping the mesh log). Decoding is wired through a new `LalInputDebugRenderer` SPI (`EnvoyAlsHttpDebugRenderer` / `EnvoyAlsTcpDebugRenderer`) so `log-analyzer` reaches the receiver-side decoders without depending on the Envoy receiver, and covers both HTTP and TCP access logs.
* Surface the effective BanyanDB configuration (`bydb.yml` / `bydb-topn.yml`) in the `/debugging/config/dump` admin API. Because the BanyanDB config moved to a separate file in 10.2.0, a BanyanDB deployment previously showed an empty `storage.banyandb` block in the dump; its post-environment-resolution values are now merged into the same response under `storage.banyandb.*` (TopN rules under `storage.banyandb.topN.*`), masked by the same secret-keyword list, via a generic `ConfigDumpExtension` SPI on `ServerStatusService` that any module loading config from a secondary file can implement.
* Fix: an MQE `top_n(metric, N, order, attrX='value')` query whose attribute is not a column of the target metric now returns a descriptive MQE error instead of a raw storage `IOException` surfaced as `Internal IO exception, query metrics error.`. Attribute columns (`attr0..attrN`) exist only on decorated metrics (`service_*` / `endpoint_*` / `kubernetes_service_*`, set to the layer name via OAL `.decorator(...)`) and the MAL meter base; metrics such as relations or database / cache / mq access carry none, so passing an attribute condition previously reached the storage engine with a tag it does not define and failed there. `MQEVisitor` now validates each attribute key against the metric's registered queryable columns before the storage call and raises `IllegalExpressionException` (naming the attribute and the metric) when it is absent.

#### UI
* Add Airflow layer dashboards and menu i18n under Workflow Scheduler in Horizon UI (SWIP-7).
* Add mobile menu icon and i18n labels for the iOS layer.
* Fix metric label rendering in multi-expression dashboard widgets.
* Add i18n menu labels for WeChat Mini Program and Alipay Mini Program (en / zh / es) — sub-menus rendered as raw keys until this bump.
* Support trace V1 view in trace single page.

#### Documentation
* Update LAL documentation with `sourceAttribute()` function and `layer: auto` mode.
* Add Airflow monitoring setup documentation (SWIP-7).
* Add iOS app monitoring setup documentation.
* Add WeChat / Alipay Mini Program monitoring setup documentation, plus a client-side-monitoring section in the security guide covering public-internet ingress (OTLP + `/v3/segments`) for mobile / browser / mini-program SDKs.
* Improve downsampling documentation
* Fix the docker-compose quickstart: OAP healthcheck no longer calls `curl` (absent from the JRE image) and probes the query port via bash `/dev/tcp`; the Horizon UI service maps the correct container port (8081) and mounts a `horizon.yaml` (binding `0.0.0.0`, OAP URLs, demo `admin`/`admin` login) instead of non-existent `SW_*_ADDRESS` env vars.
* Add PHP runtime metrics (PHM) dashboard documentation (agent setup, OAP `php-runtime` MAL rules, Horizon UI widgets).
* Add Node.js runtime metrics dashboard documentation (agent setup, OAP `nodejs-runtime` MAL rules, Horizon UI widgets).

All issues and pull requests are [here](https://github.com/apache/skywalking/issues?q=milestone:11.0.0)
