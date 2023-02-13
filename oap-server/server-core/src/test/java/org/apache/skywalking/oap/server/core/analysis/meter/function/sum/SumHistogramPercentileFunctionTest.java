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

package org.apache.skywalking.oap.server.core.analysis.meter.function.sum;

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

public class SumHistogramPercentileFunctionTest {

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
        final int[] values = inst.getValues();
        /**
         * Expected percentile dataset
         * <pre>
         *     0  , 20
         *     50 , 40
         *     100, 50 <- P50
         *     250, 80 <- P90
         * </pre>
         */
        Assertions.assertArrayEquals(new int[] {
            100,
            250
        }, values);
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
        assertEquals(inst.getSummation(), inst2.getSummation());
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
            SumHistogramPercentileFunction.SUMMATION,
            ((DataTable) map.get(SumHistogramPercentileFunction.SUMMATION)).toStorageData()
        );
        map.put(
            SumHistogramPercentileFunction.VALUE,
            ((DataTable) map.get(SumHistogramPercentileFunction.VALUE)).toStorageData()
        );
        map.put(
            SumHistogramPercentileFunction.RANKS,
            ((IntList) map.get(SumHistogramPercentileFunction.RANKS)).toStorageData()
        );

        final SumHistogramPercentileFunction inst2 = (SumHistogramPercentileFunction) storageBuilder.storage2Entity(
            new HashMapConverter.ToEntity(map));
        assertEquals(inst, inst2);
        // HistogramFunction equal doesn't include dataset.
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
            MeterEntity.newService("service-test", Layer.GENERAL),
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
        valuesB.setGroup("localhost:3306/swtest");

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
         *     0  , 20
         *     50 , 40
         *     100, 60 <- P50
         *     250, 80 <- P90
         * </pre>
         */
        assertEquals(new Long(100), values.get("localhost:3306/swtest:50"));
        assertEquals(new Long(250), values.get("localhost:3306/swtest:90"));
    }

    private static class PercentileFunctionInst extends SumHistogramPercentileFunction {
        @Override
        public AcceptableValue<PercentileArgument> createNew() {
            return new SumHistogramPercentileFunctionTest.PercentileFunctionInst();
        }
    }
}
