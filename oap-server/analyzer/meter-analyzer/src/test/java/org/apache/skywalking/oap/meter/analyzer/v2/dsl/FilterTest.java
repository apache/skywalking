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

package org.apache.skywalking.oap.meter.analyzer.v2.dsl;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static com.google.common.collect.ImmutableMap.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * v1 filtered inline as a chain method: {@code metric.filter({tags -> ...})}. v2 has no inline
 * {@code .filter()} chain method; a MAL filter closure is compiled on its own via {@link
 * FilterExpression} (the rule's {@code filter:} field) into a {@link MalFilter} and applied to the
 * whole sample-family map. This test drives the v2 {@link FilterExpression} with the same closures
 * and inputs. {@code FilterExpression.filter} filters samples within each family and drops any
 * family that becomes EMPTY, so expected values are maps rather than a single {@code Result}.
 */
@Slf4j
public class FilterTest {
    public static Collection<Object[]> data() {
        final SampleFamily sf =
            SampleFamilyBuilder.newBuilder(
                                   Sample.builder()
                                         .value(1600592418480.0)
                                         .labels(ImmutableMap.of("str", "val1"))
                                         .name("instance_cpu_percentage")
                                         .build(),
                                   Sample.builder()
                                         .value(1600592418480.0)
                                         .labels(ImmutableMap.of("str", "val2"))
                                         .name("instance_cpu_percentage")
                                         .build())
                               .build();
        return Arrays.asList(new Object[][]{
            {
                "filter-string",
                of("instance_cpu_percentage", sf),
                "{ tags -> tags.str == 'val1' }",
                of("instance_cpu_percentage", SampleFamily.build(sf.context, sf.samples[0])),
            },
            {
                "filter-equal-val2",
                of("instance_cpu_percentage", sf),
                "{ tags -> tags.str == 'val2' }",
                of("instance_cpu_percentage", SampleFamily.build(sf.context, sf.samples[1])),
            },
            {
                "filter-not-equal",
                of("instance_cpu_percentage", sf),
                "{ tags -> tags.str != 'val1' }",
                of("instance_cpu_percentage", SampleFamily.build(sf.context, sf.samples[1])),
            },
            {
                "filter-in-single",
                of("instance_cpu_percentage", sf),
                "{ tags -> tags.str in [ 'val2' ] }",
                of("instance_cpu_percentage", SampleFamily.build(sf.context, sf.samples[1])),
            },
            {
                "filter-in-multiple",
                of("instance_cpu_percentage", sf),
                "{ tags -> tags.str in [ 'val1', 'val2' ] }",
                of("instance_cpu_percentage", sf),
            },
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void test(String name,
                     Map<String, SampleFamily> input,
                     String filterLiteral,
                     Map<String, SampleFamily> want) {
        Map<String, SampleFamily> r = new FilterExpression(filterLiteral).filter(input);
        assertEquals(want, r);
    }
}
