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
public class TraceIgnorePathWatcherTest {
    private AnalyzerModuleProvider provider;

    @Before
    public void init() {
        provider = new AnalyzerModuleProvider();
    }

    @Test
    public void testInit() {
        TraceIgnorePathWatcher traceIgnorePathWatcher = new TraceIgnorePathWatcher(provider);
        Assert.assertEquals(traceIgnorePathWatcher.getTraceIgnorePathPatterns(), "");
        Assert.assertEquals(traceIgnorePathWatcher.value(), "");
    }

    @Test(timeout = 20000)
    public void testDynamicUpdate() throws InterruptedException {
        ConfigWatcherRegister register = new MockConfigWatcherRegister(3);

        TraceIgnorePathWatcher watcher = new TraceIgnorePathWatcher(provider);
        register.registerConfigChangeWatcher(watcher);
        register.start();

        while (watcher.getTraceIgnorePathPatterns().equals("")) {
            Thread.sleep(2000);
        }
        assertThat(watcher.getTraceIgnorePathPatterns(), is("/ignore/path/**"));
        assertThat(provider.getModuleConfig().getTraceIgnorePathPatterns(), is(""));
    }

    @Test
    public void testNotify() {
        TraceIgnorePathWatcher traceIgnorePathWatcher = new TraceIgnorePathWatcher(provider);
        ConfigChangeWatcher.ConfigChangeEvent value1 = new ConfigChangeWatcher.ConfigChangeEvent(
            "/ignore/path2/**", ConfigChangeWatcher.EventType.MODIFY);

        traceIgnorePathWatcher.notify(value1);
        Assert.assertEquals(traceIgnorePathWatcher.getTraceIgnorePathPatterns(), "/ignore/path2/**");
        Assert.assertEquals(traceIgnorePathWatcher.value(), "/ignore/path2/**");

        ConfigChangeWatcher.ConfigChangeEvent value2 = new ConfigChangeWatcher.ConfigChangeEvent(
            "/ignore/path2/**", ConfigChangeWatcher.EventType.DELETE);

        traceIgnorePathWatcher.notify(value2);
        Assert.assertEquals(traceIgnorePathWatcher.getTraceIgnorePathPatterns(), "");
        Assert.assertEquals(traceIgnorePathWatcher.value(), "");

        ConfigChangeWatcher.ConfigChangeEvent value3 = new ConfigChangeWatcher.ConfigChangeEvent(
            "/ignore/path3/**", ConfigChangeWatcher.EventType.ADD);

        traceIgnorePathWatcher.notify(value3);
        Assert.assertEquals(traceIgnorePathWatcher.getTraceIgnorePathPatterns(), "/ignore/path3/**");
        Assert.assertEquals(traceIgnorePathWatcher.value(), "/ignore/path3/**");

        ConfigChangeWatcher.ConfigChangeEvent value4 = new ConfigChangeWatcher.ConfigChangeEvent(
            "/ignore/path4/**", ConfigChangeWatcher.EventType.MODIFY);

        traceIgnorePathWatcher.notify(value4);
        Assert.assertEquals(traceIgnorePathWatcher.getTraceIgnorePathPatterns(), "/ignore/path4/**");
        Assert.assertEquals(traceIgnorePathWatcher.value(), "/ignore/path4/**");
    }

    public static class MockConfigWatcherRegister extends ConfigWatcherRegister {

        public MockConfigWatcherRegister(long syncPeriod) {
            super(syncPeriod);
        }

        @Override
        public Optional<ConfigTable> readConfig(Set<String> keys) {
            ConfigTable table = new ConfigTable();
            table.add(new ConfigTable.ConfigItem("agent-analyzer.default.traceIgnorePathPatterns", "/ignore/path/**"));
            return Optional.of(table);
        }
    }

}
