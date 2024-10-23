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
public class AvgFunctionTest {

    @Spy
    private AvgFunction function;

    @BeforeAll
    public static void setup() {
        MeterEntity.setNamingControl(
            new NamingControl(512, 512, 512, new EndpointNameGrouping()));
    }

    @BeforeEach
    public void before() {
        function = new AvgFunctionTest.AvgFunctionInst();
        function.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
    }

    @AfterAll
    public static void tearDown() {
        MeterEntity.setNamingControl(null);
    }

    @Test
    public void testAccept() {
        long time = 100;
        function.accept(MeterEntity.newService("avg_resp_time", Layer.GENERAL), time);
        assertThat(function.getSummation()).isEqualTo(time);
        time = 200;
        function.accept(MeterEntity.newService("avg_resp_time", Layer.GENERAL), time);
        assertThat(function.getSummation()).isEqualTo(300);
    }

    @Test
    public void testCalculate() {
        long time1 = 100;
        long time2 = 200;
        function.accept(MeterEntity.newService("avg_resp_time", Layer.GENERAL), time1);
        function.accept(MeterEntity.newService("avg_resp_time", Layer.GENERAL), time2);
        function.calculate();
        assertThat(function.getValue()).isEqualTo(150);
    }

    @Test
    public void testSerialize() {
        long time = 1597113447737L;
        MeterEntity meterEntity = MeterEntity.newService("avg_resp_time", Layer.GENERAL);
        meterEntity.setAttr0("testAttr");
        function.accept(meterEntity, time);
        AvgFunction function2 = Mockito.spy(AvgFunction.class);
        function2.deserialize(function.serialize().build());
        assertThat(function2.getEntityId()).isEqualTo(function.getEntityId());
        assertThat(function2.getTimeBucket()).isEqualTo(function.getTimeBucket());
        assertThat(function2.getAttr0()).isEqualTo(function.getAttr0());
    }

    @Test
    public void testBuilder() throws IllegalAccessException, InstantiationException {
        long time = 1597113447737L;
        MeterEntity meterEntity = MeterEntity.newService("avg_resp_time", Layer.GENERAL);
        meterEntity.setAttr0("testAttr");
        function.accept(meterEntity, time);
        function.calculate();
        StorageBuilder<AvgFunction> storageBuilder = function.builder().newInstance();

        final HashMapConverter.ToStorage toStorage = new HashMapConverter.ToStorage();
        storageBuilder.entity2Storage(function, toStorage);
        final Map<String, Object> map = toStorage.obtain();
        map.put(AvgFunction.VALUE, map.get(AvgFunction.VALUE));

        AvgFunction function2 = storageBuilder.storage2Entity(new HashMapConverter.ToEntity(map));
        assertThat(function2.getAttr0()).isEqualTo(function.getAttr0());
    }

    @Test
    public void testToHour() {
        long time1 = 100;
        long time2 = 200;
        function.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        MeterEntity meterEntity = MeterEntity.newService("avg_resp_time", Layer.GENERAL);
        meterEntity.setAttr0("testAttr");
        function.accept(meterEntity, time1);
        function.accept(meterEntity, time2);
        function.calculate();

        final AvgFunction hourFunction = (AvgFunction) function.toHour();
        hourFunction.calculate();

        assertThat(hourFunction.getValue()).isEqualTo(150);
        assertThat(hourFunction.getAttr0()).isEqualTo("testAttr");
    }

    @Test
    public void testToDay() {
        long time1 = 100;
        long time2 = 200;
        MeterEntity meterEntity = MeterEntity.newService("avg_resp_time", Layer.GENERAL);
        meterEntity.setAttr0("testAttr");
        function.accept(meterEntity, time1);
        function.accept(meterEntity, time2);
        function.calculate();

        final AvgFunction dayFunction = (AvgFunction) function.toDay();
        dayFunction.calculate();
        assertThat(dayFunction.getValue()).isEqualTo(150);
        assertThat(dayFunction.getAttr0()).isEqualTo("testAttr");
    }

    private static class AvgFunctionInst extends AvgFunction {
        @Override
        public AcceptableValue<Long> createNew() {
            return new AvgFunctionTest.AvgFunctionInst();
        }
    }
}
