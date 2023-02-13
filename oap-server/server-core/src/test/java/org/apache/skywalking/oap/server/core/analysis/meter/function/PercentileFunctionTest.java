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

import static org.junit.jupiter.api.Assertions.assertThrows;

public class PercentileFunctionTest {
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
            new PercentileFunction.PercentileArgument(
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
            new PercentileFunction.PercentileArgument(
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
         *     100, 60 <- P50
         *     250, 80 <- P90
         * </pre>
         */
        Assertions.assertArrayEquals(new int[] {
            100,
            250
        }, values);
    }

    @Test
    public void testIncompatible() {
        assertThrows(IllegalArgumentException.class, () -> {
            PercentileFunctionInst inst = new PercentileFunctionInst();
            inst.accept(
                    MeterEntity.newService("service-test", Layer.GENERAL),
                    new PercentileFunction.PercentileArgument(
                            new BucketedValues(
                                    BUCKETS,
                                    new long[]{
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
                    new PercentileFunction.PercentileArgument(
                            new BucketedValues(
                                    BUCKETS_2ND,
                                    new long[]{
                                            10,
                                            20,
                                            30,
                                            40
                                    }
                            ),
                            RANKS
                    )
            );
        });
    }

    @Test
    public void testSerialization() {
        PercentileFunctionInst inst = new PercentileFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test", Layer.GENERAL),
            new PercentileFunction.PercentileArgument(
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

        Assertions.assertEquals(inst, inst2);
        // HistogramFunction equal doesn't include dataset.
        Assertions.assertEquals(inst.getDataset(), inst2.getDataset());
        Assertions.assertEquals(inst.getRanks(), inst2.getRanks());
        Assertions.assertEquals(0, inst2.getPercentileValues().size());
    }

    @Test
    public void testBuilder() throws IllegalAccessException, InstantiationException {
        PercentileFunctionInst inst = new PercentileFunctionInst();
        inst.accept(
            MeterEntity.newService("service-test", Layer.GENERAL),
            new PercentileFunction.PercentileArgument(
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
        final HashMapConverter.ToStorage hashMapConverter = new HashMapConverter.ToStorage();
        storageBuilder.entity2Storage(inst, hashMapConverter);
        final Map<String, Object> map = hashMapConverter.obtain();
        map.put(PercentileFunction.DATASET, ((DataTable) map.get(PercentileFunction.DATASET)).toStorageData());
        map.put(PercentileFunction.VALUE, ((DataTable) map.get(PercentileFunction.VALUE)).toStorageData());
        map.put(PercentileFunction.RANKS, ((IntList) map.get(PercentileFunction.RANKS)).toStorageData());

        final PercentileFunction inst2 = (PercentileFunction) storageBuilder.storage2Entity(
            new HashMapConverter.ToEntity(map));
        Assertions.assertEquals(inst, inst2);
        // HistogramFunction equal doesn't include dataset.
        Assertions.assertEquals(inst.getDataset(), inst2.getDataset());
        Assertions.assertEquals(inst.getPercentileValues(), inst2.getPercentileValues());
        Assertions.assertEquals(inst.getRanks(), inst2.getRanks());
    }

    private static class PercentileFunctionInst extends PercentileFunction {
        @Override
        public AcceptableValue<PercentileArgument> createNew() {
            return new PercentileFunctionInst();
        }
    }
}
