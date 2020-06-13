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

import io.micrometer.core.instrument.LongTaskTimer;
import org.apache.skywalking.apm.toolkit.meter.BaseMeter;
import org.apache.skywalking.apm.toolkit.meter.Gauge;
import org.apache.skywalking.apm.toolkit.meter.MeterCenter;
import org.apache.skywalking.apm.toolkit.meter.MeterId;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SkywalkingLongTaskTimerTest extends SkywalkingMeterBaseTest {

    private Map<MeterId, BaseMeter> meterMap;

    @Before
    public void setup() {
        meterMap = Whitebox.getInternalState(MeterCenter.class, "METER_MAP");
        meterMap.clear();
    }

    @Test
    public void testSimple() throws InterruptedException {
        final SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();

        final LongTaskTimer longTaskTimer = registry.more().longTaskTimer("test_simple_long_task_timer", "skywalking", "test");
        addLongTask(longTaskTimer, 200);
        addLongTask(longTaskTimer, 20);

        TimeUnit.MILLISECONDS.sleep(50);

        Assert.assertTrue(longTaskTimer instanceof SkywalkingLongTaskTimer);
        final SkywalkingLongTaskTimer timer = (SkywalkingLongTaskTimer) longTaskTimer;

        // original data
        Assert.assertEquals(1, timer.activeTasks());
        Assert.assertTrue(timer.duration(TimeUnit.MILLISECONDS) > 0);
        Assert.assertTrue(timer.max(TimeUnit.MILLISECONDS) > 0);

        // skywalking data
        assertGauge((Gauge) meterMap.values().stream().filter(m -> m.getName().endsWith("_active_count")).findFirst().orElse(null),
            "test_simple_long_task_timer_active_count", Arrays.asList(new MeterId.Tag("skywalking", "test")), 1);
        assertGauge((Gauge) meterMap.values().stream().filter(m -> m.getName().endsWith("_duration_sum")).findFirst().orElse(null),
            "test_simple_long_task_timer_duration_sum", Arrays.asList(new MeterId.Tag("skywalking", "test")), 0, true);
        assertGauge((Gauge) meterMap.values().stream().filter(m -> m.getName().endsWith("_max")).findFirst().orElse(null),
            "test_simple_long_task_timer_max", Arrays.asList(new MeterId.Tag("skywalking", "test")), 0, true);
        assertHistogramNull(Whitebox.getInternalState(timer, "histogram"));
        assertPercentileNull(Whitebox.getInternalState(timer, "percentile"));
    }

    @Test
    public void testComplex() throws InterruptedException {
        final SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();

        final LongTaskTimer longTaskTimer = LongTaskTimer.builder("test_complex_long_task_timer")
            .tag("skywalking", "test")
            .serviceLevelObjectives(Duration.ofMillis(10), Duration.ofMillis(190), Duration.ofMillis(500))
            .minimumExpectedValue(Duration.ofMillis(0))
            .publishPercentiles(0.90)
            .register(registry);

        addLongTask(longTaskTimer, 200);
        addLongTask(longTaskTimer, 20);

        TimeUnit.MILLISECONDS.sleep(50);

        Assert.assertTrue(longTaskTimer instanceof SkywalkingLongTaskTimer);
        final SkywalkingLongTaskTimer timer = (SkywalkingLongTaskTimer) longTaskTimer;

        Assert.assertEquals(1, timer.activeTasks());
        Assert.assertTrue(timer.duration(TimeUnit.MILLISECONDS) > 0);
        Assert.assertTrue(timer.max(TimeUnit.MILLISECONDS) > 0);

        // skywalking data
        final List<MeterId.Tag> tags = Arrays.asList(new MeterId.Tag("skywalking", "test"));
        assertGauge((Gauge) meterMap.values().stream().filter(m -> m.getName().endsWith("_active_count")).findFirst().orElse(null),
            "test_complex_long_task_timer_active_count", tags, 1);
        assertGauge((Gauge) meterMap.values().stream().filter(m -> m.getName().endsWith("_duration_sum")).findFirst().orElse(null),
            "test_complex_long_task_timer_duration_sum", tags, 0, true);
        assertGauge((Gauge) meterMap.values().stream().filter(m -> m.getName().endsWith("_max")).findFirst().orElse(null),
            "test_complex_long_task_timer_max", tags, 0, true);
        assertHistogram(Whitebox.getInternalState(timer, "histogram"), "test_complex_long_task_timer_histogram",
            tags, 0, 0, 10, 1, 190, 0, 500, 0);

        // use time is not stable, using a time range to check has one of time
        boolean containsResult = false;
        for (int i = 0; i < 10; i++) {
            try {
                assertPercentile(Whitebox.getInternalState(timer, "percentile"), "test_complex_long_task_timer_percentile",
                    tags, 19 + i, 1);
                containsResult = true;
            } catch (Error e) {
            }
        }
        Assert.assertTrue(containsResult);

    }

    private void addLongTask(LongTaskTimer longTaskTimer, int sleepMills) {
        new Thread(() -> {
            longTaskTimer.record(() -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(sleepMills);
                } catch (InterruptedException e) {
                }
            });
        }).start();
    }

}
