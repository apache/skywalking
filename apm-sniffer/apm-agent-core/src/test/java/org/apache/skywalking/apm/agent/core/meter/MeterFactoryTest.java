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

package org.apache.skywalking.apm.agent.core.meter;

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.core.test.tools.TracingSegmentRunner;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(TracingSegmentRunner.class)
public class MeterFactoryTest extends MeterDataBaseTest {

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @AfterClass
    public static void afterClass() {
        ServiceManager.INSTANCE.shutdown();
    }

    @Test
    public void testCounter() {
        // Create counter with different mode
        final Counter counter1 = MeterFactory.counter("test_counter1")
            .tag("testA", "testB")
            .build();
        testCounter(counter1, "test_counter1", new String[] {"testA", "testB"}, 0.0, CounterMode.INCREMENT);

        final Counter counter2 = MeterFactory.counter("test_counter2")
            .tag("testA", "testB")
            .mode(CounterMode.RATE)
            .build();
        testCounter(counter2, "test_counter2", new String[] {"testA", "testB"}, 0.0, CounterMode.RATE);
    }

    @Test
    public void testGauge() {
        // Create gauge
        final Gauge gauge = MeterFactory.gauge("test_gauge", () -> 1d)
            .tag("testA", "testB")
            .build();
        testGauge(gauge, "test_gauge", new String[] {"testA", "testB"}, 1.0);
    }

    @Test
    public void testHistogram() {
        // Create histogram
        final Histogram histogram = MeterFactory.histogram("test_histogram")
            .tag("testA", "testB")
            .steps(Arrays.asList(0d, 10d, 20d, 30d))
            .build();

        histogram.addValue(3);
        histogram.addValue(10);
        testHistogram(histogram, "test_histogram", new String[] {"testA", "testB"}, 0d, 1d, 10d, 1d, 20d, 0d, 30d, 0d);
    }

}
