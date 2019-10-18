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

package org.apache.skywalking.oap.server.cluster.plugin.nacos;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
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

import java.util.Properties;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author caoyixiong
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(NamingFactory.class)
@PowerMockIgnore("javax.management.*")
public class ClusterModuleNacosProviderTest {

    private static final String SERVICE_NAME = "test-service_name";

    private ClusterModuleNacosProvider provider = new ClusterModuleNacosProvider();

    @Test
    public void name() {
        assertEquals("nacos", provider.name());
    }

    @Test
    public void module() {
        assertEquals(ClusterModule.class, provider.module());
    }


    @Test
    public void createConfigBeanIfAbsent() {
        ModuleConfig moduleConfig = provider.createConfigBeanIfAbsent();
        assertTrue(moduleConfig instanceof ClusterModuleNacosConfig);
    }

    @Test(expected = ModuleStartException.class)
    public void prepareWithNonHost() throws Exception {
        provider.prepare();
    }

    @Test
    public void prepare() throws Exception {
        PowerMockito.mockStatic(NamingFactory.class);
        ClusterModuleNacosConfig nacosConfig = new ClusterModuleNacosConfig();
        nacosConfig.setHostPort("10.0.0.1:1000,10.0.0.2:1001");
        nacosConfig.setServiceName(SERVICE_NAME);
        Whitebox.setInternalState(provider, "config", nacosConfig);
        NamingService namingService = mock(NamingService.class);

        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "10.0.0.1:1000,10.0.0.2:1001");

        PowerMockito.when(NamingFactory.createNamingService(properties)).thenReturn(namingService);
        provider.prepare();
        ArgumentCaptor<Properties> addressCaptor = ArgumentCaptor.forClass(Properties.class);
        PowerMockito.verifyStatic();
        NamingFactory.createNamingService(addressCaptor.capture());
        Properties data = addressCaptor.getValue();
        assertEquals("10.0.0.1:1000,10.0.0.2:1001", data.getProperty(PropertyKeyConst.SERVER_ADDR));
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
        assertArrayEquals(new String[]{CoreModule.NAME}, modules);
    }
}
