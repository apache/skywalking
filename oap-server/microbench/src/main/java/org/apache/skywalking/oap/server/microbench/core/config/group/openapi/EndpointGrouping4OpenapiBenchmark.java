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

package org.apache.skywalking.oap.server.microbench.core.config.group.openapi;

import org.apache.skywalking.oap.server.core.config.group.openapi.EndpointGroupingRule4Openapi;
import org.apache.skywalking.oap.server.core.config.group.openapi.EndpointGroupingRuleReader4Openapi;
import org.apache.skywalking.oap.server.library.util.StringFormatGroup.FormatResult;
import org.apache.skywalking.oap.server.microbench.base.AbstractMicrobenchmark;

import java.util.Collections;
import java.util.Map;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode({Mode.Throughput})
@Threads(4)
public class EndpointGrouping4OpenapiBenchmark extends AbstractMicrobenchmark {
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

}

/*
* The test is assumed each endpoint need to run all match within it's rules group.
*
# JMH version: 1.21
# VM version: JDK 1.8.0_292, OpenJDK 64-Bit Server VM, 25.292-b10
# VM invoker: /Library/Java/JavaVirtualMachines/adoptopenjdk-8.jdk/Contents/Home/jre/bin/java
# VM options: -javaagent:/Applications/IntelliJ IDEA CE.app/Contents/lib/idea_rt.jar=58702:/Applications/IntelliJ IDEA CE.app/Contents/bin -Dfile.encoding=UTF-8 -Xmx512m -Xms512m
# Warmup: 5 iterations, 10 s each
# Measurement: 5 iterations, 10 s each
# Timeout: 10 min per iteration
# Threads: 4 threads, will synchronize iterations
# Benchmark mode: Throughput, ops/time

Benchmark                                                                                              Mode  Cnt        Score        Error   Units
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20                                    thrpt    5  4318121.026 ± 529374.132   ops/s
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20:·gc.alloc.rate                     thrpt    5     4579.740 ±    561.095  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20:·gc.alloc.rate.norm                thrpt    5     1168.000 ±      0.001    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20:·gc.churn.PS_Eden_Space            thrpt    5     4604.284 ±    560.596  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20:·gc.churn.PS_Eden_Space.norm       thrpt    5     1174.266 ±      6.626    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20:·gc.churn.PS_Survivor_Space        thrpt    5        0.476 ±      0.122  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20:·gc.churn.PS_Survivor_Space.norm   thrpt    5        0.121 ±      0.031    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20:·gc.count                          thrpt    5     1427.000               counts
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths20:·gc.time                           thrpt    5      839.000                   ms
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200                                   thrpt    5   551316.187 ±  60567.899   ops/s
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200:·gc.alloc.rate                    thrpt    5     3912.675 ±    429.916  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200:·gc.alloc.rate.norm               thrpt    5     7816.000 ±      0.001    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200:·gc.churn.PS_Eden_Space           thrpt    5     3932.895 ±    421.307  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200:·gc.churn.PS_Eden_Space.norm      thrpt    5     7856.526 ±     45.989    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200:·gc.churn.PS_Survivor_Space       thrpt    5        0.396 ±      0.101  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200:·gc.churn.PS_Survivor_Space.norm  thrpt    5        0.791 ±      0.172    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200:·gc.count                         thrpt    5     1219.000               counts
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths200:·gc.time                          thrpt    5      737.000                   ms
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50                                    thrpt    5  2163149.470 ±  67179.001   ops/s
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50:·gc.alloc.rate                     thrpt    5     4508.870 ±    141.755  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50:·gc.alloc.rate.norm                thrpt    5     2296.000 ±      0.001    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50:·gc.churn.PS_Eden_Space            thrpt    5     4532.354 ±    146.421  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50:·gc.churn.PS_Eden_Space.norm       thrpt    5     2307.956 ±     10.377    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50:·gc.churn.PS_Survivor_Space        thrpt    5        0.454 ±      0.116  MB/sec
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50:·gc.churn.PS_Survivor_Space.norm   thrpt    5        0.231 ±      0.066    B/op
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50:·gc.count                          thrpt    5     1405.000               counts
EndpointGroupingBenchmark4Openapi.formatEndpointNameMatchedPaths50:·gc.time                           thrpt    5      841.000                   ms
 */
