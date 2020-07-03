package org.apache.skywalking.oap.server.receiver.trace.provider;

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
    private TraceModuleProvider traceModuleProvider;

    @Before
    public void init(){
        traceModuleProvider = new TraceModuleProvider();
    }

    @Test
    public void TestInit() {
        TraceSampleRateWatcher traceSampleRateWatcher = new TraceSampleRateWatcher(traceModuleProvider);
        Assert.assertEquals(traceSampleRateWatcher.getSampleRate(), 10000);
        Assert.assertEquals(traceSampleRateWatcher.value(), "10000");
    }

    @Test(timeout = 20000)
    public void testDynamicUpdate() throws InterruptedException {
        ConfigWatcherRegister register = new MockConfigWatcherRegister(3);

        TraceSampleRateWatcher watcher = new TraceSampleRateWatcher(traceModuleProvider);
        register.registerConfigChangeWatcher(watcher);
        register.start();

        while (watcher.getSampleRate() == 10000) {
            Thread.sleep(2000);
        }
        assertThat(watcher.getSampleRate(), is(9000));
    }

    @Test
    public void TestNotify() {
        TraceSampleRateWatcher traceSampleRateWatcher = new TraceSampleRateWatcher(traceModuleProvider);
        ConfigChangeWatcher.ConfigChangeEvent value1 = new ConfigChangeWatcher.ConfigChangeEvent("8000", ConfigChangeWatcher.EventType.MODIFY);

        traceSampleRateWatcher.notify(value1);
        Assert.assertEquals(traceSampleRateWatcher.getSampleRate(), 8000);
        Assert.assertEquals(traceSampleRateWatcher.value(),"8000");

        ConfigChangeWatcher.ConfigChangeEvent value2 = new ConfigChangeWatcher.ConfigChangeEvent("8000", ConfigChangeWatcher.EventType.DELETE);

        traceSampleRateWatcher.notify(value2);
        Assert.assertEquals(traceSampleRateWatcher.getSampleRate(), 10000);
        Assert.assertEquals(traceSampleRateWatcher.value(),"10000");

        ConfigChangeWatcher.ConfigChangeEvent value3 = new ConfigChangeWatcher.ConfigChangeEvent("500", ConfigChangeWatcher.EventType.ADD);

        traceSampleRateWatcher.notify(value3);
        Assert.assertEquals(traceSampleRateWatcher.getSampleRate(), 500);
        Assert.assertEquals(traceSampleRateWatcher.value(),"500");

        ConfigChangeWatcher.ConfigChangeEvent value4 = new ConfigChangeWatcher.ConfigChangeEvent("abc", ConfigChangeWatcher.EventType.ADD);

        traceSampleRateWatcher.notify(value4);
        Assert.assertEquals(traceSampleRateWatcher.getSampleRate(), 500);
        Assert.assertEquals(traceSampleRateWatcher.value(),"500");
    }

    public static class MockConfigWatcherRegister extends ConfigWatcherRegister {

        public MockConfigWatcherRegister(long syncPeriod) {
            super(syncPeriod);
        }

        @Override
        public Optional<ConfigTable> readConfig(Set<String> keys) {
            ConfigTable table = new ConfigTable();
            table.add(new ConfigTable.ConfigItem("receiver-trace.default.sampleRate", "9000"));
            return Optional.of(table);
        }
    }

}
