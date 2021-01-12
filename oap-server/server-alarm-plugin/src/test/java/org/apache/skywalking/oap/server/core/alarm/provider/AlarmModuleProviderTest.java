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

package org.apache.skywalking.oap.server.core.alarm.provider;

import java.util.Iterator;
import java.util.ServiceLoader;
import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.alarm.AlarmModule;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class AlarmModuleProviderTest {

    private AlarmModuleProvider moduleProvider;

    @Before
    public void setUp() throws Exception {
        ServiceLoader<ModuleProvider> serviceLoader = ServiceLoader.load(ModuleProvider.class);
        Iterator<ModuleProvider> providerIterator = serviceLoader.iterator();

        assertTrue(providerIterator.hasNext());

        moduleProvider = (AlarmModuleProvider) providerIterator.next();

        moduleProvider.createConfigBeanIfAbsent();

        moduleProvider.prepare();
    }

    @Test
    public void name() {
        assertEquals("default", moduleProvider.name());
    }

    @Test
    public void module() {
        assertEquals(AlarmModule.class, moduleProvider.module());
    }

    @Test
    public void notifyAfterCompleted() throws Exception {

        NotifyHandler handler = mock(NotifyHandler.class);

        Whitebox.setInternalState(moduleProvider, "notifyHandler", handler);
        moduleProvider.notifyAfterCompleted();
    }

    @Test
    public void requiredModules() {
        String[] modules = moduleProvider.requiredModules();
        assertArrayEquals(new String[] {
            CoreModule.NAME,
            ConfigurationModule.NAME
        }, modules);
    }
}