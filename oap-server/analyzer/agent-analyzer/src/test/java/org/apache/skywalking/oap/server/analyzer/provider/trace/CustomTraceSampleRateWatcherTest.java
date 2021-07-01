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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.util.Optional;
import java.util.Set;

public class CustomTraceSampleRateWatcherTest {

    private AnalyzerModuleProvider provider;

    @Before
    public void init() {
        provider = new AnalyzerModuleProvider();
    }

    @Test
    public void testInit() throws Exception {
        CustomTraceSampleRateWatcher customTraceSampleRateWatcher = new CustomTraceSampleRateWatcher(provider);
        CustomTraceSampleRateWatcher.ServiceInfos serviceInfos
                = Whitebox.invokeMethod(customTraceSampleRateWatcher, "parseFromFile", "custom-trace-sample-rate.yml");
        Assert.assertEquals(1, serviceInfos.getServices().size());

    }

    @Test(timeout = 20000)
    public void testDynamicUpdate() throws InterruptedException {
        ConfigWatcherRegister register = new CustomTraceSampleRateWatcherTest.MockConfigWatcherRegister(3);

        CustomTraceSampleRateWatcher watcher = new CustomTraceSampleRateWatcher(provider);
        provider.getModuleConfig().setCustomTraceSampleRateWatcher(watcher);
        register.registerConfigChangeWatcher(watcher);
        register.start();

        while (watcher.getSample("serverName1") != null) {
            Thread.sleep(2000);
        }
        // Let object to init finished; If not it will be null.
        Thread.sleep(1000);

        CustomTraceSampleRateWatcher.ServiceInfo serviceInfo = watcher.getSample("serverName1");
        Assert.assertEquals(serviceInfo.getSampleRate().get().intValue(), 2000);
        Assert.assertEquals(serviceInfo.getDuration().get().intValue(), 20000);
        Assert.assertEquals(provider.getModuleConfig().getCustomTraceSampleRateWatcher().getSample("serverName1").getSampleRate().get().intValue(), 2000);
    }

    @Test
    public void testNotify() {
        CustomTraceSampleRateWatcher watcher = new CustomTraceSampleRateWatcher(provider);
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
            table.add(new ConfigTable.ConfigItem("agent-analyzer.default.custom-trace-sample-rate", "services:\n" +
                    "  - name: serverName1\n" +
                    "    sampleRate: 2000\n" +
                    "    duration: 20000"));
            return Optional.of(table);
        }
    }
}
