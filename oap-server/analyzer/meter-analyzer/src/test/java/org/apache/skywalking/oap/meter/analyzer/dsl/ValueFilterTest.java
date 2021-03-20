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
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static com.google.common.collect.ImmutableMap.of;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

@Slf4j
@RunWith(Parameterized.class)
public class ValueFilterTest {

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
                "valueEqual",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).build(),
                    Sample.builder().labels(of("idc", "t3")).value(1).build()
                ).build()),
                "http_success_request.valueEqual(1)",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t3")).value(1).build()
                ).build()),
                false,
                },
            {
                "valueNotEqual",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).build(),
                    Sample.builder().labels(of("idc", "t3")).value(1).build()
                ).build()),
                "http_success_request.valueNotEqual(1)",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).build()
                ).build()),
                false,
                },
            {
                "valueGreater",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).build(),
                    Sample.builder().labels(of("idc", "t3")).value(1).build()
                ).build()),
                "http_success_request.valueGreater(1)",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).build()
                ).build()),
                false,
                },
            {
                "valueGreaterEqual",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).build(),
                    Sample.builder().labels(of("idc", "t3")).value(1).build()
                ).build()),
                "http_success_request.valueGreaterEqual(1)",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).build(),
                    Sample.builder().labels(of("idc", "t3")).value(1).build()
                ).build()),
                false,
                },
            {
                "valueLess",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).build(),
                    Sample.builder().labels(of("idc", "t3")).value(1).build()
                ).build()),
                "http_success_request.valueLess(2)",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t3")).value(1).build()
                ).build()),
                false,
                },
            {
                "valueLessEqual",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).build(),
                    Sample.builder().labels(of("idc", "t3")).value(1).build()
                ).build()),
                "http_success_request.valueLessEqual(2)",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).build(),
                    Sample.builder().labels(of("idc", "t3")).value(1).build()
                ).build()),
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