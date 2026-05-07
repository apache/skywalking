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

package org.apache.skywalking.oap.server.admin.dsl.debugging.module;

import com.linecorp.armeria.common.HttpMethod;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.log.analyzer.v2.dsldebug.LalStaticBindingHook;
import org.apache.skywalking.oap.meter.analyzer.v2.dsldebug.MalStaticBindingHook;
import org.apache.skywalking.oap.server.core.dsldebug.DSLDebugCodegenSwitch;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.ClusterPeerCaller;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.DSLDebuggingClusterClient;
import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.DSLDebuggingClusterServiceImpl;
import org.apache.skywalking.oap.server.admin.dsl.debugging.lal.LALDebugRecorderFactory;
import org.apache.skywalking.oap.server.admin.dsl.debugging.lal.LALHolderRegistry;
import org.apache.skywalking.oap.server.admin.dsl.debugging.mal.MALDebugRecorderFactory;
import org.apache.skywalking.oap.server.admin.dsl.debugging.mal.MALHolderRegistry;
import org.apache.skywalking.oap.server.core.dsldebug.RuleKey;
import org.apache.skywalking.oap.server.admin.dsl.debugging.oal.OALDebugRecorderFactory;
import org.apache.skywalking.oap.server.admin.dsl.debugging.oal.OALHolderLookup;
import org.apache.skywalking.oap.server.admin.server.cluster.AdminClusterChannelManager;
import org.apache.skywalking.oap.server.admin.server.module.AdminServerModule;
import org.apache.skywalking.oap.server.core.analysis.DispatcherManager;
import org.apache.skywalking.oap.server.core.classloader.Catalog;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.SourceReceiverImpl;
import org.apache.skywalking.oap.server.telemetry.api.TelemetryRelatedContext;
import org.apache.skywalking.oap.server.admin.dsl.debugging.oal.RuntimeOalRestHandler;
import org.apache.skywalking.oap.server.admin.dsl.debugging.rest.DSLDebuggingRestHandler;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugSessionRegistry;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngineLoaderService;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;

/**
 * Boots the DSL debug API surface on top of the shared {@code admin-server}
 * HTTP host. Disabled by default — the provider is only loaded when an
 * operator opts in with {@code SW_DSL_DEBUGGING=default}, which in turn
 * requires {@code SW_ADMIN_SERVER=default}.
 *
 * <h2>What this provider wires</h2>
 * <ul>
 *   <li>{@link MALHolderRegistry} — exposed as a service so static rule
 *       loaders and the runtime-rule MAL apply path can register
 *       {@code (RuleKey -> MalExpression)} bindings as rules are
 *       compiled / hot-updated. Phase 1 ships the registry; the
 *       caller-side wiring lands as those paths register against it.</li>
 *   <li>{@link DebugSessionRegistry} — process-wide registry of active
 *       sessions. Holds the per-DSL holder lookup chain
 *       ({@link MALHolderRegistry} for MAL today; LAL / OAL register
 *       theirs in their own phases) and the recorder-factory chain.</li>
 *   <li>REST routes — read-only OAL listing under {@code /runtime/oal/*},
 *       session control plane under {@code /dsl-debugging/*}, both mounted
 *       on the {@code admin-server} {@link HTTPHandlerRegister}.</li>
 *   <li>Retention reaper — single-thread scheduled executor that calls
 *       {@link DebugSessionRegistry#reapExpired(long)} on a fixed delay.
 *       Idle-cost is negligible; the reaper is what guarantees a session
 *       left running by mistake doesn't pin captures past its configured
 *       {@code retentionMillis}.</li>
 * </ul>
 */
@Slf4j
public class DSLDebuggingModuleProvider extends ModuleProvider {

    private static final long REAPER_INITIAL_DELAY_SECONDS = 30L;
    private static final long REAPER_INTERVAL_SECONDS = 30L;
    /**
     * Multiplier applied to {@code admin-server.internalCommunicationTimeout} for the
     * collect broadcast. Collect responses carry the captured payload slice (potentially
     * MiB of records), so they need more headroom than install / stop. The fan-out
     * already returns partial results for missed peers, so this is a soft upper bound.
     */
    private static final double COLLECT_DEADLINE_MULTIPLIER = 1.5;

    private DSLDebuggingModuleConfig moduleConfig;
    private DebugSessionRegistry sessionRegistry;
    private MALHolderRegistry malHolderRegistry;
    private LALHolderRegistry lalHolderRegistry;
    private DSLDebuggingClusterClient clusterClient;
    private ScheduledExecutorService reaperExecutor;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return DSLDebuggingModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<DSLDebuggingModuleConfig>() {
            @Override
            public Class type() {
                return DSLDebuggingModuleConfig.class;
            }

            @Override
            public void onInitialized(final DSLDebuggingModuleConfig initialized) {
                moduleConfig = initialized;
            }
        };
    }

    @Override
    public void prepare() {
        // Flip the codegen switch FIRST. Every MAL / LAL / OAL code generator
        // checks DSLDebugCodegenSwitch.isInjectionEnabled() before emitting
        // probe call sites and the GateHolder field — flipping here, in this
        // module's prepare() phase, ensures every static-rule loader running
        // in any later provider's start() / prepare() sees injection ON.
        // When the operator left injectionEnabled=false (default), this
        // line is the ONE place that decides codegen emits no probes.
        if (moduleConfig != null && moduleConfig.isInjectionEnabled()) {
            DSLDebugCodegenSwitch.enableInjection();
        }

        // Stand up registries early so other modules can resolve them in their
        // own start() phases (the runtime-rule apply paths resolve these
        // registries and publish per-rule GateHolder bindings as they compile).
        // One registry per DSL — same shape across MAL / LAL.
        malHolderRegistry = new MALHolderRegistry();
        registerServiceImplementation(MALHolderRegistry.class, malHolderRegistry);
        lalHolderRegistry = new LALHolderRegistry();
        registerServiceImplementation(LALHolderRegistry.class, lalHolderRegistry);

        sessionRegistry = new DebugSessionRegistry();
        sessionRegistry.registerLookup(malHolderRegistry);
        sessionRegistry.registerLookup(lalHolderRegistry);
        sessionRegistry.registerRecorderFactory(new MALDebugRecorderFactory());
        sessionRegistry.registerRecorderFactory(new LALDebugRecorderFactory());

        // Install the MAL static-binding hook in prepare() (not start()) because
        // some receiver-plugin loaders construct MetricConvert during their own
        // prepare() — installing later would miss the boot-time bindings on the
        // first OAP startup. Module bootstrap order across providers isn't
        // deterministic, so the hook is set as early as possible to maximise
        // catch coverage. Bindings published before install land on the no-op
        // sink (and are picked up only by a future hot-update); install in
        // prepare() pushes the catch-all coverage to "every static binding from
        // any receiver that prepares after dsl-debugging".
        MalStaticBindingHook.install((catalog, name, metricName, holder) -> {
            final Catalog typed = catalogOf(catalog);
            if (typed == null) {
                // Unknown catalog (a receiver pushed a wire-name not in the enum) —
                // skip rather than poison the registry. The session install path
                // would 404 anyway; eating this here keeps the static-loader path
                // crash-free.
                return;
            }
            malHolderRegistry.register(new RuleKey(typed, name, metricName), holder);
        });
        LalStaticBindingHook.install((fileName, ruleName, holder) ->
            lalHolderRegistry.register(
                new RuleKey(Catalog.LAL, fileName, ruleName),
                holder));
    }

    private static Catalog catalogOf(final String wireName) {
        try {
            return Catalog.of(wireName);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void start() {
        final HTTPHandlerRegister adminRegister = getManager().find(AdminServerModule.NAME)
                                                              .provider()
                                                              .getService(HTTPHandlerRegister.class);

        final OALEngineLoaderService oalEngineLoader = getManager().find(CoreModule.NAME)
                                                                   .provider()
                                                                   .getService(OALEngineLoaderService.class);

        // OAL holder lookup is resolved here (not in prepare()) because it depends on
        // SourceReceiverImpl.getDispatcherManager() which Core publishes during its own
        // start() phase. The lookup is stateless — it walks the dispatcher set on every
        // session install, so registering it once is enough; no later wiring required.
        final SourceReceiver sourceReceiver = getManager().find(CoreModule.NAME).provider()
                                                          .getService(SourceReceiver.class);
        DispatcherManager oalDispatcherManager = null;
        if (sourceReceiver instanceof SourceReceiverImpl) {
            oalDispatcherManager = ((SourceReceiverImpl) sourceReceiver).getDispatcherManager();
            sessionRegistry.registerLookup(new OALHolderLookup(oalDispatcherManager));
            sessionRegistry.registerRecorderFactory(new OALDebugRecorderFactory());
        } else {
            log.warn("DSL debug API: SourceReceiver is not SourceReceiverImpl (got {}); "
                + "OAL debug capture disabled.", sourceReceiver.getClass().getName());
        }

        // Cluster fan-out: outbound client (broadcasts install/collect/stop/stopByClientId
        // to peers) + inbound service (handles those RPCs on this node). Carried over the
        // ADMIN-INTERNAL gRPC bus owned by admin-server (default port 17129) — NOT the
        // public agent / cluster gRPC port (default 11800). This isolation keeps
        // privileged admin RPCs out of the agent network's blast radius: a compromised
        // node on the agent side cannot reach the admin port, which operators bind to
        // a private peer-to-peer interface.
        final String selfNodeId = TelemetryRelatedContext.INSTANCE.getId();
        final AdminClusterChannelManager adminPeerChannels =
            getManager().find(AdminServerModule.NAME).provider()
                        .getService(AdminClusterChannelManager.class);
        final long perCallDeadline = adminPeerChannels.getInternalCommunicationTimeoutMs();
        final long collectDeadline = (long) (perCallDeadline * COLLECT_DEADLINE_MULTIPLIER);
        clusterClient = new DSLDebuggingClusterClient(
            new ClusterPeerCaller(adminPeerChannels, selfNodeId),
            perCallDeadline, collectDeadline);
        final GRPCHandlerRegister clusterGrpc =
            getManager().find(AdminServerModule.NAME).provider()
                        .getService(GRPCHandlerRegister.class);
        clusterGrpc.addHandler(new DSLDebuggingClusterServiceImpl(sessionRegistry, selfNodeId));

        adminRegister.addHandler(new RuntimeOalRestHandler(oalEngineLoader, oalDispatcherManager),
                                 Arrays.asList(HttpMethod.GET));
        adminRegister.addHandler(
            new DSLDebuggingRestHandler(moduleConfig, sessionRegistry, clusterClient, selfNodeId),
            Arrays.asList(HttpMethod.POST, HttpMethod.GET));

        log.info(
            "DSL debug API: read-only OAL listing + session control plane + cluster fan-out "
                + "registered on admin-server (MAL + LAL + OAL capture live; selfNodeId={}; "
                + "injectionEnabled={}).",
            selfNodeId, moduleConfig.isInjectionEnabled()
        );
    }

    @Override
    public void notifyAfterCompleted() {
        // Reaper runs on its own single-thread executor so retention sweeps can never
        // contend for the receiver hot path. Sessions whose retention deadline has
        // elapsed are uninstalled idempotently — the same code path manual stops use.
        reaperExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread t = new Thread(r, "dsl-debugging-reaper");
            t.setDaemon(true);
            return t;
        });
        reaperExecutor.scheduleWithFixedDelay(
            () -> sessionRegistry.reapExpired(System.currentTimeMillis()),
            REAPER_INITIAL_DELAY_SECONDS, REAPER_INTERVAL_SECONDS, TimeUnit.SECONDS
        );
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            // AdminServerModule — owns the shared admin HTTP host this module mounts onto.
            // Declared so OAP fails fast at boot when SW_ADMIN_SERVER is not enabled.
            AdminServerModule.NAME,
            // CoreModule — exposes OALEngineLoaderService, which the read-only OAL endpoints
            // enumerate to populate the rule picker.
            CoreModule.NAME,
        };
    }
}
