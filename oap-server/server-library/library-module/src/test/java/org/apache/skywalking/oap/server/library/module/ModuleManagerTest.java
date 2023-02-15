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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ModuleManagerTest {
    @Test
    public void testInit() throws ServiceNotProvidedException, ModuleNotFoundException, ProviderNotFoundException, DuplicateProviderException, ModuleConfigException, ModuleStartException {
        ApplicationConfiguration configuration = new ApplicationConfiguration();
        configuration.addModule("Test").addProviderConfiguration("TestModule-Provider", new Properties());
        configuration.addModule("BaseA").addProviderConfiguration("P-A", new Properties());
        configuration.addModule("BaseB").addProviderConfiguration("P-B", new Properties());

        ModuleManager manager = new ModuleManager();
        manager.init(configuration);

        BaseModuleA.ServiceABusiness1 serviceABusiness1 = manager.find("BaseA")
                                                                 .provider()
                                                                 .getService(BaseModuleA.ServiceABusiness1.class);
        Assertions.assertTrue(serviceABusiness1 != null);
    }

    @Test
    public void testModuleConfigInit() throws ModuleConfigException, ModuleNotFoundException, ModuleStartException {
        ApplicationConfiguration configuration = new ApplicationConfiguration();
        final Properties settings = new Properties();
        settings.put("attr1", "abc");
        settings.put("attr2", 123);
        settings.put("attr3", 123L);
        settings.put("attr4", true);
        configuration.addModule("BaseA").addProviderConfiguration("P-A", settings);

        ModuleManager manager = new ModuleManager();
        manager.init(configuration);

        final ModuleServiceHolder provider = manager.find("BaseA").provider();
        Assertions.assertTrue(provider instanceof ModuleAProvider);
        final ModuleAProvider moduleAProvider = (ModuleAProvider) provider;
        final ModuleAProviderConfig config = moduleAProvider.getConfig();
        Assertions.assertEquals("abc", config.getAttr1());
        Assertions.assertEquals(123, config.getAttr2().intValue());
        Assertions.assertEquals(123L, config.getAttr3().longValue());
        Assertions.assertEquals(true, config.isAttr4());
    }

    @Test
    public void testModuleMissing() {
        assertThrows(ModuleNotFoundException.class, () -> {
            ApplicationConfiguration configuration = new ApplicationConfiguration();
            configuration.addModule("BaseA").addProviderConfiguration("P-A", new Properties());
            configuration.addModule("BaseB").addProviderConfiguration("P-B2", new Properties());

            ModuleManager manager = new ModuleManager();
            manager.init(configuration);
        });
    }

    @Test
    public void testCycleDependency() {
        assertThrows(CycleDependencyException.class, () -> {
            ApplicationConfiguration configuration = new ApplicationConfiguration();
            configuration.addModule("BaseA").addProviderConfiguration("P-A2", new Properties());
            configuration.addModule("BaseB").addProviderConfiguration("P-B3", new Properties());

            ModuleManager manager = new ModuleManager();
            manager.init(configuration);
        });
    }
}
