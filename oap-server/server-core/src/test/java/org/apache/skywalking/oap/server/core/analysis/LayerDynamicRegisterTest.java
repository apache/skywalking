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

package org.apache.skywalking.oap.server.core.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates {@link Layer#registerDynamic(String, int, boolean)} and
 * {@link Layer#unregisterDynamic(String)}: the post-seal runtime-DSL registration channel
 * introduced for hot-update of MAL/LAL rules with new {@code layerDefinitions:} entries.
 *
 * <p>Tests assert state via {@link Layer#nameOf(String)} / {@link Layer#valueOf(int)} so the
 * registry need not be sealed — sealing would taint sibling tests that share the JVM (the
 * registry is a process-wide singleton). Visibility through {@link Layer#values()} is
 * covered in the runtime-dynamic-layers L2 integration test, which runs in its own forked
 * OAP process.
 *
 * <p>Ordinals used here live in the runtime tier ({@code >= 100_000}) so collisions with
 * built-in constants, sibling tests' yaml fixtures (ordinals 1150-1199), and the
 * conventional boot-time external range (10_000-99_999) are impossible by construction.
 */
class LayerDynamicRegisterTest {

    @Test
    void registerDynamicRejectsOrdinalBelowFloor() {
        // 99_999 is one below the runtime floor — must be rejected with the operator-actionable
        // message naming the actual ordinal and reserved ranges.
        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> Layer.registerDynamic("DYN_FLOOR_REJECT", 99_999, true));
        // Message must surface the ordinal so the operator can correlate with their yaml.
        assertTrue(ex.getMessage().contains("99999") || ex.getMessage().contains("99_999"),
                   "message must quote the offending ordinal: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("100000") || ex.getMessage().contains("100_000"),
                   "message must quote the floor: " + ex.getMessage());
    }

    @Test
    void registerDynamicAtFloorSucceeds() {
        final Layer layer = Layer.registerDynamic("DYN_AT_FLOOR", 100_000, true);
        assertEquals("DYN_AT_FLOOR", layer.name());
        assertEquals(100_000, layer.value());
        assertEquals(true, layer.isNormal());
        // Round-trip lookup.
        assertSame(layer, Layer.nameOf("DYN_AT_FLOOR"));
        assertSame(layer, Layer.valueOf(100_000));
    }

    @Test
    void registerDynamicIsIdempotentOnIdenticalTriple() {
        final Layer first = Layer.registerDynamic("DYN_IDEMPOTENT", 100_001, true);
        final Layer second = Layer.registerDynamic("DYN_IDEMPOTENT", 100_001, true);
        assertSame(first, second);
    }

    @Test
    void registerDynamicRejectsNameConflict() {
        Layer.registerDynamic("DYN_NAME_CONFLICT", 100_002, true);
        // Same name, different ordinal.
        assertThrows(IllegalStateException.class,
                     () -> Layer.registerDynamic("DYN_NAME_CONFLICT", 100_003, true));
        // Same name, different normal flag.
        assertThrows(IllegalStateException.class,
                     () -> Layer.registerDynamic("DYN_NAME_CONFLICT", 100_002, false));
    }

    @Test
    void registerDynamicRejectsOrdinalCollisionWithDifferentName() {
        Layer.registerDynamic("DYN_ORDINAL_FIRST", 100_004, true);
        assertThrows(IllegalStateException.class,
                     () -> Layer.registerDynamic("DYN_ORDINAL_SECOND", 100_004, true));
    }

    @Test
    void registerDynamicRejectsBadNameShape() {
        assertThrows(IllegalArgumentException.class,
                     () -> Layer.registerDynamic("badName", 100_005, true));
        assertThrows(IllegalArgumentException.class,
                     () -> Layer.registerDynamic("9_LAYER", 100_006, true));
        assertThrows(IllegalArgumentException.class,
                     () -> Layer.registerDynamic("BAD-NAME", 100_007, true));
        assertThrows(IllegalArgumentException.class,
                     () -> Layer.registerDynamic(null, 100_008, true));
    }

    @Test
    void unregisterDynamicRemovesRegisteredLayer() {
        Layer.registerDynamic("DYN_REMOVE_ME", 100_009, true);
        assertNotSame(Layer.UNDEFINED, Layer.nameOf("DYN_REMOVE_ME"));

        Layer.unregisterDynamic("DYN_REMOVE_ME");

        // After removal the lookup falls back to UNDEFINED via nameOf, and valueOf(int) throws.
        assertSame(Layer.UNDEFINED, Layer.nameOf("DYN_REMOVE_ME"));
        assertThrows(org.apache.skywalking.oap.server.core.UnexpectedException.class,
                     () -> Layer.valueOf(100_009));
    }

    @Test
    void unregisterDynamicIsNoOpOnUnknownName() {
        // No throw, no side effects observable through nameOf.
        Layer.unregisterDynamic("DYN_NEVER_REGISTERED");
        assertSame(Layer.UNDEFINED, Layer.nameOf("DYN_NEVER_REGISTERED"));
        // Null is also tolerated.
        Layer.unregisterDynamic(null);
    }

    @Test
    void unregisterDynamicRefusesToRemoveBundledLayer() {
        // MESH is a built-in registered through Layer.register — must NOT be removable
        // through the runtime path, because its ordinal is baked into persisted
        // ServiceTraffic primary keys. Ownership is tracked explicitly through
        // DYNAMIC_NAMES, not inferred from the ordinal range.
        final IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> Layer.unregisterDynamic("MESH"));
        assertTrue(ex.getMessage().contains("MESH"), ex.getMessage());
        assertTrue(ex.getMessage().contains("non-dynamic"), ex.getMessage());
        // The built-in is still in the registry after the failed removal attempt.
        assertSame(Layer.MESH, Layer.nameOf("MESH"));
    }

    @Test
    void isDynamicReflectsRegistrationChannel() {
        // Layer registered through registerDynamic is marked dynamic.
        Layer.registerDynamic("DYN_ISDYN_PROBE", 100_020, true);
        assertTrue(Layer.isDynamic("DYN_ISDYN_PROBE"),
                   "registerDynamic-added layer must report isDynamic=true");
        // Built-in layer is not dynamic.
        assertFalse(Layer.isDynamic("MESH"),
                    "built-in MESH must report isDynamic=false");
        // Unknown name is not dynamic.
        assertFalse(Layer.isDynamic("__NO_SUCH_LAYER__"));
        assertFalse(Layer.isDynamic(null));
        // After unregister the marker is cleared.
        Layer.unregisterDynamic("DYN_ISDYN_PROBE");
        assertFalse(Layer.isDynamic("DYN_ISDYN_PROBE"),
                    "unregisterDynamic must clear the dynamic marker");
    }

    @Test
    void unregisterDynamicRefusesToRemoveAnOperatorLayerAtRuntimeRange() {
        // A layer registered through Layer.register (the boot-time channel) at an ordinal
        // that happens to fall in the runtime range MUST still be refused — the previous
        // ordinal-only check would have wrongly accepted this case (operator yaml is
        // recommended to use 10_000-99_999, but the registry's enforced range is
        // anything-non-colliding). The DYNAMIC_NAMES set is the source of truth.
        Layer.register("DYN_OPERATOR_AT_RUNTIME_TIER", 100_021, true);
        final IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> Layer.unregisterDynamic("DYN_OPERATOR_AT_RUNTIME_TIER"));
        assertTrue(ex.getMessage().contains("DYN_OPERATOR_AT_RUNTIME_TIER"), ex.getMessage());
        assertTrue(ex.getMessage().contains("non-dynamic"), ex.getMessage());
        // Still in the registry, still resolvable.
        assertNotSame(Layer.UNDEFINED, Layer.nameOf("DYN_OPERATOR_AT_RUNTIME_TIER"));
    }

    @Test
    void registerDynamicAfterUnregisterReusesOrdinal() {
        // A runtime rule introduces a layer.
        final Layer first = Layer.registerDynamic("DYN_REUSE_CYCLE", 100_010, true);
        assertSame(first, Layer.valueOf(100_010));

        // The declaring rule is removed; refcount drops to 0; the layer is freed.
        Layer.unregisterDynamic("DYN_REUSE_CYCLE");

        // A subsequent registration with the same (name, ordinal, normal) is permitted —
        // the slot is free.
        final Layer second = Layer.registerDynamic("DYN_REUSE_CYCLE", 100_010, true);
        assertNotSame(first, second);
        assertEquals(100_010, second.value());
    }
}
