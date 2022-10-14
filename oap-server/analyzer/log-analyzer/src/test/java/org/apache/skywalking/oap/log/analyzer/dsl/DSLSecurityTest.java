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

package org.apache.skywalking.oap.log.analyzer.dsl;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.powermock.reflect.Whitebox;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class DSLSecurityTest {
    @Parameterized.Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
            new String[] {
                "DDOS",
                "" +
                    "filter {\n" +
                    "  System.exit(0)\n" +
                    "  sink {}\n" +
                    "}",
                },
            new String[] {
                "DDOS",
                "" +
                    "filter {\n" +
                    "  for (;;) {}\n" +
                    "  sink {}\n" +
                    "}",
                },
            new String[] {
                "DDOS",
                "" +
                    "filter {\n" +
                    "  while (true) {}\n" +
                    "  sink {}\n" +
                    "}",
                },
            new String[] {
                "DDOS",
                "" +
                    "filter {\n" +
                    "  do {} while (true)\n" +
                    "  sink {}\n" +
                    "}",
                },
            new String[] {
                "Steal or delete files on server",
                "" +
                    "filter {\n" +
                    "  Files.delete(\"/etc/pwd\");\n" +
                    "  sink {}\n" +
                    "}",
                },
            new String[] {
                "Evaluate malicious codes in GroovyShell from inside DSL to get rid of outer DSL restriction",
                "" +
                    "filter {\n" +
                    "  new GroovyShell().evaluate('malicious codes or URL')\n" +
                    "  sink {}\n" +
                    "}",
                },
            new String[] {
                "disallowed methods",
                "filter {\n" +
                    "java.util.Collections.singleton(1)" +
                    "}",
                }
        );
    }

    @Parameterized.Parameter()
    public String name;

    @Parameterized.Parameter(1)
    public String script;

    final ModuleManager manager = mock(ModuleManager.class);

    @Before
    public void setup() {
        Whitebox.setInternalState(manager, "isInPrepareStage", false);
        when(manager.find(anyString())).thenReturn(mock(ModuleProviderHolder.class));
        when(manager.find(CoreModule.NAME).provider()).thenReturn(mock(ModuleServiceHolder.class));
        when(manager.find(CoreModule.NAME).provider().getService(SourceReceiver.class))
            .thenReturn(mock(SourceReceiver.class));
        when(manager.find(CoreModule.NAME).provider().getService(ConfigService.class))
            .thenReturn(mock(ConfigService.class));
        when(manager.find(CoreModule.NAME)
                    .provider()
                    .getService(ConfigService.class)
                    .getSearchableLogsTags())
            .thenReturn("");
    }

    @Test(expected = MultipleCompilationErrorsException.class)
    public void testSecurity() throws ModuleStartException {
        final DSL dsl = DSL.of(manager, new LogAnalyzerModuleConfig(), script);
        Whitebox.setInternalState(
            Whitebox.getInternalState(dsl, "filterSpec"), "sinkListenerFactories", Collections.emptyList()
        );

        dsl.bind(new Binding().log(LogData.newBuilder()));
        dsl.evaluate();
    }
}
