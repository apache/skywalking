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

package org.apache.skywalking.oap.server.microbench.core.config.group.uri;

import org.apache.skywalking.oap.server.core.config.group.EndpointGroupingRule;
import org.apache.skywalking.oap.server.core.config.group.uri.quickmatch.QuickUriGroupingRule;
import org.apache.skywalking.oap.server.library.util.StringFormatGroup;
import org.apache.skywalking.oap.server.microbench.base.AbstractMicrobenchmark;
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

@Warmup(iterations = 1)
@Measurement(iterations = 1)
@Fork(1)
@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput})
@Threads(4)
public class RegexVSQuickMatchBenchmark extends AbstractMicrobenchmark {

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
    public void matchFirstRegex(Blackhole bh, RegexVSQuickMatchBenchmark.RegexMatch formatClass) {
        bh.consume(formatClass.match("service1", "/products/123"));
    }

    @Benchmark
    public void matchFirstQuickUriGrouping(Blackhole bh, RegexVSQuickMatchBenchmark.QuickMatch formatClass) {
        bh.consume(formatClass.match("service1", "/products/123"));
    }

    @Benchmark
    public void matchFourthRegex(Blackhole bh, RegexVSQuickMatchBenchmark.RegexMatch formatClass) {
        bh.consume(formatClass.match("service1", "/sales/123/2"));
    }

    @Benchmark
    public void matchFourthQuickUriGrouping(Blackhole bh, RegexVSQuickMatchBenchmark.QuickMatch formatClass) {
        bh.consume(formatClass.match("service1", "/sales/123/2"));
    }

    @Benchmark
    public void notMatchRegex(Blackhole bh, RegexVSQuickMatchBenchmark.RegexMatch formatClass) {
        bh.consume(formatClass.match("service1", "/employees/123"));
    }

    @Benchmark
    public void notMatchQuickUriGrouping(Blackhole bh, RegexVSQuickMatchBenchmark.QuickMatch formatClass) {
        bh.consume(formatClass.match("service1", "/employees/123"));
    }
}

/**
 * # JMH version: 1.25
 * # VM version: JDK 11.0.18, OpenJDK 64-Bit Server VM, 11.0.18+10
 * # VM invoker: /Users/wusheng/Library/Java/JavaVirtualMachines/temurin-11.0.18/Contents/Home/bin/java
 * # VM options: -ea --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED -Didea.test.cyclic.buffer.size=1048576 -javaagent:/Applications/IntelliJ IDEA.app/Contents/lib/idea_rt.jar=53714:/Applications/IntelliJ IDEA.app/Contents/bin -Dfile.encoding=UTF-8 -Xmx512m -Xms512m -XX:MaxDirectMemorySize=512m -XX:BiasedLockingStartupDelay=0 -Djmh.executor=CUSTOM -Djmh.executor.class=org.apache.skywalking.oap.server.microbench.base.AbstractMicrobenchmark$JmhThreadExecutor
 * # Warmup: 1 iterations, 10 s each
 * # Measurement: 1 iterations, 10 s each
 * # Timeout: 10 min per iteration
 * # Threads: 4 threads, will synchronize iterations
 * # Benchmark mode: Throughput, ops/time
 * # Benchmark: org.apache.skywalking.oap.server.microbench.core.config.group.uri.RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping
 *
 Benchmark                                                                             Mode  Cnt         Score   Error   Units
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping                                thrpt       28464926.797           ops/s
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.alloc.rate                 thrpt           6194.492          MB/sec
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.alloc.rate.norm            thrpt            240.000            B/op
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.churn.G1_Eden_Space        thrpt           6222.267          MB/sec
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.churn.G1_Eden_Space.norm   thrpt            241.076            B/op
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.churn.G1_Old_Gen           thrpt              0.023          MB/sec
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.churn.G1_Old_Gen.norm      thrpt              0.001            B/op
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.count                      thrpt            214.000          counts
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.time                       thrpt            194.000              ms
 RegexVSQuickMatchBenchmark.matchFirstRegex                                           thrpt       51679120.204           ops/s
 RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.alloc.rate                            thrpt           7130.116          MB/sec
 RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.alloc.rate.norm                       thrpt            152.000            B/op
 RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.churn.G1_Eden_Space                   thrpt           7162.842          MB/sec
 RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.churn.G1_Eden_Space.norm              thrpt            152.698            B/op
 RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.churn.G1_Old_Gen                      thrpt              0.020          MB/sec
 RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.churn.G1_Old_Gen.norm                 thrpt             ≈ 10⁻³            B/op
 RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.count                                 thrpt            246.000          counts
 RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.time                                  thrpt            224.000              ms
 RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping                               thrpt       23359343.934           ops/s
 RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping:·gc.alloc.rate                thrpt           6106.164          MB/sec
 RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping:·gc.alloc.rate.norm           thrpt            288.000            B/op
 RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping:·gc.churn.G1_Eden_Space       thrpt           6143.526          MB/sec
 RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping:·gc.churn.G1_Eden_Space.norm  thrpt            289.762            B/op
 RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping:·gc.churn.G1_Old_Gen          thrpt              0.023          MB/sec
 RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping:·gc.churn.G1_Old_Gen.norm     thrpt              0.001            B/op
 RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping:·gc.count                     thrpt            211.000          counts
 RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping:·gc.time                      thrpt            143.000              ms
 RegexVSQuickMatchBenchmark.matchFourthRegex                                          thrpt       24074353.094           ops/s
 RegexVSQuickMatchBenchmark.matchFourthRegex:·gc.alloc.rate                           thrpt          17999.991          MB/sec
 RegexVSQuickMatchBenchmark.matchFourthRegex:·gc.alloc.rate.norm                      thrpt            824.000            B/op
 RegexVSQuickMatchBenchmark.matchFourthRegex:·gc.churn.G1_Eden_Space                  thrpt          18070.905          MB/sec
 RegexVSQuickMatchBenchmark.matchFourthRegex:·gc.churn.G1_Eden_Space.norm             thrpt            827.246            B/op
 RegexVSQuickMatchBenchmark.matchFourthRegex:·gc.churn.G1_Old_Gen                     thrpt              0.095          MB/sec
 RegexVSQuickMatchBenchmark.matchFourthRegex:·gc.churn.G1_Old_Gen.norm                thrpt              0.004            B/op
 RegexVSQuickMatchBenchmark.matchFourthRegex:·gc.count                                thrpt            621.000          counts
 RegexVSQuickMatchBenchmark.matchFourthRegex:·gc.time                                 thrpt            934.000              ms
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping                                  thrpt       27031477.704           ops/s
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.alloc.rate                   thrpt           6081.482          MB/sec
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.alloc.rate.norm              thrpt            248.000            B/op
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.churn.G1_Eden_Space          thrpt           6109.321          MB/sec
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.churn.G1_Eden_Space.norm     thrpt            249.135            B/op
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.churn.G1_Old_Gen             thrpt              0.022          MB/sec
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.churn.G1_Old_Gen.norm        thrpt              0.001            B/op
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.count                        thrpt            210.000          counts
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.time                         thrpt            171.000              ms
 RegexVSQuickMatchBenchmark.notMatchRegex                                             thrpt        9368757.119           ops/s
 RegexVSQuickMatchBenchmark.notMatchRegex:·gc.alloc.rate                              thrpt          23999.619          MB/sec
 RegexVSQuickMatchBenchmark.notMatchRegex:·gc.alloc.rate.norm                         thrpt           2824.000            B/op
 RegexVSQuickMatchBenchmark.notMatchRegex:·gc.churn.G1_Eden_Space                     thrpt          24087.019          MB/sec
 RegexVSQuickMatchBenchmark.notMatchRegex:·gc.churn.G1_Eden_Space.norm                thrpt           2834.284            B/op
 RegexVSQuickMatchBenchmark.notMatchRegex:·gc.churn.G1_Old_Gen                        thrpt              0.114          MB/sec
 RegexVSQuickMatchBenchmark.notMatchRegex:·gc.churn.G1_Old_Gen.norm                   thrpt              0.013            B/op
 RegexVSQuickMatchBenchmark.notMatchRegex:·gc.count                                   thrpt            828.000          counts
 RegexVSQuickMatchBenchmark.notMatchRegex:·gc.time                                    thrpt            896.000              ms
 */