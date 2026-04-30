/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.receiver.runtimerule.module;

import com.linecorp.armeria.common.HttpMethod;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.core.alarm.AlarmModule;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegisterImpl;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.server.http.HTTPServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServerConfig;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.RuntimeRuleClusterClient;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.RuntimeRuleClusterServiceImpl;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.DSLManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.rest.RuntimeRuleRestHandler;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.TelemetryRelatedContext;

/**
 * Boots the runtime-rule admin surface and the components that converge MAL / LAL rule
 * changes across an OAP cluster. Disabled by default — the provider is loaded only when
 * an operator enables it (selector {@code default} or env var
 * {@code SW_RECEIVER_RUNTIME_RULE=default}). Until then the admin port never opens, no
 * scheduled tick fires, no cluster RPC is registered.
 *
 * <h2>What this provider wires</h2>
 * <ul>
 *   <li>{@link HTTPServer} on port 17128 — the admin REST surface
 *       ({@code /runtime/rule/addOrUpdate}, {@code /inactivate}, {@code /delete},
 *       {@code /list}, {@code /dump}, single-rule fetch, bundled catalogue).</li>
 *   <li>{@link DSLManager} + a single-thread scheduled executor — local-state convergence
 *       on the periodic tick (default 30 s) plus a synchronous first tick at boot.</li>
 *   <li>{@link RuntimeRuleClusterServiceImpl} on the cluster gRPC bus — receives Suspend
 *       / Resume / Forward RPCs from peers.</li>
 *   <li>{@link RuntimeRuleClusterClient} — outbound counterpart for broadcasts and
 *       forward-to-main writes.</li>
 *   <li>{@link RuntimeRuleManagementDAO} resolved through the active storage module —
 *       per-backend upsert / read / delete on the rule rows.</li>
 * </ul>
 *
 * <h2>Architecture: scheduler · orchestrators · engines</h2>
 * Three layers, with one boundary between each:
 * <ul>
 *   <li><b>Scheduler</b> ({@link DSLManager} + REST handler). DSL-agnostic. Owns lock
 *       acquisition, cluster Suspend/Resume RPC fan-out, persistence (DAO upsert),
 *       cross-file ownership enforcement, tick cadence, self-heal, classloader
 *       graveyard lifecycle, alarm-reset dispatch. Holds
 *       references to the engines via {@code RuleEngineRegistry} and drives the two
 *       orchestrators below.</li>
 *   <li><b>Orchestrators</b>. Two of them, one per pipeline:
 *     <ul>
 *       <li>{@link org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.DSLRuntimeApply}
 *           — apply pipeline for NEW / FILTER_ONLY / STRUCTURAL classifications. Routes to
 *           the right engine via the registry, drives compile → fireSchemaChanges → verify →
 *           commit | rollback. Returns an {@code Outcome} the scheduler reads to update
 *           snapshot + persistence.</li>
 *       <li>{@link org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.DSLRuntimeUnregister}
 *           — tear-down pipeline for INACTIVE / {@code /delete} / gone-keys cleanup. Routes
 *           to {@code engine.unregister}.</li>
 *     </ul>
 *     The orchestrators are DSL-agnostic — they only know the SPI surface, not which engine
 *     is registered behind a given catalog.</li>
 *   <li><b>Engines</b> ({@code MalRuleEngine}, {@code LalRuleEngine}, future
 *       {@code OalRuleEngine}). DSL-specific. Each implements
 *       {@link org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngine}: classify,
 *       claimedKeys, compile, fireSchemaChanges, verify, commit, rollback, unregister.
 *       Engines own everything that depends on the DSL — Javassist class generation, applier
 *       registration, post-DDL probe semantics, classloader retire, alarm-reset target sets.
 *       Adding a new DSL is one SPI implementation + a {@code RuleEngineRegistry.register}
 *       call; no scheduler or orchestrator edit needed.</li>
 * </ul>
 *
 * <pre>
 *   ┌─────────────────────────  scheduler  ──────────────────────────┐
 *   │ RuntimeRuleRestHandler  →  DSLManager.applyOneRuleFileInternal │
 *   │                                       │                        │
 *   │   • catalog routing (engineRegistry.forCatalog)                 │
 *   │   • main / peer routing (MainRouter)                            │
 *   │   • per-file lock acquisition                                   │
 *   │   • Suspend/Resume RPC fan-out                                  │
 *   │   • cross-file ownership guard (DAO + appliedX)                 │
 *   │   • storage-opt selection (withSchemaChange / withoutSchemaChange /       │
 *   │     verifySchemaOnly) — gates whether DDL fires                 │
 *   │   • persistence (RuntimeRuleManagementDAO.save) + 2-PC stash    │
 *   │     for STRUCTURAL via StructuralCommitCoordinator              │
 *   │   • DSLRuntimeUnregister orchestrator routes teardown to engine │
 *   └────────────┬────────────────────────────────┬──────────────────┘
 *                │                                │
 *                ▼                                ▼
 *   ┌──────  MalRuleEngine  ──────┐    ┌──────  LalRuleEngine  ──────┐
 *   │  catalogs: otel-rules,      │    │  catalogs: lal              │
 *   │            log-mal-rules,   │    │                             │
 *   │            telegraf-rules   │    │                             │
 *   │                             │    │                             │
 *   │  classify(old, new, ina)    │    │  classify(old, new, ina)    │
 *   │  claimedKeys(content, src)  │    │  claimedKeys(content, src)  │
 *   │  compile → CompiledMalDSL   │    │  compile → CompiledLalDSL   │
 *   │  fireSchemaChanges (no-op)  │    │  fireSchemaChanges (no-op)  │
 *   │  verify → null | error str  │    │  verify (no-op, null)       │
 *   │  commit                     │    │  commit                     │
 *   │  rollback                   │    │  rollback                   │
 *   │  unregister                 │    │  unregister                 │
 *   └─────────────────────────────┘    └─────────────────────────────┘
 * </pre>
 *
 * <h2>Phase pipeline (per-file)</h2>
 * <pre>
 *   classify  ─►  NO_CHANGE   →  scheduler skips (unless forced)
 *             ─►  INACTIVE    →  scheduler routes to engine.unregister
 *             ─►  NEW / FILTER_ONLY / STRUCTURAL → continue:
 *
 *   claimedKeys                    (scheduler runs cross-file guard on this set)
 *   engine.newApplyContext(inputs) (engine narrows shared inputs into its own context)
 *   engine.compile                 (compile classes + register handlers; NO commit yet)
 *   engine.fireSchemaChanges       (drive listener chain; no-op for MAL since fused into
 *                                   compile, no-op for LAL since no backend schema)
 *   engine.verify                  (post-DDL probe; MAL: isExists; LAL: no-op)
 *           │
 *           ├─ verify failed →  engine.rollback (drop just-registered)
 *           └─ verify ok      →  engine.commit  (atomic in-memory swap, retire CL,
 *                                                fire alarm reset)
 * </pre>
 *
 * <h2>Per-file lifecycle on shared mechanism</h2>
 * <pre>
 *   POST /runtime/rule/{addOrUpdate|inactivate|delete}
 *        │
 *        ├─ scheduler:  validate catalog (registry-driven), find main, forward if peer
 *        ├─ scheduler:  acquire per-file lock; broadcast Suspend (peers park dispatch)
 *        ├─ engines:    classify → claimedKeys → compile → fire → verify → commit | rollback
 *        ├─ scheduler:  persist row (DAO.save) — STRUCTURAL stashes commit until persist OK
 *        ├─ scheduler:  finalize commit  (success)  → drop removedMetrics, snapshot RUNNING,
 *        │              broadcastResume; or
 *        │              discard commit   (failure)  → engine.rollback, broadcastResume
 *        └─ scheduler:  release lock; return HTTP status (200 / 409 / 421 / 500 / 503)
 * </pre>
 *
 * <p>Peers converge on the next dslManager tick by reading the persisted row and re-running
 * the same engines under {@link StorageManipulationOpt#withoutSchemaChange} — peers register
 * local handlers + prototypes but skip backend DDL since main has already done the writes.
 * {@code /inactivate} is soft-pause (withoutSchemaChange — backend preserved, OAP-internal state
 * torn down); {@code /delete} is destructive (withSchemaChange so the listener chain fires
 * {@code dropTable}). Both ride the same {@link
 * org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.DSLRuntimeUnregister}
 * orchestrator that dispatches to {@code engine.unregister}.
 *
 * <h2>Catalog → engine routing</h2>
 * Catalog membership is data-driven through {@code RuleEngineRegistry}: a catalog is "MAL"
 * iff a registered engine is {@code MalRuleEngine}. Adding {@code telegraf-rules} support is
 * one entry in {@code MalRuleEngine.supportedCatalogs} — REST validation, scheduler routing,
 * and tick enumeration pick it up automatically. (Full telegraf apply additionally requires
 * the telegraf receiver module to expose a {@code MalConverterRegistry} service.)
 *
 * <p>The full architecture (single-main routing, lock acquisition policy, marker-debt
 * invariant for cold-boot / topology-shift, cross-file ownership semantics, soft-pause /
 * delete split, self-heal backstop) lives in the design doc:
 * {@code docs/en/concepts-and-designs/runtime-rule-hot-update.md}.
 */
@Slf4j
public class RuntimeRuleModuleProvider extends ModuleProvider {

    /**
     * Per-peer Suspend / Resume RPC deadline. 2 s — enough for a healthy cluster round-trip,
     * short enough that a single unreachable peer doesn't stall the /addOrUpdate latency.
     */
    private static final long SUSPEND_RPC_DEADLINE_MS = 2_000L;
    /**
     * Forward-to-main RPC deadline. Longer than Suspend / Resume because the forwarded
     * workflow includes compile + DDL + persist on the main. 30 s covers the typical upper
     * bound for a BanyanDB-backed apply with a handful of added metrics; larger rule files
     * may need tuning via the module config in a future change.
     */
    private static final long FORWARD_RPC_DEADLINE_MS = 30_000L;

    /**
     * Initial delay before the scheduled dslManager's first tick. 2 seconds — just past
     * {@code RemoteClientManager}'s 1 s initial refresh, so the peer list is almost always
     * populated by the time we run. This closes the cold-boot gap for runtime-only DB rows
     * when {@link #notifyAfterCompleted} decided to defer the synchronous first tick
     * (peer list not yet populated at that moment); without this, a restart could leave
     * persisted MAL/LAL overrides absent for a full {@code reconcilerIntervalSeconds} window
     * (default 30 s) while ingest runs against static-shape workers.
     *
     * <p>Deliberately NOT read from {@code reconcilerIntervalSeconds}: that value controls
     * steady-state convergence cadence, not the one-shot catch-up that must happen as soon
     * as the peer list is ready. Tick is idempotent, so running at 2 s and again at 2 s +
     * {@code reconcilerIntervalSeconds} is cheap — the hash-match short-circuit skips
     * unchanged bundles.
     */
    private static final long SCHEDULER_INITIAL_DELAY_SECONDS = 2L;

    private RuntimeRuleModuleConfig moduleConfig;
    private HTTPServer httpServer;
    private ScheduledExecutorService reconcilerExecutor;
    private DSLManager dslManager;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return RuntimeRuleModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<RuntimeRuleModuleConfig>() {
            @Override
            public Class type() {
                return RuntimeRuleModuleConfig.class;
            }

            @Override
            public void onInitialized(final RuntimeRuleModuleConfig initialized) {
                moduleConfig = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {
        if (moduleConfig.getRestPort() <= 0) {
            throw new ServiceNotProvidedException(
                "runtime-rule: restPort must be > 0 when the module is enabled, got " + moduleConfig.getRestPort());
        }
        final HTTPServerConfig httpServerConfig =
            HTTPServerConfig.builder()
                            .host(Strings.isBlank(moduleConfig.getRestHost()) ? "0.0.0.0"
                                      : moduleConfig.getRestHost())
                            .port(moduleConfig.getRestPort())
                            .contextPath(moduleConfig.getRestContextPath())
                            .acceptQueueSize(moduleConfig.getRestAcceptQueueSize())
                            .idleTimeOut(moduleConfig.getRestIdleTimeOut())
                            .maxRequestHeaderSize(moduleConfig.getHttpMaxRequestHeaderSize())
                            .build();
        httpServer = new HTTPServer(httpServerConfig);
        httpServer.setBlockingTaskName("runtime-rule-http");
        httpServer.initialize();
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        // DSLManager is constructed first so both the HTTP handler and the cluster Suspend
        // service can reference it. The scheduled executor is started in notifyAfterCompleted
        // after all other modules have finished their boot. The DSLManager builds its own
        // RuleEngineRegistry from the per-DSL state maps it owns.
        dslManager = new DSLManager(
            getManager(),
            moduleConfig.getSelfHealThresholdSeconds() * 1000L
        );

        // Cluster-facing Suspend client: fans out to every non-self peer on the OAP cluster bus
        // during an addOrUpdate / delete / inactivate so peers stop serving the old bundle
        // before the main node commits the row change. Uses the RemoteClient's established
        // ManagedChannel — no duplicate channel caching.
        final String selfNodeId = TelemetryRelatedContext.INSTANCE.getId();
        final RemoteClientManager remoteClientManager = getManager().find(CoreModule.NAME)
                                                                    .provider()
                                                                    .getService(RemoteClientManager.class);
        final RuntimeRuleClusterClient clusterClient = new RuntimeRuleClusterClient(
            remoteClientManager, selfNodeId, SUSPEND_RPC_DEADLINE_MS);

        // The runtime-rule surface runs on its own HTTPServer bound to a distinct admin port;
        // it intentionally does not share the sharing-server HTTPHandlerRegister so the admin
        // port stays isolated from the public receiver port.
        final RuntimeRuleRestHandler restHandler = new RuntimeRuleRestHandler(
            getManager(), dslManager, clusterClient, FORWARD_RPC_DEADLINE_MS);
        final HTTPHandlerRegister adminRegister = new HTTPHandlerRegisterImpl(httpServer);
        adminRegister.addHandler(
            restHandler,
            Arrays.asList(HttpMethod.POST, HttpMethod.GET)
        );

        // Register the cluster-internal Suspend / Resume / Forward RPCs on the OAP cluster-bus
        // gRPC server (the same server that hosts RemoteService and HealthCheck). Every OAP
        // node in the cluster exposes these endpoints so: (a) the main can fan out Suspend /
        // Resume to peers during a STRUCTURAL apply, and (b) a non-main OAP that receives an
        // HTTP write can transparently forward the work to the main via Forward. The service
        // needs a late-bound REST-handler reference for the Forward dispatch target — wired
        // right after construction so the first incoming Forward RPC has a workflow to call.
        final GRPCHandlerRegister clusterGrpc = getManager().find(CoreModule.NAME)
                                                            .provider()
                                                            .getService(GRPCHandlerRegister.class);
        final RuntimeRuleClusterServiceImpl clusterService =
            new RuntimeRuleClusterServiceImpl(dslManager, selfNodeId);
        clusterService.setRuntimeRuleService(restHandler.getService());
        clusterGrpc.addHandler(clusterService);
        log.info(
            "Runtime rule Suspend / Resume / Forward RPCs registered on cluster gRPC server "
                + "(selfNodeId={}).", selfNodeId
        );
    }

    @Override
    public void notifyAfterCompleted() throws ModuleStartException {
        // Seed synthetic applied-state entries from the static rules the catalog loaders
        // already registered (MeterProcessService, OpenTelemetryMetricRequestProcessor,
        // LogFilterListener.Factory). With the seed in place, the dslManager's first tick
        // knows those bundles are live — rehydrate won't double-apply — and a later
        // /inactivate can cleanly tear down the boot-registered handlers via unregisterBundle
        // (which now consults StaticRuleRegistry when appliedMal / appliedLal has no entry).
        try {
            dslManager.getStaticRuleLoader().loadAll();
        } catch (final Throwable t) {
            log.warn("Runtime rule dslManager: static-bundle seeding failed — static rules "
                + "will still serve, but the first /inactivate against a shipped rule may "
                + "need a restart to fully converge.", t);
        }

        // Run one tick before receivers open to close the boot gap for runtime-only rows
        // (no static file substitute). Unconditional — no peer-list-readiness gate. The
        // earlier gate consulted {@code RemoteClientManager.getRemoteClient().isEmpty()},
        // but that signal is "list is non-empty right now", not "membership has stabilised".
        // In a k8s rollout the list flips to non-empty as soon as self joins it, then keeps
        // changing for tens of seconds as more pods boot. Gating on it neither guaranteed
        // membership stability nor saved a wasteful first apply. If this tick runs under
        // {@code withoutSchemaChange} because peer list is empty, the next scheduled tick (2 s
        // later) re-evaluates with whatever {@code RemoteClientManager} now shows and re-
        // applies under {@code withSchemaChange} if this node resolves as main. Backend DDL is
        // idempotent so the re-apply costs nothing.
        try {
            // atBoot=true so a no-init OAP picks verifySchemaOnly and refuses to
            // start with a missing or shape-mismatched backend (k8s pod backloop)
            // instead of silently registering local workers against schema that
            // doesn't exist. Init / default-mode OAPs are unaffected — their boot
            // opt mirrors the standard tick choice for those modes.
            dslManager.tick(true);
            log.info("Runtime rule dslManager: synchronous first tick completed "
                + "(runtime-only DB rows are now applied locally).");
        } catch (final RuntimeException re) {
            // Boot pass under verifySchemaOnly re-throws missing/mismatch as a
            // RuntimeException so module bootstrap aborts. Translate to
            // ModuleStartException so the OAP exit message points the operator at
            // the right place.
            throw new ModuleStartException(
                "Runtime rule dslManager boot pass failed under verifySchemaOnly; "
                    + "the backend schema is missing or diverges from the declared rule. "
                    + "Bring up the init OAP first or align rule files with the backend, "
                    + "then restart this node.",
                re);
        } catch (final Throwable t) {
            log.warn("Runtime rule dslManager: synchronous first tick failed — "
                + "runtime-only DB rows will be picked up on the scheduled tick.", t);
        }

        if (httpServer != null && !RunningMode.isInitMode()) {
            httpServer.start();
            log.info(
                "Runtime rule admin HTTP server listening on {}:{} (disabled-by-default "
                    + "module is now active — gateway-protect or restrict to localhost).",
                moduleConfig.getRestHost(), moduleConfig.getRestPort()
            );
        }

        // DSLManager runs on its own single-thread executor so the tick body cannot starve any
        // other OAP scheduler. Tick interval is configurable; default 30s. The DSLManager
        // instance itself was constructed in start() so the cluster Suspend service could
        // reference it — we just schedule its tick here.
        reconcilerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread t = new Thread(r, "runtime-rule-dslManager");
            t.setDaemon(true);
            return t;
        });
        final long intervalSeconds = moduleConfig.getReconcilerIntervalSeconds();
        // Initial delay is fixed at SCHEDULER_INITIAL_DELAY_SECONDS (2 s), not intervalSeconds.
        // The synchronous tick in notifyAfterCompleted may have skipped because the peer list
        // wasn't ready; running the first scheduled tick only after intervalSeconds (30 s by
        // default) would leave persisted runtime-only rules dark for that whole window. By
        // firing the first scheduled tick ~2 s in, RemoteClientManager's 1 s initial refresh
        // has almost certainly populated the peer list, and tickStorageOpt can make a stable
        // main/peer decision. Tick is idempotent, so firing at 2 s and then at 2 s +
        // intervalSeconds is cheap — unchanged bundles short-circuit on hash.
        reconcilerExecutor.scheduleWithFixedDelay(
            dslManager::tick,
            SCHEDULER_INITIAL_DELAY_SECONDS, intervalSeconds, TimeUnit.SECONDS
        );
        log.info("Runtime rule dslManager scheduled: first tick in {} s, then every {} s.",
            SCHEDULER_INITIAL_DELAY_SECONDS, intervalSeconds);
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            // CoreModule — required for RemoteClientManager (cluster peer list for routing +
            // broadcast), MeterSystem + IModelManager (apply pipeline), and GRPCHandlerRegister
            // (exposing Suspend / Resume / Forward RPCs on the cluster bus).
            CoreModule.NAME,
            // StorageModule — RuntimeRuleManagementDAO + ManagementStreamProcessor target live
            // here; without it, /list, /delete, and dslManager reads have no backend.
            StorageModule.NAME,
            // LogAnalyzerModule — exposes the LogFilterListener.Factory service the dslManager's
            // LAL apply path drives. Always declared so module boot fails fast rather than
            // masking a broken deployment behind the runtime-rule's "LAL Factory unavailable"
            // surface.
            LogAnalyzerModule.NAME,
            // AlarmModule — the dslManager fires AlarmKernelService.reset after STRUCTURAL and
            // unregister paths. Declared so module boot fails fast when deployments accidentally
            // drop the alarm module. The DSLManager still wraps the lookup in try/catch for
            // defensive handling of transient provider outages, not as an "optional module"
            // signal.
            AlarmModule.NAME,
            // TelemetryModule — exposes MetricsCreator for the lock-observability histograms +
            // counters (runtime_rule_lock_*). Declared so the module refuses to start on a
            // deployment where internal metrics wouldn't surface; LockMetrics itself still
            // null-guards the resolve call so test topologies without telemetry can instantiate
            // the handler without recording metrics.
            TelemetryModule.NAME,
        };
    }
}
