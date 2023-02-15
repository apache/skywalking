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
public class FunctionTest {
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {
                "tag-override",
                of("http_success_request", SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("region", "us")).name("http_success_request").build()).build()),
                "http_success_request.tag({ ['svc':'product', 'instance':'10.0.0.1'] })",
                Result.success(SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("svc", "product", "instance", "10.0.0.1")).name("http_success_request").build()).build()),
                false,
            },
            {
                "tag-add",
                of("http_success_request", SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("region", "us")).name("http_success_request").build()).build()),
                "http_success_request.tag({tags -> tags.az = 'az1' })",
                Result.success(SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("region", "us", "az", "az1")).name("http_success_request").build()).build()),
                false,
            },
            {
                "tag-remove",
                of("http_success_request", SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("region", "us")).name("http_success_request").build()).build()),
                "http_success_request.tag({tags -> tags.remove('region') })",
                Result.success(SampleFamilyBuilder.newBuilder(Sample.builder().labels(ImmutableMap.of()).name("http_success_request").build()).build()),
                false,
            },
            {
                "tag-update",
                of("http_success_request", SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("region", "us")).name("http_success_request").build()).build()),
                "http_success_request.tag({tags -> if (tags['region'] == 'us') {tags.region = 'zh'} })",
                Result.success(SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("region", "zh")).name("http_success_request").build()).build()),
                false,
            },
            {
                "tag-append",
                of("http_success_request", SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("region", "us")).name("http_success_request").build()).build()),
                "http_success_request.tag({tags -> tags.region = 'prefix::' + tags.region})",
                Result.success(SampleFamilyBuilder.newBuilder(Sample.builder().labels(of("region", "prefix::us")).name("http_success_request").build()).build()),
                false,
                },
            {
                "histogram",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("le", "0.025")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("le", "0.75")).value(12).name("http_success_request").build(),
                    Sample.builder().labels(of("le", "1.25")).value(36).name("http_success_request").build()).build()
                ),
                "http_success_request.histogram()",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("le", "25")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("le", "750")).value(12).name("http_success_request").build(),
                    Sample.builder().labels(of("le", "1250")).value(36).name("http_success_request").build()).build()
                ),
                false,
            },
            {
                "histogram_percentile",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("le", "0.025")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("le", "0.75")).value(22).name("http_success_request").build(),
                    Sample.builder().labels(of("le", "1.25")).value(30).name("http_success_request").build()).build()
                ),
                "http_success_request.histogram().histogram_percentile([75,99])",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("le", "25")).value(100).name("http_success_request").build(),
                    Sample.builder().labels(of("le", "750")).value(22).name("http_success_request").build(),
                    Sample.builder().labels(of("le", "1250")).value(30).name("http_success_request").build()).build()
                ),
                false,
            },
            {
                "for-each",
                of("http_success_request", SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("region", "us")).name("http_success_request").build(),
                    Sample.builder().labels(of("region", "cn")).name("http_success_request").build()
                ).build()),
                "http_success_request.forEach(['v1', 'v2'], {element, tags -> tags[element] = 'test'})",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder().labels(of("region", "us", "v1", "test", "v2", "test")).name("http_success_request").build(),
                    Sample.builder().labels(of("region", "cn", "v1", "test", "v2", "test")).name("http_success_request").build()
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
