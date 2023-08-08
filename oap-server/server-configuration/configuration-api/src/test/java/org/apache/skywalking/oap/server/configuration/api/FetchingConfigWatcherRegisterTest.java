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

import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.powermock.reflect.Whitebox;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FetchingConfigWatcherRegisterTest {
    private FetchingConfigWatcherRegister register;

    @BeforeEach
    public void setup() {
        register = new MockConfigWatcherRegister();
    }

    @AfterEach
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

        assertEquals("abc2", newValue[0]);
    }

    @Test
    public void testGroupConfInit() {
        final Map<String, String> config = new ConcurrentHashMap<>();

        register.registerConfigChangeWatcher(new GroupConfigChangeWatcher("MockModule", new MockProvider(), "groupItems1") {
            @Override
            public void notifyGroup(Map<String , ConfigChangeEvent> groupItems) {
                groupItems.forEach((groupItemName , event) -> {
                    config.put(groupItemName, event.getNewValue());
                });
            }

            @Override
            public Map<String, String> groupItems() {
                return config;
            }
        });

        register.configSync();

        assertEquals("abc", config.get("item1"));
        assertEquals("abc2", config.get("item2"));
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

        register.registerConfigChangeWatcher(new GroupConfigChangeWatcher("MockModule", new MockProvider(), "groupItems1") {
            @Override
            public Map<String, String> groupItems() {
                return null;
            }

            @Override
            public void notifyGroup(final Map<String, ConfigChangeEvent> groupItems) {

            }
        });

        register.configSync();
        FetchingConfigWatcherRegister.Register registerTable = Whitebox.getInternalState(this.register, "singleConfigChangeWatcherRegister");
        FetchingConfigWatcherRegister.Register groupRegisterTable = Whitebox.getInternalState(this.register, "groupConfigChangeWatcherRegister");

        String expected = "Following dynamic config items are available." + FetchingConfigWatcherRegister.LINE_SEPARATOR + "---------------------------------------------" + FetchingConfigWatcherRegister.LINE_SEPARATOR + "key:MockModule.provider.prop2    module:MockModule    provider:provider    value(current):null" + FetchingConfigWatcherRegister.LINE_SEPARATOR;
        String groupConfigExpected = "Following dynamic config items are available." + FetchingConfigWatcherRegister.LINE_SEPARATOR + "---------------------------------------------" + FetchingConfigWatcherRegister.LINE_SEPARATOR + "key:MockModule.provider.groupItems1    module:MockModule    provider:provider    groupItems(current):null" + FetchingConfigWatcherRegister.LINE_SEPARATOR;

        assertEquals(expected, registerTable.toString());
        assertEquals(groupConfigExpected, groupRegisterTable.toString());
    }

    public static class MockConfigWatcherRegister extends FetchingConfigWatcherRegister {

        @Override
        public Optional<ConfigTable> readConfig(Set<String> keys) {
            ConfigTable.ConfigItem item1 = new ConfigTable.ConfigItem("MockModule.provider.prop1", "abc");
            ConfigTable.ConfigItem item2 = new ConfigTable.ConfigItem("MockModule.provider.prop2", "abc2");

            ConfigTable table = new ConfigTable();
            table.add(item1);
            table.add(item2);
            return Optional.of(table);
        }

        @Override
        public Optional<GroupConfigTable> readGroupConfig(Set<String> keys) {
            ConfigTable.ConfigItem item1 = new ConfigTable.ConfigItem("item1", "abc");
            ConfigTable.ConfigItem item2 = new ConfigTable.ConfigItem("item2", "abc2");
            ConfigTable.ConfigItem item3 = new ConfigTable.ConfigItem("item3", "abc3");
            GroupConfigTable.GroupConfigItems groupConfigItems1 = new GroupConfigTable.GroupConfigItems("MockModule.provider.groupItems1");
            GroupConfigTable.GroupConfigItems groupConfigItems2 = new GroupConfigTable.GroupConfigItems("MockModule.provider.groupItems2");
            groupConfigItems1.add(item1);
            groupConfigItems1.add(item2);
            groupConfigItems2.add(item3);

            GroupConfigTable table = new GroupConfigTable();
            table.addGroupConfigItems(groupConfigItems1);
            table.addGroupConfigItems(groupConfigItems2);
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
        public ConfigCreator newConfigCreator() {
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
