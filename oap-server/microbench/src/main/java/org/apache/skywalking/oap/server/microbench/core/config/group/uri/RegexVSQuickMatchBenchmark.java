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
 Benchmark                                                                            Mode  Cnt         Score   Error   Units
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping                               thrpt       21824009.131           ops/s
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.alloc.rate                thrpt           5388.565          MB/sec
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.alloc.rate.norm           thrpt            272.000            B/op
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.churn.G1_Eden_Space       thrpt           5414.783          MB/sec
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.churn.G1_Eden_Space.norm  thrpt            273.323            B/op
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.churn.G1_Old_Gen          thrpt              0.019          MB/sec
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.churn.G1_Old_Gen.norm     thrpt              0.001            B/op
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.count                     thrpt            186.000          counts
 RegexVSQuickMatchBenchmark.matchFirstQuickUriGrouping:·gc.time                      thrpt            112.000              ms
 RegexVSQuickMatchBenchmark.matchFirstRegex                                          thrpt       52815735.190           ops/s
 RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.alloc.rate                           thrpt           7286.910          MB/sec
 RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.alloc.rate.norm                      thrpt            152.000            B/op
 RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.churn.G1_Eden_Space                  thrpt           7306.492          MB/sec
 RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.churn.G1_Eden_Space.norm             thrpt            152.408            B/op
 RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.churn.G1_Old_Gen                     thrpt              0.026          MB/sec
 RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.churn.G1_Old_Gen.norm                thrpt              0.001            B/op
 RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.count                                thrpt            251.000          counts
 RegexVSQuickMatchBenchmark.matchFirstRegex:·gc.time                                 thrpt            143.000              ms
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping                                 thrpt       23265119.660           ops/s
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.alloc.rate                  thrpt           5914.434          MB/sec
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.alloc.rate.norm             thrpt            280.000            B/op
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.churn.G1_Eden_Space         thrpt           5939.872          MB/sec
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.churn.G1_Eden_Space.norm    thrpt            281.204            B/op
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.churn.G1_Old_Gen            thrpt              0.025          MB/sec
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.churn.G1_Old_Gen.norm       thrpt              0.001            B/op
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.count                       thrpt            204.000          counts
 RegexVSQuickMatchBenchmark.notMatchQuickUriGrouping:·gc.time                        thrpt            108.000              ms
 RegexVSQuickMatchBenchmark.notMatchRegex                                            thrpt       10462244.647           ops/s
 RegexVSQuickMatchBenchmark.notMatchRegex:·gc.alloc.rate                             thrpt          26817.565          MB/sec
 RegexVSQuickMatchBenchmark.notMatchRegex:·gc.alloc.rate.norm                        thrpt           2824.000            B/op
 RegexVSQuickMatchBenchmark.notMatchRegex:·gc.churn.G1_Eden_Space                    thrpt          26925.722          MB/sec
 RegexVSQuickMatchBenchmark.notMatchRegex:·gc.churn.G1_Eden_Space.norm               thrpt           2835.389            B/op
 RegexVSQuickMatchBenchmark.notMatchRegex:·gc.churn.G1_Old_Gen                       thrpt              0.128          MB/sec
 RegexVSQuickMatchBenchmark.notMatchRegex:·gc.churn.G1_Old_Gen.norm                  thrpt              0.013            B/op
 RegexVSQuickMatchBenchmark.notMatchRegex:·gc.count                                  thrpt            925.000          counts
 RegexVSQuickMatchBenchmark.notMatchRegex:·gc.time                                   thrpt            507.000              ms
 */