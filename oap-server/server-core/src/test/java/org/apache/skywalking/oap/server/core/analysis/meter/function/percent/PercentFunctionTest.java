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

package org.apache.skywalking.oap.server.core.analysis.meter.function.percent;

import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PercentFunctionTest {
    private PercentFunction function;

    @BeforeAll
    static void setup() {
        MeterEntity.setNamingControl(new NamingControl(512, 512, 512, new EndpointNameGrouping()));
    }

    @AfterAll
    static void tearDown() {
        MeterEntity.setNamingControl(null);
    }

    @BeforeEach
    void before() {
        function = new PercentFunctionInst();
        function.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
    }

    @Test
    void theShareOfMatchesIsInTenThousandths() {
        final MeterEntity entity = MeterEntity.newService("service-test", Layer.GENERAL);
        function.accept(entity, true);
        function.accept(entity, true);
        function.accept(entity, false);
        function.accept(entity, true);
        function.calculate();
        assertEquals(4, function.getTotal());
        assertEquals(3, function.getMatch());
        assertEquals(7500, function.getValue());
    }

    @Test
    void mergedValuesAddTheirCounts() {
        final MeterEntity entity = MeterEntity.newService("service-test", Layer.GENERAL);
        function.accept(entity, true);
        final PercentFunction other = new PercentFunctionInst();
        other.accept(entity, false);
        other.accept(entity, false);
        other.accept(entity, false);

        function.combine(other);
        function.calculate();
        assertEquals(2500, function.getValue());
    }

    @Test
    void theHourAndTheDayKeepTheCounts() {
        final MeterEntity entity = MeterEntity.newService("service-test", Layer.GENERAL);
        function.accept(entity, true);
        function.accept(entity, false);
        final PercentFunction hour = (PercentFunction) function.toHour();
        hour.calculate();
        assertEquals(5000, hour.getValue());
        final PercentFunction day = (PercentFunction) function.toDay();
        day.calculate();
        assertEquals(5000, day.getValue());
    }

    @Test
    void theRemoteFormRoundTrips() {
        final MeterEntity entity = MeterEntity.newService("service-test", Layer.GENERAL);
        function.accept(entity, true);
        function.accept(entity, false);
        final PercentFunction received = new PercentFunctionInst();
        received.deserialize(function.serialize().build());
        assertEquals(function.getEntityId(), received.getEntityId());
        assertEquals(function.getTotal(), received.getTotal());
        assertEquals(function.getMatch(), received.getMatch());
    }

    private static class PercentFunctionInst extends PercentFunction {
        @Override
        public AcceptableValue<Boolean> createNew() {
            return new PercentFunctionInst();
        }
    }
}
