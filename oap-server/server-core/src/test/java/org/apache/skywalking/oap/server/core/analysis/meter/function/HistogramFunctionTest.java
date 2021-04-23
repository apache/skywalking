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

package org.apache.skywalking.oap.server.core.analysis.meter.function;

import java.util.Map;
import java.util.stream.IntStream;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.query.type.Bucket;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.junit.Assert;
import org.junit.Test;

import static org.apache.skywalking.oap.server.core.analysis.meter.function.HistogramFunction.DATASET;

public class HistogramFunctionTest {
    private static final long[] BUCKETS = new long[] {
        0,
        50,
        100,
        250
    };

    private static final long[] BUCKETS_2ND = new long[] {
        0,
        51,
        100,
        250
    };

    private static final long[] INFINITE_BUCKETS = new long[] {
        Long.MIN_VALUE,
        -5,
        0,
        10
    };

    @Test
    public void testFunction() {
        HistogramFunctionInst inst = new HistogramFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test"),
            new BucketedValues(
                BUCKETS, new long[] {
                0,
                4,
                10,
                10
            })
        );

        inst.accept(
            MeterEntity.newService("service-test"),
            new BucketedValues(
                BUCKETS, new long[] {
                1,
                2,
                3,
                4
            })
        );

        final int[] results = inst.getDataset().sortedValues(new HeatMap.KeyComparator(true)).stream()
                                  .flatMapToInt(l -> IntStream.of(l.intValue()))
                                  .toArray();
        Assert.assertArrayEquals(new int[] {
            1,
            6,
            13,
            14
        }, results);
    }

    @Test
    public void testFunctionWithInfinite() {
        HistogramFunctionInst inst = new HistogramFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test"),
            new BucketedValues(
                INFINITE_BUCKETS, new long[] {
                0,
                4,
                10,
                10
            })
        );

        inst.accept(
            MeterEntity.newService("service-test"),
            new BucketedValues(
                INFINITE_BUCKETS, new long[] {
                1,
                2,
                3,
                4
            })
        );

        Assert.assertEquals(1L, inst.getDataset().get(Bucket.INFINITE_NEGATIVE).longValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIncompatible() {
        HistogramFunctionInst inst = new HistogramFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test"),
            new BucketedValues(
                BUCKETS, new long[] {
                0,
                4,
                10,
                10
            })
        );

        inst.accept(
            MeterEntity.newService("service-test"),
            new BucketedValues(
                BUCKETS_2ND, new long[] {
                1,
                2,
                3,
                4
            })
        );
    }

    @Test
    public void testSerialization() {
        HistogramFunctionInst inst = new HistogramFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test"),
            new BucketedValues(
                BUCKETS, new long[] {
                1,
                4,
                10,
                10
            })
        );

        final HistogramFunctionInst inst2 = new HistogramFunctionInst();
        inst2.deserialize(inst.serialize().build());

        Assert.assertEquals(inst, inst2);
        // HistogramFunction equal doesn't include dataset.
        Assert.assertEquals(inst.getDataset(), inst2.getDataset());
    }

    @Test
    public void testSerializationInInfinite() {
        HistogramFunctionInst inst = new HistogramFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test"),
            new BucketedValues(
                INFINITE_BUCKETS, new long[] {
                1,
                4,
                10,
                10
            })
        );

        final HistogramFunctionInst inst2 = new HistogramFunctionInst();
        inst2.deserialize(inst.serialize().build());

        Assert.assertEquals(inst, inst2);
        // HistogramFunction equal doesn't include dataset.
        Assert.assertEquals(inst.getDataset(), inst2.getDataset());
    }

    @Test
    public void testBuilder() throws IllegalAccessException, InstantiationException {
        HistogramFunctionInst inst = new HistogramFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test"),
            new BucketedValues(
                BUCKETS, new long[] {
                1,
                4,
                10,
                10
            })
        );

        final StorageHashMapBuilder storageBuilder = inst.builder().newInstance();

        // Simulate the storage layer do, convert the datatable to string.
        final Map map = storageBuilder.entity2Storage(inst);
        map.put(DATASET, ((DataTable) map.get(DATASET)).toStorageData());

        final HistogramFunction inst2 = (HistogramFunction) storageBuilder.storage2Entity(map);
        Assert.assertEquals(inst, inst2);
        // HistogramFunction equal doesn't include dataset.
        Assert.assertEquals(inst.getDataset(), inst2.getDataset());
    }

    private static class HistogramFunctionInst extends HistogramFunction {

        @Override
        public AcceptableValue<BucketedValues> createNew() {
            return new HistogramFunctionInst();
        }
    }
}
