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

import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.ResponseHeaders;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.management.runtimerule.RuntimeRule;
import org.apache.skywalking.oap.server.core.rule.ext.StaticRuleRegistry;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
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
import org.apache.skywalking.oap.server.receiver.runtimerule.util.ContentHash;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the REST handler's path-selection logic: no_change short-circuit status
 * awareness, {@code /addOrUpdate?force=true} forceReapply bypass, and the 409 guardrail.
 * No infra, no containers; {@link DSLManager} is mocked at the integration seam
 * (applyNowForRuleFile).
 *
 * <p>The regression targets these specific bugs fixed in 8c96440d27:
 * <ul>
 *   <li>/addOrUpdate with byte-identical content on an INACTIVE row no longer returns
 *       no_change — it runs through the apply pipeline to reactivate.</li>
 *   <li>/addOrUpdate?force=true always bypasses the no_change short-circuit, even on
 *       byte-identical content, so operator recovery re-pushes actually drive a
 *       re-apply.</li>
 *   <li>/addOrUpdate on byte-identical content on an ACTIVE row still no_change's —
 *       CI idempotency preserved for the normal push path.</li>
 * </ul>
 */
class RuntimeRuleRestHandlerTest {

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

        // Wire StorageModule → DAO resolution so currentRuleFile(...) reaches the mocked dao.
        final ModuleProviderHolder storagePh = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder storageSh = mock(ModuleServiceHolder.class);
        when(moduleManager.find(StorageModule.NAME)).thenReturn(storagePh);
        when(storagePh.provider()).thenReturn(storageSh);
        when(storageSh.getService(RuntimeRuleManagementDAO.class)).thenReturn(dao);

        // CoreModule stub — some handler paths resolve services from it; empty stub is fine
        // for the doAddOrUpdate/doInactivate/doDelete paths these tests exercise.
        final ModuleProviderHolder corePh = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder coreSh = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(corePh);
        when(corePh.provider()).thenReturn(coreSh);

        // DSLManager per-file lock plumbing — the handler grabs a reentrant lock via
        // AppliedRuleScript.lockFor(dslManager.getRules(), catalog, name) before running
        // the workflow, and times the hold through dslManager.getLockMetrics()
        // .startRestHoldTimer(). Mockito returns null for unstubbed object methods, which
        // would NPE every test; wire a real ConcurrentHashMap so AppliedRuleScript.lockFor
        // lazy-creates an entry on first acquire, plus a minimal LockMetrics mock.
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

        // Engine registry — REST handler validates incoming catalogs by asking the registry
        // whether some engine claims them. Real Mal+Lal engines are cheap, no module deps.
        final RuleEngineRegistry engineRegistry = new RuleEngineRegistry();
        engineRegistry.register(new MalRuleEngine(new ConcurrentHashMap<>(), moduleManager));
        engineRegistry.register(new LalRuleEngine(new ConcurrentHashMap<>(), moduleManager));
        when(dslManager.getEngineRegistry()).thenReturn(engineRegistry);

        // DSLManager subsystem getters — the REST handler reaches Suspend/Resume + 2-PC
        // commit + /delete-backend-drop directly via DSLManager.getXxx() now (no
        // pass-through wrappers). Wire each subsystem to a mock so every test gets
        // no-op behaviour without per-test stubbing; the apply-path tests below add
        // happy-path return values on top.
        when(dslManager.getSuspendCoord()).thenReturn(mock(SuspendResumeCoordinator.class));
        when(dslManager.getCommitCoord()).thenReturn(mock(StructuralCommitCoordinator.class));
        when(dslManager.getDslRuntimeDelete()).thenReturn(mock(DSLRuntimeDelete.class));

        // persistRuleSync now calls dao.save(rule); the mocked DAO returns void by default
        // so the persist path completes successfully in these unit tests with no extra
        // wiring. The earlier ManagementStreamProcessor reflection injection is gone.

        handler = new RuntimeRuleRestHandler(moduleManager, dslManager, clusterClient, 30_000L);
    }

    @Test
    void addOrUpdateReturnsNoChangeOnByteIdenticalActiveRow() throws Exception {
        // Regression for CI idempotency: pushing the same bytes on a currently ACTIVE row
        // must still short-circuit to 200 no_change with no side effects. The classifier,
        // guardrail, Suspend broadcast, and dslManager apply are all skipped.
        final String yaml = minimalMalYaml();
        whenDaoHasRow("otel-rules", "vm", yaml, RuntimeRule.STATUS_ACTIVE);

        final HttpResponse resp = handler.addOrUpdate("otel-rules", "vm", "false", "false",
            HttpData.ofUtf8(yaml));

        assertHttpStatus(resp, HttpStatus.OK);
        verify(dslManager, never()).applyNowForRuleFile(any());
        verify(dslManager, never()).applyNowForRuleFile(any(), Mockito.anyBoolean());
        verify(clusterClient, never()).broadcastSuspend(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void addOrUpdateBypassesNoChangeOnInactiveRow() throws Exception {
        // Reactivation path: same bytes but prior row is INACTIVE. The handler must NOT
        // short-circuit — it needs to run the full apply so handlers register and the row
        // flips back to ACTIVE in storage. Previously returned no_change and left the node
        // serving nothing.
        final String yaml = minimalMalYaml();
        whenDaoHasRow("otel-rules", "vm", yaml, RuntimeRule.STATUS_INACTIVE);
        whenReconcilerApplySucceeds("otel-rules", "vm");

        final HttpResponse resp = handler.addOrUpdate("otel-rules", "vm", "false", "false",
            HttpData.ofUtf8(yaml));

        // Reactivation pushes through the STRUCTURAL/NEW path — expect 200 with a status
        // other than no_change. We don't assert on the exact applyStatus string here (that
        // depends on classifier output); the key assertion is that the two-arg deferred-
        // commit form of applyNowForRuleFile ran (STRUCTURAL path signature).
        assertHttpStatus(resp, HttpStatus.OK);
        verify(dslManager).applyNowForRuleFile(any(), Mockito.eq(true));
    }

    @Test
    void fixBypassesNoChangeEvenOnByteIdenticalActiveRow() throws Exception {
        // Recovery path: operator re-posts known-good content through
        // /addOrUpdate?allowStorageChange=true&force=true to converge from a stuck state.
        // Previously the no_change short-circuit ate this; force=true must run the full
        // apply pipeline.
        final String yaml = minimalMalYaml();
        whenDaoHasRow("otel-rules", "vm", yaml, RuntimeRule.STATUS_ACTIVE);
        whenReconcilerApplySucceeds("otel-rules", "vm");

        final HttpResponse resp = handler.addOrUpdate("otel-rules", "vm", "true", "true",
            HttpData.ofUtf8(yaml));

        assertHttpStatus(resp, HttpStatus.OK);
        // /addOrUpdate?force=true with byte-identical content → classifier returns
        // NO_CHANGE, handler falls through to applyStructural (not applyFilterOnly since
        // NO_CHANGE != FILTER_ONLY) which uses the two-arg deferred-commit form.
        verify(dslManager).applyNowForRuleFile(any(), Mockito.eq(true));
    }

    @Test
    void addOrUpdateReturnsCompileFailedOnMalformedYaml() throws Exception {
        // compile_failed is the guaranteed pre-persist error: the classifier's AST walk
        // catches a bad expression, we return 400 without persisting or broadcasting. This
        // test pins that the response is 400 and no side effects fire.
        final String garbage = "this: is: not: valid: mal: yaml: at all";
        whenDaoHasRow("otel-rules", "vm", null, null);

        final HttpResponse resp = handler.addOrUpdate("otel-rules", "vm", "false", "false",
            HttpData.ofUtf8(garbage));

        assertHttpStatus(resp, HttpStatus.BAD_REQUEST);
        verify(dslManager, never()).applyNowForRuleFile(any());
        verify(dslManager, never()).applyNowForRuleFile(any(), Mockito.anyBoolean());
        verify(clusterClient, never()).broadcastSuspend(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void addOrUpdateEmptyBodyRejected() throws Exception {
        // Basic input validation — defense-in-depth. Also verifies the empty-body check
        // runs before the DAO lookup, so an empty body doesn't trigger DAO IO.
        final HttpResponse resp = handler.addOrUpdate("otel-rules", "vm", "false", "false",
            HttpData.empty());

        assertHttpStatus(resp, HttpStatus.BAD_REQUEST);
        Mockito.verifyNoInteractions(dao);
    }

    @Test
    void deleteRejectsActiveRuleWith409() throws Exception {
        // /delete now requires the rule to be INACTIVE. Posting /delete against an ACTIVE
        // row must return HTTP 409 requires_inactivate_first without touching the DAO's
        // delete API — operators have to /inactivate first so the destructive teardown
        // (DDL drop, handler unregister, alarm reset) runs under its own endpoint.
        final String yaml = minimalMalYaml();
        whenDaoHasRow("otel-rules", "vm", yaml, RuntimeRule.STATUS_ACTIVE);

        final HttpResponse resp = handler.delete("otel-rules", "vm", "");

        assertHttpStatus(resp, HttpStatus.CONFLICT);
        Mockito.verify(dao, never()).delete(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void deleteRemovesInactiveRow() throws Exception {
        // /delete on an INACTIVE row is the happy path — the destructive work already ran
        // at /inactivate time, so this is just a row removal under the per-file lock. The
        // DAO's delete is called; no Suspend broadcast fires (no converter work to serialize
        // against on peers).
        final String yaml = minimalMalYaml();
        whenDaoHasRow("otel-rules", "vm", yaml, RuntimeRule.STATUS_INACTIVE);

        final HttpResponse resp = handler.delete("otel-rules", "vm", "");

        assertHttpStatus(resp, HttpStatus.OK);
        Mockito.verify(dao).delete("otel-rules", "vm");
        verify(clusterClient, never()).broadcastSuspend(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void deleteIsIdempotentOnAbsentRow() throws Exception {
        // Absent row + /delete → 200 "not_found" (idempotent). The desired end state
        // (no row) is already achieved; DAO.delete is not called because there's nothing
        // to remove.
        whenDaoHasRow("otel-rules", "vm", null, null);

        final HttpResponse resp = handler.delete("otel-rules", "vm", "");

        assertHttpStatus(resp, HttpStatus.OK);
        Mockito.verify(dao, never()).delete(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void inactivateUsesLocalCacheOnlySoBackendSchemaIsPreserved() throws Exception {
        // Soft-pause contract: /inactivate must drive the local teardown via the
        // applyNowForRuleFile overload that takes a StorageManipulationOpt — and that opt
        // must be localCacheOnly(). The localCacheOnly path makes per-backend
        // whenRemoving record SKIPPED_NOT_ALLOWED instead of firing dropTable, so the
        // BanyanDB measure / JDBC table / ES index plus stored data survive the pause.
        // /delete is the only path that drops backend schema (still uses fullInstall()).
        final String yaml = minimalMalYaml();
        whenDaoHasRow("otel-rules", "vm", yaml, RuntimeRule.STATUS_ACTIVE);
        whenReconcilerApplySucceeds("otel-rules", "vm");
        // The 3-arg overload returns DSLRuntimeState too; mock the same successful state.
        final DSLRuntimeState state = DSLRuntimeState.running("otel-rules", "vm", "hash", 0L);
        when(dslManager.applyNowForRuleFile(any(), Mockito.anyBoolean(),
            any(StorageManipulationOpt.class)))
            .thenReturn(state);

        final HttpResponse resp = handler.inactivate("otel-rules", "vm");

        assertHttpStatus(resp, HttpStatus.OK);
        // Verify the soft-pause path was taken: 3-arg overload with deferCommit=false and
        // a localCacheOnly opt. The destructive 2-arg overload (which would mean
        // fullInstall and a dropTable cascade) must NOT have fired.
        verify(dslManager).applyNowForRuleFile(any(), Mockito.eq(false),
            Mockito.argThat(opt -> opt != null && opt.isLocalCacheOnly()));
        verify(dslManager, never()).applyNowForRuleFile(any());
        verify(dslManager, never()).applyNowForRuleFile(any(), Mockito.anyBoolean());
    }

    // ---- GET /runtime/rule and /runtime/rule/bundled ---------------------------------------

    @Test
    void getRuleReturnsRowYamlWhenActive() throws Exception {
        final String yaml = minimalMalYaml();
        whenDaoHasRow("otel-rules", "vm", yaml, RuntimeRule.STATUS_ACTIVE);

        final HttpResponse resp = handler.get("otel-rules", "vm", "", "", "");

        assertHttpStatus(resp, HttpStatus.OK);
        final AggregatedHttpResponse agg =
            resp.aggregate().toCompletableFuture().join();
        // Default mode = raw YAML, byte-identical to /addOrUpdate's input.
        assertEquals(yaml, agg.contentUtf8());
        // Metadata headers always present so raw and JSON modes are equally introspectable.
        assertEquals("ACTIVE", agg.headers().get("X-Sw-Status"));
        assertEquals("runtime", agg.headers().get("X-Sw-Source"));
        // ETag matches contentHash so editor reload can do If-None-Match.
        assertEquals("\"" + sha256Hex(yaml) + "\"",
            agg.headers().get(HttpHeaderNames.ETAG));
    }

    @Test
    void getRuleReturnsRowYamlWhenInactive() throws Exception {
        // Soft-pause contract: an INACTIVE row keeps its original content so the editor can
        // re-edit. Status header should reflect the actual DB state, not "ACTIVE".
        final String yaml = minimalMalYaml();
        whenDaoHasRow("otel-rules", "vm", yaml, RuntimeRule.STATUS_INACTIVE);

        final HttpResponse resp = handler.get("otel-rules", "vm", "", "", "");

        assertHttpStatus(resp, HttpStatus.OK);
        final AggregatedHttpResponse agg =
            resp.aggregate().toCompletableFuture().join();
        assertEquals(yaml, agg.contentUtf8());
        assertEquals("INACTIVE", agg.headers().get("X-Sw-Status"));
    }

    @Test
    void getRuleFallsBackToStaticWhenNoRow() throws Exception {
        // No DB row, but StaticRuleRegistry has a bundled rule. Studio's catalog browser
        // displays bundled rules with the same shape as runtime ones; the editor needs to
        // be able to fetch their content too. Source header distinguishes the two.
        clearStaticRegistry();
        final String yaml = "bundled: true\n";
        StaticRuleRegistry.active()
            .record("otel-rules", "bundled-only", yaml.getBytes(StandardCharsets.UTF_8));
        whenDaoHasRow("otel-rules", "bundled-only", null, null);
        try {
            final HttpResponse resp = handler.get("otel-rules", "bundled-only", "", "", "");

            assertHttpStatus(resp, HttpStatus.OK);
            final AggregatedHttpResponse agg =
                resp.aggregate().toCompletableFuture().join();
            assertEquals(yaml, agg.contentUtf8());
            assertEquals("BUNDLED", agg.headers().get("X-Sw-Status"));
            assertEquals("bundled", agg.headers().get("X-Sw-Source"));
        } finally {
            clearStaticRegistry();
        }
    }

    @Test
    void getRuleReturns404WhenNoRowAndNoStatic() throws Exception {
        clearStaticRegistry();
        whenDaoHasRow("otel-rules", "absent", null, null);

        final HttpResponse resp = handler.get("otel-rules", "absent", "", "", "");

        assertHttpStatus(resp, HttpStatus.NOT_FOUND);
    }

    @Test
    void getRuleReturnsJsonEnvelopeOnAcceptJson() throws Exception {
        // JSON envelope must use standard JSON-string escaping (no base64). Multi-line YAML
        // → \n in the content field; a JSON parser yields the original bytes back.
        final String yaml = "metricPrefix: vm\nmetricsRules:\n  - name: cpu\n";
        whenDaoHasRow("otel-rules", "vm", yaml, RuntimeRule.STATUS_ACTIVE);

        final HttpResponse resp = handler.get("otel-rules", "vm", "", "application/json", "");

        assertHttpStatus(resp, HttpStatus.OK);
        final AggregatedHttpResponse agg =
            resp.aggregate().toCompletableFuture().join();
        final String body = agg.contentUtf8();
        assertEquals(true, body.startsWith("{") && body.endsWith("}"),
            "expected JSON envelope, got: " + body);
        // Newline in the YAML must be JSON-escaped, NOT raw and NOT base64.
        assertEquals(true, body.contains("\\n"),
            "expected JSON-escaped newline in content field, got: " + body);
        assertEquals(true, body.contains("\"source\":\"runtime\""),
            "expected source=runtime, got: " + body);
    }

    @Test
    void getRuleReturns304OnIfNoneMatchHashMatch() throws Exception {
        final String yaml = minimalMalYaml();
        whenDaoHasRow("otel-rules", "vm", yaml, RuntimeRule.STATUS_ACTIVE);
        final String currentETag = "\"" + sha256Hex(yaml) + "\"";

        final HttpResponse resp = handler.get("otel-rules", "vm", "", "", currentETag);

        assertHttpStatus(resp, HttpStatus.NOT_MODIFIED);
        // 304 still emits metadata headers so the editor can refresh its cached state
        // without re-downloading the body.
        final AggregatedHttpResponse agg =
            resp.aggregate().toCompletableFuture().join();
        assertEquals(currentETag, agg.headers().get(HttpHeaderNames.ETAG));
    }

    @Test
    void getRuleReturns400OnInvalidCatalog() throws Exception {
        final HttpResponse resp = handler.get("not-a-catalog", "vm", "", "", "");

        assertHttpStatus(resp, HttpStatus.BAD_REQUEST);
    }

    @Test
    void listBundledReturnsAllForCatalog() throws Exception {
        clearStaticRegistry();
        final StaticRuleRegistry registry =
            StaticRuleRegistry.active();
        registry.record("otel-rules", "alpha", "alpha\n".getBytes(StandardCharsets.UTF_8));
        registry.record("otel-rules", "beta",  "beta\n".getBytes(StandardCharsets.UTF_8));
        // A different catalog's entries must be excluded from this catalog's response.
        registry.record("lal", "gamma", "gamma\n".getBytes(StandardCharsets.UTF_8));
        whenDaoHasRow("otel-rules", "absent", null, null); // empty DAO so overridden=false everywhere
        try {
            final HttpResponse resp = handler.listBundled("otel-rules", "true");

            assertHttpStatus(resp, HttpStatus.OK);
            final String body = resp.aggregate().toCompletableFuture().join().contentUtf8();
            assertEquals(true, body.contains("\"name\":\"alpha\""),
                "expected alpha in bundled list, got: " + body);
            assertEquals(true, body.contains("\"name\":\"beta\""),
                "expected beta in bundled list, got: " + body);
            assertEquals(false, body.contains("\"name\":\"gamma\""),
                "lal entry leaked into otel-rules list: " + body);
            assertEquals(true, body.contains("\"kind\":\"bundled\""));
        } finally {
            clearStaticRegistry();
        }
    }

    @Test
    void listBundledOmitsContentWhenWithContentFalse() throws Exception {
        clearStaticRegistry();
        StaticRuleRegistry.active()
            .record("otel-rules", "alpha", "alpha\n".getBytes(StandardCharsets.UTF_8));
        whenDaoHasRow("otel-rules", "absent", null, null);
        try {
            final HttpResponse resp = handler.listBundled("otel-rules", "false");

            assertHttpStatus(resp, HttpStatus.OK);
            final String body = resp.aggregate().toCompletableFuture().join().contentUtf8();
            // contentHash must always be present so Studio can decide whether to fetch content
            // lazily; content must be absent when withContent=false.
            assertEquals(true, body.contains("\"contentHash\""),
                "expected contentHash, got: " + body);
            assertEquals(false, body.contains("\"content\":\""),
                "expected no content field when withContent=false, got: " + body);
        } finally {
            clearStaticRegistry();
        }
    }

    @Test
    void listBundledMarksOverriddenWhenRuntimeRowExists() throws Exception {
        clearStaticRegistry();
        StaticRuleRegistry.active()
            .record("otel-rules", "vm", "static-vm\n".getBytes(StandardCharsets.UTF_8));
        // Operator override exists for "vm" via a runtime row.
        whenDaoHasRow("otel-rules", "vm", "override-vm\n", RuntimeRule.STATUS_ACTIVE);
        try {
            final HttpResponse resp = handler.listBundled("otel-rules", "true");

            assertHttpStatus(resp, HttpStatus.OK);
            final String body = resp.aggregate().toCompletableFuture().join().contentUtf8();
            assertEquals(true, body.contains("\"overridden\":true"),
                "expected overridden=true for the bundled rule, got: " + body);
        } finally {
            clearStaticRegistry();
        }
    }

    @Test
    void listBundledReturns400OnInvalidCatalog() throws Exception {
        final HttpResponse resp = handler.listBundled("not-a-catalog", "true");

        assertHttpStatus(resp, HttpStatus.BAD_REQUEST);
    }

    /** Reflection helper — StaticRuleRegistry.clear() is package-private. */
    private static void clearStaticRegistry() throws Exception {
        final Method m = StaticRuleRegistry.class.getDeclaredMethod("clear");
        m.setAccessible(true);
        m.invoke(StaticRuleRegistry.active());
    }

    private static String sha256Hex(final String s) {
        return ContentHash.sha256Hex(s);
    }

    // ---- helpers --------------------------------------------------------------------------

    private void whenDaoHasRow(final String catalog, final String name,
                                final String content, final String status) throws Exception {
        if (content == null) {
            when(dao.getAll()).thenReturn(Collections.emptyList());
            return;
        }
        final RuntimeRuleManagementDAO.RuntimeRuleFile row =
            new RuntimeRuleManagementDAO.RuntimeRuleFile(catalog, name, content, status, 0L);
        when(dao.getAll()).thenReturn(Arrays.asList(row));
    }

    private void whenReconcilerApplySucceeds(final String catalog, final String name) {
        final DSLRuntimeState state = DSLRuntimeState.running(catalog, name, "hash", 0L);
        // Stub both overloads — FILTER_ONLY path uses the single-arg form; STRUCTURAL /
        // NEW paths use the two-arg form (deferCommit=true).
        when(dslManager.applyNowForRuleFile(any())).thenReturn(state);
        when(dslManager.applyNowForRuleFile(any(), Mockito.anyBoolean())).thenReturn(state);
        // Apply path needs SUSPENDED on localSuspend so the workflow proceeds. The other
        // subsystem getters were stubbed in setUp() with default mocks; here we just
        // override localSuspend to return SUSPENDED instead of the default null.
        final SuspendResumeCoordinator suspendCoord = dslManager.getSuspendCoord();
        when(suspendCoord.localSuspend(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(SuspendResult.SUSPENDED);
    }

    private static void assertHttpStatus(final HttpResponse resp, final HttpStatus expected) {
        final ResponseHeaders headers = resp.aggregate().toCompletableFuture().join().headers();
        assertEquals(expected.code(), headers.status().code(),
            "unexpected HTTP status (full response: " + headers + ")");
    }

    private static String minimalMalYaml() {
        return "metricPrefix: meter_vm\n"
            + "expSuffix: service(['host'], Layer.OS_LINUX)\n"
            + "metricsRules:\n"
            + "  - name: cpu\n"
            + "    exp: cpu_seconds.sum(['host'])\n";
    }

    @SuppressWarnings("unused")
    private static List<Object> ignoreListReturn() {
        // Surfaces the unused-import check for ResponseHeaders if it ever loses usage — the
        // compile fails before the ignore would matter, so this helper just keeps the import
        // alive for future assertions on individual headers.
        return Collections.emptyList();
    }
}
