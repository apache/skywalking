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

import org.apache.skywalking.apm.agent.core.meter.MeterId;
import org.apache.skywalking.apm.agent.core.meter.MeterTag;
import org.apache.skywalking.apm.agent.core.meter.MeterType;
import org.apache.skywalking.apm.toolkit.activation.meter.adapter.TookitCounterAdapter;
import org.apache.skywalking.apm.toolkit.meter.impl.CounterImpl;
import org.junit.Assert;
import org.junit.Test;

public class ToolkitCounterAdapterTest {

    @Test
    public void testGetCount() {
        final Counter counter = MeterFactory.counter("test").build();
        final TookitCounterAdapter adapter = new TookitCounterAdapter((CounterImpl) counter);

        counter.increment(1d);
        counter.increment(2d);

        Assert.assertEquals(adapter.getCount(), 3d, 0.0);

        counter.increment(-4d);
        Assert.assertEquals(adapter.getCount(), -1d, 0.0);
    }

    @Test
    public void testGetCountWithRate() {
        final Counter counter = MeterFactory.counter("test_with_rate").mode(Counter.Mode.RATE).build();
        final TookitCounterAdapter adapter = new TookitCounterAdapter((CounterImpl) counter);

        counter.increment(1d);
        counter.increment(2d);

        Assert.assertEquals(adapter.getCount(), 3d, 0.0);
        Assert.assertEquals(adapter.getCount(), 0d, 0.0);

        counter.increment(-4d);
        Assert.assertEquals(adapter.getCount(), -4d, 0.0);
    }

    @Test
    public void testGetId() {
        final Counter counter = MeterFactory.counter("test").tag("k1", "v1").build();
        final TookitCounterAdapter adapter = new TookitCounterAdapter((CounterImpl) counter);

        final MeterId id = adapter.getId();
        Assert.assertEquals("test", id.getName());
        Assert.assertEquals(MeterType.COUNTER, id.getType());
        Assert.assertEquals(1, id.getTags().size());
        Assert.assertEquals(new MeterTag("k1", "v1"), id.getTags().get(0));
    }
}
