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

import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Validates the registry-backed {@link Layer} contract: built-in lookups, name-based and
 * ordinal-based valueOf, and the four conflict checks enforced by
 * {@link Layer#register(String, int, boolean)} (name shape, name uniqueness, ordinal
 * uniqueness, idempotent re-registration).
 *
 * <p>Tests do <strong>not</strong> call {@link Layer#seal()} — the registry is a
 * process-wide singleton and sealing here would taint sibling tests in the same JVM.
 * Each test owns a distinct ordinal range to avoid collisions when multiple tests run in
 * one surefire fork:
 * <ul>
 *   <li>{@code LayerTest} → ordinals {@code 1100..1149}</li>
 *   <li>{@link LayerExtensionLoaderTest} → ordinals {@code 1150..1199}</li>
 * </ul>
 */
class LayerTest {

    @Test
    void builtInsAreLookedUpByNameAndOrdinal() {
        assertSame(Layer.MESH, Layer.nameOf("MESH"));
        assertSame(Layer.MESH, Layer.valueOf("MESH"));
        assertSame(Layer.MESH, Layer.valueOf(1));
        assertEquals("MESH", Layer.MESH.name());
        assertEquals(1, Layer.MESH.value());
        assertEquals(true, Layer.MESH.isNormal());
    }

    @Test
    void nameOfReturnsUndefinedOnMiss() {
        assertSame(Layer.UNDEFINED, Layer.nameOf("__NO_SUCH_LAYER__"));
    }

    @Test
    void valueOfStringThrowsOnMiss() {
        assertThrows(IllegalArgumentException.class, () -> Layer.valueOf("__NO_SUCH_LAYER__"));
    }

    @Test
    void valueOfIntThrowsOnMiss() {
        assertThrows(UnexpectedException.class, () -> Layer.valueOf(987654));
    }

    @Test
    void registerReturnsExistingInstanceOnIdenticalReregistration() {
        final Layer first = Layer.register("TEST_LAYER_IDEMPOTENT", 1100, true);
        final Layer second = Layer.register("TEST_LAYER_IDEMPOTENT", 1100, true);
        assertSame(first, second);
    }

    @Test
    void registerRejectsNameConflict() {
        Layer.register("TEST_LAYER_NAME_CONFLICT", 1101, true);
        // Same name, different ordinal — must fail loudly.
        assertThrows(IllegalStateException.class,
                     () -> Layer.register("TEST_LAYER_NAME_CONFLICT", 1102, true));
        // Same name, different normal flag — also fails.
        assertThrows(IllegalStateException.class,
                     () -> Layer.register("TEST_LAYER_NAME_CONFLICT", 1101, false));
    }

    @Test
    void registerRejectsOrdinalConflict() {
        Layer.register("TEST_LAYER_ORDINAL_FIRST", 1103, true);
        assertThrows(IllegalStateException.class,
                     () -> Layer.register("TEST_LAYER_ORDINAL_SECOND", 1103, true));
    }

    @Test
    void registerRejectsBadNameShape() {
        // Lower-case leading char.
        assertThrows(IllegalArgumentException.class,
                     () -> Layer.register("badName", 1104, true));
        // Starts with digit.
        assertThrows(IllegalArgumentException.class,
                     () -> Layer.register("9_LAYER", 1105, true));
        // Contains hyphen.
        assertThrows(IllegalArgumentException.class,
                     () -> Layer.register("BAD-NAME", 1106, true));
        // Null.
        assertThrows(IllegalArgumentException.class,
                     () -> Layer.register(null, 1107, true));
    }

    @Test
    void externalLayerIsLookedUpLikeABuiltIn() {
        final Layer registered = Layer.register("TEST_LAYER_LOOKUP", 1108, false);
        assertSame(registered, Layer.nameOf("TEST_LAYER_LOOKUP"));
        assertSame(registered, Layer.valueOf("TEST_LAYER_LOOKUP"));
        assertSame(registered, Layer.valueOf(1108));
        assertEquals(false, Layer.nameOf("TEST_LAYER_LOOKUP").isNormal());
    }
}
