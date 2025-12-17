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
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
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
public class SumPerMinLabeledFunctionTest {

    private SumPerMinLabeledFunctionInst function;
    private DataTable table1;
    private DataTable table2;

    @BeforeAll
    public static void setup() {
        MeterEntity.setNamingControl(
            new NamingControl(512, 512, 512, new EndpointNameGrouping()));
    }

    @BeforeEach
    public void before() {
        function = new SumPerMinLabeledFunctionInst();
        function.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));

        table1 = new DataTable();
        table1.put("200", 100L);
        table1.put("300", 50L);

        table2 = new DataTable();
        table2.put("200", 120L);
        table2.put("300", 17L);
        table2.put("400", 77L);
    }

    @AfterAll
    public static void tearDown() {
        MeterEntity.setNamingControl(null);
    }

    @Test
    public void testAccept() {
        function.accept(MeterEntity.newService("sum_sync_time", Layer.GENERAL), table1);
        function.calculate();
        assertThat(function.getValue()).isEqualTo(table1);
        function.accept(MeterEntity.newService("sum_sync_time", Layer.GENERAL), table2);
        function.calculate();
        assertThat(function.getValue()).isEqualTo(table1.append(table2));
    }

    @Test
    public void testCalculate() {
        function.accept(MeterEntity.newService("sum_sync_time", Layer.GENERAL), table1);
        function.accept(MeterEntity.newService("sum_sync_time", Layer.GENERAL), table2);
        function.calculate();
        assertThat(function.getValue()).isEqualTo(table1.append(table2));
    }

    @Test
    public void testHour() {
        MeterEntity meterEntity1 = MeterEntity.newService("sum_sync_time", Layer.GENERAL);
        meterEntity1.setAttr0("testAttr");
        function.accept(meterEntity1, table1);
        MeterEntity meterEntity2 = MeterEntity.newService("sum_sync_time", Layer.GENERAL);
        meterEntity2.setAttr0("testAttr");
        function.accept(meterEntity2, table2);
        function.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        function.calculate();
        final SumPerMinLabeledFunction hourFunction = (SumPerMinLabeledFunction) function.toHour();
        hourFunction.calculate();
        final DataTable result = new DataTable();
        result.append(table1);
        result.append(table2);
        for (String key : result.keys()) {
            result.put(key, result.get(key) / 60);
        }
        assertThat(hourFunction.getValue()).isEqualTo(result);
        assertThat(hourFunction.getAttr0()).isEqualTo("testAttr");
    }

    @Test
    public void testSerialize() {
        function.accept(MeterEntity.newService("sum_sync_time", Layer.GENERAL), table1);
        SumPerMinLabeledFunction function2 = Mockito.spy(SumPerMinLabeledFunction.class);
        function2.deserialize(function.serialize().build());
        assertThat(function2.getEntityId()).isEqualTo(function.getEntityId());
        assertThat(function2.getTimeBucket()).isEqualTo(function.getTimeBucket());
    }

    @Test
    public void testBuilder() throws IllegalAccessException, InstantiationException {
        function.accept(MeterEntity.newService("sum_sync_time", Layer.GENERAL), table1);
        function.calculate();
        StorageBuilder<SumPerMinLabeledFunction> storageBuilder = function.builder().newInstance();

        final HashMapConverter.ToStorage toStorage = new HashMapConverter.ToStorage();
        storageBuilder.entity2Storage(function, toStorage);
        final Map<String, Object> map = toStorage.obtain();
        map.put(SumPerMinLabeledFunction.VALUE, ((DataTable) map.get(SumPerMinLabeledFunction.VALUE)).toStorageData());
        map.put(SumPerMinLabeledFunction.TOTAL, ((DataTable) map.get(SumPerMinLabeledFunction.TOTAL)).toStorageData());

        SumPerMinLabeledFunction function2 = storageBuilder.storage2Entity(new HashMapConverter.ToEntity(map));
        assertThat(function2.getValue()).isEqualTo(function.getValue());
    }

    private static class SumPerMinLabeledFunctionInst extends SumPerMinLabeledFunction {
        @Override
        public AcceptableValue<DataTable> createNew() {
            return new SumPerMinLabeledFunctionInst();
        }
    }
}
