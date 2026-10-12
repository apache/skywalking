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

package org.apache.skywalking.oap.server.core.analysis.meter.function.apdex;

import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.ApdexArgument;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApdexFunctionTest {
    private ApdexFunction function;

    @BeforeAll
    static void setup() {
        MeterEntity.setNamingControl(new NamingControl(512, 512, 512, new EndpointNameGrouping()));
        // T is 100 ms for `fast`, 500 ms for everything else.
        ApdexFunction.setDICT(name -> "fast".equals(name) ? 100 : 500);
    }

    @AfterAll
    static void tearDown() {
        MeterEntity.setNamingControl(null);
        ApdexFunction.setDICT(null);
    }

    @BeforeEach
    void before() {
        function = new ApdexFunctionInst();
        function.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
    }

    @Test
    void satisfiedToleratedAndFrustratedAsCoreOalCountsThem() {
        final MeterEntity entity = MeterEntity.newService("fast", Layer.GENERAL);
        function.accept(entity, new ApdexArgument(100, "fast", true));   // at T: satisfied
        function.accept(entity, new ApdexArgument(400, "fast", true));   // at 4T: tolerated
        function.accept(entity, new ApdexArgument(401, "fast", true));   // over 4T: frustrated
        function.accept(entity, new ApdexArgument(10, "fast", false));   // failed: frustrated however fast
        function.calculate();
        assertEquals(4, function.getTotalNum());
        assertEquals(1, function.getSNum());
        assertEquals(1, function.getTNum());
        assertEquals((10000 + 10000 / 2) / 4, function.getValue());
    }

    @Test
    void theThresholdIsTheArgumentsService() {
        final MeterEntity entity = MeterEntity.newService("slow", Layer.GENERAL);
        function.accept(entity, new ApdexArgument(300, "slow", true));
        function.calculate();
        assertEquals(10000, function.getValue(), "300 ms is under T=500");
    }

    @Test
    void mergedValuesAddTheirCounts() {
        final MeterEntity entity = MeterEntity.newService("fast", Layer.GENERAL);
        function.accept(entity, new ApdexArgument(50, "fast", true));
        final ApdexFunction other = new ApdexFunctionInst();
        other.accept(entity, new ApdexArgument(1000, "fast", true));

        function.combine(other);
        function.calculate();
        assertEquals(5000, function.getValue());
    }

    @Test
    void theRemoteFormRoundTrips() {
        final MeterEntity entity = MeterEntity.newService("fast", Layer.GENERAL);
        function.accept(entity, new ApdexArgument(50, "fast", true));
        function.accept(entity, new ApdexArgument(200, "fast", true));
        final ApdexFunction received = new ApdexFunctionInst();
        received.deserialize(function.serialize().build());
        assertEquals(function.getEntityId(), received.getEntityId());
        assertEquals(function.getTotalNum(), received.getTotalNum());
        assertEquals(function.getSNum(), received.getSNum());
        assertEquals(function.getTNum(), received.getTNum());
    }

    private static class ApdexFunctionInst extends ApdexFunction {
        @Override
        public AcceptableValue<ApdexArgument> createNew() {
            return new ApdexFunctionInst();
        }
    }
}
