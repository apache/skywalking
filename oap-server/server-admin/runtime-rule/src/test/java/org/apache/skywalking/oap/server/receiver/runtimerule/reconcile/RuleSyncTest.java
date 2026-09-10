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

package org.apache.skywalking.oap.server.receiver.runtimerule.reconcile;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.skywalking.oap.server.core.rule.ext.StaticRuleRegistry;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngineRegistry;
import org.apache.skywalking.oap.server.receiver.runtimerule.metrics.LockMetrics;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.AppliedRuleScript;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.DSLRuntimeState;
import org.apache.skywalking.oap.server.receiver.runtimerule.util.ContentHash;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The gone-keys pass of a tick tears down an entry without a DB row only when it is an operator override.
 * A rule that runs its bundled file as shipped never had a row, and tearing it down to reload the same file
 * would leave the receiver without its converter for a persistence round, dropping every sample in that window.
 */
public class RuleSyncTest {
    private static final String CATALOG = "otel-rules";
    private static final String NAME = "ai-agent/runtime-service";
    private static final String BUNDLED = "metricPrefix: meter_ai_agent\n"
        + "metricsRules:\n"
        + "  - name: tokens\n"
        + "    exp: claude_code_token_usage.sum(['service_name'])\n";
    private static final String KEY = DSLScriptKey.key(CATALOG, NAME);

    private final Map<String, AppliedRuleScript> rules = new ConcurrentHashMap<>();
    private final List<String> tornDown = new ArrayList<>();
    private final List<String> reloaded = new ArrayList<>();

    @BeforeEach
    public void shipTheBundledFile() throws Exception {
        clearRegistry();
        StaticRuleRegistry.active().record(CATALOG, NAME, BUNDLED.getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    public void unshipIt() throws Exception {
        clearRegistry();
    }

    @Test
    public void aBundledRuleSeededAtBootIsLeftAlone() throws IOException {
        final AppliedRuleScript seeded = new AppliedRuleScript(CATALOG, NAME, BUNDLED, running(BUNDLED));
        rules.put(KEY, seeded);

        sync(true).runOnce(true);

        assertTrue(tornDown.isEmpty(), "torn down: " + tornDown);
        assertSame(seeded, rules.get(KEY));
        assertTrue(reloaded.isEmpty(), "reloaded: " + reloaded);
    }

    @Test
    public void aBundledRuleAlreadyFallenOverIsLeftAlone() throws IOException {
        final AppliedRuleScript fallenOver = new AppliedRuleScript(CATALOG, NAME, BUNDLED, null);
        rules.put(KEY, fallenOver);

        sync(true).runOnce(false);

        assertTrue(tornDown.isEmpty(), "torn down: " + tornDown);
        assertSame(fallenOver, rules.get(KEY));
    }

    @Test
    public void anOverrideWhoseRowIsGoneFallsOverToTheBundle() throws IOException {
        final String edited = BUNDLED + "  - name: tokens_by_type\n"
            + "    exp: claude_code_token_usage.sum(['type', 'service_name'])\n";
        rules.put(KEY, new AppliedRuleScript(CATALOG, NAME, edited, running(edited)));

        sync(true).runOnce(false);

        assertEquals(Collections.singletonList(KEY), tornDown);
        assertTrue(rules.containsKey(KEY), "kept: the engine reported a bundled fall-over");
    }

    @Test
    public void anOverrideWithNoBundledTwinIsForgottenOnceTornDown() throws IOException {
        final String orphan = DSLScriptKey.key(CATALOG, "ai-agent/no-such-file");
        final String content = "metricPrefix: meter_none\nmetricsRules: []\n";
        rules.put(orphan, new AppliedRuleScript(CATALOG, "ai-agent/no-such-file", content, running(content)));

        sync(false).runOnce(false);

        assertEquals(Collections.singletonList(orphan), tornDown);
        assertFalse(rules.containsKey(orphan));
    }

    private static DSLRuntimeState running(final String content) {
        return DSLRuntimeState.running(CATALOG, NAME, ContentHash.sha256Hex(content), System.currentTimeMillis());
    }

    /**
     * @param bundledReloads what the engine answers when asked to tear a rule down and install its bundled twin
     * @return a sync over an empty runtime-rule table
     * @throws IOException never; the DAO is a mock
     */
    private RuleSync sync(final boolean bundledReloads) throws IOException {
        final RuntimeRuleManagementDAO dao = mock(RuntimeRuleManagementDAO.class);
        when(dao.getAll()).thenReturn(Collections.emptyList());
        final ModuleServiceHolder storage = mock(ModuleServiceHolder.class);
        when(storage.getService(RuntimeRuleManagementDAO.class)).thenReturn(dao);
        final ModuleProviderHolder storageModule = mock(ModuleProviderHolder.class);
        when(storageModule.provider()).thenReturn(storage);
        final ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(StorageModule.NAME)).thenReturn(storageModule);
        final LockMetrics lockMetrics = new LockMetrics(moduleManager);
        final StaticRuleLoader loader = new StaticRuleLoader(
            new RuleEngineRegistry(), rules, lockMetrics,
            (file, hash, prev, now, key, defer, opt) -> reloaded.add(key));
        return new RuleSync(
            moduleManager, lockMetrics, rules, loader,
            (file, hash, prev, now, key, defer, opt) -> reloaded.add(key),
            (catalog, name, alarm, opt, installBundledAfter) -> {
                tornDown.add(DSLScriptKey.key(catalog, name));
                return bundledReloads;
            },
            atBoot -> StorageManipulationOpt.withoutSchemaChange());
    }

    private static void clearRegistry() throws Exception {
        final Method clear = StaticRuleRegistry.class.getDeclaredMethod("clear");
        clear.setAccessible(true);
        clear.invoke(StaticRuleRegistry.active());
    }
}
