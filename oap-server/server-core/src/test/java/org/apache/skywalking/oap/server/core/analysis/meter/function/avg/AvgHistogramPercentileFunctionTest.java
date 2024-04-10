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

import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.BucketedValues;
import org.apache.skywalking.oap.server.core.analysis.meter.function.PercentileArgument;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntList;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        PercentileFunctionInst inst = new PercentileFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test", Layer.GENERAL),
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
            MeterEntity.newService("service-test", Layer.GENERAL),
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
        final DataTable values = inst.getValue();
        /**
         * Expected percentile dataset
         * <pre>
         *     0  , 10
         *     50 , 20
         *     100, 30 <- P50
         *     250, 40 <- P90
         * </pre>
         */
        Assertions.assertEquals(new DataTable("{p=50},100|{p=90},250"), values);
    }

    @Test
    public void testSerialization() {
        PercentileFunctionInst inst = new PercentileFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test", Layer.GENERAL),
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
            MeterEntity.newService("service-test", Layer.GENERAL),
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

        final StorageBuilder storageBuilder = inst.builder().newInstance();

        // Simulate the storage layer do, convert the datatable to string.
        final HashMapConverter.ToStorage toStorage = new HashMapConverter.ToStorage();
        storageBuilder.entity2Storage(inst, toStorage);
        final Map<String, Object> map = toStorage.obtain();
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

        final AvgHistogramPercentileFunction inst2 = (AvgHistogramPercentileFunction) storageBuilder.storage2Entity(
            new HashMapConverter.ToEntity(map));
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
        valuesA.setGroup("localhost:3306/swtestA");

        PercentileFunctionInst inst = new PercentileFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test", Layer.GENERAL),
            new PercentileArgument(
                valuesA,
                RANKS
            )
        );
        BucketedValues valuesB = new BucketedValues(
            BUCKETS,
            new long[] {
                30,
                40,
                20,
                10
            }
        );
        valuesB.setGroup("localhost:3306/swtestB");

        inst.accept(
            MeterEntity.newService("service-test", Layer.GENERAL),
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
        assertEquals(
            new DataTable(
                "{p=localhost:3306/swtestB:50},50|{p=localhost:3306/swtestA:50},100|{p=localhost:3306/swtestB:90},100|{p=localhost:3306/swtestA:90},250"),
            values
        );
    }

    private static class PercentileFunctionInst extends AvgHistogramPercentileFunction {
        @Override
        public AcceptableValue<PercentileArgument> createNew() {
            return new AvgHistogramPercentileFunctionTest.PercentileFunctionInst();
        }
    }
}
