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
import org.apache.skywalking.oap.meter.analyzer.dsl.counter.CounterWindow;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.List;

import static com.google.common.collect.ImmutableMap.of;
import static java.time.Instant.parse;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

@Slf4j
@RunWith(Parameterized.class)
public class IncreaseTest {

    @Parameterized.Parameter
    public String name;

    @Parameterized.Parameter(1)
    public List<ImmutableMap<String, SampleFamily>> input;

    @Parameterized.Parameter(2)
    public String expression;

    @Parameterized.Parameter(3)
    public List<Result> want;

    @Parameterized.Parameter(4)
    public boolean isThrow;

    @Parameterized.Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        return asList(new Object[][] {
            {
                "increase",
                asList(
                    of("http_success_request", SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:11:01.00Z").toEpochMilli()).value(50).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:11:01.00Z").toEpochMilli()).value(150).build()
                    ).build()),
                    of("http_success_request", SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:13:41.00Z").toEpochMilli()).value(80).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:13:41.00Z").toEpochMilli()).value(250).build()
                    ).build()),
                    of("http_success_request", SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:16:31.00Z").toEpochMilli()).value(90).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:16:31.00Z").toEpochMilli()).value(280).build()
                    ).build()),
                    of("http_success_request", SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:19:31.02Z").toEpochMilli()).value(130).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:19:31.02Z").toEpochMilli()).value(330).build()
                    ).build())
                ),
                "http_success_request.increase('PT5M')",
                asList(
                    Result.success(SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:11:01.00Z").toEpochMilli()).value(0).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:11:01.00Z").toEpochMilli()).value(0).build()
                    ).build()),
                    Result.success(SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:13:41.00Z").toEpochMilli()).value(30).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:13:41.00Z").toEpochMilli()).value(100).build()
                    ).build()),
                    Result.success(SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:16:31.00Z").toEpochMilli()).value(40).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:16:31.00Z").toEpochMilli()).value(130).build()
                    ).build()),
                    Result.success(SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:19:31.02Z").toEpochMilli()).value(50).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:19:31.02Z").toEpochMilli()).value(80).build()
                    ).build())
                ),
                false,
            },
            {
                "rate",
                asList(
                    of("http_success_request", SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:11:01.00Z").toEpochMilli()).value(50).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:11:01.00Z").toEpochMilli()).value(150).build()
                    ).build()),
                    of("http_success_request", SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:13:41.00Z").toEpochMilli()).value(330).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:13:41.00Z").toEpochMilli()).value(500).build()
                    ).build()),
                    of("http_success_request", SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:16:31.00Z").toEpochMilli()).value(380).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:16:31.00Z").toEpochMilli()).value(810).build()
                    ).build()),
                    of("http_success_request", SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:19:31.02Z").toEpochMilli()).value(1380).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:19:31.02Z").toEpochMilli()).value(1900).build()
                    ).build())
                ),
                "http_success_request.rate('PT5M')",
                asList(
                    Result.success(SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:11:01.00Z").toEpochMilli()).value(0).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:11:01.00Z").toEpochMilli()).value(0).build()
                    ).build()),
                    Result.success(SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:13:41.00Z").toEpochMilli()).value(1.75D).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:13:41.00Z").toEpochMilli()).value(2.1875D).build()
                    ).build()),
                    Result.success(SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:16:31.00Z").toEpochMilli()).value(1D).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:16:31.00Z").toEpochMilli()).value(2D).build()
                    ).build()),
                    Result.success(SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:19:31.02Z").toEpochMilli()).value(3D).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:19:31.02Z").toEpochMilli()).value(4D).build()
                    ).build())
                ),
                false,
            },
            {
                "irate",
                asList(
                    of("http_success_request", SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:11:01.00Z").toEpochMilli()).value(50).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:11:01.00Z").toEpochMilli()).value(150).build()
                    ).build()),
                    of("http_success_request", SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:13:41.00Z").toEpochMilli()).value(330).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:13:41.00Z").toEpochMilli()).value(500).build()
                    ).build()),
                    of("http_success_request", SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:16:31.00Z").toEpochMilli()).value(500).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:16:31.00Z").toEpochMilli()).value(840).build()
                    ).build()),
                    of("http_success_request", SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:19:31.02Z").toEpochMilli()).value(1040).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:19:31.02Z").toEpochMilli()).value(1560).build()
                    ).build())
                ),
                "http_success_request.irate()",
                asList(
                    Result.success(SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:11:01.00Z").toEpochMilli()).value(0).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:11:01.00Z").toEpochMilli()).value(0).build()
                    ).build()),
                    Result.success(SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:13:41.00Z").toEpochMilli()).value(1.75D).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:13:41.00Z").toEpochMilli()).value(2.1875D).build()
                    ).build()),
                    Result.success(SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:16:31.00Z").toEpochMilli()).value(1D).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:16:31.00Z").toEpochMilli()).value(2D).build()
                    ).build()),
                    Result.success(SampleFamilyBuilder.newBuilder(
                        Sample.builder().name("http_success_request").labels(of("svc", "product"))
                            .timestamp(parse("2020-09-11T11:19:31.02Z").toEpochMilli()).value(3D).build(),
                        Sample.builder().name("http_success_request").labels(of("svc", "catalog"))
                            .timestamp(parse("2020-09-11T11:19:31.02Z").toEpochMilli()).value(4D).build()
                    ).build())
                ),
                false,
            },
        });
    }

    @Test
    public void test() {
        Expression e = DSL.parse(expression);
        CounterWindow.INSTANCE.reset();
        for (int i = 0; i < input.size(); i++) {
            Result r = null;
            try {
                r = e.run(input.get(i));
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
            assertThat(r, is(want.get(i)));
        }
    }
}