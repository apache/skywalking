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
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;

import static com.google.common.collect.ImmutableMap.of;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

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
                "instance_cpu_percentage.filter({ tags -> tags.str == 'val1' })",
                Result.success(SampleFamily.build(sf.context, sf.samples[0]))
            },
            {
                "filter-none",
                of("instance_cpu_percentage", sf),
                "instance_cpu_percentage.filter({ tags -> tags.str == 'val2' })",
                Result.success(SampleFamily.build(sf.context, sf.samples[1]))
            },
            {
                "filter-not-equal",
                of("instance_cpu_percentage", sf),
                "instance_cpu_percentage.filter({ tags -> tags.str != 'val1' })",
                Result.success(SampleFamily.build(sf.context, sf.samples[1]))
            },
            {
                "filter-in",
                of("instance_cpu_percentage", sf),
                "instance_cpu_percentage.filter({ tags -> tags.str in [ 'val2' ] })",
                Result.success(SampleFamily.build(sf.context, sf.samples[1]))
            },
            {
                "filter-in",
                of("instance_cpu_percentage", sf),
                "instance_cpu_percentage.filter({ tags -> tags.str in [ 'val1', 'val2' ] })",
                Result.success(sf)
            },
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void test(String name,
                     ImmutableMap<String, SampleFamily> input,
                     String expression,
                     Result want) {
        Expression e = DSL.parse(name, expression);
        Result r = e.run(input);
        assertThat(r).isEqualTo(want);
    }
}
