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
 * # VM version: JDK 16.0.1, OpenJDK 64-Bit Server VM, 16.0.1+9-24
 * # VM invoker: C:\Users\Sky\.jdks\openjdk-16.0.1\bin\java.exe
 * # VM options: -ea --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED -Didea.test.cyclic.buffer.size=1048576 -javaagent:Y:\jetbrains\apps\IDEA-U\ch-0\231.8109.175\lib\idea_rt.jar=54938:Y:\jetbrains\apps\IDEA-U\ch-0\231.8109.175\bin -Dfile.encoding=UTF-8 -Xmx512m -Xms512m -XX:MaxDirectMemorySize=512m -XX:BiasedLockingStartupDelay=0 -Djmh.executor=CUSTOM -Djmh.executor.class=org.apache.skywalking.oap.server.microbench.base.AbstractMicrobenchmark$JmhThreadExecutor
 * # Warmup: 1 iterations, 10 s each
 * # Measurement: 1 iterations, 10 s each
 * # Timeout: 10 min per iteration
 * # Threads: 4 threads, will synchronize iterations
 * # Benchmark mode: Throughput, ops/time
 * # Benchmark: org.apache.skywalking.oap.server.microbench.core.config.group.uri.RegexVSQuickMatchBenchmark.notMatchRegex
 * Benchmark                                                                                 Mode  Cnt         Score   Error   Units
 * RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping                                    thrpt       48317763.786           ops/s
 * RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.alloc.rate                     thrpt           8773.225          MB/sec
 * RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.alloc.rate.norm                thrpt            200.014            B/op
 * RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.churn.G1_Eden_Space            thrpt           8807.405          MB/sec
 * RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.churn.G1_Eden_Space.norm       thrpt            200.794            B/op
 * RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.churn.G1_Survivor_Space        thrpt              0.050          MB/sec
 * RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.churn.G1_Survivor_Space.norm   thrpt              0.001            B/op
 * RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.count                          thrpt            303.000          counts
 * RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.time                           thrpt            325.000              ms
 * RegexVSQuickMatchBenchmark.matchFirstRegex                                               thrpt       41040542.288           ops/s
 * RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.alloc.rate                                thrpt           8348.690          MB/sec
 * RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.alloc.rate.norm                           thrpt            224.016            B/op
 * RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.churn.G1_Eden_Space                       thrpt           8378.454          MB/sec
 * RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.churn.G1_Eden_Space.norm                  thrpt            224.815            B/op
 * RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.churn.G1_Survivor_Space                   thrpt              0.057          MB/sec
 * RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.churn.G1_Survivor_Space.norm              thrpt              0.002            B/op
 * RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.count                                     thrpt            288.000          counts
 * RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.time                                      thrpt            282.000              ms
 * RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping                                   thrpt       35658131.267           ops/s
 * RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping:·gc.alloc.rate                    thrpt           8020.546          MB/sec
 * RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping:·gc.alloc.rate.norm               thrpt            248.018            B/op
 * RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping:·gc.churn.G1_Eden_Space           thrpt           8043.279          MB/sec
 * RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping:·gc.churn.G1_Eden_Space.norm      thrpt            248.721            B/op
 * RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping:·gc.churn.G1_Survivor_Space       thrpt              0.045          MB/sec
 * RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping:·gc.churn.G1_Survivor_Space.norm  thrpt              0.001            B/op
 * RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping:·gc.count                         thrpt            277.000          counts
 * RegexVSQuickMatchBenchmark.matchFourthQuickUriGrouping:·gc.time                          thrpt            302.000              ms
 * RegexVSQuickMatchBenchmark.matchFourthRegex                                              thrpt       11066068.208           ops/s
 * RegexVSQuickMatchBenchmark.matchFourthRegex:·gc.alloc.rate                               thrpt           8273.312          MB/sec
 * RegexVSQuickMatchBenchmark.matchFourthRegex:·gc.alloc.rate.norm                          thrpt            824.060            B/op
 * RegexVSQuickMatchBenchmark.matchFourthRegex:·gc.churn.G1_Eden_Space                      thrpt           8279.984          MB/sec
 * RegexVSQuickMatchBenchmark.matchFourthRegex:·gc.churn.G1_Eden_Space.norm                 thrpt            824.724            B/op
 * RegexVSQuickMatchBenchmark.matchFourthRegex:·gc.churn.G1_Survivor_Space                  thrpt              0.052          MB/sec
 * RegexVSQuickMatchBenchmark.matchFourthRegex:·gc.churn.G1_Survivor_Space.norm             thrpt              0.005            B/op
 * RegexVSQuickMatchBenchmark.matchFourthRegex:·gc.count                                    thrpt            285.000          counts
 * RegexVSQuickMatchBenchmark.matchFourthRegex:·gc.time                                     thrpt            324.000              ms
 * RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping                                      thrpt       45843193.472           ops/s
 * RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.alloc.rate                       thrpt           8653.215          MB/sec
 * RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.alloc.rate.norm                  thrpt            208.015            B/op
 * RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.churn.G1_Eden_Space              thrpt           8652.365          MB/sec
 * RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.churn.G1_Eden_Space.norm         thrpt            207.995            B/op
 * RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.churn.G1_Survivor_Space          thrpt              0.048          MB/sec
 * RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.churn.G1_Survivor_Space.norm     thrpt              0.001            B/op
 * RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.count                            thrpt            298.000          counts
 * RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.time                             thrpt            358.000              ms
 * RegexVSQuickMatchBenchmark.notMatchRegex                                                 thrpt        3434953.426           ops/s
 * RegexVSQuickMatchBenchmark.notMatchRegex:·gc.alloc.rate                                  thrpt           8898.075          MB/sec
 * RegexVSQuickMatchBenchmark.notMatchRegex:·gc.alloc.rate.norm                             thrpt           2856.206            B/op
 * RegexVSQuickMatchBenchmark.notMatchRegex:·gc.churn.G1_Eden_Space                         thrpt           8886.568          MB/sec
 * RegexVSQuickMatchBenchmark.notMatchRegex:·gc.churn.G1_Eden_Space.norm                    thrpt           2852.512            B/op
 * RegexVSQuickMatchBenchmark.notMatchRegex:·gc.churn.G1_Survivor_Space                     thrpt              0.052          MB/sec
 * RegexVSQuickMatchBenchmark.notMatchRegex:·gc.churn.G1_Survivor_Space.norm                thrpt              0.017            B/op
 * RegexVSQuickMatchBenchmark.notMatchRegex:·gc.count                                       thrpt            306.000          counts
 * RegexVSQuickMatchBenchmark.notMatchRegex:·gc.time                                        thrpt            377.000              ms
 *
 * Process finished with exit code 0
 */