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
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.skywalking.oap.server.core.analysis.meter.function.sum.SumLabeledFunction.VALUE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(MockitoExtension.class)
public class SumLabeledFunctionTest {

    private SumLabeledFunction function;

    private static final DataTable HTTP_CODE_COUNT_1 = new DataTable("200,1|301,2|404,3|502,4");
    private static final DataTable HTTP_CODE_COUNT_2 = new DataTable("200,2|404,4|502,5|505,1");

    @BeforeAll
    public static void setup() {
        MeterEntity.setNamingControl(
            new NamingControl(512, 512, 512, new EndpointNameGrouping()));
    }

    @BeforeEach
    public void before() {
        function = new SumLabeledFunctionInst();
        function.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
    }

    @AfterAll
    public static void tearDown() {
        MeterEntity.setNamingControl(null);
    }

    @Test
    public void testAccept() {
        function.accept(
            MeterEntity.newService("service-test", Layer.GENERAL),
            HTTP_CODE_COUNT_1
        );
        function.accept(
            MeterEntity.newService("service-test", Layer.GENERAL),
            HTTP_CODE_COUNT_2
        );

        Assertions.assertEquals(function.getValue(), new DataTable("200,3|301,2|404,7|502,9|505,1"));
    }

    @Test
    public void testToHour() {
        MeterEntity meterEntity1 = MeterEntity.newService("service-test", Layer.GENERAL);
        meterEntity1.setAttr0("testAttr");
        function.accept(meterEntity1, HTTP_CODE_COUNT_1);
        MeterEntity meterEntity2 = MeterEntity.newService("service-test", Layer.GENERAL);
        meterEntity2.setAttr0("testAttr");
        function.accept(meterEntity2, HTTP_CODE_COUNT_2);
        function.calculate();

        final SumLabeledFunction hourFunction = (SumLabeledFunction) function.toHour();
        hourFunction.calculate();

        Assertions.assertEquals(hourFunction.getValue(), new DataTable("200,3|301,2|404,7|502,9|505,1"));
        assertThat(hourFunction.getAttr0()).isEqualTo("testAttr");
    }

    @Test
    public void testToDay() {
        MeterEntity meterEntity1 = MeterEntity.newService("service-test", Layer.GENERAL);
        meterEntity1.setAttr0("testAttr");
        function.accept(meterEntity1, HTTP_CODE_COUNT_1);
        MeterEntity meterEntity2 = MeterEntity.newService("service-test", Layer.GENERAL);
        meterEntity2.setAttr0("testAttr");
        function.accept(meterEntity2, HTTP_CODE_COUNT_2);
        function.calculate();

        final SumLabeledFunction dayFunction = (SumLabeledFunction) function.toDay();
        dayFunction.calculate();

        Assertions.assertEquals(dayFunction.getValue(), new DataTable("200,3|301,2|404,7|502,9|505,1"));
        assertThat(dayFunction.getAttr0()).isEqualTo("testAttr");
    }

    @Test
    public void testSerialization() {
        MeterEntity meterEntity1 = MeterEntity.newService("service-test", Layer.GENERAL);
        meterEntity1.setAttr0("testAttr");
        function.accept(meterEntity1, HTTP_CODE_COUNT_1);
        MeterEntity meterEntity2 = MeterEntity.newService("service-test", Layer.GENERAL);
        meterEntity2.setAttr0("testAttr");
        function.accept(meterEntity2, HTTP_CODE_COUNT_2);

        SumLabeledFunction function2 = new SumLabeledFunctionInst();
        function2.deserialize(function.serialize().build());

        Assertions.assertEquals(function, function2);
        Assertions.assertEquals(function.getValue(), function2.getValue());
        assertThat(function2.getAttr0()).isEqualTo(function.getAttr0());
    }

    @Test
    public void testBuilder() throws IllegalAccessException, InstantiationException {
        MeterEntity meterEntity = MeterEntity.newService("service-test", Layer.GENERAL);
        meterEntity.setAttr0("testAttr");
        function.accept(meterEntity, HTTP_CODE_COUNT_1);
        function.calculate();

        StorageBuilder<SumLabeledFunction> storageBuilder = function.builder().newInstance();

        final HashMapConverter.ToStorage toStorage = new HashMapConverter.ToStorage();
        storageBuilder.entity2Storage(function, toStorage);
        final Map<String, Object> map = toStorage.obtain();
        map.put(VALUE, ((DataTable) map.get(VALUE)).toStorageData());

        SumLabeledFunction function2 = storageBuilder.storage2Entity(new HashMapConverter.ToEntity(map));

        Assertions.assertEquals(function, function2);
        Assertions.assertEquals(function2.getValue(), function2.getValue());
        assertThat(function2.getAttr0()).isEqualTo(function.getAttr0());
    }

    private static class SumLabeledFunctionInst extends SumLabeledFunction {
        @Override
        public AcceptableValue<DataTable> createNew() {
            return new SumLabeledFunctionInst();
        }
    }
}