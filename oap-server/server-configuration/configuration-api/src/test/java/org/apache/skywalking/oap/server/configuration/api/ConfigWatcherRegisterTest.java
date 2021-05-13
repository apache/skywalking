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

package org.apache.skywalking.oap.server.configuration.api;

import java.util.Optional;
import java.util.Set;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

public class ConfigWatcherRegisterTest {
    private ConfigWatcherRegister register;

    @Before
    public void setup() {
        register = new MockConfigWatcherRegister();
    }

    @After
    public void tearDown() {
        register = null;
    }

    @Test
    public void testInit() {
        final String[] newValue = new String[1];

        register.registerConfigChangeWatcher(new ConfigChangeWatcher("MockModule", new MockProvider(), "prop2") {
            @Override
            public void notify(ConfigChangeEvent value) {
                newValue[0] = value.getNewValue();
            }

            @Override
            public String value() {
                return null;
            }
        });

        register.configSync();

        Assert.assertEquals("abc2", newValue[0]);
    }

    @Test
    public void testRegisterTableLog() {
        register.registerConfigChangeWatcher(new ConfigChangeWatcher("MockModule", new MockProvider(), "prop2") {
            @Override
            public void notify(ConfigChangeEvent value) {
            }

            @Override
            public String value() {
                return null;
            }
        });

        register.configSync();
        ConfigWatcherRegister.Register registerTable = Whitebox.getInternalState(this.register, "register");

        String expected = "Following dynamic config items are available." + ConfigWatcherRegister.LINE_SEPARATOR + "---------------------------------------------" + ConfigWatcherRegister.LINE_SEPARATOR + "key:MockModule.provider.prop2    module:MockModule    provider:provider    value(current):null" + ConfigWatcherRegister.LINE_SEPARATOR;

        Assert.assertEquals(expected, registerTable.toString());
    }

    public static class MockConfigWatcherRegister extends ConfigWatcherRegister {

        @Override
        public Optional<ConfigTable> readConfig(Set<String> keys) {
            ConfigTable.ConfigItem item1 = new ConfigTable.ConfigItem("module.provider.prop1", "abc");
            ConfigTable.ConfigItem item2 = new ConfigTable.ConfigItem("MockModule.provider.prop2", "abc2");

            ConfigTable table = new ConfigTable();
            table.add(item1);
            table.add(item2);
            return Optional.of(table);
        }
    }

    public static class MockModule extends ModuleDefine {

        public MockModule() {
            super("MockModule");
        }

        @Override
        public Class[] services() {
            return new Class[0];
        }
    }

    public static class MockProvider extends ModuleProvider {

        @Override
        public String name() {
            return "provider";
        }

        @Override
        public Class<? extends ModuleDefine> module() {
            return MockModule.class;
        }

        @Override
        public ModuleConfig createConfigBeanIfAbsent() {
            return null;
        }

        @Override
        public void prepare() throws ServiceNotProvidedException, ModuleStartException {

        }

        @Override
        public void start() throws ServiceNotProvidedException, ModuleStartException {

        }

        @Override
        public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

        }

        @Override
        public String[] requiredModules() {
            return new String[0];
        }
    }
}
