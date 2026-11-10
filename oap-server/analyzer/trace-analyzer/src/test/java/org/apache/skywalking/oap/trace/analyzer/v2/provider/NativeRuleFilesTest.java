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

package org.apache.skywalking.oap.trace.analyzer.v2.provider;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TALTestModules;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalDSL;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.listener.SpanFilterListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The native rule files load in the order listed, a listed file must exist, and every rule in them is a native rule
 * of its own name.
 */
class NativeRuleFilesTest {
    private static final String DIR = "tal-native-files";

    @Test
    void theFilesLoadInTheOrderListed() throws Exception {
        final List<String> names = new ArrayList<>();
        for (final TALConfigs file : TALConfigs.loadInOrder(DIR, List.of("third", "first", "second"), null)) {
            file.getRules().forEach(rule -> names.add(rule.getName()));
        }
        assertEquals(List.of("third-rule", "first-rule", "second-rule"), names);
    }

    @Test
    void aListedFileThatDoesNotExistFails() {
        final Exception e = assertThrows(ModuleStartException.class,
            () -> TALConfigs.loadInOrder(DIR, List.of("first", "nope"), null));
        assertTrue(e.getMessage().contains(DIR + "/nope.yaml is listed but does not exist"), e.getMessage());
    }

    @Test
    void theNativeRulesCompileInFileOrder() throws Exception {
        final List<String> names = new ArrayList<>();
        for (final TalDSL rule : factory("second,first").compileNativeRules()) {
            names.add(rule.getRuleName());
        }
        assertEquals(List.of("second-rule", "first-rule"), names);
    }

    @Test
    void aNativeFileHoldsNativeRulesOfDistinctNames() {
        final Exception otlp = assertThrows(ModuleStartException.class,
            () -> factory("first,otlp-rule").compileNativeRules());
        assertTrue(otlp.getMessage().contains("otlp-rule of tal/otlp-rule.yaml declares inputType otlp"),
            otlp.getMessage());
        final Exception duplicate = assertThrows(ModuleStartException.class,
            () -> factory("first,duplicate").compileNativeRules());
        assertTrue(duplicate.getMessage().contains("two native TAL rules are named first-rule"),
            duplicate.getMessage());
    }

    private static SpanFilterListener.Factory factory(final String nativeTalFiles) {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleServiceHolder coreProvider = mock(ModuleServiceHolder.class);
        final ModuleProviderHolder coreModule = mock(ModuleProviderHolder.class);
        when(coreModule.provider()).thenReturn(coreProvider);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);
        TALTestModules.stubServices(moduleManager, coreProvider, mock(MeterSystem.class),
            new NamingControl(70, 70, 150, new EndpointNameGrouping()), mock(SourceReceiver.class));
        final TraceAnalyzerModuleConfig config = new TraceAnalyzerModuleConfig();
        config.setTalPath(DIR);
        config.setNativeTalFiles(nativeTalFiles);
        return new SpanFilterListener.Factory(moduleManager, config);
    }
}
