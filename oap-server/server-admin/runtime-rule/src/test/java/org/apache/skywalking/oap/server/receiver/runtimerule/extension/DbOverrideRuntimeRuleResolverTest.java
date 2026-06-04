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

package org.apache.skywalking.oap.server.receiver.runtimerule.extension;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.apache.skywalking.oap.server.core.rule.ext.StaticRuleRegistry;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.module.RuntimeRuleModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the layer-override-forbidden guard {@link DbOverrideRuntimeRuleResolver}
 * applies to runtime overrides of bundled MAL/LAL rules. The guard exists because
 * bundled and runtime are separate ownership channels for layers; the runtime path
 * must not overwrite the bundled channel.
 */
class DbOverrideRuntimeRuleResolverTest {

    @BeforeEach
    @AfterEach
    void clearRegistry() throws Exception {
        // StaticRuleRegistry is process-wide; isolate from sibling tests.
        final Method m = StaticRuleRegistry.class.getDeclaredMethod("clear");
        m.setAccessible(true);
        m.invoke(StaticRuleRegistry.active());
    }

    @Test
    void disabledModuleSkipsStorageRead() {
        // receiver-runtime-rule not in the loaded module set: the resolver must short-circuit
        // before any storage access so a disabled module never drives a runtime_rule DAO read
        // on the MAL/LAL static load path.
        final ModuleManager manager = mock(ModuleManager.class);
        when(manager.has(RuntimeRuleModule.NAME)).thenReturn(false);

        assertTrue(new DbOverrideRuntimeRuleResolver().loadAll("otel-rules", manager).isEmpty());
        verify(manager, never()).find(anyString());
    }

    @Test
    void nullManagerReturnsEmpty() {
        // No module context (tests / static-loader call without a manager) — serve disk content.
        assertTrue(new DbOverrideRuntimeRuleResolver().loadAll("otel-rules", null).isEmpty());
    }

    @Test
    void overrideWithoutLayerDefinitionsIsAllowed() {
        StaticRuleRegistry.active().record(
            "otel-rules", "no_layers_bundled",
            "metricPrefix: foo\nmetricsRules:\n  - name: x\n    exp: a.sum(['s'])\n"
                .getBytes(StandardCharsets.UTF_8));
        final byte[] runtimeBody =
            "metricPrefix: foo\nmetricsRules:\n  - name: x\n    exp: a.sum(['s'])\n"
                .getBytes(StandardCharsets.UTF_8);
        assertFalse(DbOverrideRuntimeRuleResolver.isLayerOverrideOnBundled(
            "otel-rules", "no_layers_bundled", runtimeBody));
    }

    @Test
    void overrideWithLayerDefinitionsOnDiskTwinIsForbidden() {
        // Disk twin exists.
        StaticRuleRegistry.active().record(
            "otel-rules", "with_layers_bundled",
            "metricPrefix: foo\nmetricsRules:\n  - name: x\n    exp: a.sum(['s'])\n"
                .getBytes(StandardCharsets.UTF_8));
        // Runtime override adds layerDefinitions — the case the resolver must drop.
        final String override =
            "metricPrefix: foo\n"
                + "layerDefinitions:\n"
                + "  - name: TEST_RT_OVERRIDE_LAYER\n"
                + "    ordinal: 100700\n"
                + "metricsRules:\n  - name: x\n    exp: a.sum(['s'])\n";
        assertTrue(DbOverrideRuntimeRuleResolver.isLayerOverrideOnBundled(
            "otel-rules", "with_layers_bundled",
            override.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void pureRuntimeRuleWithLayerDefinitionsIsNotForbidden() {
        // No disk twin recorded — pure runtime rule. layerDefinitions are allowed and
        // go through the dynamic channel via RuleSync.runOnce. The guard returns false.
        final String pureRuntime =
            "metricPrefix: foo\n"
                + "layerDefinitions:\n"
                + "  - name: TEST_PURE_RUNTIME_LAYER\n"
                + "    ordinal: 100701\n"
                + "metricsRules:\n  - name: x\n    exp: a.sum(['s'])\n";
        assertFalse(DbOverrideRuntimeRuleResolver.isLayerOverrideOnBundled(
            "otel-rules", "no_disk_twin_pure_runtime",
            pureRuntime.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void unknownCatalogReturnsFalse() {
        // Catalog the guard doesn't know how to parse — returns false (apply will fail
        // its own validation later if the content is malformed).
        assertFalse(DbOverrideRuntimeRuleResolver.isLayerOverrideOnBundled(
            "unknown-catalog", "x",
            "anything".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void malformedYamlReturnsFalse() {
        StaticRuleRegistry.active().record(
            "otel-rules", "malformed_check",
            "metricPrefix: foo\n".getBytes(StandardCharsets.UTF_8));
        // Garbage bytes: the guard returns false rather than throwing. The apply path
        // surfaces the YAML error with a clearer message; the override-forbidden guard
        // is not the right place to flag parse failures.
        assertFalse(DbOverrideRuntimeRuleResolver.isLayerOverrideOnBundled(
            "otel-rules", "malformed_check",
            "this: is: not: valid: yaml: at: all".getBytes(StandardCharsets.UTF_8)));
    }
}
