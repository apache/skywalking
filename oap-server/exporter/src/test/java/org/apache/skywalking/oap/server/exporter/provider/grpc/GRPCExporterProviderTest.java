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

package org.apache.skywalking.oap.server.exporter.provider.grpc;

import java.util.Iterator;
import java.util.ServiceLoader;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.exporter.ExporterModule;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Ignore
public class GRPCExporterProviderTest {

    private ServiceLoader<ModuleProvider> serviceLoader = ServiceLoader.load(ModuleProvider.class);
    private ModuleProvider grpcExporterProvider;

    @Before
    public void setUp() throws ModuleStartException {
        Iterator<ModuleProvider> moduleProviderIterator = serviceLoader.iterator();
        assertTrue(moduleProviderIterator.hasNext());

        grpcExporterProvider = moduleProviderIterator.next();
        assertTrue(grpcExporterProvider instanceof GRPCExporterProvider);

        GRPCExporterSetting config = (GRPCExporterSetting) grpcExporterProvider.createConfigBeanIfAbsent();
        assertNotNull(config);
        assertNull(config.getTargetHost());
        assertEquals(0, config.getTargetPort());
        assertEquals(20000, config.getBufferChannelSize());
        assertEquals(2, config.getBufferChannelNum());

        //for test
        config.setTargetHost("localhost");

        grpcExporterProvider.prepare();

        grpcExporterProvider.start();
    }

    @Test
    public void name() {
        assertEquals("grpc", grpcExporterProvider.name());
    }

    @Test
    public void module() {
        assertEquals(ExporterModule.class, grpcExporterProvider.module());
    }

    @Test
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
        GRPCExporter exporter = mock(GRPCExporter.class);

        ModuleManager manager = mock(ModuleManager.class);
        ModuleProviderHolder providerHolder = mock(ModuleProviderHolder.class);

        ModuleServiceHolder serviceHolder = mock(ModuleServiceHolder.class);

        when(manager.find(CoreModule.NAME)).thenReturn(providerHolder);
        when(providerHolder.provider()).thenReturn(serviceHolder);

        doNothing().when(exporter).fetchSubscriptionList();

        grpcExporterProvider.setManager(manager);
        Whitebox.setInternalState(grpcExporterProvider, "exporter", exporter);
        grpcExporterProvider.notifyAfterCompleted();
    }

    @Test
    public void requiredModules() {
        String[] requireModules = grpcExporterProvider.requiredModules();
        assertNotNull(requireModules);
        assertEquals(1, requireModules.length);
        assertEquals("core", requireModules[0]);
    }
}