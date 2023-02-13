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

package org.apache.skywalking.oap.server.core.analysis;

import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.ConfigWatcherRegister;
import org.apache.skywalking.oap.server.configuration.api.GroupConfigTable;
import org.apache.skywalking.oap.server.core.CoreModuleProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApdexThresholdConfigTest {

    @Mock
    private CoreModuleProvider provider;

    @Test
    public void testLookupOfBeforeInit() {
        ApdexThresholdConfig config = new ApdexThresholdConfig(provider);
        assertThat(config.lookup("foo")).isEqualTo(500);
        assertThat(config.lookup("default")).isEqualTo(500);
        assertThat(config.lookup("bar")).isEqualTo(500);
    }

    @Test
    @Timeout(20)
    public void testLookupOfDynamicUpdate() throws InterruptedException {
        ConfigWatcherRegister register = new MockConfigWatcherRegister(3);
        when(provider.name()).thenReturn("default");
        ApdexThresholdConfig config = new ApdexThresholdConfig(provider);
        register.registerConfigChangeWatcher(config);
        register.start();

        while (config.lookup("foo").intValue() == 500) {
            Thread.sleep(2000);
        }
        assertThat(config.lookup("foo")).isEqualTo(200);
        assertThat(config.lookup("default")).isEqualTo(1000);
        assertThat(config.lookup("bar")).isEqualTo(1000);
    }

    public static class MockConfigWatcherRegister extends ConfigWatcherRegister {

        public MockConfigWatcherRegister(long syncPeriod) {
            super(syncPeriod);
        }

        @Override
        public Optional<ConfigTable> readConfig(Set<String> keys) {
            ConfigTable table = new ConfigTable();
            table.add(new ConfigTable.ConfigItem("core.default.apdexThreshold", "default: 1000 \nfoo: 200"));
            return Optional.of(table);
        }

        @Override
        public Optional<GroupConfigTable> readGroupConfig(final Set<String> keys) {
            return Optional.empty();
        }
    }
}
