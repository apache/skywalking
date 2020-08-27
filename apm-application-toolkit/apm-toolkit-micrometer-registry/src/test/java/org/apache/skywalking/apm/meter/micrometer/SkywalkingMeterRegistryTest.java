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

package org.apache.skywalking.apm.meter.micrometer;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tags;
import org.apache.skywalking.apm.toolkit.meter.BaseMeter;
import org.apache.skywalking.apm.toolkit.meter.Counter;
import org.apache.skywalking.apm.toolkit.meter.Gauge;
import org.apache.skywalking.apm.toolkit.meter.impl.MeterCenter;
import org.apache.skywalking.apm.toolkit.meter.MeterId;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Only test in build-in meters
 */
public class SkywalkingMeterRegistryTest extends SkywalkingMeterBaseTest {

    private Map<MeterId, BaseMeter> meterMap;

    @Before
    public void setup() {
        // Make sure meters are clear
        meterMap = Whitebox.getInternalState(MeterCenter.class, "METER_MAP");
        meterMap.clear();
    }

    @After
    public void cleanup() {
        // Clear meters after finish each test case
        meterMap.clear();
    }

    @Test
    public void testGauge() {
        final SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();
        final GaugeTestBean gaugeTestBean = new GaugeTestBean(1d);
        registry.gauge("test_counter", gaugeTestBean, GaugeTestBean::getCount);

        // Check meter and count
        Assert.assertEquals(1, meterMap.size());
        final BaseMeter meter = meterMap.values().iterator().next();
        Assert.assertTrue(meter instanceof Gauge);
        final Gauge gauge = (Gauge) meter;

        Assert.assertEquals(1d, gauge.get(), 0.0);
    }

    @Test
    public void testFunctionTimer() {
        final SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();
        final FunctionTimerBean task = new FunctionTimerBean(1, 200);
        registry.more().timer("test_function_timer", Tags.of("skywalking", "test"), task,
            FunctionTimerBean::getCount, FunctionTimerBean::getTotalTime, TimeUnit.MILLISECONDS);
        final List<MeterId.Tag> tags = Arrays.asList(new MeterId.Tag("skywalking", "test"));

        // Check is has appoint meter
        Assert.assertEquals(2, meterMap.size());
        Gauge countGauge = null;
        Gauge sumGauge = null;
        for (BaseMeter meter : meterMap.values()) {
            if (meter.getName().endsWith("count")) {
                countGauge = (Gauge) meter;
            } else if (meter.getName().endsWith("sum")) {
                sumGauge = (Gauge) meter;
            }
        }

        // Check data
        assertGauge(countGauge, "test_function_timer_count", tags, 1);
        assertGauge(sumGauge, "test_function_timer_sum", tags, 200);
    }

    @Test
    public void testFunctionCounter() {
        final SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();
        final FunctionTimerBean task = new FunctionTimerBean(1, 200);
        registry.more().counter("test_function_counter", Tags.of("skywalking", "test"), task,
            FunctionTimerBean::getCount);
        final List<MeterId.Tag> tags = Arrays.asList(new MeterId.Tag("skywalking", "test"));

        // Check meter and count
        Assert.assertEquals(1, meterMap.size());
        Counter countGauge = (Counter) meterMap.values().iterator().next();

        // Check data
        assertCounter(countGauge, "test_function_counter", tags, 1);
    }

    @Test
    public void testNewMeterSum() {
        // sum
        testNewMeter("test_meter", Meter.Type.GAUGE, Statistic.TOTAL, data -> {
            assertCounter((Counter) data.getMeter(),
                "test_meter_sum", data.getTags(), 1d);
        });

        // count
        testNewMeter("test_meter", Meter.Type.COUNTER, Statistic.COUNT, data -> {
            assertCounter((Counter) data.getMeter(),
                "test_meter", data.getTags(), 1d);
        });

        // max
        testNewMeter("test_meter", Meter.Type.GAUGE, Statistic.MAX, data -> {
            assertGauge((Gauge) data.getMeter(),
                "test_meter_max", data.getTags(), 1d);
        });

        // activeCount
        testNewMeter("test_meter", Meter.Type.GAUGE, Statistic.ACTIVE_TASKS, data -> {
            assertGauge((Gauge) data.getMeter(),
                "test_meter_active_count", data.getTags(), 1d);
        });

        // durationSum
        testNewMeter("test_meter", Meter.Type.GAUGE, Statistic.DURATION, data -> {
            assertGauge((Gauge) data.getMeter(),
                "test_meter_duration_sum", data.getTags(), 1d);
        });

        // others
        testNewMeter("test_meter", Meter.Type.GAUGE, Statistic.VALUE, data -> {
            assertGauge((Gauge) data.getMeter(),
                "test_meter", data.getTags(), 1d);
        });
    }

    /**
     * Check custom measurement
     */
    private void testNewMeter(String meterName, Meter.Type type, Statistic statistic, Consumer<MeterData> meterChecker) {
        final SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();

        // Create measurement
        Meter.builder(meterName, type, Arrays.asList(new Measurement(() -> 1d, statistic)))
            .tag("skywalking", "test")
            .register(registry);
        final List<MeterId.Tag> tags = Arrays.asList(new MeterId.Tag("skywalking", "test"));
        Assert.assertEquals(1, meterMap.size());
        meterChecker.accept(new MeterData(meterMap.values().iterator().next(), tags));

        // clear all data
        cleanup();
    }

    @Test
    public void testRemove() {
        final SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();
        final io.micrometer.core.instrument.Counter counter = registry.counter("test_remove_counter");
        Assert.assertEquals(1, meterMap.size());

        registry.remove(counter.getId());
        Assert.assertEquals(0, meterMap.size());
    }

    /**
     * Working on {@link io.micrometer.core.instrument.Gauge} check
     */
    private static class GaugeTestBean {
        private final double count;

        public GaugeTestBean(double count) {
            this.count = count;
        }

        public double getCount() {
            return count;
        }
    }

    /**
     * Working on {@link io.micrometer.core.instrument.FunctionTimer} check
     */
    private static class FunctionTimerBean {
        private final long count;
        private final double totalTime;

        public FunctionTimerBean(long count, double totalTime) {
            this.count = count;
            this.totalTime = totalTime;
        }

        public long getCount() {
            return count;
        }

        public double getTotalTime() {
            return totalTime;
        }
    }

    /**
     * Working on custom {@link Measurement} check
     */
    private static class MeterData {
        private final BaseMeter meter;
        private final List<MeterId.Tag> tags;

        public MeterData(BaseMeter meter, List<MeterId.Tag> tags) {
            this.meter = meter;
            this.tags = tags;
        }

        public BaseMeter getMeter() {
            return meter;
        }

        public List<MeterId.Tag> getTags() {
            return tags;
        }
    }
}
