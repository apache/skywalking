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

import io.vavr.collection.Stream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import static org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgLabeledFunction.COUNT;
import static org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgLabeledFunction.SUMMATION;
import static org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgLabeledFunction.VALUE;
import static org.hamcrest.core.Is.is;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class AvgLabeledFunctionTest {
    @Spy
    private AvgLabeledFunction function;

    @Test
    public void testAccept() {
        function.accept(MeterEntity.newService("request_count"), build(asList("200", "404"), asList(10L, 2L)));
        assertResult(asList("200", "404"), asList(10L, 2L), asList(1L, 1L));
        function.accept(MeterEntity.newService("request_count"), build(asList("200", "500"), asList(2L, 3L)));
        assertResult(asList("200", "404", "500"), asList(12L, 2L, 3L), asList(2L, 1L, 1L));
    }

    @Test
    public void testCalculate() {
        function.accept(MeterEntity.newService("request_count"), build(asList("200", "404"), asList(10L, 2L)));
        function.accept(MeterEntity.newService("request_count"), build(asList("200", "500"), asList(2L, 3L)));
        function.calculate();

        assertThat(function.getValue().sortedKeys(Comparator.naturalOrder()), is(asList("200", "404", "500")));
        assertThat(function.getValue().sortedValues(Comparator.naturalOrder()), is(asList(6L, 2L, 3L)));
    }

    @Test
    public void testSerialize() {
        function.accept(MeterEntity.newService("request_count"), build(asList("200", "404"), asList(10L, 2L)));
        AvgLabeledFunction function2 = Mockito.spy(AvgLabeledFunction.class);
        function2.deserialize(function.serialize().build());
        assertThat(function2.getEntityId(), is(function.getEntityId()));
        assertThat(function2.getTimeBucket(), is(function.getTimeBucket()));
    }

    @Test
    public void testBuilder() throws IllegalAccessException, InstantiationException {
        function.accept(MeterEntity.newService("request_count"), build(asList("200", "404"), asList(10L, 2L)));
        function.calculate();
        StorageHashMapBuilder<AvgLabeledFunction> storageBuilder = function.builder().newInstance();

        Map<String, Object> map = storageBuilder.entity2Storage(function);
        map.put(SUMMATION, ((DataTable) map.get(SUMMATION)).toStorageData());
        map.put(COUNT, ((DataTable) map.get(COUNT)).toStorageData());
        map.put(VALUE, ((DataTable) map.get(VALUE)).toStorageData());

        AvgLabeledFunction function2 = storageBuilder.storage2Entity(map);
        assertThat(function2.getValue(), is(function.getValue()));
    }

    private DataTable build(List<String> keys, List<Long> values) {
        DataTable result = new DataTable();
        Stream.ofAll(keys).forEachWithIndex((key, i) -> result.put(key, values.get(i)));
        return result;
    }

    private void assertResult(List<String> expectedKeys, List<Long> expectedValues, List<Long> expectedCount) {
        assertSummation(expectedKeys, expectedValues);
        assertCount(expectedKeys, expectedCount);
    }

    private void assertCount(List<String> expectedKeys, List<Long> expectedCount) {
        List<String> keys = function.getCount().sortedKeys(Comparator.comparingInt(Integer::parseInt));
        assertThat(keys, is(expectedKeys));
        List<Long> values = function.getCount().sortedValues(Comparator.comparingLong(Long::parseLong));
        assertThat(values, is(expectedCount));
    }

    private void assertSummation(List<String> expectedKeys, List<Long> expectedValues) {
        List<String> keys = function.getSummation().sortedKeys(Comparator.comparingInt(Integer::parseInt));
        assertThat(keys, is(expectedKeys));
        List<Long> values = function.getSummation().sortedValues(Comparator.comparingLong(Long::parseLong));
        assertThat(values, is(expectedValues));
    }
}