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

package org.apache.skywalking.oap.server.core.analysis.meter.function.latest;

import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class LatestFunctionTest {

    @Spy
    private LatestFunction function;

    @Test
    public void testAccept() {
        long time = 1597113318673L;
        function.accept(MeterEntity.newService("latest_sync_time"), time);
        assertThat(function.getValue(), is(time));
        time = 1597113447737L;
        function.accept(MeterEntity.newService("latest_sync_time"), time);
        assertThat(function.getValue(), is(time));
    }

    @Test
    public void testCalculate() {
        long time1 = 1597113318673L;
        long time2 = 1597113447737L;
        function.accept(MeterEntity.newService("latest_sync_time"), time1);
        function.accept(MeterEntity.newService("latest_sync_time"), time2);
        function.calculate();
        assertThat(function.getValue(), is(time2));
    }

    @Test
    public void testSerialize() {
        long time = 1597113447737L;
        function.accept(MeterEntity.newService("latest_sync_time"), time);
        LatestFunction function2 = Mockito.spy(LatestFunction.class);
        function2.deserialize(function.serialize().build());
        assertThat(function2.getEntityId(), is(function.getEntityId()));
        assertThat(function2.getTimeBucket(), is(function.getTimeBucket()));
    }

    @Test
    public void testBuilder() throws IllegalAccessException, InstantiationException {
        long time = 1597113447737L;
        function.accept(MeterEntity.newService("latest_sync_time"), time);
        function.calculate();
        StorageBuilder<LatestFunction> storageBuilder = function.builder().newInstance();

        Map<String, Object> map = storageBuilder.data2Map(function);
        map.put(LatestFunction.VALUE, map.get(LatestFunction.VALUE));

        LatestFunction function2 = storageBuilder.map2Data(map);
        assertThat(function2.getValue(), is(function.getValue()));
    }
}