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

package org.apache.skywalking.oap.log.analyzer.v2.provider;

import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Verifies that a LAL rule file declaring a top-level {@code layerDefinitions:} block has
 * each entry funneled through {@link Layer#register} before the rules compile, so the LAL
 * file is self-describing for any custom layers it references.
 *
 * <p>Owns ordinals 1300–1309. Does not call {@link Layer#seal()} — the registry is a
 * process-wide singleton and sealing here would taint sibling tests.
 */
class LALConfigsLayerDefinitionsTest {

    @Test
    void inlineLayerDefinitionsRegisterAndRulesParse() throws Exception {
        final List<LALConfigs> configs = LALConfigs.load(
            "test-lal-with-layer-defs",
            Collections.singletonList("test-rule"),
            null
        );

        assertEquals(1, configs.size(), "Expected exactly one LALConfigs loaded from the fixture");

        // Inline layerDefinitions: produced two registry entries before the LAL DSL was
        // compiled.
        final Layer a = Layer.nameOf("TEST_LAL_LAYER_A");
        assertNotSame(Layer.UNDEFINED, a);
        assertEquals(1300, a.value());
        assertEquals(true, a.isNormal());

        final Layer b = Layer.nameOf("TEST_LAL_LAYER_B");
        assertNotSame(Layer.UNDEFINED, b);
        assertEquals(1301, b.value());
        assertEquals(false, b.isNormal());

        // The rule list survived the layerDefinitions parse.
        final LALConfigs single = configs.get(0);
        assertEquals(1, single.getRules().size());
        assertEquals("TEST_LAL_LAYER_A", single.getRules().get(0).getLayer());
        assertEquals(2, single.getLayerDefinitions().size());
    }

    @Test
    void reloadingTheSameFileIsIdempotent() throws Exception {
        LALConfigs.load("test-lal-with-layer-defs", Collections.singletonList("test-rule"), null);
        LALConfigs.load("test-lal-with-layer-defs", Collections.singletonList("test-rule"), null);

        assertEquals(1300, Layer.nameOf("TEST_LAL_LAYER_A").value());
    }
}
