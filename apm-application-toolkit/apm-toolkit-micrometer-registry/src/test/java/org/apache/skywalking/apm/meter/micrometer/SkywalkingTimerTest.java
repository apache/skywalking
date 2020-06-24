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

import io.micrometer.core.instrument.Timer;
import org.apache.skywalking.apm.toolkit.meter.MeterId;
import org.junit.Assert;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SkywalkingTimerTest extends SkywalkingMeterBaseTest {

    @Test
    public void testSimpleTimer() {
        // Creating a simplify timer
        final SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();
        final Timer timer = registry.timer("test_simple_timer", "skywalking", "test");

        // Check Skywalking type
        Assert.assertTrue(timer instanceof SkywalkingTimer);
        final List<MeterId.Tag> tags = Arrays.asList(new MeterId.Tag("skywalking", "test"));

        // Multiple record data
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(20, TimeUnit.MILLISECONDS);
        timer.record(3, TimeUnit.MILLISECONDS);

        // Check micrometer data
        Assert.assertEquals(3, timer.count());
        Assert.assertEquals(33d, timer.totalTime(TimeUnit.MILLISECONDS), 0.0);
        Assert.assertEquals(20d, timer.max(TimeUnit.MILLISECONDS), 0.0);

        // Check Skywalking data
        assertCounter(Whitebox.getInternalState(timer, "counter"), "test_simple_timer_count", tags, 3d);
        assertCounter(Whitebox.getInternalState(timer, "sum"), "test_simple_timer_sum", tags, 33d);
        assertGauge(Whitebox.getInternalState(timer, "max"), "test_simple_timer_max", tags, 20d);
        assertHistogramNull(Whitebox.getInternalState(timer, "histogram"));
    }

    @Test
    public void testBuilder() {
        // Creating a support histogram timer
        final SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();
        Timer timer = Timer.builder("test_complex_timer")
            .tag("skywalking", "test")
            .publishPercentiles(0.5, 0.95) // median and 95th percentile
            .serviceLevelObjectives(Duration.ofMillis(10), Duration.ofMillis(20))
            .minimumExpectedValue(Duration.ofMillis(1))
            .register(registry);

        // Check Skywalking type
        Assert.assertTrue(timer instanceof SkywalkingTimer);
        final List<MeterId.Tag> tags = Arrays.asList(new MeterId.Tag("skywalking", "test"));

        // Multiple record data
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(22, TimeUnit.MILLISECONDS);
        timer.record(13, TimeUnit.MILLISECONDS);

        // Check micrometer data
        Assert.assertEquals(3, timer.count());
        Assert.assertEquals(45d, timer.totalTime(TimeUnit.MILLISECONDS), 0.0);
        Assert.assertEquals(22d, timer.max(TimeUnit.MILLISECONDS), 0.0);

        // Check Skywalking data
        assertCounter(Whitebox.getInternalState(timer, "counter"), "test_complex_timer_count", tags, 3d);
        assertCounter(Whitebox.getInternalState(timer, "sum"), "test_complex_timer_sum", tags, 45d);
        assertGauge(Whitebox.getInternalState(timer, "max"), "test_complex_timer_max", tags, 22d);
        assertHistogram(Whitebox.getInternalState(timer, "histogram"), "test_complex_timer_histogram", tags, 1, 0, 10, 2, 20, 1);
    }
}
