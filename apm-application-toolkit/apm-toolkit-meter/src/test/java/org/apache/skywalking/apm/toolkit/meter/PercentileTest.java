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
import org.mockito.internal.util.reflection.Whitebox;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class PercentileTest {

    @Test
    public void testBuild() {
        final Percentile percentile1 = Percentile.create("test_percentile1").build();
        Assert.assertNotNull(percentile1);

        final Percentile percentile2 = new Percentile.Builder(
            new MeterId("test_percentile2", MeterId.MeterType.PERCENTILE, Collections.emptyList())).build();
        Assert.assertNotNull(percentile2);
    }

    @Test
    public void testRecord() {
        Percentile percentile = Percentile.create("test_percentile2").build();
        percentile.record(5);
        validatePercentile(percentile, 5, 1);

        percentile.record(10);
        percentile.record(5);
        validatePercentile(percentile, 5, 2, 10, 1);
    }

    /**
     * validate percentile records
     * @param values the records map, i = record key, i + 1 = record value
     */
    private void validatePercentile(Percentile percentile, double... values) {
        Assert.assertNotNull(percentile);
        final Map<Double, AtomicLong> records = percentile.getRecordWithCount();

        Assert.assertEquals(records.size(), values.length / 2);
        for (int i = 0; i < values.length; i += 2) {
            final AtomicLong val = records.get(values[i]);
            Assert.assertNotNull(val);
            Assert.assertEquals(val.get(), (long) values[i + 1]);
        }
    }
}
