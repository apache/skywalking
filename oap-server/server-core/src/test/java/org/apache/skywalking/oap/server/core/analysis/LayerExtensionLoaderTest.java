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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Drives the two extension-load paths owned by {@link LayerExtensionLoader}: yaml fixture
 * file (ordinals 1150–1159) and {@link LayerExtension} SPI registered via
 * {@code META-INF/services/...} on the test classpath (ordinals 1160–1169).
 *
 * <p>The test does not call {@link Layer#seal()} — the registry is a process-wide
 * singleton and sealing here would taint sibling tests. Each test path uses idempotent
 * re-registration so re-runs in the same surefire fork are harmless.
 */
class LayerExtensionLoaderTest {

    @Test
    void loadFromYamlRegistersFixtureLayers() {
        LayerExtensionLoader.loadFromYaml("layer-extensions-test.yml");

        final Layer a = Layer.nameOf("TEST_YAML_LAYER_A");
        assertNotSame(Layer.UNDEFINED, a);
        assertEquals(1150, a.value());
        assertEquals(true, a.isNormal());

        final Layer b = Layer.nameOf("TEST_YAML_LAYER_B");
        assertNotSame(Layer.UNDEFINED, b);
        assertEquals(1151, b.value());
        assertEquals(false, b.isNormal());
    }

    @Test
    void loadFromYamlMissingFileIsSilent() {
        // Absent file is not an error — the production loader treats yaml as optional.
        LayerExtensionLoader.loadFromYaml("__no_such_yaml_file__.yml");
        // No layer registered with this hypothetical name — verify nameOf returns UNDEFINED.
        assertSame(Layer.UNDEFINED, Layer.nameOf("__NOT_PRESENT__"));
    }

    @Test
    void loadFromSpiAppliesContributionsFromTestClasspath() {
        LayerExtensionLoader.loadFromSpi();

        final Layer a = Layer.nameOf("TEST_SPI_LAYER_A");
        assertNotSame(Layer.UNDEFINED, a);
        assertEquals(1160, a.value());
        assertEquals(true, a.isNormal());

        final Layer b = Layer.nameOf("TEST_SPI_LAYER_B");
        assertNotSame(Layer.UNDEFINED, b);
        assertEquals(1161, b.value());
        assertEquals(false, b.isNormal());
    }

    @Test
    void repeatedLoadIsIdempotent() {
        // First call seeds the layers. Second call re-applies — must not throw because
        // Layer.register treats identical (name, ordinal, normal) as a no-op.
        LayerExtensionLoader.loadFromYaml("layer-extensions-test.yml");
        LayerExtensionLoader.loadFromYaml("layer-extensions-test.yml");
        LayerExtensionLoader.loadFromSpi();
        LayerExtensionLoader.loadFromSpi();

        assertEquals(1150, Layer.nameOf("TEST_YAML_LAYER_A").value());
        assertEquals(1160, Layer.nameOf("TEST_SPI_LAYER_A").value());
    }
}
