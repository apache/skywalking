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

import org.junit.Assert;

/**
 * Meter data base checker
 */
public class MeterDataBaseTest {

    /**
     * Check Counter values
     */
    public void testCounter(Counter counter, String name, String[] tags, double val, CounterMode counterMode) {
        // Check meter name
        Assert.assertEquals(name, counter.getName());
        // Check tags
        if (tags != null && tags.length > 0) {
            for (int inx = 0; inx < tags.length; inx += 2) {
                Assert.assertEquals(tags[inx + 1], counter.getTag(tags[inx]));
            }
        }
        // Check current value
        Assert.assertEquals(val, counter.get(), 0.0);

        // Check mode
        Assert.assertEquals(counterMode, counter.mode);
    }

    /**
     * Check Gauge values
     */
    public void testGauge(Gauge gauge, String name, String[] tags, double val) {
        // Check meter name
        Assert.assertEquals(name, gauge.getName());
        // Check tags
        if (tags != null && tags.length > 0) {
            for (int inx = 0; inx < tags.length; inx += 2) {
                Assert.assertEquals(tags[inx + 1], gauge.getTag(tags[inx]));
            }
        }
        // Check current value
        Assert.assertEquals(val, gauge.get(), 0.0);
    }

    /**
     * Check Histogram values
     */
    public void testHistogram(Histogram histogram, String name, String[] tags, Double... data) {
        // Check meter name
        Assert.assertEquals(name, histogram.getName());
        // Check tags
        if (tags != null && tags.length > 0) {
            for (int inx = 0; inx < tags.length; inx += 2) {
                Assert.assertEquals(tags[inx + 1], histogram.getTag(tags[inx]));
            }
        }
        // Check buckets
        for (int i = 0; i < data.length / 2; i++) {
            Assert.assertEquals(data[i * 2], histogram.buckets[i].bucket, 0.0);
            Assert.assertEquals(data[i * 2 + 1].longValue(), histogram.buckets[i].count.get());
        }
    }
}
