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

import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class TraceSamplingPolicyWatcherTest {

    private AnalyzerModuleProvider provider;
    private AnalyzerModuleConfig moduleConfig;

    @Before
    public void init() {
        provider = new AnalyzerModuleProvider();
        moduleConfig = new AnalyzerModuleConfig();
        moduleConfig.setTraceSampleRateSettingFile("trace-sample-rate-setting.yml");
    }

    @Test
    public void testStaticConfigInit() {
        TraceSamplingPolicyWatcher traceSampleRateWatcher = new TraceSamplingPolicyWatcher(moduleConfig, provider);
        Assert.assertEquals(traceSampleRateWatcher.getSampleRate(), 10000);
    }

    @Test(timeout = 20000)
    public void testTraceLatencyThresholdDynamicUpdate() throws InterruptedException {
        ConfigWatcherRegister register = new TraceLatencyThresholdMockConfigWatcherRegister(3);

        TraceSamplingPolicyWatcher watcher = new TraceSamplingPolicyWatcher(moduleConfig, provider);
        register.registerConfigChangeWatcher(watcher);
        register.start();

        while (watcher.getSlowTraceSegmentThreshold() == -1) {
            Thread.sleep(2000);
        }

        assertThat(watcher.getSlowTraceSegmentThreshold(), is(3000));
        assertThat(provider.getModuleConfig().getSlowTraceSegmentThreshold(), is(-1));
    }

    @Test
    public void testTraceLatencyThresholdNotify() {
        TraceSamplingPolicyWatcher traceLatencyThresholdsAndWatcher = new TraceSamplingPolicyWatcher(moduleConfig, provider);
        ConfigChangeWatcher.ConfigChangeEvent value1 = new ConfigChangeWatcher.ConfigChangeEvent(
                "default:\n" +
                        "  duration: 8000", ConfigChangeWatcher.EventType.MODIFY);

        traceLatencyThresholdsAndWatcher.notify(value1);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.getSlowTraceSegmentThreshold(), 8000);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.value(), "default:\n" +
                "  duration: 8000");

        ConfigChangeWatcher.ConfigChangeEvent value2 = new ConfigChangeWatcher.ConfigChangeEvent(
                "default:\n" +
                        "  duration: 8000", ConfigChangeWatcher.EventType.DELETE);

        traceLatencyThresholdsAndWatcher.notify(value2);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.getSlowTraceSegmentThreshold(), -1);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.value(), "");

        ConfigChangeWatcher.ConfigChangeEvent value3 = new ConfigChangeWatcher.ConfigChangeEvent(
                "default:\n" +
                        "  duration: 800", ConfigChangeWatcher.EventType.ADD);

        traceLatencyThresholdsAndWatcher.notify(value3);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.getSlowTraceSegmentThreshold(), 800);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.value(), "default:\n" +
                "  duration: 800");

        ConfigChangeWatcher.ConfigChangeEvent value4 = new ConfigChangeWatcher.ConfigChangeEvent(
                "default:\n" +
                        "  duration: abc", ConfigChangeWatcher.EventType.MODIFY);

        traceLatencyThresholdsAndWatcher.notify(value4);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.getSlowTraceSegmentThreshold(), 800);
        Assert.assertEquals(traceLatencyThresholdsAndWatcher.value(), "default:\n" +
                "  duration: 800");
    }

    public static class TraceLatencyThresholdMockConfigWatcherRegister extends ConfigWatcherRegister {

        public TraceLatencyThresholdMockConfigWatcherRegister(long syncPeriod) {
            super(syncPeriod);
        }

        @Override
        public Optional<ConfigTable> readConfig(Set<String> keys) {
            ConfigTable table = new ConfigTable();
            table.add(new ConfigTable.ConfigItem("agent-analyzer.default.traceSamplingPolicy", "default:\n" +
                    "  duration: 3000"));
            return Optional.of(table);
        }

        @Override
        public Optional<GroupConfigTable> readGroupConfig(final Set<String> keys) {
            return Optional.empty();
        }
    }

    @Test(timeout = 20000)
    public void testDefaultSampleRateDynamicUpdate() throws InterruptedException {
        ConfigWatcherRegister register = new DefaultSampleRateMockConfigWatcherRegister(3);

        TraceSamplingPolicyWatcher watcher = new TraceSamplingPolicyWatcher(moduleConfig, provider);
        register.registerConfigChangeWatcher(watcher);
        register.start();

        while (watcher.getSampleRate() == 10000) {
            Thread.sleep(2000);
        }

        assertThat(watcher.getSampleRate(), is(9000));
        assertThat(provider.getModuleConfig().getSampleRate(), is(10000));
    }

    @Test
    public void testDefaultSampleRateNotify() {
        TraceSamplingPolicyWatcher traceSampleRateWatcher = new TraceSamplingPolicyWatcher(moduleConfig, provider);
        ConfigChangeWatcher.ConfigChangeEvent value1 = new ConfigChangeWatcher.ConfigChangeEvent(
                "default:\n" +
                        "  rate: 8000", ConfigChangeWatcher.EventType.MODIFY);

        traceSampleRateWatcher.notify(value1);
        Assert.assertEquals(traceSampleRateWatcher.getSampleRate(), 8000);
        Assert.assertEquals(traceSampleRateWatcher.value(), "default:\n" +
                "  rate: 8000");

        ConfigChangeWatcher.ConfigChangeEvent value2 = new ConfigChangeWatcher.ConfigChangeEvent(
                "default:\n" +
                        "  rate: 1000", ConfigChangeWatcher.EventType.DELETE);

        traceSampleRateWatcher.notify(value2);
        Assert.assertEquals(traceSampleRateWatcher.getSampleRate(), 10000);
        Assert.assertEquals(traceSampleRateWatcher.value(), "");

        ConfigChangeWatcher.ConfigChangeEvent value3 = new ConfigChangeWatcher.ConfigChangeEvent(
                "default:\n" +
                        "  rate: 500", ConfigChangeWatcher.EventType.ADD);

        traceSampleRateWatcher.notify(value3);
        Assert.assertEquals(traceSampleRateWatcher.getSampleRate(), 500);
        Assert.assertEquals(traceSampleRateWatcher.value(), "default:\n" +
                "  rate: 500");

        ConfigChangeWatcher.ConfigChangeEvent value4 = new ConfigChangeWatcher.ConfigChangeEvent(
                "default:\n" +
                        "  rate: abc", ConfigChangeWatcher.EventType.MODIFY);

        traceSampleRateWatcher.notify(value4);
        Assert.assertEquals(traceSampleRateWatcher.getSampleRate(), 500);
        Assert.assertEquals(traceSampleRateWatcher.value(), "default:\n" +
                "  rate: 500");
    }

    public static class DefaultSampleRateMockConfigWatcherRegister extends ConfigWatcherRegister {

        public DefaultSampleRateMockConfigWatcherRegister(long syncPeriod) {
            super(syncPeriod);
        }

        @Override
        public Optional<ConfigTable> readConfig(Set<String> keys) {
            ConfigTable table = new ConfigTable();
            table.add(new ConfigTable.ConfigItem("agent-analyzer.default.traceSamplingPolicy", "default:\n" +
                    "  rate: 9000"));
            return Optional.of(table);
        }

        @Override
        public Optional<GroupConfigTable> readGroupConfig(final Set<String> keys) {
            return Optional.empty();
        }
    }

    @Test(timeout = 20000)
    public void testServiceSampleRateDynamicUpdate() throws InterruptedException {
        ConfigWatcherRegister register = new ServiceMockConfigWatcherRegister(3);

        TraceSamplingPolicyWatcher watcher = new TraceSamplingPolicyWatcher(moduleConfig, provider);
        provider.getModuleConfig().setTraceSamplingPolicyWatcher(watcher);
        register.registerConfigChangeWatcher(watcher);
        register.start();

        while (watcher.getSample("serverName1") == null) {
            Thread.sleep(1000);
        }

        TraceSamplingPolicyWatcher.SampleConfig serviceInfo = watcher.getSample("serverName1");
        Assert.assertEquals(serviceInfo.getRate().intValue(), 2000);
        Assert.assertEquals(serviceInfo.getDuration().intValue(), 30000);
        Assert.assertEquals(provider.getModuleConfig().getTraceSamplingPolicyWatcher().getSample("serverName1").getRate().intValue(), 2000);
    }

    @Test
    public void testServiceSampleRateNotify() {
        TraceSamplingPolicyWatcher watcher = new TraceSamplingPolicyWatcher(moduleConfig, provider);
        ConfigChangeWatcher.ConfigChangeEvent value1 = new ConfigChangeWatcher.ConfigChangeEvent(
                "services:\n" +
                        "  - name: serverName1\n" +
                        "    rate: 8000\n" +
                        "    duration: 20000", ConfigChangeWatcher.EventType.MODIFY);

        watcher.notify(value1);
        Assert.assertEquals(watcher.getSample("serverName1").getRate().intValue(), 8000);
        Assert.assertEquals(watcher.getSample("serverName1").getDuration().intValue(), 20000);
        Assert.assertEquals(watcher.value(), "services:\n" +
                "  - name: serverName1\n" +
                "    rate: 8000\n" +
                "    duration: 20000");

        ConfigChangeWatcher.ConfigChangeEvent value2 = new ConfigChangeWatcher.ConfigChangeEvent(
                "", ConfigChangeWatcher.EventType.DELETE);

        watcher.notify(value2);

        Assert.assertNull(watcher.getSample("serverName1"));
        Assert.assertEquals(watcher.value(), "");

        ConfigChangeWatcher.ConfigChangeEvent value3 = new ConfigChangeWatcher.ConfigChangeEvent(
                "services:\n" +
                        "  - name: serverName1\n" +
                        "    rate: 8000\n" +
                        "    duration: 20000", ConfigChangeWatcher.EventType.ADD);

        watcher.notify(value3);
        Assert.assertEquals(watcher.getSample("serverName1").getRate().intValue(), 8000);
        Assert.assertEquals(watcher.getSample("serverName1").getDuration().intValue(), 20000);
        Assert.assertEquals(watcher.value(), "services:\n" +
                "  - name: serverName1\n" +
                "    rate: 8000\n" +
                "    duration: 20000");

        ConfigChangeWatcher.ConfigChangeEvent value4 = new ConfigChangeWatcher.ConfigChangeEvent(
                "services:\n" +
                        "  - name: serverName1\n" +
                        "    rate: 9000\n" +
                        "    duration: 30000", ConfigChangeWatcher.EventType.MODIFY);

        watcher.notify(value4);
        Assert.assertEquals(watcher.getSample("serverName1").getRate().intValue(), 9000);
        Assert.assertEquals(watcher.getSample("serverName1").getDuration().intValue(), 30000);
        Assert.assertEquals(watcher.value(), "services:\n" +
                "  - name: serverName1\n" +
                "    rate: 9000\n" +
                "    duration: 30000");

    }

    public static class ServiceMockConfigWatcherRegister extends ConfigWatcherRegister {

        public ServiceMockConfigWatcherRegister(long syncPeriod) {
            super(syncPeriod);
        }

        @Override
        public Optional<ConfigTable> readConfig(Set<String> keys) {
            ConfigTable table = new ConfigTable();
            table.add(new ConfigTable.ConfigItem("agent-analyzer.default.traceSamplingPolicy", "services:\n" +
                    "  - name: serverName1\n" +
                    "    rate: 2000\n" +
                    "    duration: 30000"));
            return Optional.of(table);
        }

        @Override
        public Optional<GroupConfigTable> readGroupConfig(final Set<String> keys) {
            return Optional.empty();
        }
    }
}
