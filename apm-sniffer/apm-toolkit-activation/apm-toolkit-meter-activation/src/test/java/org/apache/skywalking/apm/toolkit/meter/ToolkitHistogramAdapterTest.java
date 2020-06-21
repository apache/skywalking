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
import org.apache.skywalking.apm.toolkit.activation.meter.adapter.ToolkitHistogramAdapter;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class ToolkitHistogramAdapterTest {

    @Test
    public void testGetAllBuckets() {
        final Histogram histogram = MeterFactory.histogram("test").steps(Arrays.asList(2d, 5d)).build();
        final ToolkitHistogramAdapter adapter = new ToolkitHistogramAdapter(histogram);

        Assert.assertArrayEquals(adapter.getAllBuckets(), new double[] {0d, 2d, 5d}, 0.0);
    }

    @Test
    public void testGetBucketValues() {
        final Histogram histogram = MeterFactory.histogram("test").steps(Arrays.asList(2d, 5d)).build();
        final ToolkitHistogramAdapter adapter = new ToolkitHistogramAdapter(histogram);

        histogram.addValue(1d);
        histogram.addValue(2d);
        histogram.addValue(3d);
        histogram.addValue(20d);

        Assert.assertArrayEquals(adapter.getBucketValues(), new long[] {1L, 2L, 1L});
    }

    @Test
    public void testGetId() {
        final Histogram histogram = MeterFactory.histogram("test").steps(Arrays.asList(2d, 5d)).tag("k1", "v1").build();
        final ToolkitHistogramAdapter adapter = new ToolkitHistogramAdapter(histogram);

        final MeterId id = adapter.getId();
        Assert.assertEquals("test", id.getName());
        Assert.assertEquals(MeterType.HISTOGRAM, id.getType());
        Assert.assertEquals(1, id.getTags().size());
        Assert.assertEquals(new MeterTag("k1", "v1"), id.getTags().get(0));
    }
}
