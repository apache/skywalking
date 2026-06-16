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

package org.apache.skywalking.oap.server.receiver.runtimerule.apply;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javassist.ClassPool;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.receiver.runtimerule.layer.LayerConflictException;
import org.apache.skywalking.oap.server.receiver.runtimerule.layer.RuntimeLayerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Covers the MAL hot-apply path with a mocked {@link MeterSystem} — real Javassist compile
 * (so per-file loader semantics are exercised end-to-end), but the terminal
 * {@code meterSystem.create} call is captured rather than registering into a live OAP
 * subsystem. These tests pin the behaviour the dslManager relies on: parse failures
 * propagate as {@link MalFileApplier.ApplyException} with the metric-name set so callers
 * can roll back, removed metrics hit {@code MeterSystem.removeMetric} for each name, and a
 * successful apply produces an {@code Applied} with the derived name set and a non-null
 * per-file loader.
 */
class MalFileApplierTest {

    private MeterSystem meterSystem;
    private MalFileApplier applier;

    @BeforeEach
    void setUp() {
        // Mock-only — we do not exercise MeterSystem's dynamic class generation. The per-file
        // RuleClassLoader test coverage lives in RuleClassLoaderTest + ClassLoaderGcTest; here
        // we only verify that MalFileApplier itself threads content through correctly and
        // raises the right exceptions on bad input.
        meterSystem = Mockito.mock(MeterSystem.class);
        applier = new MalFileApplier(meterSystem);
    }

    @Test
    void nullYamlRaisesApplyException() {
        // SnakeYAML returns null for a null input stream. The applier must detect that and
        // surface a clear message instead of an opaque NullPointerException deeper in the
        // pipeline.
        final MalFileApplier.ApplyException ex = assertThrows(
            MalFileApplier.ApplyException.class,
            () -> applier.apply(null, "dummy/vm", "hash-0"));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getPartiallyRegistered().isEmpty(),
            "no metrics registered yet on parse failure");
    }

    @Test
    void emptyYamlRaisesApplyException() {
        final MalFileApplier.ApplyException ex = assertThrows(
            MalFileApplier.ApplyException.class,
            () -> applier.apply("", "dummy/empty", "hash-0"));
        assertNotNull(ex.getMessage());
    }

    @Test
    void malformedYamlRaisesApplyExceptionWithEmptyPartial() {
        // Garbage bytes — SnakeYAML throws. The applier must wrap so the caller has a
        // consistent exception type to catch; the partial-registration list is empty because
        // we bailed before any MeterSystem.create was invoked.
        final MalFileApplier.ApplyException ex = assertThrows(
            MalFileApplier.ApplyException.class,
            () -> applier.apply("this: is: not: valid: yaml: at all", "dummy/bad", "h"));
        assertTrue(ex.getPartiallyRegistered().isEmpty());
    }

    @Test
    void successfulApplyRegistersDerivedMetricNames() throws Exception {
        // Valid minimal MAL file with two rules. The applier's metric-name enumeration should
        // join metricPrefix + "_" + rule.name — this is the same formula MetricConvert uses,
        // and callers depend on it for the STRUCTURAL diff's "removedMetrics" set.
        final String yaml =
            "metricPrefix: meter_vm\n"
                + "expSuffix: service(['host'], Layer.OS_LINUX)\n"
                + "metricsRules:\n"
                + "  - name: cpu_total_percentage\n"
                + "    exp: node_cpu_seconds_total.sum(['host']).rate('PT1M')\n"
                + "  - name: mem_total_used_percentage\n"
                + "    exp: node_memory_MemTotal_bytes.sum(['host'])\n";

        final MalFileApplier.Applied applied =
            applier.apply(yaml, "otel-rules/vm", "hashA");
        assertNotNull(applied);
        assertNotNull(applied.getRuleClassLoader(),
            "per-file loader must be retained for graveyard observation");
        assertEquals(org.apache.skywalking.oap.server.core.classloader.Catalog.OTEL_RULES,
            applied.getRuleClassLoader().getCatalog());
        assertEquals("vm", applied.getRuleClassLoader().getRule());
        assertEquals("hashA", applied.getRuleClassLoader().getContentHash());

        assertEquals(
            setOf("meter_vm_cpu_total_percentage", "meter_vm_mem_total_used_percentage"),
            applied.getRegisteredMetricNames());

        // MeterSystem.create must have been called once per metric name (6-arg pool + opt
        // overload, since we're on the hot-update path). Plain Mockito verify — not strict,
        // just a minimum count assertion.
        verify(meterSystem, times(2))
            .create(anyString(), anyString(), any(), any(ClassPool.class), any(ClassLoader.class),
                any(StorageManipulationOpt.class));
    }

    @Test
    void deferredFenceFailureRollsBackAndCarriesPartialMetricsForCallerRollback() throws Exception {
        // The DDL for every metric fires first; the batched schema fence runs once at the end. A
        // barrier transport error there must abort the apply as an ApplyException (so the REST
        // caller rolls back), carrying the metric names registered before the fence so the caller
        // knows what to unwind. Inline path (fenceRunByCaller=false) so the applier runs the fence.
        final String yaml =
            "metricPrefix: meter_vm\n"
                + "expSuffix: service(['host'], Layer.OS_LINUX)\n"
                + "metricsRules:\n"
                + "  - name: cpu_total_percentage\n"
                + "    exp: node_cpu_seconds_total.sum(['host']).rate('PT1M')\n"
                + "  - name: mem_total_used_percentage\n"
                + "    exp: node_memory_MemTotal_bytes.sum(['host'])\n";
        final StorageManipulationOpt opt = StorageManipulationOpt.withSchemaChangeDeferredFence();
        opt.setDeferredFence(() -> {
            throw new StorageException("barrier transport down");
        });

        final MalFileApplier.ApplyException ex = assertThrows(
            MalFileApplier.ApplyException.class,
            () -> applier.apply(yaml, "otel-rules/vm-fence", "hashF", opt));

        assertTrue(ex.getMessage().contains("schema fence failed"),
            "fence failure must surface as an apply error, not be swallowed");
        assertEquals(
            setOf("meter_vm_cpu_total_percentage", "meter_vm_mem_total_used_percentage"),
            ex.getPartiallyRegistered(),
            "metrics registered before the fence must be carried for the caller's rollback");
    }

    @Test
    void removeCallsMeterSystemPerName() {
        // The inverse side of the contract: on unregister every metric name the prior apply
        // recorded must flow to MeterSystem.removeMetric. The dslManager relies on this to
        // drain L1/L2 handlers + drop the BanyanDB measure. The applier's no-opt overload
        // delegates to the opt-aware removeMetric with withSchemaChange(), which is what we
        // verify here.
        final Set<String> names = setOf("meter_a", "meter_b", "meter_c");
        applier.remove(names);
        for (final String n : names) {
            verify(meterSystem).removeMetric(Mockito.eq(n), any(StorageManipulationOpt.class));
        }
    }

    @Test
    void removeWithNullSetIsNoOp() {
        applier.remove(null);
        verify(meterSystem, Mockito.never())
            .removeMetric(anyString(), any(StorageManipulationOpt.class));
    }

    @Test
    void removeWithEmptySetIsNoOp() {
        applier.remove(Collections.emptySet());
        verify(meterSystem, Mockito.never())
            .removeMetric(anyString(), any(StorageManipulationOpt.class));
    }

    @Test
    void removeContinuesAfterIndividualFailureThenThrowsSummary() {
        // Best-effort-with-surfacing: the dslManager expects "all gone" as the end state, so if
        // one metric throws we must still attempt the rest — otherwise an upstream corruption
        // would leave half-registered state that the next tick sees and gets confused by. But
        // after the loop completes, remove() throws RemoveException so the REST sync path
        // surfaces 500 teardown_deferred / commit_deferred to the operator instead of
        // misleading them with 200 inactivated / structural_applied.
        Mockito.doThrow(new RuntimeException("simulated drain failure"))
            .when(meterSystem).removeMetric(Mockito.eq("meter_b"), any(StorageManipulationOpt.class));
        final MalFileApplier.RemoveException thrown = assertThrows(
            MalFileApplier.RemoveException.class,
            () -> applier.remove(setOf("meter_a", "meter_b", "meter_c")));
        // Every sibling was still attempted — the throw happens at the end, not on first failure.
        verify(meterSystem).removeMetric(Mockito.eq("meter_a"), any(StorageManipulationOpt.class));
        verify(meterSystem).removeMetric(Mockito.eq("meter_b"), any(StorageManipulationOpt.class));
        verify(meterSystem).removeMetric(Mockito.eq("meter_c"), any(StorageManipulationOpt.class));
        // Only the failing name is in the failures map; the other two succeeded.
        assertEquals(1, thrown.getFailures().size());
        assertTrue(thrown.getFailures().containsKey("meter_b"));
    }

    @Test
    void removeReturnsNormallyWhenAllMetricsSucceed() {
        // Sanity check on the happy path — no throw when every removeMetric returns cleanly.
        // Protects against accidentally turning remove() into a "throws always" implementation
        // in the process of making it surface failures.
        applier.remove(setOf("meter_a", "meter_b"));
        verify(meterSystem).removeMetric(Mockito.eq("meter_a"), any(StorageManipulationOpt.class));
        verify(meterSystem).removeMetric(Mockito.eq("meter_b"), any(StorageManipulationOpt.class));
    }

    @Test
    void ruleNameFallsBackToSourceNameWhenMissing() throws Exception {
        // The applier tolerates YAML that doesn't declare a name at the file level — it
        // stamps sourceName in so stack traces are still identifiable. Rule.name null is
        // handled, but individual metric rules must still have names (else enumeration
        // skips them).
        final String yaml =
            "metricPrefix: meter_x\n"
                + "expSuffix: service(['host'], Layer.OS_LINUX)\n"
                + "metricsRules:\n"
                + "  - name: one\n"
                + "    exp: m.sum(['host'])\n";
        final MalFileApplier.Applied applied = applier.apply(yaml, "otel-rules/myfile", "h");
        assertEquals(setOf("meter_x_one"), applied.getRegisteredMetricNames());
    }

    @Test
    void applyWithLayerDefinitionsRegistersLayerAndCarriesAppliedClaims() throws Exception {
        // Happy path: a runtime MAL rule that declares a new layer in layerDefinitions
        // with a properly pinned ordinal in the runtime tier (>=100_000) must:
        //   (a) register the layer through the dynamic channel (Layer.isDynamic = true);
        //   (b) carry the AppliedClaims token on the returned Applied artifact so the
        //       structural commit coordinator can roll it back on persist failure;
        //   (c) populate the layer-registry refcount with this rule as the claimant.
        // Uses an isolated RuntimeLayerRegistry to avoid touching JVM-wide state.
        // `normal:` is intentionally omitted to exercise the LayerDefinition default
        // (true) — same operator ergonomics the bundled yaml + layer-extensions tier
        // already enjoy.
        final RuntimeLayerRegistry isolatedRegistry = new RuntimeLayerRegistry();
        final MalFileApplier layerApplier = new MalFileApplier(meterSystem, isolatedRegistry);
        final String yaml =
            "metricPrefix: meter_hl\n"
                + "layerDefinitions:\n"
                + "  - name: MAL_APPLIER_HAPPY_LAYER\n"
                + "    ordinal: 100600\n"
                + "expSuffix: service(['host'], Layer.OS_LINUX)\n"
                + "metricsRules:\n"
                + "  - name: ok\n"
                + "    exp: m.sum(['host'])\n";
        final MalFileApplier.Applied applied =
            layerApplier.apply(yaml, "otel-rules/happy_layer_mal", "hashHL");
        try {
            // (a) layer registered through the dynamic channel with the default normal
            assertEquals(100_600, Layer.nameOf("MAL_APPLIER_HAPPY_LAYER").value());
            assertTrue(Layer.nameOf("MAL_APPLIER_HAPPY_LAYER").isNormal(),
                       "omitted normal: defaults to true, same as the other tiers");
            assertTrue(Layer.isDynamic("MAL_APPLIER_HAPPY_LAYER"));
            // (b) AppliedClaims carried for orchestrator rollback
            assertNotNull(applied.appliedLayerClaims());
            assertTrue(applied.appliedLayerClaims().getCurrentLayerNames()
                              .contains("MAL_APPLIER_HAPPY_LAYER"));
            assertTrue(applied.appliedLayerClaims().getNewlyRegistered()
                              .contains("MAL_APPLIER_HAPPY_LAYER"));
            // (c) refcount tracks this rule as the claimant
            assertTrue(isolatedRegistry.snapshot().get("MAL_APPLIER_HAPPY_LAYER")
                                       .contains("otel-rules/happy_layer_mal"));
        } finally {
            // Clean up so JVM-wide Layer state stays sane for sibling tests.
            isolatedRegistry.removeRule("otel-rules/happy_layer_mal");
        }
    }

    @Test
    void applyWithLayerOrdinalBelowFloorThrowsLayerConflict() {
        // Sad path mirror of the happy-path test: ordinal=99_999 (the last ordinal of
        // the boot-time external tier — one slot below the 100_000 runtime floor) must
        // be rejected with layer_ordinal_out_of_range. The boundary value is the most
        // realistic operator mistake: the operator remembers "external layers go in
        // 10_000-99_999" and reuses that convention for a runtime rule. The applier
        // wraps the LayerConflictException through; the REST handler maps it to HTTP
        // 400 with the structured envelope. This proves the validation gate fires
        // BEFORE compile so no MeterSystem.create runs for a rule we're going to
        // reject.
        final RuntimeLayerRegistry isolatedRegistry = new RuntimeLayerRegistry();
        final MalFileApplier layerApplier = new MalFileApplier(meterSystem, isolatedRegistry);
        final String yaml =
            "metricPrefix: meter_bad_floor\n"
                + "layerDefinitions:\n"
                + "  - name: MAL_APPLIER_BAD_FLOOR\n"
                + "    ordinal: 99999\n"
                + "expSuffix: service(['host'], Layer.OS_LINUX)\n"
                + "metricsRules:\n"
                + "  - name: ok\n"
                + "    exp: m.sum(['host'])\n";
        final LayerConflictException ex = assertThrows(
            LayerConflictException.class,
            () -> layerApplier.apply(yaml, "otel-rules/bad_floor", "h"));
        assertEquals(LayerConflictException.Status.LAYER_ORDINAL_OUT_OF_RANGE, ex.getStatus());
        // MeterSystem.create was never invoked because validation fired first.
        verify(meterSystem, Mockito.never())
            .create(anyString(), anyString(), any(), any(ClassPool.class), any(ClassLoader.class),
                    any(StorageManipulationOpt.class));
    }

    @Test
    void legacyTwoArgApplyUsesEmptyHash() throws Exception {
        // Back-compat overload — loader identity still constructible, just with a less
        // traceable hash. DSLManager callers all use the 3-arg form; the 2-arg form exists
        // for tests and manual invocation.
        final String yaml =
            "metricPrefix: meter_y\n"
                + "expSuffix: service(['host'], Layer.OS_LINUX)\n"
                + "metricsRules:\n"
                + "  - name: rule1\n"
                + "    exp: m.sum(['host'])\n";
        final MalFileApplier.Applied applied = applier.apply(yaml, "otel-rules/legacy");
        assertEquals("", applied.getRuleClassLoader().getContentHash());
        assertFalse(applied.getRegisteredMetricNames().isEmpty());
    }

    private static Set<String> setOf(final String... s) {
        final Set<String> r = new HashSet<>();
        Collections.addAll(r, s);
        return r;
    }
}
