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
import java.util.List;
import javassist.ClassPool;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LALConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogFilterListener;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the LAL hot-apply path with a mocked {@link LogFilterListener.Factory}. LAL compile /
 * register has no direct BanyanDB interaction (the {@code metrics&#123;&#125;} sink defers to
 * MeterSystem at log-processing time, not at compile time), so the apply-path contract can be
 * exercised entirely at the unit-test layer — parallel to {@link MalFileApplierTest}.
 *
 * <p>The contract pinned here: YAML parse failures propagate as {@link LalFileApplier.ApplyException}
 * with an empty {@code partial} list; a compile-phase failure propagates with an empty partial
 * (Phase 1 aborts before any registry mutation); a register-phase failure propagates with the
 * rules registered up to the point of failure so the dslManager can roll them back via
 * {@link LalFileApplier#remove(LalFileApplier.Applied)}; and {@code planKeys} is a pure
 * read-only inspection with no side effects on the factory.
 *
 * <p>LAL DSL shape — for reference when reading the inline YAML fixtures below:
 * <pre>
 * rules:
 *   - name: default
 *     layer: GENERAL         # or LAYER_AUTO (=&gt; null Layer at registration time)
 *     dsl: |
 *       filter &#123;
 *         sink &#123;
 *         &#125;
 *       &#125;
 * </pre>
 * The DSL body here is never actually compiled (the factory is mocked); we only verify
 * that the applier passes the right {@link LALConfig} through to {@code factory.compile}
 * and that successful compilation results in {@code factory.addOrReplace} being called
 * exactly once per rule.
 */
class LalFileApplierTest {

    private LogFilterListener.Factory factory;
    private LalFileApplier applier;

    /**
     * Baseline single-rule LAL YAML. Layer resolves to {@link Layer#GENERAL} at registration
     * time, so after a successful apply the applier's {@code Applied.registered} contains
     * exactly one {@code RegisteredRule(GENERAL, "default")}.
     * <pre>
     * rules:
     *   - name: default
     *     layer: GENERAL
     *     dsl: |
     *       filter { sink { } }
     * </pre>
     */
    private static final String VALID_LAL_YAML =
        "rules:\n"
            + "  - name: default\n"
            + "    layer: GENERAL\n"
            + "    dsl: |\n"
            + "      filter {\n"
            + "        sink {\n"
            + "        }\n"
            + "      }\n";

    /**
     * Two-rule variant of {@link #VALID_LAL_YAML} — used to verify cross-rule bookkeeping
     * (all rules compiled before any gets registered; registration happens per rule in order).
     * <pre>
     *  rules:
     *    - name: default
     *      layer: GENERAL
     *      dsl: |
     *        filter { sink { } }
     * +  - name: second
     * +    layer: MESH
     * +    dsl: |
     * +      filter { sink { } }
     * </pre>
     */
    private static final String TWO_RULE_LAL_YAML =
        VALID_LAL_YAML
            + "  - name: second\n"
            + "    layer: MESH\n"
            + "    dsl: |\n"
            + "      filter {\n"
            + "        sink {\n"
            + "        }\n"
            + "      }\n";

    /**
     * Auto-layer variant — {@code layer: auto} is the marker used for rules that decide their
     * layer at sample-time. Registration-side behaviour: {@code RegisteredRule.layer} is null.
     * <pre>
     *  rules:
     *    - name: default
     * -    layer: GENERAL
     * +    layer: auto
     *      dsl: |
     *        filter { sink { } }
     * </pre>
     */
    private static final String AUTO_LAYER_LAL_YAML =
        "rules:\n"
            + "  - name: default\n"
            + "    layer: auto\n"
            + "    dsl: |\n"
            + "      filter {\n"
            + "        sink {\n"
            + "        }\n"
            + "      }\n";

    @BeforeEach
    void setUp() throws Exception {
        factory = Mockito.mock(LogFilterListener.Factory.class);
        // Default: factory.compile returns a CompiledLAL with the declared (layer, name) and
        // a null DSL — LalFileApplier never dereferences the DSL during apply/remove, so
        // passing null lets us avoid spinning up a real compiled expression.
        when(factory.compile(any(LALConfig.class), any(ClassPool.class), any(ClassLoader.class)))
            .thenAnswer(inv -> {
                final LALConfig c = inv.getArgument(0);
                final Layer layer = LALConfig.LAYER_AUTO.equalsIgnoreCase(c.getLayer())
                    ? null : Layer.nameOf(c.getLayer());
                return new LogFilterListener.Factory.CompiledLAL(layer, c.getName(), null);
            });
        applier = new LalFileApplier(factory);
    }

    @Test
    void nullYamlRaisesApplyExceptionWithEmptyPartial() {
        // SnakeYAML reads null → parse path explicitly guards. Applier wraps so the caller
        // catches one exception type regardless of where in the pipeline the failure landed.
        final LalFileApplier.ApplyException ex = assertThrows(
            LalFileApplier.ApplyException.class,
            () -> applier.apply(null, "lal/default", "h0"));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getPartial().isEmpty(),
            "nothing registered before parse failure — partial list must be empty");
    }

    @Test
    void emptyYamlRaisesApplyException() {
        final LalFileApplier.ApplyException ex = assertThrows(
            LalFileApplier.ApplyException.class,
            () -> applier.apply("", "lal/empty", "h0"));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getPartial().isEmpty());
    }

    @Test
    void malformedYamlRaisesApplyException() {
        // Garbage bytes — SnakeYAML throws during loadAs. Applier wraps into ApplyException
        // so callers don't need to know the snakeyaml exception hierarchy.
        final LalFileApplier.ApplyException ex = assertThrows(
            LalFileApplier.ApplyException.class,
            () -> applier.apply("this: is: not: valid: yaml: at all", "lal/bad", "h"));
        assertTrue(ex.getPartial().isEmpty());
    }

    @Test
    void yamlWithoutRulesListRaisesApplyException() {
        // LAL YAML must have a top-level "rules:" list with at least one entry; anything
        // else is treated as a parse-level failure so the caller can surface the exact file
        // name in the operator-facing error response.
        final LalFileApplier.ApplyException ex = assertThrows(
            LalFileApplier.ApplyException.class,
            () -> applier.apply("notRules: []\n", "lal/wrongShape", "h"));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getPartial().isEmpty());
    }

    @Test
    void successfulApplyCompilesAndRegistersEveryRule() throws Exception {
        final LalFileApplier.Applied applied = applier.apply(TWO_RULE_LAL_YAML, "lal/multi", "h-ok");
        assertNotNull(applied);
        assertNotNull(applied.getRuleClassLoader(),
            "per-file loader must be retained so the dslManager can retire it through the "
                + "graveyard on unregister");
        assertEquals(org.apache.skywalking.oap.server.core.classloader.Catalog.LAL,
            applied.getRuleClassLoader().getCatalog());
        assertEquals("multi", applied.getRuleClassLoader().getRule());
        assertEquals("h-ok", applied.getRuleClassLoader().getContentHash());

        // Both rules compiled; both registered. Order matters — the factory must see rules
        // in the same order they appeared in the YAML so layer-keyed replace semantics stay
        // deterministic.
        assertEquals(2, applied.getRegistered().size());
        assertEquals(Layer.GENERAL, applied.getRegistered().get(0).getLayer());
        assertEquals("default", applied.getRegistered().get(0).getRuleName());
        assertEquals(Layer.MESH, applied.getRegistered().get(1).getLayer());
        assertEquals("second", applied.getRegistered().get(1).getRuleName());

        verify(factory, Mockito.times(2))
            .compile(any(LALConfig.class), any(ClassPool.class), any(ClassLoader.class));
        verify(factory, Mockito.times(2))
            .addOrReplace(any(LogFilterListener.Factory.CompiledLAL.class));
    }

    @Test
    void autoLayerRuleRegistersWithNullLayer() throws Exception {
        // layer: auto is a marker for rules that pick their layer at sample-time. At apply
        // time the registration-side Layer is null — the factory's addOrReplace uses the
        // autoDsls map, keyed on name alone, not (layer, name).
        final LalFileApplier.Applied applied =
            applier.apply(AUTO_LAYER_LAL_YAML, "lal/auto", "h");
        assertEquals(1, applied.getRegistered().size());
        assertNull(applied.getRegistered().get(0).getLayer(),
            "auto-layer rule must have null Layer at registration");
        assertEquals("default", applied.getRegistered().get(0).getRuleName());
    }

    @Test
    void compilePhaseFailurePropagatesWithEmptyPartial() throws Exception {
        // Phase 1 — factory.compile throws for the first rule. The two-phase apply must NOT
        // have registered anything yet (addOrReplace is Phase 2), so the partial list the
        // caller gets is empty. Matches the "LAL rollback-safe apply (two-phase compile +
        // defer-old-removal)" contract.
        when(factory.compile(any(LALConfig.class), any(ClassPool.class), any(ClassLoader.class)))
            .thenThrow(new ModuleStartException("synthetic compile failure"));

        final LalFileApplier.ApplyException ex = assertThrows(
            LalFileApplier.ApplyException.class,
            () -> applier.apply(TWO_RULE_LAL_YAML, "lal/broken", "h"));
        assertTrue(ex.getPartial().isEmpty(),
            "compile-phase failure means zero registrations landed — partial MUST be empty");
        // The factory must never have been asked to addOrReplace anything since Phase 1
        // aborted.
        verify(factory, never())
            .addOrReplace(any(LogFilterListener.Factory.CompiledLAL.class));
    }

    @Test
    void registerPhaseFailurePropagatesWithProgressSoFar() throws Exception {
        // Phase 2 — compile succeeds for both, but factory.addOrReplace throws on the
        // second rule's turn. The partial list MUST include the first rule so the caller
        // can roll it back via remove(Applied). This is the rollback-safe contract.
        Mockito.doNothing()
            .doThrow(new RuntimeException("synthetic register failure"))
            .when(factory).addOrReplace(any(LogFilterListener.Factory.CompiledLAL.class));

        final LalFileApplier.ApplyException ex = assertThrows(
            LalFileApplier.ApplyException.class,
            () -> applier.apply(TWO_RULE_LAL_YAML, "lal/half", "h"));
        final List<LalFileApplier.RegisteredRule> partial = ex.getPartial();
        assertEquals(1, partial.size(),
            "one rule landed in the factory before the second threw — partial must reflect that");
        assertEquals(Layer.GENERAL, partial.get(0).getLayer());
        assertEquals("default", partial.get(0).getRuleName());
    }

    @Test
    void removeUnregistersEveryRegisteredRule() throws Exception {
        final LalFileApplier.Applied applied = applier.apply(TWO_RULE_LAL_YAML, "lal/multi", "h");
        applier.remove(applied);

        verify(factory).remove(eq(Layer.GENERAL), eq("default"));
        verify(factory).remove(eq(Layer.MESH), eq("second"));
    }

    @Test
    void removeWithNullAppliedIsNoOp() {
        applier.remove(null);
        verify(factory, never()).remove(any(), Mockito.anyString());
    }

    @Test
    void removeWithEmptyRegisteredIsNoOp() {
        // Empty Applied (e.g. the empty-partial result from a compile-phase failure caller
        // tries to roll back) is a no-op rather than a null-dereference.
        final LalFileApplier.Applied empty = new LalFileApplier.Applied(
            "lal/nothing", Collections.emptyList(), null);
        applier.remove(empty);
        verify(factory, never()).remove(any(), Mockito.anyString());
    }

    @Test
    void removeContinuesAfterIndividualFailure() throws Exception {
        // Best-effort removal — one rule throwing must not prevent the others from being
        // unregistered. The factory.remove calls are wrapped in try/catch with log.warn in
        // LalFileApplier; we verify that all three rules still hit factory.remove.
        final String threeRuleYaml =
            TWO_RULE_LAL_YAML
                + "  - name: third\n"
                + "    layer: K8S_SERVICE\n"
                + "    dsl: |\n"
                + "      filter {\n"
                + "        sink {\n"
                + "        }\n"
                + "      }\n";
        final LalFileApplier.Applied applied = applier.apply(threeRuleYaml, "lal/three", "h");
        Mockito.doThrow(new RuntimeException("simulated"))
            .when(factory).remove(eq(Layer.MESH), eq("second"));

        applier.remove(applied);

        verify(factory).remove(eq(Layer.GENERAL), eq("default"));
        verify(factory).remove(eq(Layer.MESH), eq("second"));
        verify(factory).remove(eq(Layer.K8S_SERVICE), eq("third"));
    }

    @Test
    void planKeysReturnsLayerAndNameWithoutRegistering() throws Exception {
        // planKeys is a read-only inspection so the dslManager can detect cross-file
        // collisions before any compile work. factory must see ZERO calls.
        final List<LalFileApplier.RegisteredRule> keys = applier.planKeys(TWO_RULE_LAL_YAML, "lal/plan");
        assertEquals(2, keys.size());
        assertEquals(Layer.GENERAL, keys.get(0).getLayer());
        assertEquals("default", keys.get(0).getRuleName());
        assertEquals(Layer.MESH, keys.get(1).getLayer());
        assertEquals("second", keys.get(1).getRuleName());

        verify(factory, never())
            .compile(any(LALConfig.class), any(ClassPool.class), any(ClassLoader.class));
        verify(factory, never())
            .addOrReplace(any(LogFilterListener.Factory.CompiledLAL.class));
    }

    @Test
    void planKeysAutoLayerYieldsNullLayer() throws Exception {
        final List<LalFileApplier.RegisteredRule> keys =
            applier.planKeys(AUTO_LAYER_LAL_YAML, "lal/auto");
        assertEquals(1, keys.size());
        assertNull(keys.get(0).getLayer());
        assertEquals("default", keys.get(0).getRuleName());
    }

    @Test
    void planKeysRaisesOnYamlParseError() {
        final LalFileApplier.ApplyException ex = assertThrows(
            LalFileApplier.ApplyException.class,
            () -> applier.planKeys("this: is: not: valid: yaml", "lal/bad"));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getPartial().isEmpty());
    }

    @Test
    void legacyTwoArgApplyUsesEmptyHash() throws Exception {
        // Back-compat 2-arg entry point — loader identity is still constructed but the hash
        // is empty. Production callers always use the 3-arg form; 2-arg remains for tests
        // and manual invocation.
        final LalFileApplier.Applied applied = applier.apply(VALID_LAL_YAML, "lal/legacy");
        assertNotNull(applied);
        // The legacy overload still constructs a per-file loader internally; its contentHash
        // is empty string.
        assertNotNull(applied.getRuleClassLoader());
        assertEquals("", applied.getRuleClassLoader().getContentHash());
        assertFalse(applied.getRegistered().isEmpty());
    }
}
