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
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Only test in build-in meters
 */
public class SkywalkingMeterRegistryTest {

    @Test
    public void testGauge() {
        final SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();
        final GaugeTestBean gaugeTestBean = new GaugeTestBean(1d);
        registry.gauge("test_counter", gaugeTestBean, GaugeTestBean::getCount);
    }

    @Test
    public void testFunctionTimer() {
        final SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();
        final FunctionTimerBean task = new FunctionTimerBean(1, 200);
        registry.more().timer("test_function_timer", Tags.of("skywalking", "test"), task,
            FunctionTimerBean::getCount, FunctionTimerBean::getTotalTime, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testFunctionCounter() {
        final SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();
        final FunctionTimerBean task = new FunctionTimerBean(1, 200);
        registry.more().counter("test_function_counter", Tags.of("skywalking", "test"), task,
            FunctionTimerBean::getCount);
    }

    @Test
    public void testNewMeterSum() {
        // sum
        testNewMeter("test_meter", Meter.Type.GAUGE, Statistic.TOTAL);

        // count
        testNewMeter("test_meter", Meter.Type.COUNTER, Statistic.COUNT);

        // max
        testNewMeter("test_meter", Meter.Type.GAUGE, Statistic.MAX);

        // activeCount
        testNewMeter("test_meter", Meter.Type.GAUGE, Statistic.ACTIVE_TASKS);

        // durationSum
        testNewMeter("test_meter", Meter.Type.GAUGE, Statistic.DURATION);

        // others
        testNewMeter("test_meter", Meter.Type.GAUGE, Statistic.VALUE);
    }

    /**
     * Check custom measurement
     */
    private void testNewMeter(String meterName, Meter.Type type, Statistic statistic) {
        final SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();

        // Create measurement
        Meter.builder(meterName, type, Arrays.asList(new Measurement(() -> 1d, statistic)))
            .tag("skywalking", "test")
            .register(registry);
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

}
