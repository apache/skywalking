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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
public class ValueFilterTest {
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {
                "valueEqual",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3")).value(1).name("http_success_request").build()
                ).build()),
                "http_success_request.valueEqual(1)",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t3")).value(1).name("http_success_request").build()
                ).build()),
                false,
                },
            {
                "valueNotEqual",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3")).value(1).name("http_success_request").build()
                ).build()),
                "http_success_request.valueNotEqual(1)",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).name("http_success_request").build()
                ).build()),
                false,
                },
            {
                "valueGreater",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3")).value(1).name("http_success_request").build()
                ).build()),
                "http_success_request.valueGreater(1)",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).name("http_success_request").build()
                ).build()),
                false,
                },
            {
                "valueGreaterEqual",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3")).value(1).name("http_success_request").build()
                ).build()),
                "http_success_request.valueGreaterEqual(1)",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3")).value(1).name("http_success_request").build()
                ).build()),
                false,
                },
            {
                "valueLess",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3")).value(1).name("http_success_request").build()
                ).build()),
                "http_success_request.valueLess(2)",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t3")).value(1).name("http_success_request").build()
                ).build()),
                false,
                },
            {
                "valueLessEqual",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3")).value(1).name("http_success_request").build()
                ).build()),
                "http_success_request.valueLessEqual(2)",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t2")).value(2).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3")).value(1).name("http_success_request").build()
                ).build()),
                false,
                },
            });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void test(final String name,
                     final ImmutableMap<String, SampleFamily> input,
                     final String expression,
                     final Result want,
                     final boolean isThrow) {
        Expression e = DSL.parse(name, expression);
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
        assertThat(r).isEqualTo(want);
    }
}
