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

package org.apache.skywalking.apm.collector.ui.jetty;

import org.apache.skywalking.apm.collector.core.module.MockModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;

import static org.mockito.Matchers.anyString;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
public class UIModuleJettyProviderTest {
    private UIModuleJettyProvider uiModuleJettyProvider;

    @Before
    public void setUp() {
        uiModuleJettyProvider = new UIModuleJettyProvider();
        ModuleManager moduleManager = Mockito.mock(ModuleManager.class);
        Whitebox.setInternalState(uiModuleJettyProvider, "manager", moduleManager);
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
    }

    @Test
    public void name() {
        Assert.assertEquals(uiModuleJettyProvider.name(), "jetty");
    }

    @Test
    public void module() {
        Assert.assertNotNull(uiModuleJettyProvider.module());
    }

    @Test
    public void createConfigBeanIfAbsent() {
        Assert.assertNotNull(uiModuleJettyProvider.createConfigBeanIfAbsent());
    }

    @Test
    public void prepare() {
        uiModuleJettyProvider.prepare();
    }

    @Test
    public void start() {
        uiModuleJettyProvider.start();
    }

    @Test
    public void notifyAfterCompleted() {
        uiModuleJettyProvider.notifyAfterCompleted();
    }

    @Test
    public void requiredModules() {
        Assert.assertTrue(uiModuleJettyProvider.requiredModules().length > 0);
    }
}