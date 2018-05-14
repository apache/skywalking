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

package org.apache.skywalking.apm.collector.agent.jetty.provider;

import org.apache.skywalking.apm.collector.agent.jetty.define.AgentJettyModule;
import org.apache.skywalking.apm.collector.core.module.MockModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
@RunWith(MockitoJUnitRunner.class)
public class AgentModuleJettyProviderTest {


    @Mock
    private ModuleManager moduleManager;

    @Mock
    private AgentModuleJettyConfig config;

    private AgentModuleJettyProvider provider;

    @Before
    public void setUp() throws Exception {
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        provider = new AgentModuleJettyProvider();
        Whitebox.setInternalState(provider, "manager", moduleManager);
        Whitebox.setInternalState(provider, "config", config);

    }

    @Test
    public void name() {
        assertEquals(provider.name(), "jetty");
    }

    @Test
    public void module() {
        assertEquals(provider.module(), AgentJettyModule.class);
    }

    @Test
    public void createConfigBeanIfAbsent() {
        assertEquals(provider.createConfigBeanIfAbsent(), config);
    }

    @Test
    public void prepare() {
        provider.prepare();
    }

    @Test
    public void start() {
        provider.start();
    }

    @Test
    public void notifyAfterCompleted() {
        provider.notifyAfterCompleted();
    }

    @Test
    public void requiredModules() {
        assertTrue(provider.requiredModules().length > 0);
    }
}