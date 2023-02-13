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

import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.query.type.Bucket;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.IntStream;

import static org.apache.skywalking.oap.server.core.analysis.meter.function.HistogramFunction.DATASET;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @BeforeAll
    public static void setup() {
        MeterEntity.setNamingControl(
            new NamingControl(512, 512, 512, new EndpointNameGrouping()));
    }

    @AfterAll
    public static void tearDown() {
        MeterEntity.setNamingControl(null);
    }

    @Test
    public void testFunction() {
        HistogramFunctionInst inst = new HistogramFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test", Layer.GENERAL),
            new BucketedValues(
                BUCKETS, new long[] {
                0,
                4,
                10,
                10
            })
        );

        inst.accept(
            MeterEntity.newService("service-test", Layer.GENERAL),
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
        Assertions.assertArrayEquals(new int[] {
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
            MeterEntity.newService("service-test", Layer.GENERAL),
            new BucketedValues(
                INFINITE_BUCKETS, new long[] {
                0,
                4,
                10,
                10
            })
        );

        inst.accept(
            MeterEntity.newService("service-test", Layer.GENERAL),
            new BucketedValues(
                INFINITE_BUCKETS, new long[] {
                1,
                2,
                3,
                4
            })
        );

        Assertions.assertEquals(1L, inst.getDataset().get(Bucket.INFINITE_NEGATIVE).longValue());
    }

    @Test
    public void testIncompatible() {
        assertThrows(IllegalArgumentException.class, () -> {
            HistogramFunctionInst inst = new HistogramFunctionInst();
            inst.accept(
                    MeterEntity.newService("service-test", Layer.GENERAL),
                    new BucketedValues(
                            BUCKETS, new long[]{
                            0,
                            4,
                            10,
                            10
                    })
            );

            inst.accept(
                    MeterEntity.newService("service-test", Layer.GENERAL),
                    new BucketedValues(
                            BUCKETS_2ND, new long[]{
                            1,
                            2,
                            3,
                            4
                    })
            );
        });
    }

    @Test
    public void testSerialization() {
        HistogramFunctionInst inst = new HistogramFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test", Layer.GENERAL),
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

        Assertions.assertEquals(inst, inst2);
        // HistogramFunction equal doesn't include dataset.
        Assertions.assertEquals(inst.getDataset(), inst2.getDataset());
    }

    @Test
    public void testSerializationInInfinite() {
        HistogramFunctionInst inst = new HistogramFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test", Layer.GENERAL),
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

        Assertions.assertEquals(inst, inst2);
        // HistogramFunction equal doesn't include dataset.
        Assertions.assertEquals(inst.getDataset(), inst2.getDataset());
    }

    @Test
    public void testBuilder() throws IllegalAccessException, InstantiationException {
        HistogramFunctionInst inst = new HistogramFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test", Layer.GENERAL),
            new BucketedValues(
                BUCKETS, new long[] {
                1,
                4,
                10,
                10
            })
        );

        final StorageBuilder storageBuilder = inst.builder().newInstance();

        // Simulate the storage layer do, convert the datatable to string.
        final HashMapConverter.ToStorage hashMapConverter = new HashMapConverter.ToStorage();
        storageBuilder.entity2Storage(inst, hashMapConverter);
        final Map<String, Object> map = hashMapConverter.obtain();
        map.put(DATASET, ((DataTable) map.get(DATASET)).toStorageData());

        final HistogramFunction inst2 = (HistogramFunction) storageBuilder.storage2Entity(
            new HashMapConverter.ToEntity(map));
        Assertions.assertEquals(inst, inst2);
        // HistogramFunction equal doesn't include dataset.
        Assertions.assertEquals(inst.getDataset(), inst2.getDataset());
    }

    private static class HistogramFunctionInst extends HistogramFunction {

        @Override
        public AcceptableValue<BucketedValues> createNew() {
            return new HistogramFunctionInst();
        }
    }
}
