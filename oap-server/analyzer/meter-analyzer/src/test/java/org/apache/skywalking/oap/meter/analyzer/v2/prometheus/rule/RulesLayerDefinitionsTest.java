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

package org.apache.skywalking.oap.meter.analyzer.v2.prometheus.rule;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Verifies that a MAL rule file declaring a top-level {@code layerDefinitions:} block has
 * each entry funneled through {@link Layer#register} before the rule's expressions
 * compile, so the rule file is self-describing for any custom layers it references.
 *
 * <p>Owns ordinals 1200–1209. Does not call {@link Layer#seal()} — the registry is a
 * process-wide singleton and sealing here would taint sibling tests.
 */
class RulesLayerDefinitionsTest {

    @Test
    void inlineLayerDefinitionsRegisterAndRuleParses() throws IOException {
        final List<Rule> rules = Rules.loadRules(
            "test-mal-with-layer-defs",
            Collections.singletonList("test-rule"),
            null
        );

        assertEquals(1, rules.size(), "Expected exactly one rule loaded from the fixture");

        // Inline layerDefinitions: produced two registry entries before the rule's
        // expressions were parsed.
        final Layer a = Layer.nameOf("TEST_MAL_LAYER_A");
        assertNotSame(Layer.UNDEFINED, a);
        assertEquals(1200, a.value());
        assertEquals(true, a.isNormal());

        final Layer b = Layer.nameOf("TEST_MAL_LAYER_B");
        assertNotSame(Layer.UNDEFINED, b);
        assertEquals(1201, b.value());
        assertEquals(false, b.isNormal());

        // The rule's own fields survived the layerDefinitions parse.
        final Rule rule = rules.get(0);
        assertEquals("test_mal", rule.getMetricPrefix());
        assertEquals(2, rule.getLayerDefinitions().size());
    }

    @Test
    void reloadingTheSameFileIsIdempotent() throws IOException {
        // Boot pipelines may load the same rules more than once across restarts in a single
        // JVM (e.g. during unit-test reuseForks). Identical re-registration must be a no-op.
        Rules.loadRules("test-mal-with-layer-defs", Collections.singletonList("test-rule"), null);
        Rules.loadRules("test-mal-with-layer-defs", Collections.singletonList("test-rule"), null);

        assertEquals(1200, Layer.nameOf("TEST_MAL_LAYER_A").value());
    }
}
