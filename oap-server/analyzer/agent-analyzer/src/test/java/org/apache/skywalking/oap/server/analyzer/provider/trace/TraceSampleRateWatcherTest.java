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
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Optional;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class TraceSampleRateWatcherTest {
    private AnalyzerModuleProvider provider;

    @Before
    public void init() {
        provider = new AnalyzerModuleProvider();
    }

    @Test
    public void testInit() {
        TraceSampleRateWatcher traceSampleRateWatcher = new TraceSampleRateWatcher(provider);
        Assert.assertEquals(traceSampleRateWatcher.getSampleRate(), 10000);
        Assert.assertEquals(traceSampleRateWatcher.value(), "10000");
    }

    @Test(timeout = 20000)
    public void testDynamicUpdate() throws InterruptedException {
        ConfigWatcherRegister register = new MockConfigWatcherRegister(3);

        TraceSampleRateWatcher watcher = new TraceSampleRateWatcher(provider);
        register.registerConfigChangeWatcher(watcher);
        register.start();

        while (watcher.getSampleRate() == 10000) {
            Thread.sleep(2000);
        }
        assertThat(watcher.getSampleRate(), is(9000));
        assertThat(provider.getModuleConfig().getSampleRate(), is(10000));
    }

    @Test
    public void testNotify() {
        TraceSampleRateWatcher traceSampleRateWatcher = new TraceSampleRateWatcher(provider);
        ConfigChangeWatcher.ConfigChangeEvent value1 = new ConfigChangeWatcher.ConfigChangeEvent(
            "8000", ConfigChangeWatcher.EventType.MODIFY);

        traceSampleRateWatcher.notify(value1);
        Assert.assertEquals(traceSampleRateWatcher.getSampleRate(), 8000);
        Assert.assertEquals(traceSampleRateWatcher.value(), "8000");

        ConfigChangeWatcher.ConfigChangeEvent value2 = new ConfigChangeWatcher.ConfigChangeEvent(
            "8000", ConfigChangeWatcher.EventType.DELETE);

        traceSampleRateWatcher.notify(value2);
        Assert.assertEquals(traceSampleRateWatcher.getSampleRate(), 10000);
        Assert.assertEquals(traceSampleRateWatcher.value(), "10000");

        ConfigChangeWatcher.ConfigChangeEvent value3 = new ConfigChangeWatcher.ConfigChangeEvent(
            "500", ConfigChangeWatcher.EventType.ADD);

        traceSampleRateWatcher.notify(value3);
        Assert.assertEquals(traceSampleRateWatcher.getSampleRate(), 500);
        Assert.assertEquals(traceSampleRateWatcher.value(), "500");

        ConfigChangeWatcher.ConfigChangeEvent value4 = new ConfigChangeWatcher.ConfigChangeEvent(
            "abc", ConfigChangeWatcher.EventType.MODIFY);

        traceSampleRateWatcher.notify(value4);
        Assert.assertEquals(traceSampleRateWatcher.getSampleRate(), 500);
        Assert.assertEquals(traceSampleRateWatcher.value(), "500");
    }

    public static class MockConfigWatcherRegister extends ConfigWatcherRegister {

        public MockConfigWatcherRegister(long syncPeriod) {
            super(syncPeriod);
        }

        @Override
        public Optional<ConfigTable> readConfig(Set<String> keys) {
            ConfigTable table = new ConfigTable();
            table.add(new ConfigTable.ConfigItem("agent-analyzer.default.sampleRate", "9000"));
            return Optional.of(table);
        }
    }

}
