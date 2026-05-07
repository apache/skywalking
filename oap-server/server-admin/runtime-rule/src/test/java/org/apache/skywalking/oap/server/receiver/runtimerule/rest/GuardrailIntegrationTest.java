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

package org.apache.skywalking.oap.server.receiver.runtimerule.rest;

import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.ResponseHeaders;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.management.runtimerule.RuntimeRule;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.RuntimeRuleClusterClient;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngineRegistry;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.lal.LalRuleEngine;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.mal.MalRuleEngine;
import org.apache.skywalking.oap.server.receiver.runtimerule.metrics.LockMetrics;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.DSLManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.DSLRuntimeDelete;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.StructuralCommitCoordinator;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.SuspendResult;
import org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.SuspendResumeCoordinator;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.DSLRuntimeState;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level IT for the {@code allowStorageChange} guardrail + {@code force=true} bypass.
 * Complements {@link RuntimeRuleRestHandlerTest}'s path-selection coverage with scenarios
 * focused on the shape-break / rule-add gate that the design treats as the "data-loss
 * affirmation" UX surface.
 *
 * <p>No containers — the assertions are all on the classifier + guardrail path the REST
 * handler walks before any persist or apply. {@link DSLManager} stays mocked at
 * {@code applyNowForRuleFile}; {@link RuntimeRuleManagementDAO} stays mocked so the prior-row
 * lookup returns what each scenario needs.
 *
 * <p>Scenarios:
 * <ul>
 *   <li>{@link #malScopeChangeRejectedWithoutAllowStorageChange} — SERVICE→INSTANCE scope
 *       move via /addOrUpdate (no flag) is the canonical "dangerous push" the guardrail
 *       was added for. Expect 409, no persist, no apply.</li>
 *   <li>{@link #malScopeChangeAcceptedWithAllowStorageChangeTrue} — same edit via
 *       /addOrUpdate?allowStorageChange=true passes the guardrail and drives the apply
 *       pipeline. Combine with {@code force=true} for recovery pushes.</li>
 *   <li>{@link #malBodyOnlyEditNeverTripsGuardrail} — changing expression body but keeping
 *       (functionName, scopeType) identical is FILTER_ONLY per classifier, guardrail
 *       stays quiet even without the flag. Common operator workflow; must never be
 *       blocked.</li>
 *   <li>{@link #malAddedMetricNeverTripsGuardrail} — pure-additive rule-file edit (new
 *       metric added, unchanged metrics intact) is safe on BanyanDB (new measure created,
 *       existing ones untouched). Must not require the flag.</li>
 *   <li>{@link #lalOutputTypeChangeRejectedWithoutAllowStorageChange} — LAL outputType
 *       change reroutes log records to a different AbstractLog subclass; orphans the
 *       previous type's rows. Guardrail-gated; 409 without flag.</li>
 *   <li>{@link #lalRuleAddedIsRejectedWithoutAllowStorageChange} — LAL rule keys added
 *       bring inline-MAL metrics that fire DDL; gated.</li>
 *   <li>{@link #lalBodyOnlyEditAccepted} — same rule keys + same outputType + body tweaks
 *       pass without flag. CI-friendly normal-edit path.</li>
 * </ul>
 */
class GuardrailIntegrationTest {

    private ModuleManager moduleManager;
    private DSLManager dslManager;
    private RuntimeRuleClusterClient clusterClient;
    private RuntimeRuleManagementDAO dao;
    private RuntimeRuleRestHandler handler;

    @BeforeEach
    void setUp() {
        moduleManager = mock(ModuleManager.class);
        dslManager = mock(DSLManager.class);
        clusterClient = mock(RuntimeRuleClusterClient.class);
        dao = mock(RuntimeRuleManagementDAO.class);

        final ModuleProviderHolder storagePh = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder storageSh = mock(ModuleServiceHolder.class);
        when(moduleManager.find(StorageModule.NAME)).thenReturn(storagePh);
        when(storagePh.provider()).thenReturn(storageSh);
        when(storageSh.getService(RuntimeRuleManagementDAO.class)).thenReturn(dao);

        final ModuleProviderHolder corePh = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder coreSh = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(corePh);
        when(corePh.provider()).thenReturn(coreSh);

        // DSLManager per-file lock plumbing — the REST handler grabs a reentrant lock + a
        // timer before running the workflow. The lock now lives on each AppliedRuleScript;
        // a real ConcurrentHashMap stands in so AppliedRuleScript.lockFor lazy-creates an
        // entry on first acquire.
        when(dslManager.getRules()).thenReturn(new ConcurrentHashMap<>());
        final LockMetrics lockMetrics = mock(LockMetrics.class);
        when(dslManager.getLockMetrics()).thenReturn(lockMetrics);
        when(lockMetrics.acquireForRest(Mockito.any(ReentrantLock.class), Mockito.anyLong(),
            Mockito.anyString(), Mockito.anyString())).thenAnswer(inv -> {
                final ReentrantLock l = inv.getArgument(0);
                l.lock();
                return true;
            });
        when(lockMetrics.startRestHoldTimer()).thenReturn(mock(HistogramMetrics.Timer.class));

        final RuleEngineRegistry engineRegistry = new RuleEngineRegistry();
        engineRegistry.register(new MalRuleEngine(new ConcurrentHashMap<>(), moduleManager));
        engineRegistry.register(new LalRuleEngine(new ConcurrentHashMap<>(), moduleManager));
        when(dslManager.getEngineRegistry()).thenReturn(engineRegistry);

        final SuspendResumeCoordinator suspendCoord = mock(SuspendResumeCoordinator.class);
        when(dslManager.getSuspendCoord()).thenReturn(suspendCoord);
        when(suspendCoord.localSuspend(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(SuspendResult.SUSPENDED);
        when(suspendCoord.localResume(Mockito.anyString(), Mockito.anyString())).thenReturn(0);
        when(dslManager.getCommitCoord())
            .thenReturn(mock(StructuralCommitCoordinator.class));
        when(dslManager.getDslRuntimeDelete())
            .thenReturn(mock(DSLRuntimeDelete.class));
        // Stub both overloads — the REST handler calls the single-arg form on the
        // FILTER_ONLY path and the two-arg form (deferCommit=true) on STRUCTURAL.
        when(dslManager.applyNowForRuleFile(any())).thenAnswer(inv -> {
            final Object arg = inv.getArgument(0);
            if (arg instanceof RuntimeRuleManagementDAO.RuntimeRuleFile) {
                final RuntimeRuleManagementDAO.RuntimeRuleFile file =
                    (RuntimeRuleManagementDAO.RuntimeRuleFile) arg;
                return DSLRuntimeState.running(file.getCatalog(), file.getName(), "h", 0L);
            }
            return null;
        });
        when(dslManager.applyNowForRuleFile(any(), Mockito.anyBoolean())).thenAnswer(inv -> {
            final Object arg = inv.getArgument(0);
            if (arg instanceof RuntimeRuleManagementDAO.RuntimeRuleFile) {
                final RuntimeRuleManagementDAO.RuntimeRuleFile file =
                    (RuntimeRuleManagementDAO.RuntimeRuleFile) arg;
                return DSLRuntimeState.running(file.getCatalog(), file.getName(), "h", 0L);
            }
            return null;
        });

        handler = new RuntimeRuleRestHandler(moduleManager, dslManager, clusterClient, 30_000L);
    }

    // ---- MAL scenarios --------------------------------------------------------------------

    @Test
    void malScopeChangeRejectedWithoutAllowStorageChange() throws Exception {
        whenDaoHasRow(CATALOG_MAL, "vm", SERVICE_YAML, RuntimeRule.STATUS_ACTIVE);

        final HttpResponse resp = handler.addOrUpdate(CATALOG_MAL, "vm", "false", "false",
            HttpData.ofUtf8(INSTANCE_YAML));

        assertHttp(resp, HttpStatus.CONFLICT);
        // Guardrail runs BEFORE any Suspend broadcast or applyNowForRuleFile — rejection must
        // leave the downstream clean. Check both overloads (single-arg FILTER_ONLY path and
        // two-arg STRUCTURAL path).
        verify(dslManager, never()).applyNowForRuleFile(any());
        verify(dslManager, never()).applyNowForRuleFile(any(), Mockito.anyBoolean());
        verify(clusterClient, never()).broadcastSuspend(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void malScopeChangeAcceptedWithAllowStorageChangeTrue() throws Exception {
        whenDaoHasRow(CATALOG_MAL, "vm", SERVICE_YAML, RuntimeRule.STATUS_ACTIVE);

        final HttpResponse resp = handler.addOrUpdate(CATALOG_MAL, "vm", "true", "false",
            HttpData.ofUtf8(INSTANCE_YAML));

        assertHttp(resp, HttpStatus.OK);
        // STRUCTURAL path uses the two-arg overload (deferCommit=true) so row-persist
        // failure can cleanly roll back.
        verify(dslManager).applyNowForRuleFile(any(), Mockito.eq(true));
    }

    @Test
    void malScopeChangeAcceptedThroughFixRoute() throws Exception {
        // /addOrUpdate?allowStorageChange=true&force=true. Same end-state as
        // the allowStorageChange=true case, different audit-log surface.
        whenDaoHasRow(CATALOG_MAL, "vm", SERVICE_YAML, RuntimeRule.STATUS_ACTIVE);

        final HttpResponse resp = handler.addOrUpdate(CATALOG_MAL, "vm", "true", "true",
            HttpData.ofUtf8(INSTANCE_YAML));

        assertHttp(resp, HttpStatus.OK);
        verify(dslManager).applyNowForRuleFile(any(), Mockito.eq(true));
    }

    @Test
    void malBodyOnlyEditNeverTripsGuardrail() throws Exception {
        // Same (function, scope) on the single metric — classifier reports FILTER_ONLY.
        // No shape-break set, guardrail stays quiet. Must pass without the flag.
        final String bodyEdited = SERVICE_YAML.replace(
            "throughput_total.sum(['host'])",
            "throughput_total.sum(['host']).rate('PT1M')");
        whenDaoHasRow(CATALOG_MAL, "vm", SERVICE_YAML, RuntimeRule.STATUS_ACTIVE);

        final HttpResponse resp = handler.addOrUpdate(CATALOG_MAL, "vm", "false", "false",
            HttpData.ofUtf8(bodyEdited));

        assertHttp(resp, HttpStatus.OK);
        // FILTER_ONLY path uses the single-arg overload — no deferred commit needed because
        // no destructive tail exists for body-only edits.
        verify(dslManager).applyNowForRuleFile(any());
    }

    @Test
    void malAddedMetricNeverTripsGuardrail() throws Exception {
        // New metric added, existing one unchanged. Pure-additive on BanyanDB (new measure,
        // old measure untouched). Guardrail does not flag this — shapeBreakMetrics stays
        // empty.
        final String addedMetric = SERVICE_YAML
            + "  - name: latency\n"
            + "    exp: latency_seconds.sum(['host'])\n";
        whenDaoHasRow(CATALOG_MAL, "vm", SERVICE_YAML, RuntimeRule.STATUS_ACTIVE);

        final HttpResponse resp = handler.addOrUpdate(CATALOG_MAL, "vm", "false", "false",
            HttpData.ofUtf8(addedMetric));

        assertHttp(resp, HttpStatus.OK);
        // Non-empty addedMetrics makes this STRUCTURAL (NEW classification on first apply
        // or STRUCTURAL on update) — goes through the deferred-commit path.
        verify(dslManager).applyNowForRuleFile(any(), Mockito.eq(true));
    }

    // ---- LAL scenarios --------------------------------------------------------------------

    @Test
    void lalOutputTypeChangeRejectedWithoutAllowStorageChange() throws Exception {
        final String oldLal = "rules:\n"
            + "  - name: r1\n    layer: MESH\n    outputType: org.example.TypeA\n"
            + "    dsl: 'filter { sink {} }'\n";
        final String newLal = oldLal.replace("org.example.TypeA", "org.example.TypeB");
        whenDaoHasRow(CATALOG_LAL, "lal-file", oldLal, RuntimeRule.STATUS_ACTIVE);

        final HttpResponse resp = handler.addOrUpdate(CATALOG_LAL, "lal-file", "false", "false",
            HttpData.ofUtf8(newLal));

        assertHttp(resp, HttpStatus.CONFLICT);
        verify(dslManager, never()).applyNowForRuleFile(any());
        verify(dslManager, never()).applyNowForRuleFile(any(), Mockito.anyBoolean());
    }

    @Test
    void lalRuleAddedIsRejectedWithoutAllowStorageChange() throws Exception {
        final String oneRule = "rules:\n"
            + "  - name: r1\n    layer: MESH\n    dsl: 'filter { sink {} }'\n";
        final String twoRules = oneRule
            + "  - name: r2\n    layer: MESH\n    dsl: 'filter { json {} sink {} }'\n";
        whenDaoHasRow(CATALOG_LAL, "lal-file", oneRule, RuntimeRule.STATUS_ACTIVE);

        final HttpResponse resp = handler.addOrUpdate(CATALOG_LAL, "lal-file", "false", "false",
            HttpData.ofUtf8(twoRules));

        assertHttp(resp, HttpStatus.CONFLICT);
    }

    @Test
    void lalBodyOnlyEditAccepted() throws Exception {
        // Same rule keys, same outputType (absent both times = default), different DSL body.
        // Storage identity unchanged → guardrail passes.
        final String bodyA = "rules:\n"
            + "  - name: r1\n    layer: MESH\n    dsl: 'filter { sink {} }'\n";
        final String bodyB = "rules:\n"
            + "  - name: r1\n    layer: MESH\n    dsl: 'filter { json {} sink {} }'\n";
        whenDaoHasRow(CATALOG_LAL, "lal-file", bodyA, RuntimeRule.STATUS_ACTIVE);

        final HttpResponse resp = handler.addOrUpdate(CATALOG_LAL, "lal-file", "false", "false",
            HttpData.ofUtf8(bodyB));

        assertHttp(resp, HttpStatus.OK);
        // LAL always routes through the STRUCTURAL path (classifyLal reports STRUCTURAL on
        // every content change), so the two-arg overload fires.
        verify(dslManager).applyNowForRuleFile(any(), Mockito.eq(true));
    }

    @Test
    void lalRuleAddedAcceptedWithAllowStorageChangeTrue() throws Exception {
        final String oneRule = "rules:\n"
            + "  - name: r1\n    layer: MESH\n    dsl: 'filter { sink {} }'\n";
        final String twoRules = oneRule
            + "  - name: r2\n    layer: MESH\n    dsl: 'filter { json {} sink {} }'\n";
        whenDaoHasRow(CATALOG_LAL, "lal-file", oneRule, RuntimeRule.STATUS_ACTIVE);

        final HttpResponse resp = handler.addOrUpdate(CATALOG_LAL, "lal-file", "true", "false",
            HttpData.ofUtf8(twoRules));

        assertHttp(resp, HttpStatus.OK);
        verify(dslManager).applyNowForRuleFile(any(), Mockito.eq(true));
    }

    // ---- helpers --------------------------------------------------------------------------

    private void whenDaoHasRow(final String catalog, final String name,
                                final String content, final String status) throws Exception {
        final RuntimeRuleManagementDAO.RuntimeRuleFile row =
            new RuntimeRuleManagementDAO.RuntimeRuleFile(catalog, name, content, status, 0L);
        when(dao.getAll()).thenReturn(Arrays.asList(row));
    }

    private static void assertHttp(final HttpResponse resp, final HttpStatus expected) {
        final ResponseHeaders headers = resp.aggregate().toCompletableFuture().join().headers();
        assertEquals(expected.code(), headers.status().code(),
            "unexpected HTTP status (headers: " + headers + ")");
        assertTrue(headers.status().isSuccess() || headers.status().isClientError()
            || headers.status().isServerError(), "status classified as success/client/server");
    }

    private static final String CATALOG_MAL = "otel-rules";
    private static final String CATALOG_LAL = "lal";

    private static final String SERVICE_YAML =
        "metricPrefix: it_vm\n"
            + "expSuffix: service(['host'], Layer.OS_LINUX)\n"
            + "metricsRules:\n"
            + "  - name: throughput\n"
            + "    exp: throughput_total.sum(['host'])\n";

    private static final String INSTANCE_YAML =
        "metricPrefix: it_vm\n"
            + "expSuffix: instance(['host','instance'], Layer.OS_LINUX)\n"
            + "metricsRules:\n"
            + "  - name: throughput\n"
            + "    exp: throughput_total.sum(['host','instance'])\n";
}
