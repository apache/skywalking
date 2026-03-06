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

package org.apache.skywalking.oap.server.core.config.group.openapi;

import org.apache.skywalking.oap.server.library.util.StringFormatGroup.FormatResult;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(2)
@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput})
@Threads(4)
public class EndpointGroupingBenchmark4Openapi {
    private static final String APT_TEST_DATA = "  /products1/{id}/%d:\n" + "    get:\n" + "    post:\n"
        + "  /products2/{id}/%d:\n" + "    get:\n" + "    post:\n"
        + "  /products3/{id}/%d:\n" + "    get:\n";

    private static Map<String, String> createTestFile(int size) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("paths:\n");
        for (int i = 0; i <= size; i++) {
            stringBuilder.append(String.format(APT_TEST_DATA, i, i, i));
        }
        return Collections.singletonMap("whatever", stringBuilder.toString());
    }

    @State(Scope.Benchmark)
    public static class FormatClassPaths20 {
        private final EndpointGroupingRule4Openapi rule = new EndpointGroupingRuleReader4Openapi(createTestFile(3)).read();

        public FormatResult format(String serviceName, String endpointName) {
            return rule.format(serviceName, endpointName);
        }
    }

    @State(Scope.Benchmark)
    public static class FormatClassPaths50 {
        private final EndpointGroupingRule4Openapi rule = new EndpointGroupingRuleReader4Openapi(createTestFile(9)).read();

        public FormatResult format(String serviceName, String endpointName) {
            return rule.format(serviceName, endpointName);
        }
    }

    @State(Scope.Benchmark)
    public static class FormatClassPaths200 {
        private final EndpointGroupingRule4Openapi rule = new EndpointGroupingRuleReader4Openapi(createTestFile(39)).read();

        public FormatResult format(String serviceName, String endpointName) {
            return rule.format(serviceName, endpointName);
        }
    }

    @Benchmark
    public void formatEndpointNameMatchedPaths20(Blackhole bh, FormatClassPaths20 formatClass) {
        bh.consume(formatClass.format("serviceA", "GET:/products1/123"));
    }

    @Benchmark
    public void formatEndpointNameMatchedPaths50(Blackhole bh, FormatClassPaths50 formatClass) {
        bh.consume(formatClass.format("serviceA", "GET:/products1/123"));
    }

    @Benchmark
    public void formatEndpointNameMatchedPaths200(Blackhole bh, FormatClassPaths200 formatClass) {
        bh.consume(formatClass.format("serviceA", "GET:/products1/123"));
    }

    @Test
    public void run() throws Exception {
        new Runner(new OptionsBuilder()
                .include(".*" + getClass().getSimpleName() + ".*")
                .jvmArgsAppend("-Xmx512m", "-Xms512m")
                .build()).run();
    }

}
