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

package org.apache.skywalking.oap.server.core.analysis.meter.function.avg;

import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.BucketedValues;
import org.apache.skywalking.oap.server.core.analysis.meter.function.PercentileArgument;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntList;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AvgHistogramPercentileFunctionTest {

    private static final long[] BUCKETS = new long[] {
        0,
        50,
        100,
        250
    };

    private static final int[] RANKS = new int[] {
        50,
        90
    };

    @Test
    public void testFunction() {
        PercentileFunctionInst inst = new PercentileFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test"),
            new PercentileArgument(
                new BucketedValues(
                    BUCKETS,
                    new long[] {
                        10,
                        20,
                        30,
                        40
                    }
                ),
                RANKS
            )
        );

        inst.accept(
            MeterEntity.newService("service-test"),
            new PercentileArgument(
                new BucketedValues(
                    BUCKETS,
                    new long[] {
                        10,
                        20,
                        30,
                        40
                    }
                ),
                RANKS
            )
        );

        inst.calculate();
        final int[] values = inst.getValues();
        /**
         * Expected percentile dataset
         * <pre>
         *     0  , 10
         *     50 , 20
         *     100, 30 <- P50
         *     250, 40 <- P90
         * </pre>
         */
        Assert.assertArrayEquals(new int[] {
            100,
            250
        }, values);
    }

    @Test
    public void testSerialization() {
        PercentileFunctionInst inst = new PercentileFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test"),
            new PercentileArgument(
                new BucketedValues(
                    BUCKETS,
                    new long[] {
                        10,
                        20,
                        30,
                        40
                    }
                ),
                RANKS
            )
        );

        PercentileFunctionInst inst2 = new PercentileFunctionInst();
        inst2.deserialize(inst.serialize().build());

        assertEquals(inst, inst2);
        // HistogramFunction equal doesn't include dataset.
        assertEquals(inst.getDataset(), inst2.getDataset());
        assertEquals(inst.getRanks(), inst2.getRanks());
        assertEquals(0, inst2.getPercentileValues().size());
    }

    @Test
    public void testBuilder() throws IllegalAccessException, InstantiationException {
        PercentileFunctionInst inst = new PercentileFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test"),
            new PercentileArgument(
                new BucketedValues(
                    BUCKETS,
                    new long[] {
                        10,
                        20,
                        30,
                        40
                    }
                ),
                RANKS
            )
        );
        inst.calculate();

        final StorageHashMapBuilder storageBuilder = inst.builder().newInstance();

        // Simulate the storage layer do, convert the datatable to string.
        final Map map = storageBuilder.entity2Storage(inst);
        map.put(
            AvgHistogramPercentileFunction.COUNT,
            ((DataTable) map.get(AvgHistogramPercentileFunction.COUNT)).toStorageData()
        );
        map.put(
            AvgHistogramPercentileFunction.SUMMATION,
            ((DataTable) map.get(AvgHistogramPercentileFunction.SUMMATION)).toStorageData()
        );
        map.put(
            AvgHistogramPercentileFunction.DATASET,
            ((DataTable) map.get(AvgHistogramPercentileFunction.DATASET)).toStorageData()
        );
        map.put(
            AvgHistogramPercentileFunction.VALUE,
            ((DataTable) map.get(AvgHistogramPercentileFunction.VALUE)).toStorageData()
        );
        map.put(
            AvgHistogramPercentileFunction.RANKS,
            ((IntList) map.get(AvgHistogramPercentileFunction.RANKS)).toStorageData()
        );

        final AvgHistogramPercentileFunction inst2 = (AvgHistogramPercentileFunction) storageBuilder.storage2Entity(map);
        assertEquals(inst, inst2);
        // HistogramFunction equal doesn't include dataset.
        assertEquals(inst.getDataset(), inst2.getDataset());
        assertEquals(inst.getPercentileValues(), inst2.getPercentileValues());
        assertEquals(inst.getRanks(), inst2.getRanks());
    }

    @Test
    public void testFunctionWhenGroupContainsColon() {
        BucketedValues valuesA = new BucketedValues(
            BUCKETS,
            new long[] {
                10,
                20,
                30,
                40
            }
        );
        valuesA.setGroup("localhost:3306/swtest");

        PercentileFunctionInst inst = new PercentileFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test"),
            new PercentileArgument(
                valuesA,
                RANKS
            )
        );
        BucketedValues valuesB = new BucketedValues(
            BUCKETS,
            new long[] {
                10,
                20,
                30,
                40
            }
        );
        valuesA.setGroup("localhost:3306/swtest");

        inst.accept(
            MeterEntity.newService("service-test"),
            new PercentileArgument(
                valuesB,
                RANKS
            )
        );

        inst.calculate();
        final DataTable values = inst.getPercentileValues();
        /**
         * Expected percentile dataset
         * <pre>
         *     0  , 10
         *     50 , 20
         *     100, 30 <- P50
         *     250, 40 <- P90
         * </pre>
         */
        assertEquals(new Long(100), values.get("localhost:3306/swtest:50"));
        assertEquals(new Long(250), values.get("localhost:3306/swtest:90"));
    }

    private static class PercentileFunctionInst extends AvgHistogramPercentileFunction {
        @Override
        public AcceptableValue<PercentileArgument> createNew() {
            return new AvgHistogramPercentileFunctionTest.PercentileFunctionInst();
        }
    }
}