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

package org.apache.skywalking.oap.trace.analyzer.v2.provider.span.listener;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TALTestModules;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TALConfig;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TraceAnalyzerModuleConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the rule registry refuses before a rule is compiled.
 */
class SpanFilterListenerFactoryTest {
    @Test
    void aRuleOnAnUnregisteredLayerIsRefused() {
        // Layer.nameOf would answer UNDEFINED and the rule would install under a layer no span ever carries,
        // compiling fine and never running; the registry names the typo instead.
        final SpanFilterListener.Factory factory =
            new SpanFilterListener.Factory(mock(ModuleManager.class), new TraceAnalyzerModuleConfig());
        final TALConfig rule = new TALConfig();
        rule.setName("typo");
        rule.setLayer("OTLP_SERVCE");
        rule.setDsl("filter { }");

        final ModuleStartException refused = assertThrows(ModuleStartException.class, () -> factory.compile(rule));
        assertTrue(refused.getMessage().contains("typo") && refused.getMessage().contains("OTLP_SERVCE"),
            refused.getMessage());
    }

    @Test
    void aRuleWithNoLayerIsRefused() {
        final SpanFilterListener.Factory factory =
            new SpanFilterListener.Factory(mock(ModuleManager.class), new TraceAnalyzerModuleConfig());
        final TALConfig rule = new TALConfig();
        rule.setName("no-layer");
        rule.setDsl("filter { }");

        final ModuleStartException refused = assertThrows(ModuleStartException.class, () -> factory.compile(rule));
        assertTrue(refused.getMessage().contains("no-layer") && refused.getMessage().contains("declares no layer"),
            refused.getMessage());
    }

    @Test
    void twoRulesOfOneNameInOneLayerAreRefused() {
        // A rule is stored by its layer and name, so the second would replace the first.
        final Exception refused = assertThrows(ModuleStartException.class,
            () -> factory("duplicate-name").loadRules(false));
        assertTrue(refused.getMessage().contains("two TAL rules of layer OTLP_GENERAL are named twice"),
            refused.getMessage());
    }

    @Test
    void twoFilesNamingOneRuleOfOneLayerAreRefusedWithBothFiles() {
        final Exception refused = assertThrows(ModuleStartException.class,
            () -> factory("twice-otlp,twice-otlp-again").loadRules(false));
        assertTrue(refused.getMessage().contains("two TAL rules of layer OTLP_GENERAL are named twice")
            && refused.getMessage().contains("tal/twice-otlp.yaml")
            && refused.getMessage().contains("tal/twice-otlp-again.yaml"), refused.getMessage());
    }

    @Test
    void aCallOutsideTheListStopsTheBootNamingTheRuleAndTheSupportedMethods() {
        // The provider's start() loads the rules so, and rethrows the failure as its own, which stops OAP.
        final Exception refused = assertThrows(ModuleStartException.class,
            () -> factory("refused-call").loadRules(false));
        assertTrue(refused.getMessage().contains("TAL rule refused (tal/refused-call.yaml:")
            && refused.getMessage().contains("String.strip(...) is not supported in a TAL rule. A String allows "
                + "[charAt, concat, contains, endsWith, equals, equalsIgnoreCase, indexOf, isEmpty, lastIndexOf, "
                + "length, matches, replace, replaceAll, replaceFirst, split, startsWith, substring, toLowerCase, "
                + "toString, toUpperCase, trim]"), refused.getMessage());
    }

    @Test
    void oneNameInTwoLayersNamesTwoRules() throws Exception {
        final SpanFilterListener.Factory factory = factory("twice-otlp,other-layer");
        factory.loadRules(false);
        assertTrue(factory.contains(Layer.OTLP_GENERAL, "twice"));
        assertTrue(factory.contains(Layer.GENERAL, "twice"));
    }

    @Test
    void aLayerANativeFileDeclaresServesAStaticRule() throws Exception {
        // Every listed file is read, its layers registered, before a rule of either list compiles.
        final SpanFilterListener.Factory factory = factory("edge-layer-otlp", "edge-layer-native");
        assertEquals(1, factory.loadRules(true).size());
        assertTrue(factory.contains(Layer.OTLP_GENERAL, "edge-otlp"));
    }

    @Test
    void twoNativeRulesOfOneNameNameBothFiles() {
        final Exception refused = assertThrows(ModuleStartException.class,
            () -> factory("tal-native-files", "", "first,duplicate").loadRules(true));
        assertTrue(refused.getMessage().contains("two native TAL rules are named first-rule, in tal/first.yaml and "
            + "tal/duplicate.yaml"), refused.getMessage());
    }

    private static SpanFilterListener.Factory factory(final String talFiles) {
        return factory("tal-static-files", talFiles, "");
    }

    private static SpanFilterListener.Factory factory(final String talFiles, final String nativeTalFiles) {
        return factory("tal-static-files", talFiles, nativeTalFiles);
    }

    private static SpanFilterListener.Factory factory(final String talPath, final String talFiles,
                                                      final String nativeTalFiles) {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleServiceHolder coreProvider = mock(ModuleServiceHolder.class);
        final ModuleProviderHolder coreModule = mock(ModuleProviderHolder.class);
        when(coreModule.provider()).thenReturn(coreProvider);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);
        TALTestModules.stubServices(moduleManager, coreProvider, mock(MeterSystem.class),
            new NamingControl(70, 70, 150, new EndpointNameGrouping()), mock(SourceReceiver.class));
        final TraceAnalyzerModuleConfig config = new TraceAnalyzerModuleConfig();
        config.setTalPath(talPath);
        config.setTalFiles(talFiles);
        config.setNativeTalFiles(nativeTalFiles);
        return new SpanFilterListener.Factory(moduleManager, config);
    }
}
