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

package org.apache.skywalking.oap.server.core.rule.ext;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticRuleRegistryTest {

    private StaticRuleRegistry registry;

    @BeforeEach
    void setUp() {
        registry = StaticRuleRegistry.active();
        registry.clear();
    }

    @AfterEach
    void tearDown() {
        registry.clear();
    }

    @Test
    void recordThenFindRoundtripsBytes() {
        // The runtime-rule REST handler's priorContent fallback depends on this: what the
        // boot extension recorded is what the handler must see back on a later lookup.
        final byte[] content = "metricPrefix: foo\n".getBytes(StandardCharsets.UTF_8);

        registry.record("otel-rules", "vm", content);

        final Optional<String> out = registry.find("otel-rules", "vm");
        assertTrue(out.isPresent(), "expected recorded content to round-trip through find()");
        assertEquals("metricPrefix: foo\n", out.get());
    }

    @Test
    void findReturnsEmptyForUnknownKey() {
        // Classifier fallback treats Optional.empty() as "no static version exists" — this
        // must be the response when we haven't recorded anything for this (catalog, name).
        registry.record("otel-rules", "vm", "x".getBytes(StandardCharsets.UTF_8));

        assertFalse(registry.find("otel-rules", "other").isPresent());
        assertFalse(registry.find("other-catalog", "vm").isPresent());
    }

    @Test
    void recordIsIdempotentOnRepeatedKey() {
        // Boot may read the same file twice in some test topologies (or during re-derive
        // pathways). The last write wins — there is no append semantics.
        registry.record("otel-rules", "vm", "first".getBytes(StandardCharsets.UTF_8));
        registry.record("otel-rules", "vm", "second".getBytes(StandardCharsets.UTF_8));

        assertEquals("second", registry.find("otel-rules", "vm").orElse(null));
    }

    @Test
    void nullArgsAreIgnored() {
        // Defensive contract: the extension chain may invoke with a null catalog or name in
        // edge cases (test doubles, mis-wired loaders). Don't NPE, and don't poison the map
        // with a spurious "null:null" key that a later find() could return.
        registry.record(null, "vm", "x".getBytes(StandardCharsets.UTF_8));
        registry.record("otel-rules", null, "x".getBytes(StandardCharsets.UTF_8));
        registry.record("otel-rules", "vm", null);

        assertFalse(registry.find("otel-rules", "vm").isPresent());
        assertFalse(registry.find(null, "vm").isPresent());
        assertFalse(registry.find("otel-rules", null).isPresent());
    }

    @Test
    void singletonReturnsSameInstance() {
        // Process-wide singleton — extension + REST handler must read from the same map.
        // If this ever returns a new instance per call, the REST handler would see an
        // empty registry in production.
        assertTrue(StaticRuleRegistry.active() == StaticRuleRegistry.active());
    }
}
