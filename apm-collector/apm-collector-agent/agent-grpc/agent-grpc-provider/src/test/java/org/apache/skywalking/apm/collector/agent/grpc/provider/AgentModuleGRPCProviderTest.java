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
 */

package org.apache.skywalking.apm.collector.agent.grpc.provider;

import org.apache.skywalking.apm.collector.agent.grpc.define.AgentGRPCModule;
import org.apache.skywalking.apm.collector.core.module.MockModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
@RunWith(MockitoJUnitRunner.class)
public class AgentModuleGRPCProviderTest {

    private AgentModuleGRPCProvider agentModuleGRPCProvider;

    @Mock
    private AgentModuleGRPCConfig config;

    @Mock
    private ModuleManager moduleManager;


    @Before
    public void setUp() throws Exception {
        agentModuleGRPCProvider = new AgentModuleGRPCProvider();
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        Whitebox.setInternalState(agentModuleGRPCProvider, "config", config);
        Whitebox.setInternalState(agentModuleGRPCProvider, "manager", moduleManager);
    }

    @Test
    public void name() {
        Assert.assertEquals(agentModuleGRPCProvider.name(), "gRPC");
    }

    @Test
    public void module() {
        Assert.assertEquals(agentModuleGRPCProvider.module(), AgentGRPCModule.class);
    }

    @Test
    public void createConfigBeanIfAbsent() {
        assertEquals(agentModuleGRPCProvider.createConfigBeanIfAbsent(), config);
    }

    @Test
    public void prepare() {
        agentModuleGRPCProvider.prepare();
    }

    @Test
    public void start() throws ServiceNotProvidedException {
        String host = "127.0.0.1";
        Integer port = 12801;
        Mockito.when(config.getHost()).thenReturn(host);
        Mockito.when(config.getPort()).thenReturn(port);
        Mockito.when(config.getAuthentication()).thenReturn("test_token");
        agentModuleGRPCProvider.start();

    }

    @Test
    public void notifyAfterCompleted() {
        agentModuleGRPCProvider.notifyAfterCompleted();
    }

    @Test
    public void requiredModules() {
        String[] strings = agentModuleGRPCProvider.requiredModules();
        assertTrue(strings.length > 0);
    }
}