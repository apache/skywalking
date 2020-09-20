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

package org.apache.skywalking.apm.agent.core.meter.builder.adapter;

import org.apache.skywalking.apm.agent.core.meter.MeterId;
import org.apache.skywalking.apm.agent.core.meter.MeterTag;
import org.apache.skywalking.apm.agent.core.meter.MeterType;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class InternalBaseAdapterTest {

    @Test
    public void testGetName() {
        final InternalBaseAdapter adapter = new InternalBaseAdapter(new MeterId("test", MeterType.COUNTER, Arrays.asList(new MeterTag("k1", "v1"))));
        Assert.assertEquals("test", adapter.getName());
    }

    @Test
    public void testGetTag() {
        final InternalBaseAdapter adapter = new InternalBaseAdapter(new MeterId("test", MeterType.COUNTER, Arrays.asList(new MeterTag("k1", "v1"))));
        Assert.assertEquals("v1", adapter.getTag("k1"));
        Assert.assertNull(adapter.getTag("k2"));
    }

    @Test
    public void testEquals() {
        final InternalBaseAdapter adapter1 = new InternalBaseAdapter(new MeterId("test", MeterType.COUNTER, Arrays.asList(new MeterTag("k1", "v1"))));
        final InternalBaseAdapter adapter2 = new InternalBaseAdapter(new MeterId("test", MeterType.COUNTER, Arrays.asList(new MeterTag("k1", "v1"))));
        final InternalBaseAdapter adapter3 = new InternalBaseAdapter(new MeterId("test2", MeterType.COUNTER, Arrays.asList(new MeterTag("k1", "v1"))));

        Assert.assertEquals(adapter1, adapter2);
        Assert.assertNotEquals(adapter3, adapter2);
    }
}
