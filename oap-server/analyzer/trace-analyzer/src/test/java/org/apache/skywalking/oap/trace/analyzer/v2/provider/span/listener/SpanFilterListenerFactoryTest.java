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

import org.apache.skywalking.oap.server.core.source.DatabaseAccess;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TALConfig;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TraceAnalyzerModuleConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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
    void anOutputTypeThatIsNotABuilderIsRefused() {
        // The generated code binds the span to the record through TALOutputBuilder; a plain Source would compile
        // and then fail on every span.
        final SpanFilterListener.Factory factory =
            new SpanFilterListener.Factory(mock(ModuleManager.class), new TraceAnalyzerModuleConfig());
        final TALConfig rule = new TALConfig();
        rule.setName("plain-source");
        rule.setLayer("OTLP_SERVICE");
        rule.setInputType("otlp");
        rule.setOutputType(DatabaseAccess.class.getName());
        rule.setDsl("filter { }");

        final ModuleStartException refused = assertThrows(ModuleStartException.class, () -> factory.compile(rule));
        assertTrue(refused.getMessage().contains("plain-source") && refused.getMessage().contains("TALOutputBuilder"),
            refused.getMessage());
    }
}
