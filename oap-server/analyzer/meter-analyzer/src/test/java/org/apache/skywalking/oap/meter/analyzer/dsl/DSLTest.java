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
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static com.google.common.collect.ImmutableMap.of;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

@RunWith(Parameterized.class)
public class DSLTest {

    @Parameterized.Parameter
    public String name;

    @Parameterized.Parameter(1)
    public ImmutableMap<String, SampleFamily> input;

    @Parameterized.Parameter(2)
    public String expression;

    @Parameterized.Parameter(3)
    public Result want;

    @Parameterized.Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {
               "default",
                of("instance_cpu_percentage", SampleFamily.EMPTY),
                "instance_cpu_percentage",
                Result.success(SampleFamily.EMPTY),
            },
            {
                "single-value",
                of("instance_cpu_percentage", SampleFamily.build(Sample.builder().value(1600592418480.0).build())),
                "instance_cpu_percentage",
                Result.success(SampleFamily.build(Sample.builder().value(1600592418480.0).build())),
            },
            {
                "label-equal",
                of("instance_cpu_percentage", SampleFamily.build(
                    Sample.builder().labels(of("idc", "t1")).value(1600592418480.0).build(),
                    Sample.builder().labels(of("idc", "t2")).value(1600592418481.0).build()
                )),
                "instance_cpu_percentage.tagEqual('idc','t1')",
                Result.success(SampleFamily.build(
                    Sample.builder().labels(of("idc", "t1")).value(1600592418480.0).build()
                )),
            },
            {
                "label-not-equal",
                of("instance_cpu_percentage", SampleFamily.build(
                    Sample.builder().labels(of("idc", "t1")).value(1600592418480.0).build(),
                    Sample.builder().labels(of("idc", "t2")).value(1600592418481.0).build()
                )),
                "instance_cpu_percentage.tagNotEqual('idc','t2')",
                Result.success(SampleFamily.build(
                    Sample.builder().labels(of("idc", "t1")).value(1600592418480.0).build()
                )),
            },
            {
                "plus",
                of("instance_cpu_percentage", SampleFamily.build(
                    Sample.builder().labels(of("idc", "t1")).value(1600592418480.0).build(),
                    Sample.builder().labels(of("idc", "t2")).value(1600592418481.0).build()
                )),
                "instance_cpu_percentage.tagEqual('idc','t1') + 1000",
                Result.success(SampleFamily.build(
                    Sample.builder().labels(of("idc", "t1")).value(1600592419480.0).build()
                )),
            },
        });
    }

    @Test
    public void test() {
        Expression e = DSL.parse(expression);
        Result r = e.run(input);
        assertThat(r, is(want));
    }
}