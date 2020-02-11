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

package org.apache.skywalking.oap.server.cluster.plugin.etcd;

import java.net.URI;
import java.util.List;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@RunWith(PowerMockRunner.class)
@PrepareForTest(EtcdUtils.class)
@PowerMockIgnore("javax.management.*")
public class ClusterModuleEtcdProviderTest {

    private ClusterModuleEtcdProvider provider = new ClusterModuleEtcdProvider();

    @Test
    public void name() {
        assertEquals("etcd", provider.name());
    }

    @Test
    public void module() {
        assertEquals(ClusterModule.class, provider.module());
    }

    @Test
    public void createConfigBeanIfAbsent() {
        ModuleConfig moduleConfig = provider.createConfigBeanIfAbsent();
        assertTrue(moduleConfig instanceof ClusterModuleEtcdConfig);
    }

    @Test(expected = ModuleStartException.class)
    public void prepareWithNonHost() throws Exception {
        provider.prepare();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void prepare() throws Exception {
        PowerMockito.mockStatic(EtcdUtils.class);
        ClusterModuleEtcdConfig etcdConfig = new ClusterModuleEtcdConfig();
        etcdConfig.setHostPort("10.0.0.1:1000,10.0.0.2:1001");
        Whitebox.setInternalState(provider, "config", etcdConfig);
        provider.prepare();

        List<URI> uris = mock(List.class);
        PowerMockito.when(EtcdUtils.parse(etcdConfig)).thenReturn(uris);
        ArgumentCaptor<ClusterModuleEtcdConfig> addressCaptor = ArgumentCaptor.forClass(ClusterModuleEtcdConfig.class);
        PowerMockito.verifyStatic();
        EtcdUtils.parse(addressCaptor.capture());
        ClusterModuleEtcdConfig cfg = addressCaptor.getValue();
        assertEquals(etcdConfig.getHostPort(), cfg.getHostPort());
    }

    @Test
    public void prepareSingle() throws Exception {
        PowerMockito.mockStatic(EtcdUtils.class);
        ClusterModuleEtcdConfig etcdConfig = new ClusterModuleEtcdConfig();
        etcdConfig.setHostPort("10.0.0.1:1000");
        Whitebox.setInternalState(provider, "config", etcdConfig);
        provider.prepare();

        List<URI> uris = mock(List.class);
        PowerMockito.when(EtcdUtils.parse(etcdConfig)).thenReturn(uris);
        ArgumentCaptor<ClusterModuleEtcdConfig> addressCaptor = ArgumentCaptor.forClass(ClusterModuleEtcdConfig.class);
        PowerMockito.verifyStatic();
        EtcdUtils.parse(addressCaptor.capture());
        ClusterModuleEtcdConfig cfg = addressCaptor.getValue();
        assertEquals(etcdConfig.getHostPort(), cfg.getHostPort());
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
        String[] modules = provider.requiredModules();
        assertArrayEquals(new String[] {CoreModule.NAME}, modules);
    }
}
