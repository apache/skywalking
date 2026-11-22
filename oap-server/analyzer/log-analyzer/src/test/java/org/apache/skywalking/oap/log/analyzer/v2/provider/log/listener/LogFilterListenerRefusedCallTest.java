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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A rule calling a method outside LAL's list fails the boot (a static rule) or the apply (a runtime rule), naming
 * the rule's file and line and what is supported, rather than being skipped.
 */
class LogFilterListenerRefusedCallTest {

    @Test
    void aRefusedCallFailsTheCompileNamingTheRuleAndTheList() throws Exception {
        final LALConfig c = new LALConfig();
        c.setName("refused");
        c.setSourcePath("lal/custom.yaml");
        c.setLineNo(7);
        c.setLayer("GENERAL");
        c.setDsl("filter { extractor { tag size: tag(\"s\").getBytes() } sink {} }");
        final LogFilterListener.Factory factory =
            new LogFilterListener.Factory(mock(ModuleManager.class), mock(LogAnalyzerModuleConfig.class));
        final ModuleStartException e = assertThrows(ModuleStartException.class, () -> factory.compile(c));
        assertTrue(e.getMessage().contains("'refused' at lal/custom.yaml:7"), e.getMessage());
        assertTrue(e.getMessage().contains("String.getBytes(...) is not supported"), e.getMessage());
        assertTrue(e.getMessage().contains("(see the LAL documentation)"), e.getMessage());
    }

    @Test
    void aStaticRuleWithARefusedCallFailsTheBoot() throws Exception {
        final LogAnalyzerModuleConfig config = new LogAnalyzerModuleConfig();
        config.setLalPath("lal-refused-call");
        config.setLalFiles("refused");
        final LogFilterListener.Factory factory = new LogFilterListener.Factory(mock(ModuleManager.class), config);
        final ModuleStartException e = assertThrows(ModuleStartException.class, factory::loadStaticRules);
        assertTrue(e.getMessage().contains("refused.yaml"), e.getMessage());
        assertTrue(e.getMessage().contains("String.getBytes(...) is not supported"), e.getMessage());
    }
}
