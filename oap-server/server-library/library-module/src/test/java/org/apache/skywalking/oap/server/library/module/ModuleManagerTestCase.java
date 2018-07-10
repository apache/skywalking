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

package org.apache.skywalking.oap.server.library.module;

import java.util.Properties;
import org.junit.*;

/**
 * @author peng-yongsheng
 */
public class ModuleManagerTestCase {

    private ApplicationConfiguration configuration;

    @Before
    public void init() {
        Properties providerAConfig = new Properties();
        providerAConfig.setProperty("host", "oap");
        providerAConfig.setProperty("A-key1", "A-value1");
        providerAConfig.setProperty("A-key2", "A-value2");

        configuration = new ApplicationConfiguration();
        configuration.addModule(TestModule.NAME).addProviderConfiguration(TestModuleProvider.NAME, new Properties());
        configuration.addModule(BaseModuleA.NAME).addProviderConfiguration(ModuleAProvider.NAME, providerAConfig);
        configuration.addModule(BaseModuleB.NAME).addProviderConfiguration(ModuleBProvider.NAME, new Properties());
    }

    @Test
    public void testHas() throws ModuleNotFoundException, ModuleConfigException, ServiceNotProvidedException, ProviderNotFoundException, ModuleStartException, DuplicateProviderException {
        ModuleManager manager = new ModuleManager();
        manager.init(configuration);

        Assert.assertTrue(manager.has(TestModule.NAME));
        Assert.assertTrue(manager.has(BaseModuleA.NAME));
        Assert.assertTrue(manager.has(BaseModuleB.NAME));

        Assert.assertFalse(manager.has("Undefined"));
    }

    @Test
    public void testFind() throws ModuleNotFoundException, ModuleConfigException, ServiceNotProvidedException, ProviderNotFoundException, ModuleStartException, DuplicateProviderException {
        ModuleManager manager = new ModuleManager();
        manager.init(configuration);

        try {
            manager.find("Undefined");
        } catch (ModuleNotFoundRuntimeException e) {
            Assert.assertEquals("Undefined missing.", e.getMessage());
        }
    }

    @Test
    public void testInit() throws ServiceNotProvidedException, DuplicateProviderException, ModuleConfigException, ModuleNotFoundException, ProviderNotFoundException, ModuleStartException {
        ModuleManager manager = new ModuleManager();
        manager.init(configuration);
        BaseModuleA.ServiceABusiness1 serviceABusiness1 = manager.find(BaseModuleA.NAME).provider().getService(BaseModuleA.ServiceABusiness1.class);
        Assert.assertNotNull(serviceABusiness1);

        ModuleAProvider.Config config = (ModuleAProvider.Config)manager.find(BaseModuleA.NAME).provider().createConfigBeanIfAbsent();
        Assert.assertEquals("oap", config.getHost());
    }

    @Test
    public void testAssertPreparedStage() {
        ModuleManager manager = new ModuleManager();

        try {
            manager.find("Undefined");
        } catch (AssertionError e) {
            Assert.assertEquals("Still in preparing stage.", e.getMessage());
        }
    }

    @Test
    public void testEmptyConfig() throws ModuleConfigException, ServiceNotProvidedException, ProviderNotFoundException, ModuleStartException, DuplicateProviderException {
        configuration.addModule("Undefined").addProviderConfiguration("Undefined", new Properties());

        ModuleManager manager = new ModuleManager();

        try {
            manager.init(configuration);
        } catch (ModuleNotFoundException e) {
            Assert.assertEquals("[Undefined] missing.", e.getMessage());
        }
    }
}
