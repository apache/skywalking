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

package org.apache.skywalking.apm.toolkit.meter;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class CounterTest {

    @Test
    public void testBuild() {
        Counter counter1 = MeterFactory.counter("test_counter").tag("k1", "v1").build();
        Assert.assertNotNull(counter1);

        final Counter counter2 = MeterFactory.counter(new MeterId("test_counter", MeterId.MeterType.COUNTER, Arrays.asList(new MeterId.Tag("k1", "v1")))).build();
        Assert.assertNotNull(counter2);
        Assert.assertEquals(counter1, counter2);
    }

    @Test
    public void testIncrement() {
        Counter counter = MeterFactory.counter("test_counter1").tag("k1", "v1").build();
        counter.increment(1);
        Assert.assertEquals(counter.getCount(), 1d, 0.0);

        counter.increment(1.5);
        Assert.assertEquals(counter.getCount(), 2.5d, 0.0);

        counter.increment(-1d);
        Assert.assertEquals(counter.getCount(), 1.5d, 0.0);
    }

    @Test
    public void testAccept() {
        Counter counter = MeterFactory.counter("test_counter_accept")
            .tag("k1", "v1")
            .mode(Counter.Mode.INCREMENT)
            .build();

        // Check the same mode
        try {
            MeterFactory.counter("test_counter_accept")
                .tag("k1", "v1")
                .mode(Counter.Mode.INCREMENT)
                .build();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
        }

        // Check the different mode
        try {
            MeterFactory.counter("test_counter_accept")
                .tag("k1", "v1")
                .mode(Counter.Mode.RATE)
                .build();
            throw new IllegalStateException();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
        }
    }

}
