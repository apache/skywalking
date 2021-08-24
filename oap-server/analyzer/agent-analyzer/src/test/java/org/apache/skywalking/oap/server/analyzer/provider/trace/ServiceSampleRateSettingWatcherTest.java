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

package org.apache.skywalking.oap.server.analyzer.provider.trace;

import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleProvider;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.ConfigWatcherRegister;
import org.apache.skywalking.oap.server.configuration.api.GroupConfigTable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class ServiceSampleRateSettingWatcherTest {

    private AnalyzerModuleProvider provider;

    @Before
    public void init() {
        provider = new AnalyzerModuleProvider();
    }

    @Test(timeout = 20000)
    public void testDynamicUpdate() throws InterruptedException {
        ConfigWatcherRegister register = new MockConfigWatcherRegister(3);

        TraceSampleRateSettingWatcher watcher = new TraceSampleRateSettingWatcher("trace-sample-rate-setting.yml", provider);
        provider.getModuleConfig().setTraceSampleRateSettingWatcher(watcher);
        register.registerConfigChangeWatcher(watcher);
        register.start();

        while (watcher.getSample("serverName1") == null) {
            Thread.sleep(1000);
        }

        TraceSampleRateSettingWatcher.ServiceSampleConfig serviceInfo = watcher.getSample("serverName1");
        Assert.assertEquals(serviceInfo.getSampleRate().get().intValue(), 2000);
        Assert.assertEquals(serviceInfo.getDuration().get().intValue(), 30000);
        Assert.assertEquals(provider.getModuleConfig().getTraceSampleRateSettingWatcher().getSample("serverName1").getSampleRate().get().intValue(), 2000);
    }

    @Test
    public void testNotify() {
        TraceSampleRateSettingWatcher watcher = new TraceSampleRateSettingWatcher("trace-sample-rate-setting.yml", provider);
        ConfigChangeWatcher.ConfigChangeEvent value1 = new ConfigChangeWatcher.ConfigChangeEvent(
                "services:\n" +
                        "  - name: serverName1\n" +
                        "    sampleRate: 8000\n" +
                        "    duration: 20000", ConfigChangeWatcher.EventType.MODIFY);

        watcher.notify(value1);
        Assert.assertEquals(watcher.getSample("serverName1").getSampleRate().get().intValue(), 8000);
        Assert.assertEquals(watcher.getSample("serverName1").getDuration().get().intValue(), 20000);
        Assert.assertEquals(watcher.value(), "services:\n" +
                "  - name: serverName1\n" +
                "    sampleRate: 8000\n" +
                "    duration: 20000");

        ConfigChangeWatcher.ConfigChangeEvent value2 = new ConfigChangeWatcher.ConfigChangeEvent(
                "", ConfigChangeWatcher.EventType.DELETE);

        watcher.notify(value2);

        Assert.assertNull(watcher.getSample("serverName1"));
        Assert.assertEquals(watcher.value(), "");

        ConfigChangeWatcher.ConfigChangeEvent value3 = new ConfigChangeWatcher.ConfigChangeEvent(
                "services:\n" +
                        "  - name: serverName1\n" +
                        "    sampleRate: 8000\n" +
                        "    duration: 20000", ConfigChangeWatcher.EventType.ADD);

        watcher.notify(value3);
        Assert.assertEquals(watcher.getSample("serverName1").getSampleRate().get().intValue(), 8000);
        Assert.assertEquals(watcher.getSample("serverName1").getDuration().get().intValue(), 20000);
        Assert.assertEquals(watcher.value(), "services:\n" +
                "  - name: serverName1\n" +
                "    sampleRate: 8000\n" +
                "    duration: 20000");

        ConfigChangeWatcher.ConfigChangeEvent value4 = new ConfigChangeWatcher.ConfigChangeEvent(
                "services:\n" +
                        "  - name: serverName1\n" +
                        "    sampleRate: 9000\n" +
                        "    duration: 30000", ConfigChangeWatcher.EventType.MODIFY);

        watcher.notify(value4);
        Assert.assertEquals(watcher.getSample("serverName1").getSampleRate().get().intValue(), 9000);
        Assert.assertEquals(watcher.getSample("serverName1").getDuration().get().intValue(), 30000);
        Assert.assertEquals(watcher.value(), "services:\n" +
                "  - name: serverName1\n" +
                "    sampleRate: 9000\n" +
                "    duration: 30000");

    }

    public static class MockConfigWatcherRegister extends ConfigWatcherRegister {

        public MockConfigWatcherRegister(long syncPeriod) {
            super(syncPeriod);
        }

        @Override
        public Optional<ConfigTable> readConfig(Set<String> keys) {
            ConfigTable table = new ConfigTable();
            table.add(new ConfigTable.ConfigItem("agent-analyzer.default.traceSampleRateSetting", "services:\n" +
                    "  - name: serverName1\n" +
                    "    sampleRate: 2000\n" +
                    "    duration: 30000"));
            return Optional.of(table);
        }

        @Override
        public Optional<GroupConfigTable> readGroupConfig(final Set<String> keys) {
            return Optional.empty();
        }
    }
}
