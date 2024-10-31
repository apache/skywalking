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

import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(MockitoExtension.class)
public class SumFunctionTest {

    @Spy
    private SumFunction function;

    @BeforeAll
    public static void setup() {
        MeterEntity.setNamingControl(
            new NamingControl(512, 512, 512, new EndpointNameGrouping()));
    }

    @BeforeEach
    public void before() {
        function = new SumFunctionTest.SumFunctionInst();
        function.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
    }

    @AfterAll
    public static void tearDown() {
        MeterEntity.setNamingControl(null);
    }

    @Test
    public void testAccept() {
        long time = 100;
        function.accept(MeterEntity.newService("sum_resp_time", Layer.GENERAL), time);
        assertThat(function.getValue()).isEqualTo(time);
        time = 200;
        function.accept(MeterEntity.newService("sum_resp_time", Layer.GENERAL), time);
        assertThat(function.getValue()).isEqualTo(300);
    }

    @Test
    public void testSerialize() {
        long time = 1597113447737L;
        MeterEntity meterEntity = MeterEntity.newService("sum_resp_time", Layer.GENERAL);
        meterEntity.setAttr0("testAttr");
        function.accept(meterEntity, time);
        SumFunction function2 = Mockito.spy(SumFunction.class);
        function2.deserialize(function.serialize().build());
        assertThat(function2.getEntityId()).isEqualTo(function.getEntityId());
        assertThat(function2.getTimeBucket()).isEqualTo(function.getTimeBucket());
        assertThat(function2.getAttr0()).isEqualTo(function.getAttr0());
    }

    @Test
    public void testBuilder() throws IllegalAccessException, InstantiationException {
        long time = 1597113447737L;
        MeterEntity meterEntity = MeterEntity.newService("sum_resp_time", Layer.GENERAL);
        meterEntity.setAttr0("testAttr");
        function.accept(meterEntity, time);
        function.calculate();
        StorageBuilder<SumFunction> storageBuilder = (StorageBuilder<SumFunction>) function.builder().newInstance();

        final HashMapConverter.ToStorage toStorage = new HashMapConverter.ToStorage();
        storageBuilder.entity2Storage(function, toStorage);
        final Map<String, Object> map = toStorage.obtain();
        map.put(SumFunction.VALUE, map.get(SumFunction.VALUE));

        SumFunction function2 = storageBuilder.storage2Entity(new HashMapConverter.ToEntity(map));
        assertThat(function2.getAttr0()).isEqualTo(function.getAttr0());
    }

    @Test
    public void testToHour() {
        long time1 = 100;
        long time2 = 200;
        function.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        MeterEntity meterEntity = MeterEntity.newService("sum_resp_time", Layer.GENERAL);
        meterEntity.setAttr0("testAttr");
        function.accept(meterEntity, time1);
        function.accept(meterEntity, time2);
        function.calculate();

        final SumFunction hourFunction = (SumFunction) function.toHour();
        hourFunction.calculate();

        assertThat(hourFunction.getValue()).isEqualTo(300);
        assertThat(hourFunction.getAttr0()).isEqualTo("testAttr");
    }

    @Test
    public void testToDay() {
        long time1 = 100;
        long time2 = 200;
        MeterEntity meterEntity = MeterEntity.newService("sum_resp_time", Layer.GENERAL);
        meterEntity.setAttr0("testAttr");
        function.accept(meterEntity, time1);
        function.accept(meterEntity, time2);
        function.calculate();

        final SumFunction dayFunction = (SumFunction) function.toDay();
        dayFunction.calculate();
        assertThat(dayFunction.getValue()).isEqualTo(300);
        assertThat(dayFunction.getAttr0()).isEqualTo("testAttr");
    }

    private static class SumFunctionInst extends SumFunction {
        @Override
        public AcceptableValue<Long> createNew() {
            return new SumFunctionTest.SumFunctionInst();
        }
    }
}
