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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.DSL;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LALConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A rule's layer must be a registered one: an unregistered or missing layer would leave the rule
 * silently never running, so a static rule declaring one is skipped and a runtime rule rejected. {@code fallback: true} is an auto rule's only, and puts it in a group of its own.
 */
class LogFilterListenerLayerTest {

    private static LALConfig rule(final String layer) {
        final LALConfig c = new LALConfig();
        c.setName("r");
        c.setSourcePath("lal/test.yaml");
        c.setLayer(layer);
        return c;
    }

    @Test
    void registeredLayerResolves() throws ModuleStartException {
        assertEquals(Layer.GENERAL, LogFilterListener.Factory.resolveLayer(rule("GENERAL")));
    }

    @Test
    void unregisteredLayerIsRejected() {
        final ModuleStartException e = assertThrows(
            ModuleStartException.class, () -> LogFilterListener.Factory.resolveLayer(rule("GENERL")));
        assertTrue(e.getMessage().contains("GENERL"), e.getMessage());
    }

    @Test
    void undefinedLayerIsRejected() {
        assertThrows(ModuleStartException.class, () -> LogFilterListener.Factory.resolveLayer(rule("UNDEFINED")));
    }

    @Test
    void missingLayerIsRejected() {
        assertThrows(ModuleStartException.class, () -> LogFilterListener.Factory.resolveLayer(rule(null)));
        assertThrows(ModuleStartException.class, () -> LogFilterListener.Factory.resolveLayer(rule("")));
    }

    @Test
    void registeredLayerNamesAreCaseSensitive() {
        assertTrue(LogFilterListener.Factory.isRegisteredLayer("GENERAL"));
        assertFalse(LogFilterListener.Factory.isRegisteredLayer("General"));
        assertFalse(LogFilterListener.Factory.isRegisteredLayer("UNDEFINED"));
        assertFalse(LogFilterListener.Factory.isRegisteredLayer(null));
    }

    @Test
    void fallbackIsRejectedOnALayeredRule() throws Exception {
        final LALConfig c = rule("GENERAL");
        c.setFallback(true);
        final ModuleStartException e = assertThrows(ModuleStartException.class, () -> factory().compile(c));
        assertTrue(e.getMessage().contains("fallback"), e.getMessage());
    }

    @Test
    void autoAndFallbackRulesAreSeparateGroupsOfOneNameSpace() throws Exception {
        final LogFilterListener.Factory factory = factory();
        final DSL autoRule = mock(DSL.class);
        final DSL fallbackRule = mock(DSL.class);
        factory.addOrReplace(new LogFilterListener.Factory.CompiledLAL(null, "a", autoRule, false));
        factory.addOrReplace(new LogFilterListener.Factory.CompiledLAL(null, "f", fallbackRule, true));

        final AutoListeners passes = factory.createAuto();
        evaluate(passes.getAuto());
        verify(autoRule).evaluate(any());
        verify(fallbackRule, never()).evaluate(any());

        evaluate(passes.getFallback());
        verify(fallbackRule).evaluate(any());

        assertSame(fallbackRule, factory.find(null, "f"));
        assertTrue(factory.contains(null, "f"));

        // A replace that turns "a" into a fallback rule moves it between the groups.
        factory.addOrReplace(new LogFilterListener.Factory.CompiledLAL(null, "a", autoRule, true));
        assertNull(factory.createAuto().getAuto());

        factory.remove(null, "a");
        factory.remove(null, "f");
        assertNull(factory.createAuto().getFallback());
        assertFalse(factory.contains(null, "a"));
    }

    /**
     * A rule whose {@code fallback} flag flips on a runtime replace moves between the two groups; a reader
     * running at the same time must always find it in one of them, never in neither.
     */
    @Test
    void ruleMovingBetweenAutoAndFallbackIsNeverMissing() throws Exception {
        final LogFilterListener.Factory factory = factory();
        final DSL rule = mock(DSL.class);
        factory.addOrReplace(new LogFilterListener.Factory.CompiledLAL(null, "r", rule, false));

        final AtomicBoolean stop = new AtomicBoolean();
        final Thread mover = new Thread(() -> {
            boolean fallback = false;
            while (!stop.get()) {
                fallback = !fallback;
                factory.addOrReplace(new LogFilterListener.Factory.CompiledLAL(null, "r", rule, fallback));
            }
        });
        mover.start();
        int missed = 0;
        int notExactlyOnePass = 0;
        try {
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime() < deadline) {
                if (factory.find(null, "r") == null) {
                    missed++;
                }
                // The two passes of one log come from one view: the rule is in exactly one of them.
                final AutoListeners passes = factory.createAuto();
                if ((passes.getAuto() == null) == (passes.getFallback() == null)) {
                    notExactlyOnePass++;
                }
            }
        } finally {
            stop.set(true);
            mover.join();
        }
        assertEquals(0, missed, "lookups that found the rule in neither group");
        assertEquals(0, notExactlyOnePass, "createAuto() views with the rule in neither or both passes");
    }

    private static LogFilterListener.Factory factory() throws Exception {
        return new LogFilterListener.Factory(mock(ModuleManager.class), mock(LogAnalyzerModuleConfig.class));
    }

    private static void evaluate(final LogAnalysisListener listener) {
        listener.parse(LogMetadata.builder().service("svc").layer("").build(), null);
        listener.build();
    }
}
