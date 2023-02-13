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

import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
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

import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(MockitoExtension.class)
public class LatestFunctionTest {

    @Spy
    private LatestFunction function;

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
        long time = 1597113318673L;
        function.accept(MeterEntity.newService("latest_sync_time", Layer.GENERAL), time);
        assertThat(function.getValue()).isEqualTo(time);
        time = 1597113447737L;
        function.accept(MeterEntity.newService("latest_sync_time", Layer.GENERAL), time);
        assertThat(function.getValue()).isEqualTo(time);
    }

    @Test
    public void testCalculate() {
        long time1 = 1597113318673L;
        long time2 = 1597113447737L;
        function.accept(MeterEntity.newService("latest_sync_time", Layer.GENERAL), time1);
        function.accept(MeterEntity.newService("latest_sync_time", Layer.GENERAL), time2);
        function.calculate();
        assertThat(function.getValue()).isEqualTo(time2);
    }

    @Test
    public void testSerialize() {
        long time = 1597113447737L;
        function.accept(MeterEntity.newService("latest_sync_time", Layer.GENERAL), time);
        LatestFunction function2 = Mockito.spy(LatestFunction.class);
        function2.deserialize(function.serialize().build());
        assertThat(function2.getEntityId()).isEqualTo(function.getEntityId());
        assertThat(function2.getTimeBucket()).isEqualTo(function.getTimeBucket());
    }

    @Test
    public void testBuilder() throws IllegalAccessException, InstantiationException {
        long time = 1597113447737L;
        function.accept(MeterEntity.newService("latest_sync_time", Layer.GENERAL), time);
        function.calculate();
        StorageBuilder<LatestFunction> storageBuilder = function.builder().newInstance();

        final HashMapConverter.ToStorage toStorage = new HashMapConverter.ToStorage();
        storageBuilder.entity2Storage(function, toStorage);
        final Map<String, Object> map = toStorage.obtain();
        map.put(LatestFunction.VALUE, map.get(LatestFunction.VALUE));

        LatestFunction function2 = storageBuilder.storage2Entity(new HashMapConverter.ToEntity(map));
        assertThat(function2.getValue()).isEqualTo(function.getValue());
    }
}
