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
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;

import static com.google.common.collect.ImmutableMap.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Grouped aggregation expressions were adapted from the v1 named-arg Groovy idiom to the v2
 * positional-list form: {@code sum(by = ['region', 'idc'])} became {@code sum(['region', 'idc'])}.
 * v2's ANTLR grammar has no named arguments. Expected outputs are unchanged from v1.
 *
 * <p>Total (un-grouped) aggregation has no v2 MAL syntax and is pinned as a negative case per
 * aggregation function: v1 {@code sum()} aggregated every sample into one, but v2's grammar
 * ({@code stringList : L_BRACKET STRING (COMMA STRING)* R_BRACKET}) requires at least one grouping
 * label and there is no no-arg overload, so both {@code sum()} and {@code sum([])} are rejected at
 * parse time. Grouping labels are mandatory in v2; every production MAL rule supplies them.
 */
@Slf4j
public class AggregationTest {
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {
                "sum",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                        Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                        Sample.builder().labels(of("idc", "t2")).value(3).name("http_success_request").build()
                    ).build()),
                // total aggregation: no v2 MAL syntax (see class javadoc), pinned as a negative case
                "http_success_request.sum()",
                Result.success(SampleFamilyBuilder.newBuilder(Sample.builder().labels(ImmutableMap.of()).value(53).name("http_success_request").build()).build()),
                true,
            },
            {
                "sum-by",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "svc", "catalog")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "svc", "product")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1")).name("http_success_request").value(50).build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1")).name("http_success_request").value(3).build()
                ).build()),
                "http_success_request.sum(['region', 'idc'])",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1", "region", "")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn")).value(53).name("http_success_request").build()
                ).build()),
                false,
            },

            {
                "min",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t3")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t2")).value(3).name("http_success_request").build()
                ).build()),
                // total aggregation: no v2 MAL syntax (see class javadoc), pinned as a negative case
                "http_success_request.min()",
                Result.success(SampleFamilyBuilder.newBuilder(Sample.builder().labels(ImmutableMap.of()).value(3).name("http_success_request").build()).build()),
                true,
            },
            {
                "min-by",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "svc", "catalog")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "svc", "product")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1")).value(3).name("http_success_request").build()
                ).build()),
                "http_success_request.min(['region', 'idc'])",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1", "region", "")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn")).value(3).name("http_success_request").build()
                ).build()),
                false,
            },
            {
                "max",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t3")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t2")).value(3).name("http_success_request").build()
                ).build()),
                // total aggregation: no v2 MAL syntax (see class javadoc), pinned as a negative case
                "http_success_request.max()",
                Result.success(SampleFamilyBuilder.newBuilder(Sample.builder().labels(ImmutableMap.of()).value(100).name("http_success_request").build()).build()),
                true,
            },
            {
                "max-by",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "svc", "catalog")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "svc", "product")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1")).value(3).name("http_success_request").build()
                ).build()),
                "http_success_request.max(['region', 'idc'])",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1", "region", "")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn")).value(50).name("http_success_request").build()
                ).build()),
                false,
            },

            {
                "avg",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t3")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t2")).value(3).name("http_success_request").build()
                ).build()),
                // total aggregation: no v2 MAL syntax (see class javadoc), pinned as a negative case
                "http_success_request.avg()",
                Result.success(SampleFamilyBuilder.newBuilder(Sample.builder().labels(ImmutableMap.of()).value(51).name("http_success_request").build()).build()),
                true,
            },
            {
                "avg-by",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "svc", "catalog")).value(51).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "svc", "product")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1")).value(3).name("http_success_request").build()
                ).build()),
                "http_success_request.avg(['region', 'idc'])",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t1", "region", "")).value(50).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t1", "region", "us")).value(75).name("http_success_request").build(),
                    Sample.builder().labels(of("idc", "t3", "region", "cn")).value(27).name("http_success_request").build()
                ).build()),
                false,
            },

            {
                "count",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                        Sample.builder().labels(of("idc", "t3")).value(100).name("http_success_request").build(),
                        Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                        Sample.builder().labels(of("idc", "t2")).value(3).name("http_success_request").build()
                ).build()),
                // total aggregation: no v2 MAL syntax (see class javadoc), pinned as a negative case
                "http_success_request.count()",
                Result.success(SampleFamilyBuilder.newBuilder(Sample.builder().labels(ImmutableMap.of()).value(3).name("http_success_request").build()).build()),
                true,
            },
            {
                "count-by-one",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                        Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                        Sample.builder().labels(of("idc", "t1", "region", "cn", "instance", "10.0.0.1")).value(50).name("http_success_request").build(),
                        Sample.builder().labels(of("idc", "t2", "region", "us", "instance", "10.0.0.2")).value(50).name("http_success_request").build(),
                        Sample.builder().labels(of("idc", "t1", "region", "us", "instance", "10.0.0.3")).value(100).name("http_success_request").build(),
                        Sample.builder().labels(of("idc", "t2", "region", "cn", "instance", "10.0.0.3")).value(3).name("http_success_request").build()
                ).build()),
                "http_success_request.count(['instance'])",
                Result.success(SampleFamilyBuilder.newBuilder(
                        Sample.builder().labels(ImmutableMap.of()).value(3).name("http_success_request").build()
                ).build()),
                false,
            },
            {
                "count-by-multi",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                        Sample.builder().labels(of("idc", "t1")).value(50).name("http_success_request").build(),
                        Sample.builder().labels(of("idc", "t1", "region", "cn", "instance", "10.0.0.1")).value(50).name("http_success_request").build(),
                        Sample.builder().labels(of("idc", "t1", "region", "cn", "instance", "10.0.0.1")).value(50).name("http_success_request").build(),
                        Sample.builder().labels(of("idc", "t2", "region", "us", "instance", "10.0.0.2")).value(50).name("http_success_request").build(),
                        Sample.builder().labels(of("idc", "t1", "region", "us", "instance", "10.0.0.3")).value(100).name("http_success_request").build(),
                        Sample.builder().labels(of("idc", "t1", "region", "us", "instance", "10.0.0.4")).value(100).name("http_success_request").build(),
                        Sample.builder().labels(of("idc", "t2", "region", "cn", "instance", "10.0.0.5")).value(3).name("http_success_request").build()
                ).build()),
                "http_success_request.count(['region','instance'])",
                Result.success(SampleFamilyBuilder.newBuilder(
                        Sample.builder().labels(of("region", "us")).value(3).name("http_success_request").build(),
                        Sample.builder().labels(of("region", "cn")).value(2).name("http_success_request").build()
                ).build()),
                false,
            },
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void test(String name,
                     ImmutableMap<String, SampleFamily> input,
                     String expression,
                     Result want,
                     boolean isThrow) {
        Result r = null;
        try {
            // v2 compiles eagerly, so parse-time failures surface here too (v1 surfaced them at run()).
            Expression e = DSL.parse(name, expression);
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
        assertEquals(want, r);
    }
}
