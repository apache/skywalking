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

import io.vavr.collection.Stream;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.apache.skywalking.oap.server.core.analysis.meter.function.sum.SumLabeledFunction.VALUE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(MockitoExtension.class)
public class SumLabeledFunctionTest {
    @Spy
    private SumLabeledFunction function;

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
    public void testAccept() {
        function.accept(
            MeterEntity.newService("request_count", Layer.GENERAL), build(asList("200", "404"), asList(10L, 2L)));
        assertResult(asList("200", "404"), asList(10L, 2L));
        function.accept(
            MeterEntity.newService("request_count", Layer.GENERAL), build(asList("200", "500"), asList(2L, 3L)));
        assertResult(asList("200", "404", "500"), asList(12L, 2L, 3L));
    }

    @Test
    public void testCalculate() {
        function.accept(
            MeterEntity.newService("request_count", Layer.GENERAL), build(asList("200", "404"), asList(10L, 2L)));
        function.accept(
            MeterEntity.newService("request_count", Layer.GENERAL), build(asList("200", "500"), asList(2L, 3L)));
        function.calculate();

        assertThat(function.getValue().sortedKeys(Comparator.naturalOrder())).isEqualTo(asList("200", "404", "500"));
        assertThat(function.getValue().sortedValues(Comparator.naturalOrder())).isEqualTo(asList(12L, 2L, 3L));
    }

    @Test
    public void testSerialize() {
        function.accept(
            MeterEntity.newService("request_count", Layer.GENERAL), build(asList("200", "404"), asList(10L, 2L)));
        SumLabeledFunction function2 = Mockito.spy(SumLabeledFunction.class);
        function2.deserialize(function.serialize().build());
        assertThat(function2.getEntityId()).isEqualTo(function.getEntityId());
        assertThat(function2.getTimeBucket()).isEqualTo(function.getTimeBucket());
    }

    @Test
    public void testBuilder() throws IllegalAccessException, InstantiationException {
        function.accept(
            MeterEntity.newService("request_count", Layer.GENERAL), build(asList("200", "404"), asList(10L, 2L)));
        function.calculate();
        StorageBuilder<SumLabeledFunction> storageBuilder = function.builder().newInstance();

        final HashMapConverter.ToStorage toStorage = new HashMapConverter.ToStorage();
        storageBuilder.entity2Storage(function, toStorage);
        final Map<String, Object> map = toStorage.obtain();
        map.put(VALUE, ((DataTable) map.get(VALUE)).toStorageData());

        SumLabeledFunction function2 = storageBuilder.storage2Entity(new HashMapConverter.ToEntity(map));
        assertThat(function2.getValue()).isEqualTo(function.getValue());
    }

    private DataTable build(List<String> keys, List<Long> values) {
        DataTable result = new DataTable();
        Stream.ofAll(keys).forEachWithIndex((key, i) -> result.put(key, values.get(i)));
        return result;
    }

    private void assertResult(List<String> expectedKeys, List<Long> expectedValues) {
        List<String> keys = function.getValue().sortedKeys(Comparator.comparingInt(Integer::parseInt));
        assertThat(keys).isEqualTo(expectedKeys);
        List<Long> values = function.getValue().sortedValues(Comparator.comparingLong(Long::parseLong));
        assertThat(values).isEqualTo(expectedValues);
    }
}
