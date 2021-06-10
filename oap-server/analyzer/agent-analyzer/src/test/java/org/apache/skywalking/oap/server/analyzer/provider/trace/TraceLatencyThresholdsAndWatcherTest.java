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

import java.util.Optional;
import java.util.Set;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleProvider;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.ConfigWatcherRegister;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class TraceLatencyThresholdsAndWatcherTest {
    private AnalyzerModuleProvider provider;

    @Before
    public void init() {
        provider = new AnalyzerModuleProvider();
    }

    @Test
    public void testInit() {
        TraceLatencyThresholdsAndWatcher traceLatencyThresholdsAndWatcher = new TraceLatencyThresholdsAndWatcher(provider);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.getSlowTraceSegmentThreshold(), -1);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.value(), "-1");
    }

    @Test(timeout = 20000)
    public void testDynamicUpdate() throws InterruptedException {
        ConfigWatcherRegister register = new MockConfigWatcherRegister(3);

        TraceLatencyThresholdsAndWatcher watcher = new TraceLatencyThresholdsAndWatcher(provider);
        register.registerConfigChangeWatcher(watcher);
        register.start();

        while (watcher.getSlowTraceSegmentThreshold() < 0) {
            Thread.sleep(2000);
        }
        assertThat(watcher.getSlowTraceSegmentThreshold(), is(3000));
        assertThat(provider.getModuleConfig().getSlowTraceSegmentThreshold(), is(-1));
    }

    @Test
    public void testNotify() {
        TraceLatencyThresholdsAndWatcher traceLatencyThresholdsAndWatcher = new TraceLatencyThresholdsAndWatcher(provider);
        ConfigChangeWatcher.ConfigChangeEvent value1 = new ConfigChangeWatcher.ConfigChangeEvent(
            "8000", ConfigChangeWatcher.EventType.MODIFY);

        traceLatencyThresholdsAndWatcher.notify(value1);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.getSlowTraceSegmentThreshold(), 8000);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.value(), "8000");

        ConfigChangeWatcher.ConfigChangeEvent value2 = new ConfigChangeWatcher.ConfigChangeEvent(
            "8000", ConfigChangeWatcher.EventType.DELETE);

        traceLatencyThresholdsAndWatcher.notify(value2);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.getSlowTraceSegmentThreshold(), -1);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.value(), "-1");

        ConfigChangeWatcher.ConfigChangeEvent value3 = new ConfigChangeWatcher.ConfigChangeEvent(
            "800", ConfigChangeWatcher.EventType.ADD);

        traceLatencyThresholdsAndWatcher.notify(value3);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.getSlowTraceSegmentThreshold(), 800);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.value(), "800");

        ConfigChangeWatcher.ConfigChangeEvent value4 = new ConfigChangeWatcher.ConfigChangeEvent(
            "abc", ConfigChangeWatcher.EventType.MODIFY);

        traceLatencyThresholdsAndWatcher.notify(value4);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.getSlowTraceSegmentThreshold(), 800);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.value(), "800");
    }

    public static class MockConfigWatcherRegister extends ConfigWatcherRegister {

        public MockConfigWatcherRegister(long syncPeriod) {
            super(syncPeriod);
        }

        @Override
        public Optional<ConfigTable> readConfig(Set<String> keys) {
            ConfigTable table = new ConfigTable();
            table.add(new ConfigTable.ConfigItem("agent-analyzer.default.slowTraceSegmentThreshold", "3000"));
            return Optional.of(table);
        }
    }

}
