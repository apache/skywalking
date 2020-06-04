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
import org.apache.skywalking.apm.agent.core.meter.adapter.PercentileAdapter;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class ToolkitPercentileAdapterTest {

    @Test
    public void testDrainRecords() {
        final Percentile percentile = Percentile.create("test").build();
        final PercentileAdapter adapter = new ToolkitPercentileAdapter(percentile);

        percentile.record(5);
        percentile.record(7);
        percentile.record(5);

        Map<Double, Long> data = adapter.drainRecords();
        Assert.assertEquals(2, data.size());
        Assert.assertEquals(2L, data.get(5d).longValue());
        Assert.assertEquals(1L, data.get(7d).longValue());
        Assert.assertEquals(null, data.get(9d));

        // empty
        data = adapter.drainRecords();
        Assert.assertEquals(2, data.size());
        Assert.assertEquals(0L, data.get(5d).longValue());
        Assert.assertEquals(0L, data.get(7d).longValue());
        Assert.assertEquals(null, data.get(9d));
    }

    @Test
    public void testGetId() {
        final Percentile counter = Percentile.create("test").tag("k1", "v1").build();
        final PercentileAdapter adapter = new ToolkitPercentileAdapter(counter);

        final MeterId id = adapter.getId();
        Assert.assertEquals("test", id.getName());
        Assert.assertEquals(MeterType.PERCENTILE, id.getType());
        Assert.assertEquals(1, id.getTags().size());
        Assert.assertEquals(new MeterTag("k1", "v1"), id.getTags().get(0));
    }
}
