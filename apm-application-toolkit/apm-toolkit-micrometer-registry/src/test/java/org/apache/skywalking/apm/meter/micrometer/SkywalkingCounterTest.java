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

import io.micrometer.core.instrument.Counter;
import org.apache.skywalking.apm.toolkit.meter.MeterId;
import org.junit.Assert;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.util.Arrays;
import java.util.List;

public class SkywalkingCounterTest extends SkywalkingMeterBaseTest {

    @Test
    public void testCounter() {
        final SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();
        Counter counter = registry.counter("test_counter", "skywalking", "test");

        Assert.assertTrue(counter instanceof SkywalkingCounter);
        final SkywalkingCounter skywalkingCounter = (SkywalkingCounter) counter;
        final org.apache.skywalking.apm.toolkit.meter.Counter realCounter =
            Whitebox.getInternalState(skywalkingCounter, "counter");

        final List<MeterId.Tag> tags = Arrays.asList(new MeterId.Tag("skywalking", "test"));

        skywalkingCounter.increment(1d);
        assertCounter(realCounter, "test_counter", tags, 1);
        Assert.assertEquals(1d, skywalkingCounter.count(), 0.0);
        skywalkingCounter.increment(2d);
        skywalkingCounter.increment(3d);
        assertCounter(realCounter, "test_counter", tags, 6);
        Assert.assertEquals(6d, skywalkingCounter.count(), 0.0);
    }
}
