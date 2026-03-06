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

package org.apache.skywalking.oap.meter.analyzer.dsl;

import com.google.common.collect.ImmutableMap;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.google.common.collect.ImmutableMap.of;

public class DecorateTest {
    @Test
    public void testDecorate() {
        MeterEntity.setNamingControl(
            new NamingControl(512, 512, 512, new EndpointNameGrouping()));
        ImmutableMap<String, SampleFamily> input = ImmutableMap.of(
            "http_success_request",
            SampleFamilyBuilder.newBuilder(
                Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                Sample.builder()
                      .labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1"))
                      .value(100)
                      .name("http_success_request")
                      .build()
            ).build()
        );
        String expression = "http_success_request.sum(['idc']).service(['idc'], Layer.GENERAL).decorate({ me -> me.attr0 = me.layer.name()})";
        Expression e = DSL.parse("decorate", expression);
        Result r = e.run(input);
        Assertions.assertTrue(r.isSuccess());
        Assertions.assertEquals(
            "GENERAL", r.getData().context.getMeterSamples().keySet().stream().findFirst().get().getAttr0());
    }
}
