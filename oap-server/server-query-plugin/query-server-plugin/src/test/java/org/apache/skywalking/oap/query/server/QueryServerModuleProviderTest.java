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

package org.apache.skywalking.oap.query.server;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.annotation.Get;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.module.TerminalFriendlyTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryServerModuleProviderTest {

    @Mock
    private ModuleManager moduleManager;

    @Mock
    private ModuleProviderHolder coreHolder;

    @Mock
    private ModuleServiceHolder coreServices;

    @Mock
    private HTTPHandlerRegister coreRESTRegister;

    private QueryServerModuleProvider provider;

    @BeforeEach
    void setUp() {
        provider = new QueryServerModuleProvider();
        provider.setManager(moduleManager);
        provider.setBootingParameters(new TerminalFriendlyTable("test"));
    }

    @Test
    void handlersGoToCoreRESTWithoutOwnPort() throws Exception {
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreHolder);
        when(coreHolder.provider()).thenReturn(coreServices);
        when(coreServices.getService(HTTPHandlerRegister.class)).thenReturn(coreRESTRegister);
        configure(0);

        provider.prepare();
        provider.start();
        final Object handler = new TestProbeHandler();
        final List<HttpMethod> methods = Collections.singletonList(HttpMethod.GET);
        provider.getService(HTTPHandlerRegister.class).addHandler(handler, methods);

        verify(coreRESTRegister).addHandler(handler, methods);
    }

    @Test
    void handlersServeOnOwnPortAndCoreRESTIsNotUsed() throws Exception {
        final int port = freePort();
        configure(port);

        provider.prepare();
        provider.start();
        provider.getService(HTTPHandlerRegister.class)
                .addHandler(new TestProbeHandler(), Collections.singletonList(HttpMethod.GET));
        provider.notifyBootCompleted();

        final AggregatedHttpResponse response =
            WebClient.of().get("http://127.0.0.1:" + port + "/probe").aggregate().get();
        assertEquals(200, response.status().code());
        assertEquals("query-server", response.contentUtf8());
        verify(moduleManager, never()).find(anyString());
    }

    private void configure(final int restPort) {
        final QueryServerConfig config = new QueryServerConfig();
        config.setRestHost("127.0.0.1");
        config.setRestPort(restPort);
        config.setRestContextPath("/");
        @SuppressWarnings("unchecked")
        final ModuleProvider.ConfigCreator<QueryServerConfig> creator = provider.newConfigCreator();
        creator.onInitialized(config);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    static class TestProbeHandler {
        @Get("/probe")
        public HttpResponse probe() {
            return HttpResponse.of("query-server");
        }
    }
}
