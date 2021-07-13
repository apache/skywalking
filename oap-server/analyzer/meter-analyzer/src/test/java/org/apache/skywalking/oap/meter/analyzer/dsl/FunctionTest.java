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
public class FunctionTest {

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
                "tag-override",
                of("instance_cpu_percentage", SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("region", "us")).name("instance_cpu_percentage").build()).build()),
                "instance_cpu_percentage.tag({ ['svc':'product', 'instance':'10.0.0.1'] })",
                Result.success(SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("svc", "product", "instance", "10.0.0.1")).name("instance_cpu_percentage").build()).build()),
                false,
            },
            {
                "tag-add",
                of("instance_cpu_percentage", SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("region", "us")).name("instance_cpu_percentage").build()).build()),
                "instance_cpu_percentage.tag({tags -> tags.az = 'az1' })",
                Result.success(SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("region", "us", "az", "az1")).name("instance_cpu_percentage").build()).build()),
                false,
            },
            {
                "tag-remove",
                of("instance_cpu_percentage", SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("region", "us")).name("instance_cpu_percentage").build()).build()),
                "instance_cpu_percentage.tag({tags -> tags.remove('region') })",
                Result.success(SampleFamilyBuilder.newBuilder(Sample.builder().labels(ImmutableMap.of()).name("instance_cpu_percentage").build()).build()),
                false,
            },
            {
                "tag-update",
                of("instance_cpu_percentage", SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("region", "us")).name("instance_cpu_percentage").build()).build()),
                "instance_cpu_percentage.tag({tags -> if (tags['region'] == 'us') {tags.region = 'zh'} })",
                Result.success(SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("region", "zh")).name("instance_cpu_percentage").build()).build()),
                false,
            },
            {
                "tag-append",
                of("instance_cpu_percentage", SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("region", "us")).name("instance_cpu_percentage").build()).build()),
                "instance_cpu_percentage.tag({tags -> tags.region = 'prefix::' + tags.region})",
                Result.success(SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("region", "prefix::us")).name("instance_cpu_percentage").build()).build()),
                false,
                },
            {
                "histogram",
                of("instance_cpu_percentage", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("le", "0.025")).value(100).name("instance_cpu_percentage").build(),
                    Sample.builder().labels(of("le", "1.25")).value(300).name("instance_cpu_percentage").build(),
                    Sample.builder().labels(of("le", "0.75")).value(122).name("instance_cpu_percentage").build(),
                    Sample.builder().labels(of("le", String.valueOf(Integer.MAX_VALUE))).value(410).name("instance_cpu_percentage").build()).build()
                ),
                "instance_cpu_percentage.histogram()",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("le", "0")).value(100).name("instance_cpu_percentage").build(),
                    Sample.builder().labels(of("le", "25")).value(22).name("instance_cpu_percentage").build(),
                    Sample.builder().labels(of("le", "750")).value(178).name("instance_cpu_percentage").build(),
                    Sample.builder().labels(of("le", "1250")).value(110).name("instance_cpu_percentage").build()).build()
                ),
                false,
            },
            {
                "histogram_percentile",
                of("instance_cpu_percentage", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("le", "0.025")).value(100).name("instance_cpu_percentage").build(),
                    Sample.builder().labels(of("le", "1.25")).value(300).name("instance_cpu_percentage").build(),
                    Sample.builder().labels(of("le", "0.75")).value(122).name("instance_cpu_percentage").build(),
                    Sample.builder().labels(of("le", String.valueOf(Integer.MAX_VALUE))).value(410).name("instance_cpu_percentage").build()).build()
                ),
                "instance_cpu_percentage.histogram().histogram_percentile([75,99])",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("le", "0")).value(100).name("instance_cpu_percentage").build(),
                    Sample.builder().labels(of("le", "25")).value(22).name("instance_cpu_percentage").build(),
                    Sample.builder().labels(of("le", "750")).value(178).name("instance_cpu_percentage").build(),
                    Sample.builder().labels(of("le", "1250")).value(110).name("instance_cpu_percentage").build()).build()
                ),
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