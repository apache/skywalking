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

package org.apache.skywalking.oap.server.receiver.runtimerule.layer;

import java.util.Arrays;
import java.util.Collections;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link RuntimeLayerRegistry}'s refcount semantics: net-new register/unregister
 * on first/last claim, idempotent re-claim by the same rule, multi-rule sharing, and the
 * apply/rollback round-trip used by the structural commit coordinator.
 *
 * <p>Uses ordinals in {@code [200_000, 200_999]} so collisions with sibling tests and any
 * boot-time registrations cannot occur. Cleanup in {@link #cleanup} unregisters every
 * dynamic layer it touched so JVM-shared state stays clean for other test classes.
 */
class RuntimeLayerRegistryTest {

    private static final String RULE_A = "otel-rules/test-a";
    private static final String RULE_B = "otel-rules/test-b";

    private final RuntimeLayerRegistry registry = new RuntimeLayerRegistry();

    @AfterEach
    void cleanup() {
        // Drop every claim, which cascades into Layer.unregisterDynamic for any net-zero
        // layer. Defensive: walk a snapshot so we don't mutate the map mid-iteration.
        for (final String rule : Arrays.asList(RULE_A, RULE_B, "otel-rules/test-c")) {
            registry.removeRule(rule);
        }
    }

    @Test
    void applyRegistersNewLayerAndCountsClaim() {
        final LayerClaim claim = new LayerClaim("DYN_REGISTRY_A", 200_000, true);
        final AppliedClaims applied = registry.apply(RULE_A, Collections.singletonList(claim));

        assertTrue(applied.getNewlyRegistered().contains("DYN_REGISTRY_A"));
        assertEquals(Collections.singleton("DYN_REGISTRY_A"), registry.claimsOf(RULE_A));
        assertEquals(200_000, Layer.nameOf("DYN_REGISTRY_A").value());
    }

    @Test
    void secondClaimOnSameLayerIsIdempotent() {
        final LayerClaim claim = new LayerClaim("DYN_REGISTRY_SHARED", 200_001, true);
        final AppliedClaims firstApplied = registry.apply(RULE_A, Collections.singletonList(claim));
        final AppliedClaims secondApplied = registry.apply(RULE_B, Collections.singletonList(claim));

        // First apply registers the layer; second only adds B as a claimant.
        assertTrue(firstApplied.getNewlyRegistered().contains("DYN_REGISTRY_SHARED"));
        assertTrue(secondApplied.getNewlyRegistered().isEmpty());

        // Both A and B are claimants.
        assertEquals(2, registry.snapshot().get("DYN_REGISTRY_SHARED").size());
    }

    @Test
    void unregisterDropsLayerWhenLastClaimGone() {
        final LayerClaim claim = new LayerClaim("DYN_REGISTRY_DROP", 200_002, true);
        registry.apply(RULE_A, Collections.singletonList(claim));
        registry.apply(RULE_B, Collections.singletonList(claim));

        // Removing A keeps the layer alive (B still claims it).
        registry.removeRule(RULE_A);
        assertEquals(200_002, Layer.nameOf("DYN_REGISTRY_DROP").value());

        // Removing B drops the last claim — layer goes away.
        registry.removeRule(RULE_B);
        assertSame(Layer.UNDEFINED, Layer.nameOf("DYN_REGISTRY_DROP"));
    }

    @Test
    void updateReplacesPriorClaimsAndUnregistersOrphans() {
        final LayerClaim original = new LayerClaim("DYN_REGISTRY_ORPHAN", 200_003, true);
        final LayerClaim replacement = new LayerClaim("DYN_REGISTRY_KEEP", 200_004, true);

        registry.apply(RULE_A, Collections.singletonList(original));
        assertEquals(200_003, Layer.nameOf("DYN_REGISTRY_ORPHAN").value());

        registry.apply(RULE_A, Collections.singletonList(replacement));

        // The orphaned layer is gone (no other rule claims it).
        assertSame(Layer.UNDEFINED, Layer.nameOf("DYN_REGISTRY_ORPHAN"));
        // The replacement is now registered and claimed by RULE_A.
        assertEquals(200_004, Layer.nameOf("DYN_REGISTRY_KEEP").value());
        assertEquals(Collections.singleton("DYN_REGISTRY_KEEP"), registry.claimsOf(RULE_A));
    }

    @Test
    void rollbackRestoresPriorStateAfterAppliedUpdate() {
        final LayerClaim original = new LayerClaim("DYN_REGISTRY_ROLLBACK_PRIOR", 200_005, true);
        final LayerClaim replacement = new LayerClaim("DYN_REGISTRY_ROLLBACK_NEW", 200_006, true);

        registry.apply(RULE_A, Collections.singletonList(original));
        final AppliedClaims applied = registry.apply(RULE_A, Collections.singletonList(replacement));

        // Confirm intermediate state.
        assertSame(Layer.UNDEFINED, Layer.nameOf("DYN_REGISTRY_ROLLBACK_PRIOR"));
        assertEquals(200_006, Layer.nameOf("DYN_REGISTRY_ROLLBACK_NEW").value());

        registry.rollback(applied);

        // Prior layer restored; replacement unregistered.
        assertEquals(200_005, Layer.nameOf("DYN_REGISTRY_ROLLBACK_PRIOR").value());
        assertSame(Layer.UNDEFINED, Layer.nameOf("DYN_REGISTRY_ROLLBACK_NEW"));
        assertEquals(Collections.singleton("DYN_REGISTRY_ROLLBACK_PRIOR"),
                     registry.claimsOf(RULE_A));
    }

    @Test
    void selfUpdateWithDifferentOrdinalIsPermitted() {
        // Rule edits its OWN previously-declared layer — conflict checker treats the
        // prior claim as already-removed, so a triple change is allowed.
        final LayerClaim before = new LayerClaim("DYN_REGISTRY_SELF_EDIT", 200_007, true);
        final LayerClaim after = new LayerClaim("DYN_REGISTRY_SELF_EDIT", 200_008, false);

        registry.apply(RULE_A, Collections.singletonList(before));
        registry.apply(RULE_A, Collections.singletonList(after));

        final Layer current = Layer.nameOf("DYN_REGISTRY_SELF_EDIT");
        assertEquals(200_008, current.value());
        assertEquals(false, current.isNormal());
    }

    @Test
    void conflictAcrossRulesRaisesLayerNameConflict() {
        registry.apply(RULE_A, Collections.singletonList(
            new LayerClaim("DYN_REGISTRY_CROSS_RULE", 200_009, true)));

        // Rule B declares same name but different ordinal — must be rejected.
        final LayerConflictException ex = assertThrows(
            LayerConflictException.class,
            () -> registry.apply(RULE_B, Collections.singletonList(
                new LayerClaim("DYN_REGISTRY_CROSS_RULE", 200_010, true))));

        assertEquals(LayerConflictException.Status.LAYER_NAME_CONFLICT, ex.getStatus());
        assertEquals("layer_name_conflict", ex.applyStatus());
        assertTrue(ex.getMessage().contains("DYN_REGISTRY_CROSS_RULE"));
        assertTrue(ex.getMessage().contains(RULE_A), "message must name the source rule");
        assertTrue(ex.getMessage().contains(RULE_B), "message must name the offender");
    }

    @Test
    void conflictRaisesOrdinalCollisionForDifferentNameSameOrdinal() {
        registry.apply(RULE_A, Collections.singletonList(
            new LayerClaim("DYN_REGISTRY_ORD_A", 200_011, true)));

        final LayerConflictException ex = assertThrows(
            LayerConflictException.class,
            () -> registry.apply(RULE_B, Collections.singletonList(
                new LayerClaim("DYN_REGISTRY_ORD_B", 200_011, true))));

        assertEquals(LayerConflictException.Status.LAYER_ORDINAL_COLLISION, ex.getStatus());
        assertEquals("layer_ordinal_collision", ex.applyStatus());
        assertTrue(ex.getMessage().contains("200011") || ex.getMessage().contains("200_011"));
    }

    @Test
    void conflictRaisesOrdinalOutOfRangeBelowFloor() {
        final LayerConflictException ex = assertThrows(
            LayerConflictException.class,
            () -> registry.apply(RULE_A, Collections.singletonList(
                new LayerClaim("DYN_REGISTRY_LOW", 99_999, true))));

        assertEquals(LayerConflictException.Status.LAYER_ORDINAL_OUT_OF_RANGE, ex.getStatus());
        assertTrue(ex.getMessage().contains("99999"));
        assertTrue(ex.getMessage().contains("100000"));
    }

    @Test
    void conflictRaisesNameInvalidForBadShape() {
        final LayerConflictException ex = assertThrows(
            LayerConflictException.class,
            () -> registry.apply(RULE_A, Collections.singletonList(
                new LayerClaim("bad-name", 200_012, true))));

        assertEquals(LayerConflictException.Status.LAYER_NAME_INVALID, ex.getStatus());
        assertTrue(ex.getMessage().contains("bad-name"));
    }

    @Test
    void conflictAgainstBundledLayerLabelsSourceClearly() {
        // MESH is the built-in layer with ordinal 1 — declaring it with a runtime
        // ordinal yields a name conflict; the source must be labelled "built-in or
        // boot-time extension" so the operator knows it can't be aligned at all.
        final LayerConflictException ex = assertThrows(
            LayerConflictException.class,
            () -> registry.apply(RULE_A, Collections.singletonList(
                new LayerClaim("MESH", 200_013, true))));

        assertEquals(LayerConflictException.Status.LAYER_NAME_CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("MESH"));
        assertTrue(ex.getMessage().contains("built-in"),
                   "message must label the source as built-in: " + ex.getMessage());
    }

    @Test
    void inBatchDuplicateNameWithDifferentTriplesIsRejected() {
        // A single batch declaring the same name twice with different triples can never
        // be satisfied — must be caught BEFORE any mutation lands so apply stays atomic.
        final LayerConflictException ex = assertThrows(
            LayerConflictException.class,
            () -> registry.apply(RULE_A, Arrays.asList(
                new LayerClaim("DYN_BATCH_DUP_NAME", 200_020, true),
                new LayerClaim("DYN_BATCH_DUP_NAME", 200_021, true))));
        assertEquals(LayerConflictException.Status.LAYER_NAME_CONFLICT, ex.getStatus());
        // Neither variant should have been registered — the registry must look unchanged.
        assertSame(Layer.UNDEFINED,
                   Layer.nameOf("DYN_BATCH_DUP_NAME"));
        assertTrue(registry.claimsOf(RULE_A).isEmpty());
    }

    @Test
    void inBatchDuplicateOrdinalUnderDifferentNamesIsRejected() {
        final LayerConflictException ex = assertThrows(
            LayerConflictException.class,
            () -> registry.apply(RULE_A, Arrays.asList(
                new LayerClaim("DYN_BATCH_ORD_X", 200_022, true),
                new LayerClaim("DYN_BATCH_ORD_Y", 200_022, true))));
        assertEquals(LayerConflictException.Status.LAYER_ORDINAL_COLLISION, ex.getStatus());
        // Neither name registered.
        assertSame(Layer.UNDEFINED,
                   Layer.nameOf("DYN_BATCH_ORD_X"));
        assertSame(Layer.UNDEFINED,
                   Layer.nameOf("DYN_BATCH_ORD_Y"));
    }

    @Test
    void inBatchSameTripleRepeatedIsAccepted() {
        // Idempotent duplicate within a batch (operator copy-paste, or a generator that
        // emits the same entry twice) is harmless and must not be rejected.
        registry.apply(RULE_A, Arrays.asList(
            new LayerClaim("DYN_BATCH_IDEMPOTENT", 200_023, true),
            new LayerClaim("DYN_BATCH_IDEMPOTENT", 200_023, true)));
        assertEquals(200_023,
                     Layer
                         .nameOf("DYN_BATCH_IDEMPOTENT").value());
    }

    @Test
    void selfUpdateSwappingOrdinalsAcrossTwoLayersIsAtomic() {
        // Rule R declares LAYER_X@200_030 and LAYER_Y@200_031. Operator pushes a swap:
        // LAYER_X@200_031, LAYER_Y@200_030. validate must permit it (each is a self-claim
        // freeing the other's ordinal), apply must execute the swap without intermediate
        // ordinal collision throwing — that's the bug finding 2 surfaced for the per-claim
        // loop. The two-pass apply unregisters all prior claims first, then registers all
        // new triples, so the freed slots are available for the swap.
        registry.apply(RULE_A, Arrays.asList(
            new LayerClaim("DYN_SWAP_X", 200_030, true),
            new LayerClaim("DYN_SWAP_Y", 200_031, true)));
        registry.apply(RULE_A, Arrays.asList(
            new LayerClaim("DYN_SWAP_X", 200_031, true),
            new LayerClaim("DYN_SWAP_Y", 200_030, true)));

        assertEquals(200_031,
                     Layer
                         .nameOf("DYN_SWAP_X").value());
        assertEquals(200_030,
                     Layer
                         .nameOf("DYN_SWAP_Y").value());
    }

    @Test
    void softClaimAgainstBundledLayerSucceedsButCannotRemove() {
        // Pre-register a layer through the bundled channel (Layer.register) at an
        // ordinal in the runtime range. This simulates a boot-time external entry
        // (layer-extensions.yml / bundled MAL/LAL) that — against the recommended
        // tier convention — happens to live at >=100_000. A runtime rule claiming the
        // SAME triple gets a soft claim: refcount tracks the rule, Layer stays bundled,
        // unregisterDynamic refuses on rule delete.
        Layer.register("DYN_REG_SOFT_CLAIM_TARGET", 250_010, true);
        try {
            registry.apply(RULE_A, Collections.singletonList(
                new LayerClaim("DYN_REG_SOFT_CLAIM_TARGET", 250_010, true)));

            assertTrue(registry.snapshot().get("DYN_REG_SOFT_CLAIM_TARGET").contains(RULE_A));
            assertFalse(Layer.isDynamic("DYN_REG_SOFT_CLAIM_TARGET"),
                        "soft claim does not flip the channel from bundled to dynamic");
            assertEquals(250_010, Layer.nameOf("DYN_REG_SOFT_CLAIM_TARGET").value());

            registry.removeRule(RULE_A);

            assertEquals(250_010, Layer.nameOf("DYN_REG_SOFT_CLAIM_TARGET").value(),
                         "removing the soft-claimant rule must NOT unregister the bundled layer");
            assertFalse(registry.snapshot().containsKey("DYN_REG_SOFT_CLAIM_TARGET"));
        } finally {
            // Cannot unregister via the dynamic path (it's bundled). Layer stays in the
            // JVM-wide registry but the unique name prevents sibling-test interference.
        }
    }

    @Test
    void selfUpdateReusingMultiClaimantOrdinalIsRejectedStructured() {
        // Rules A + B both claim OLD@200_090. A then tries to update to NEW@200_090 —
        // reusing the ordinal of its own prior layer under a new name. Without the
        // multi-claimant check, validate would pass (A held OLD priorly), apply's Pass-1
        // would only drop A's claim from OLD (B still holds it), and registerDynamic
        // would throw an unstructured ordinal collision deep in apply. The structured
        // layer_ordinal_collision must surface from validate before any mutation lands.
        final LayerClaim shared = new LayerClaim("DYN_MULTI_CLAIM_OLD", 200_090, true);
        registry.apply(RULE_A, Collections.singletonList(shared));
        registry.apply(RULE_B, Collections.singletonList(shared));

        final LayerConflictException ex = assertThrows(
            LayerConflictException.class,
            () -> registry.apply(RULE_A, Collections.singletonList(
                new LayerClaim("DYN_MULTI_CLAIM_NEW", 200_090, true))));
        assertEquals(LayerConflictException.Status.LAYER_ORDINAL_COLLISION, ex.getStatus());
        assertTrue(ex.getMessage().contains(RULE_B),
                   "message must name the rule still holding the prior layer");
        // State unchanged: OLD still owned by A + B, no NEW.
        assertSame(Layer.UNDEFINED, Layer.nameOf("DYN_MULTI_CLAIM_NEW"));
        assertEquals(200_090, Layer.nameOf("DYN_MULTI_CLAIM_OLD").value());
    }

    @Test
    void reactivationAfterInactiveSurfacesConflictAgainstNewClaimant() {
        // Rule A claims a dynamic layer. A is then "inactivated" (its claims removed
        // from the registry). While A is inactive, rule B starts claiming the same
        // name at a different ordinal. When the operator reactivates A, the apply
        // must surface the conflict so the operator can edit or drop A.
        registry.apply(RULE_A, Collections.singletonList(
            new LayerClaim("DYN_REACTIVATE_PROBE", 200_080, true)));
        // Inactivate A.
        registry.removeRule(RULE_A);
        assertSame(Layer.UNDEFINED, Layer.nameOf("DYN_REACTIVATE_PROBE"));

        // Meanwhile, B claims the same name at a different ordinal.
        registry.apply(RULE_B, Collections.singletonList(
            new LayerClaim("DYN_REACTIVATE_PROBE", 200_081, true)));

        // Operator pushes A again (reactivation). The original triple no longer fits.
        final LayerConflictException ex = assertThrows(
            LayerConflictException.class,
            () -> registry.apply(RULE_A, Collections.singletonList(
                new LayerClaim("DYN_REACTIVATE_PROBE", 200_080, true))));
        assertEquals(LayerConflictException.Status.LAYER_NAME_CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains(RULE_B),
                   "message must name the currently-active claimant so operator knows what to align with");
    }

    @Test
    void noOpRedeclareDoesNotPerturbLiveLayer() {
        // Re-applying the SAME triple by the SAME rule should be a no-op at the Layer
        // level (no unregister + re-register churn that briefly leaves the layer
        // unresolvable). Confirmed by re-checking the identity of the Layer instance
        // before and after the second apply.
        registry.apply(RULE_A, Collections.singletonList(
            new LayerClaim("DYN_STABLE_REAPPLY", 200_040, true)));
        final Layer before =
            Layer.nameOf("DYN_STABLE_REAPPLY");
        registry.apply(RULE_A, Collections.singletonList(
            new LayerClaim("DYN_STABLE_REAPPLY", 200_040, true)));
        final Layer after =
            Layer.nameOf("DYN_STABLE_REAPPLY");
        assertSame(before, after, "same-triple re-apply must not churn the Layer instance");
    }
}
