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

package org.apache.skywalking.oap.server.core.config.group.uri;

import org.apache.skywalking.oap.server.core.config.group.EndpointGroupingRule;
import org.apache.skywalking.oap.server.core.config.group.uri.quickmatch.QuickUriGroupingRule;
import org.apache.skywalking.oap.server.library.util.StringFormatGroup;
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

@Warmup(iterations = 1)
@Measurement(iterations = 1)
@Fork(1)
@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput})
@Threads(4)
public class RegexVSQuickMatchBenchmark {

    @State(Scope.Benchmark)
    public static class RegexMatch {
        private final EndpointGroupingRule rule = new EndpointGroupingRule();

        public RegexMatch() {
            rule.addRule("service1", "/products/{var}", "/products/.+");
            rule.addRule("service1", "/products/{var}/detail", "/products/.+/detail");
            rule.addRule("service1", "/sales/{var}/1", "/sales/.+/1");
            rule.addRule("service1", "/sales/{var}/2", "/sales/.+/2");
            rule.addRule("service1", "/sales/{var}/3", "/sales/.+/3");
            rule.addRule("service1", "/sales/{var}/4", "/sales/.+/4");
            rule.addRule("service1", "/sales/{var}/5", "/sales/.+/5");
            rule.addRule("service1", "/sales/{var}/6", "/sales/.+/6");
            rule.addRule("service1", "/sales/{var}/7", "/sales/.+/7");
            rule.addRule("service1", "/sales/{var}/8", "/sales/.+/8");
            rule.addRule("service1", "/sales/{var}/9", "/sales/.+/9");
            rule.addRule("service1", "/sales/{var}/10", "/sales/.+/10");
            rule.addRule("service1", "/sales/{var}/11", "/sales/.+/11");
            rule.addRule("service1", "/employees/{var}/profile", "/employees/.+/profile");
        }

        public StringFormatGroup.FormatResult match(String serviceName, String endpointName) {
            return rule.format(serviceName, endpointName);
        }
    }

    @State(Scope.Benchmark)
    public static class QuickMatch {
        private final QuickUriGroupingRule rule = new QuickUriGroupingRule();

        public QuickMatch() {
            rule.addRule("service1", "/products/{var}");
            rule.addRule("service1", "/products/{var}/detail");
            rule.addRule("service1", "/sales/{var}/1");
            rule.addRule("service1", "/sales/{var}/2");
            rule.addRule("service1", "/sales/{var}/3");
            rule.addRule("service1", "/sales/{var}/4");
            rule.addRule("service1", "/sales/{var}/5");
            rule.addRule("service1", "/sales/{var}/6");
            rule.addRule("service1", "/sales/{var}/7");
            rule.addRule("service1", "/sales/{var}/8");
            rule.addRule("service1", "/sales/{var}/9");
            rule.addRule("service1", "/sales/{var}/10");
            rule.addRule("service1", "/sales/{var}/11");
            rule.addRule("service1", "/employees/{var}/profile");
        }

        public StringFormatGroup.FormatResult match(String serviceName, String endpointName) {
            return rule.format(serviceName, endpointName);
        }
    }

    @Benchmark
    public void matchFirstRegex(Blackhole bh, RegexMatch formatClass) {
        bh.consume(formatClass.match("service1", "/products/123"));
    }

    @Benchmark
    public void matchFirstQuickUriGrouping(Blackhole bh, QuickMatch formatClass) {
        bh.consume(formatClass.match("service1", "/products/123"));
    }

    @Benchmark
    public void matchFourthRegex(Blackhole bh, RegexMatch formatClass) {
        bh.consume(formatClass.match("service1", "/sales/123/2"));
    }

    @Benchmark
    public void matchFourthQuickUriGrouping(Blackhole bh, QuickMatch formatClass) {
        bh.consume(formatClass.match("service1", "/sales/123/2"));
    }

    @Benchmark
    public void notMatchRegex(Blackhole bh, RegexMatch formatClass) {
        bh.consume(formatClass.match("service1", "/employees/123"));
    }

    @Benchmark
    public void notMatchQuickUriGrouping(Blackhole bh, QuickMatch formatClass) {
        bh.consume(formatClass.match("service1", "/employees/123"));
    }

    @Test
    public void run() throws Exception {
        new Runner(new OptionsBuilder()
                .include(".*" + getClass().getSimpleName() + ".*")
                .jvmArgsAppend("-Xmx512m", "-Xms512m")
                .build()).run();
    }
}
