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

import java.io.FileNotFoundException;
import lombok.SneakyThrows;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode({Mode.Throughput})
@Threads(4)
public class EndpointGroupingBenchmark4Openapi {

    @State(Scope.Benchmark)
    public static class FormatClassPaths20 {
        private EndpointGroupingRule4Openapi rule;

        @SneakyThrows
        public FormatClassPaths20() {
            rule = new EndpointGroupingRule4Openapi();
            for (int i = 0; i <= 3; i++) {
                rule.addGroupedRule("serviceA", "GET:/products1/{id}/" + i, "GET:/products1/([^/]+)/" + i);
                rule.addGroupedRule("serviceA", "POST:/products1/{id}/" + i, "POST:/products1/([^/]+)/" + i);
                rule.addGroupedRule("serviceA", "GET:/products2/{id}/" + i, "GET:/products2/([^/]+)/" + i);
                rule.addGroupedRule("serviceA", "POST:/products3/{id}/" + i, "POST:/products3/([^/]+)/" + i);
                rule.addGroupedRule("serviceA", "GET:/products3/{id}/" + i, "GET:/products3/([^/]+)/" + i);
            }
        }

        public void format(String serviceName, String endpointName) {
            rule.format(serviceName, endpointName);
        }
    }

    @State(Scope.Benchmark)
    public static class FormatClassPaths50 {
        private EndpointGroupingRule4Openapi rule;

        @SneakyThrows
        public FormatClassPaths50() {
            rule = new EndpointGroupingRule4Openapi();
            for (int i = 0; i <= 9; i++) {
                rule.addGroupedRule("serviceA", "GET:/products1/{id}/" + i, "GET:/products1/([^/]+)/" + i);
                rule.addGroupedRule("serviceA", "POST:/products1/{id}/" + i, "POST:/products1/([^/]+)/" + i);
                rule.addGroupedRule("serviceA", "GET:/products2/{id}/" + i, "GET:/products2/([^/]+)/" + i);
                rule.addGroupedRule("serviceA", "POST:/products3/{id}/" + i, "POST:/products3/([^/]+)/" + i);
                rule.addGroupedRule("serviceA", "GET:/products3/{id}/" + i, "GET:/products3/([^/]+)/" + i);
            }
        }

        public void format(String serviceName, String endpointName) {
            rule.format(serviceName, endpointName);
        }
    }

    @State(Scope.Benchmark)
    public static class FormatClassPaths200 {
        private EndpointGroupingRule4Openapi rule;

        @SneakyThrows
        public FormatClassPaths200() {
            rule = new EndpointGroupingRule4Openapi();
            for (int i = 0; i <= 39; i++) {
                rule.addGroupedRule("serviceA", "GET:/products1/{id}/" + i, "GET:/products1/([^/]+)/" + i);
                rule.addGroupedRule("serviceA", "POST:/products1/{id}/" + i, "POST:/products1/([^/]+)/" + i);
                rule.addGroupedRule("serviceA", "GET:/products2/{id}/" + i, "GET:/products2/([^/]+)/" + i);
                rule.addGroupedRule("serviceA", "POST:/products3/{id}/" + i, "POST:/products3/([^/]+)/" + i);
                rule.addGroupedRule("serviceA", "GET:/products3/{id}/" + i, "GET:/products3/([^/]+)/" + i);
            }
        }

        public void format(String serviceName, String endpointName) {
            rule.format(serviceName, endpointName);
        }

    }

    @Benchmark
    public void formatEndpointNameMatchedPaths20(FormatClassPaths20 formatClass) {
        formatClass.format("serviceA", "GET:/products1/123");
    }

    @Benchmark
    public void formatEndpointNameMatchedPaths50(FormatClassPaths50 formatClass) {
        formatClass.format("serviceA", "GET:/products1/123");
    }

    @Benchmark
    public void formatEndpointNameMatchedPaths200(FormatClassPaths200 formatClass) {
        formatClass.format("serviceA", "GET:/products1/123");
    }

    public static void main(String[] args) throws RunnerException, FileNotFoundException {

        Options opt = new OptionsBuilder()
            .include(EndpointGroupingBenchmark4Openapi.class.getName())
            .addProfiler(GCProfiler.class)
            .jvmArgsAppend("-Xmx512m", "-Xms512m")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}

/*
* The test is assumed each endpoint need to run all match within it's rules group.
*
# JMH version: 1.21
# VM version: JDK 1.8.0_271, Java HotSpot(TM) 64-Bit Server VM, 25.271-b09
# VM invoker: /Library/Java/JavaVirtualMachines/jdk1.8.0_271.jdk/Contents/Home/jre/bin/java
# VM options: -javaagent:/Applications/IntelliJ IDEA CE.app/Contents/lib/idea_rt.jar=51431:/Applications/IntelliJ IDEA CE.app/Contents/bin -Dfile.encoding=UTF-8 -Xmx512m -Xms512m
# Warmup: 5 iterations, 10 s each
# Measurement: 5 iterations, 10 s each
# Timeout: 10 min per iteration
# Threads: 4 threads, will synchronize iterations
# Benchmark mode: Throughput, ops/time

Benchmark                                                                                              Mode  Cnt        Score        Error   Units
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20                                    thrpt    5  4180207.544 ± 833644.395   ops/s
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20:·gc.alloc.rate                     thrpt    5     4524.954 ±    903.291  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20:·gc.alloc.rate.norm                thrpt    5     1192.000 ±      0.001    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20:·gc.churn.PS_Eden_Space            thrpt    5     4550.511 ±    916.117  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20:·gc.churn.PS_Eden_Space.norm       thrpt    5     1198.713 ±     10.572    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20:·gc.churn.PS_Survivor_Space        thrpt    5        0.493 ±      0.118  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20:·gc.churn.PS_Survivor_Space.norm   thrpt    5        0.130 ±      0.039    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20:·gc.count                          thrpt    5     1410.000               counts
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20:·gc.time                           thrpt    5      783.000                   ms
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200                                   thrpt    5   600313.461 ±  58702.201   ops/s
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200:·gc.alloc.rate                    thrpt    5     4260.484 ±    415.215  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200:·gc.alloc.rate.norm               thrpt    5     7816.000 ±      0.001    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200:·gc.churn.PS_Eden_Space           thrpt    5     4285.685 ±    407.822  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200:·gc.churn.PS_Eden_Space.norm      thrpt    5     7862.339 ±     46.737    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200:·gc.churn.PS_Survivor_Space       thrpt    5        0.444 ±      0.061  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200:·gc.churn.PS_Survivor_Space.norm  thrpt    5        0.815 ±      0.062    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200:·gc.count                         thrpt    5     1328.000               counts
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200:·gc.time                          thrpt    5      729.000                   ms
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50                                    thrpt    5  2001647.224 ± 139386.146   ops/s
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50:·gc.alloc.rate                     thrpt    5     4173.062 ±    291.166  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50:·gc.alloc.rate.norm                thrpt    5     2296.000 ±      0.001    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50:·gc.churn.PS_Eden_Space            thrpt    5     4198.202 ±    271.551  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50:·gc.churn.PS_Eden_Space.norm       thrpt    5     2309.878 ±     14.994    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50:·gc.churn.PS_Survivor_Space        thrpt    5        0.393 ±      0.171  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50:·gc.churn.PS_Survivor_Space.norm   thrpt    5        0.216 ±      0.086    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50:·gc.count                          thrpt    5     1301.000               counts
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50:·gc.time                           thrpt    5      715.000                   ms
 */