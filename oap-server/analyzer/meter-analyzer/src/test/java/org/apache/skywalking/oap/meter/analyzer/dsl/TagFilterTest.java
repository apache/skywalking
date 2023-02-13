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
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
public class TagFilterTest {
    public static Collection<Object[]> data() {
        final SampleFamily sf =
            SampleFamilyBuilder.newBuilder(
                Sample.builder().labels(of("idc", "t2")).value(50).name("http_success_request").build(),
                Sample.builder()
                      .labels(of("idc", "t3", "region", "cn", "svc", "catalog"))
                      .value(50)
                      .name("http_success_request")
                      .build(),
                Sample.builder()
                      .labels(of("idc", "t1", "region", "us", "svc", "product"))
                      .value(50)
                      .name("http_success_request")
                      .build(),
                Sample.builder()
                      .labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1"))
                      .name("http_success_request")
                      .value(50)
                      .build(),
                Sample.builder()
                      .labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1"))
                      .name("http_success_request")
                      .value(3)
                      .build()
            ).build();
        return Arrays.asList(new Object[][] {
            {
                "tagEqual",
                of("http_success_request", sf),
                "http_success_request.tagEqual('idc', 't3')",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "svc", "catalog"))
                          .value(50)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1"))
                          .name("http_success_request")
                          .value(3)
                          .build()
                ).build()),
                false,
                },
            {
                "tagNotEqual",
                of("http_success_request", sf),
                "http_success_request.tagNotEqual('idc', 't1')",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t2")).value(50).name("http_success_request").build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "svc", "catalog"))
                          .value(50)
                          .name("http_success_request")
                          .build(),
                    Sample.builder()
                          .labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1"))
                          .name("http_success_request")
                          .value(3)
                          .build()
                ).build()),
                false,
                },
            {
                "tagMatch",
                of("http_success_request", sf),
                "http_success_request.tagMatch('idc', 't1|t2|t3')",
                Result.success(sf),
                false,
                },
            {
                "tagNotMatch",
                of("http_success_request", sf),
                "http_success_request.tagNotMatch('idc', 't1|t3')",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("idc", "t2")).value(50).name("http_success_request").build()).build()),
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
        assertThat(r).isEqualTo(want);
    }
}
