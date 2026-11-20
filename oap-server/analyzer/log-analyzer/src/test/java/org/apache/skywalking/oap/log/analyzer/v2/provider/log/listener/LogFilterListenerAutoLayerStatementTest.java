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

package org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener;

import org.apache.skywalking.oap.log.analyzer.v2.provider.LALConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A {@code layer: auto} rule that can save a log must decide its layer: one with a {@code sink} but no
 * {@code layer} statement is skipped at boot and rejected as a runtime rule.
 */
class LogFilterListenerAutoLayerStatementTest {

    private static LALConfig rule(final String layer, final String dsl) {
        final LALConfig c = new LALConfig();
        c.setName("r");
        c.setSourcePath("lal/test.yaml");
        c.setLayer(layer);
        c.setDsl(dsl);
        return c;
    }

    @Test
    void anAutoRuleThatSavesWithoutALayerIsRefused() {
        final String reason = LogFilterListener.Factory.savesWithoutLayer(
            rule("auto", "filter { extractor { service \"svc\" } sink {} }"));

        assertNotNull(reason);
        assertTrue(reason.contains("lal/test.yaml") && reason.contains("layer statement"), reason);
    }

    @Test
    void aLayerStatementOnAnyBranchIsEnough() {
        assertNull(LogFilterListener.Factory.savesWithoutLayer(
            rule("auto", "filter { extractor { layer \"IOS\" } sink {} }")));
        assertNull(LogFilterListener.Factory.savesWithoutLayer(rule("auto",
            "filter { if (tag(\"a\") == \"1\") { extractor { layer \"IOS\" } } else { abort {} } sink {} }")));
        assertNull(LogFilterListener.Factory.savesWithoutLayer(rule("auto",
            "filter { extractor { if (tag(\"a\") == \"1\") { layer \"IOS\" } } sink {} }")));
    }

    @Test
    void anAutoRuleWithoutASinkNeedsNoLayer() {
        // It never saves a log, e.g. one that only extracts metrics.
        assertNull(LogFilterListener.Factory.savesWithoutLayer(rule("auto", "filter { extractor { service \"svc\" } }")));
    }

    @Test
    void aLayeredRuleNeedsNoLayerStatement() {
        assertNull(LogFilterListener.Factory.savesWithoutLayer(rule("GENERAL", "filter { sink {} }")));
    }

    @Test
    void aScriptThatDoesNotParseIsLeftToTheCompiler() {
        assertNull(LogFilterListener.Factory.savesWithoutLayer(rule("auto", "filter { sink {")));
    }

    @Test
    void aRuntimeAutoRuleThatSavesWithoutALayerIsRejected() throws Exception {
        final LogFilterListener.Factory factory =
            new LogFilterListener.Factory(mock(ModuleManager.class), mock(LogAnalyzerModuleConfig.class));

        final ModuleStartException e = assertThrows(
            ModuleStartException.class, () -> factory.compile(rule("auto", "filter { sink {} }")));
        assertTrue(e.getMessage().contains("layer statement"), e.getMessage());
    }
}
