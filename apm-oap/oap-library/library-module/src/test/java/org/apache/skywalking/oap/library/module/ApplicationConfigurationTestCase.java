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

package org.apache.skywalking.oap.library.module;

import java.util.Properties;
import org.junit.*;

/**
 * @author peng-yongsheng
 */
public class ApplicationConfigurationTestCase {

    @Test
    public void test() {
        ApplicationConfiguration configuration = new ApplicationConfiguration();
        Properties providerAConfig = new Properties();
        providerAConfig.setProperty("A-key1", "A-value1");
        providerAConfig.setProperty("A-key2", "A-value2");
        Properties providerBConfig = new Properties();
        providerBConfig.setProperty("B-key1", "B-value1");
        providerBConfig.setProperty("B-key2", "B-value2");

        final String module = "Module";
        final String providerA = "ProviderA";
        final String providerB = "ProviderB";
        configuration.addModule(module)
            .addProviderConfiguration(providerA, providerAConfig)
            .addProviderConfiguration(providerB, providerBConfig);

        Assert.assertArrayEquals(new String[] {module}, configuration.moduleList());
        Assert.assertTrue(configuration.has(module));
        Assert.assertFalse(configuration.has("ModuleB"));

        Assert.assertTrue(configuration.getModuleConfiguration(module).has(providerA));
        Assert.assertFalse(configuration.getModuleConfiguration(module).has("ProviderC"));

        Assert.assertEquals("B-value1", configuration.getModuleConfiguration(module).getProviderConfiguration(providerB).getProperty("B-key1"));
        Assert.assertEquals(providerAConfig, configuration.getModuleConfiguration(module).getProviderConfiguration(providerA));
    }
}
