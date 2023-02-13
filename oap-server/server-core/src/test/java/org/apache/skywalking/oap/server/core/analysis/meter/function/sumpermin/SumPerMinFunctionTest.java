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

package org.apache.skywalking.oap.server.core.analysis.meter.function.sumpermin;

import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.latest.LatestFunction;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(MockitoExtension.class)
public class SumPerMinFunctionTest {

    private SumPerMinFunctionInst function;

    @BeforeAll
    public static void setup() {
        MeterEntity.setNamingControl(
            new NamingControl(512, 512, 512, new EndpointNameGrouping()));
    }

    @BeforeEach
    public void before() {
        function = new SumPerMinFunctionInst();
        function.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
    }

    @AfterAll
    public static void tearDown() {
        MeterEntity.setNamingControl(null);
    }

    @Test
    public void testAccept() {
        long time1 = 1597113318673L;
        function.accept(MeterEntity.newService("sum_sync_time", Layer.GENERAL), time1);
        function.calculate();
        assertThat(function.getValue()).isEqualTo(time1);
        long time2 = 1597113447737L;
        function.accept(MeterEntity.newService("sum_sync_time", Layer.GENERAL), time2);
        function.calculate();
        assertThat(function.getValue()).isEqualTo(time1 + time2);
    }

    @Test
    public void testCalculate() {
        long time1 = 1597113318673L;
        long time2 = 1597113447737L;
        function.accept(MeterEntity.newService("sum_sync_time", Layer.GENERAL), time1);
        function.accept(MeterEntity.newService("sum_sync_time", Layer.GENERAL), time2);
        function.calculate();
        assertThat(function.getValue()).isEqualTo(time1 + time2);
    }

    @Test
    public void testHour() {
        long time1 = 1597113318673L;
        long time2 = 1597113447737L;
        function.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        function.accept(MeterEntity.newService("sum_sync_time", Layer.GENERAL), time1);
        function.accept(MeterEntity.newService("sum_sync_time", Layer.GENERAL), time2);
        function.calculate();
        final SumPerMinFunction hourFunction = (SumPerMinFunction) function.toHour();
        hourFunction.calculate();
        assertThat(hourFunction.getValue()).isEqualTo((time1 + time2) / 60);
    }

    @Test
    public void testSerialize() {
        long time = 1597113447737L;
        function.accept(MeterEntity.newService("sum_sync_time", Layer.GENERAL), time);
        LatestFunction function2 = Mockito.spy(LatestFunction.class);
        function2.deserialize(function.serialize().build());
        assertThat(function2.getEntityId()).isEqualTo(function.getEntityId());
        assertThat(function2.getTimeBucket()).isEqualTo(function.getTimeBucket());
    }

    @Test
    public void testBuilder() throws IllegalAccessException, InstantiationException {
        long time = 1597113447737L;
        function.accept(MeterEntity.newService("sum_sync_time", Layer.GENERAL), time);
        function.calculate();
        StorageBuilder<SumPerMinFunction> storageBuilder = function.builder().newInstance();

        final HashMapConverter.ToStorage toStorage = new HashMapConverter.ToStorage();
        storageBuilder.entity2Storage(function, toStorage);
        final Map<String, Object> map = toStorage.obtain();
        map.put(SumPerMinFunction.VALUE, map.get(SumPerMinFunction.VALUE));

        SumPerMinFunction function2 = storageBuilder.storage2Entity(new HashMapConverter.ToEntity(map));
        assertThat(function2.getValue()).isEqualTo(function.getValue());
    }

    private static class SumPerMinFunctionInst extends SumPerMinFunction {
        @Override
        public AcceptableValue<Long> createNew() {
            return new SumPerMinFunctionTest.SumPerMinFunctionInst();
        }
    }
}
