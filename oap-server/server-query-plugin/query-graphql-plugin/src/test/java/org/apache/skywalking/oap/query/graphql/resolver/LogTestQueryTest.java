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

package org.apache.skywalking.oap.query.graphql.resolver;

import junit.framework.TestCase;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleProvider;
import org.apache.skywalking.oap.query.graphql.GraphQLQueryConfig;
import org.apache.skywalking.oap.query.graphql.type.LogTestRequest;
import org.apache.skywalking.oap.query.graphql.type.LogTestResponse;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LogTestQueryTest extends TestCase {
    @Mock
    private ModuleManager moduleManager;

    @Mock
    private GraphQLQueryConfig config;

    @Mock
    private ModuleProviderHolder providerHolder;

    @Mock
    private LogAnalyzerModuleProvider serviceHolder;

    @Mock
    private LogAnalyzerModuleConfig lalConfig;

    @Before
    public void setup() {
        when(moduleManager.find(anyString()))
            .thenReturn(providerHolder);
        when(providerHolder.provider())
            .thenReturn(serviceHolder);
        when(serviceHolder.newConfigCreator()).thenCallRealMethod();
        when(serviceHolder.getModuleConfig()).thenCallRealMethod();
        final ModuleProvider.ConfigCreator configCreator = serviceHolder.newConfigCreator();
        configCreator.onInitialized(lalConfig);

        final ModuleProviderHolder m = mock(ModuleProviderHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(m);
        final ModuleServiceHolder s = mock(ModuleServiceHolder.class);
        when(m.provider()).thenReturn(s);
        final ConfigService c = mock(ConfigService.class);
        when(s.getService(ConfigService.class)).thenReturn(c);
        final NamingControl namingControl = mock(NamingControl.class);
        when(s.getService(NamingControl.class)).thenReturn(namingControl);
        when(namingControl.formatServiceName(anyString())).thenCallRealMethod();
        when(c.getSearchableLogsTags()).thenReturn("");
    }

    @Test
    public void shouldThrowWhenDisabled() {
        final LogTestQuery query = new LogTestQuery(moduleManager, config);
        try {
            query.test(new LogTestRequest());
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof IllegalAccessException);
            assertTrue(e.getMessage().contains("LAL debug tool is not enabled"));
        }
    }

    @Test
    public void test() throws Exception {
        when(config.isEnableLogTestTool()).thenReturn(true);
        final LogTestQuery query = new LogTestQuery(moduleManager, config);
        final LogTestRequest request = new LogTestRequest();
        request.setLog("" +
                           "{" +
                           "  body: {" +
                           "    text: {" +
                           "      text: 'Save user test'" +
                           "    }" +
                           "  }," +
                           "  type: TEXT," +
                           "  timestamp: 12312313," +
                           "  service: 'test'" +
                           "}");
        request.setDsl("" +
                           "filter {\n" +
                           "  extractor {\n" +
                           "    metrics {\n" +
                           "      timestamp log.timestamp as Long\n" +
                           "      labels level: parsed.level, service: log.service, instance: log.serviceInstance\n" +
                           "      name 'log_count'\n" +
                           "      value 1\n" +
                           "    }\n" +
                           "  }\n" +
                           "  sink {\n" +
                           "  }\n" +
                           "}");
        final LogTestResponse response = query.test(request);
        assertEquals("Save user test", response.getLog().getContent());
        assertFalse(response.getMetrics().isEmpty());
        assertEquals("log_count", response.getMetrics().iterator().next().getName());
        assertEquals(1, response.getMetrics().iterator().next().getValue());
        assertEquals(12312313, response.getMetrics().iterator().next().getTimestamp());
    }
}
