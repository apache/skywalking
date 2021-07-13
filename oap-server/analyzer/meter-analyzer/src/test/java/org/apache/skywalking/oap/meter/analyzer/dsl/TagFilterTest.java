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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static com.google.common.collect.ImmutableMap.of;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

@Slf4j
@RunWith(Parameterized.class)
public class TagFilterTest {

    @Parameterized.Parameter
    public String name;

    @Parameterized.Parameter(1)
    public ImmutableMap<String, SampleFamily> input;

    @Parameterized.Parameter(2)
    public String expression;

    @Parameterized.Parameter(3)
    public Result want;

    @Parameterized.Parameter(4)
    public boolean isThrow;

    @Parameterized.Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {
                "default",
                of("instance_cpu_percentage", SampleFamily.EMPTY),
                "instance_cpu_percentage",
                Result.fail("Parsed result is an EMPTY sample family"),
                false,
            },
            {
                "single-value",
                of("instance_cpu_percentage", SampleFamilyBuilder.newBuilder(Sample.builder().value(1600592418480.0).name("instance_cpu_percentage").build()).build()),
                "instance_cpu_percentage",
                Result.success(SampleFamilyBuilder.newBuilder(Sample.builder().value(1600592418480.0).name("instance_cpu_percentage").build()).build()),
                false,
            },
        });
    }

    @Test
    public void test() {
        Expression e = DSL.parse(expression);
        Result r = null;
        try {
            r = e.run(input);
        } catch (Throwable t) {
            if (isThrow) {
                return;
            }
            log.error("Test failed", t);
            fail("Should not throw anything");
        }
        if (isThrow) {
            fail("Should throw something");
        }
        assertThat(r, is(want));
    }
}