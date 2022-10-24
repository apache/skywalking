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

package org.apache.skywalking.oap.server.microbench.core.profiling.ebpf;

import org.apache.skywalking.oap.server.core.profiling.ebpf.analyze.EBPFProfilingAnalyzer;
import org.apache.skywalking.oap.server.core.profiling.ebpf.analyze.EBPFProfilingStack;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingStackType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzation;
import org.apache.skywalking.oap.server.microbench.base.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput})
@Threads(4)
public class EBPFProfilingAnalyzerBenchmark extends AbstractMicrobenchmark {

    private static final Random RANDOM = new Random(System.currentTimeMillis());
    private static final int SYMBOL_LENGTH = 10;
    private static final char[] SYMBOL_TABLE = "abcdefgABCDEFG1234567890_[]<>.".toCharArray();
    private static final EBPFProfilingStackType[] STACK_TYPES = new EBPFProfilingStackType[]{
            EBPFProfilingStackType.KERNEL_SPACE, EBPFProfilingStackType.USER_SPACE};

    private static List<EBPFProfilingStack> generateStacks(int totalStackCount,
                                                           int perStackMinDepth, int perStackMaxDepth,
                                                           double[] stackSymbolDuplicateRate,
                                                           double stackDuplicateRate) {
        int uniqStackCount = (int) (100 / stackDuplicateRate);
        final List<EBPFProfilingStack> stacks = new ArrayList<>(totalStackCount);
        final StackSymbolGenerator stackSymbolGenerator = new StackSymbolGenerator(stackSymbolDuplicateRate, perStackMaxDepth);
        for (int inx = 0; inx < uniqStackCount; inx++) {
            final EBPFProfilingStack s = generateStack(perStackMinDepth, perStackMaxDepth, stackSymbolGenerator);
            stacks.add(s);
        }
        for (int inx = uniqStackCount; inx < totalStackCount; inx++) {
            stacks.add(stacks.get(RANDOM.nextInt(uniqStackCount)));
        }
        return stacks;
    }

    private static class StackSymbolGenerator {
        private final Map<Integer, Integer> stackDepthSymbolCount;
        private final Map<Integer, List<String>> existingSymbolMap;

        public StackSymbolGenerator(double[] stackSymbolDuplicateRate, int maxDepth) {
            this.stackDepthSymbolCount = new HashMap<>();
            for (int depth = 0; depth < maxDepth; depth++) {
                double rate = stackSymbolDuplicateRate[stackSymbolDuplicateRate.length - 1];
                if (stackSymbolDuplicateRate.length > depth) {
                    rate = stackSymbolDuplicateRate[depth];
                }
                int uniqStackCount = (int) (100 / rate);
                stackDepthSymbolCount.put(depth, uniqStackCount);
            }
            this.existingSymbolMap = new HashMap<>();
        }

        public String generate(int depth) {
            List<String> symbols = existingSymbolMap.get(depth);
            if (symbols == null) {
                existingSymbolMap.put(depth, symbols = new ArrayList<>());
            }
            final Integer needCount = this.stackDepthSymbolCount.get(depth);
            if (symbols.size() < needCount) {
                final StringBuilder sb = new StringBuilder(SYMBOL_LENGTH);
                for (int j = 0; j < SYMBOL_LENGTH; j++) {
                    sb.append(SYMBOL_TABLE[RANDOM.nextInt(SYMBOL_TABLE.length)]);
                }
                final String s = sb.toString();
                symbols.add(s);
                return s;
            } else {
                return symbols.get(RANDOM.nextInt(symbols.size()));
            }
        }
    }

    private static EBPFProfilingStack generateStack(int stackMinDepth, int stackMaxDepth,
                                                    StackSymbolGenerator stackSymbolGenerator) {
        int stackDepth = stackMinDepth + RANDOM.nextInt(stackMaxDepth - stackMinDepth);
        final List<EBPFProfilingStack.Symbol> symbols = new ArrayList<>(stackDepth);
        for (int i = 0; i < stackDepth; i++) {
            final EBPFProfilingStack.Symbol symbol = new EBPFProfilingStack.Symbol(
                    stackSymbolGenerator.generate(i),  buildStackType(i, stackDepth));
            symbols.add(symbol);
        }
        final EBPFProfilingStack stack = new EBPFProfilingStack();
        stack.setDumpCount(RANDOM.nextInt(100));
        stack.setSymbols(symbols);
        return stack;
    }

    private static EBPFProfilingStackType buildStackType(int currentDepth, int totalDepth) {
        final int partition = totalDepth / STACK_TYPES.length;
        for (int i = 1; i <= STACK_TYPES.length; i++) {
            if (currentDepth < i * partition) {
                return STACK_TYPES[i - 1];
            }
        }
        return STACK_TYPES[STACK_TYPES.length - 1];
    }

    public static class DataSource {
        private final List<EBPFProfilingStack> stackStream;

        public DataSource(List<EBPFProfilingStack> stackStream) {
            this.stackStream = stackStream;
        }

        public void analyze() {
            new EBPFProfilingAnalyzer(null, 100, 5).generateTrees(new EBPFProfilingAnalyzation(), stackStream.parallelStream());
        }
    }

    private static int calculateStackCount(int stackReportPeriodSecond, int totalTimeMinute, int combineInstanceCount) {
        return (int) (TimeUnit.MINUTES.toSeconds(totalTimeMinute) / stackReportPeriodSecond * combineInstanceCount);
    }

    @State(Scope.Benchmark)
    public static class LowDataSource extends DataSource {
        // rover report period: 5s
        // dump duration: 60m
        // 10 instance analyze
        // stack depth range: 15, 30
        // stack duplicate rate: 5%
        // stack symbol duplicate rate: 100%, 40%, 35%, 30%, 15%, 10%, 7%, 5%
        public LowDataSource() {
            super(generateStacks(calculateStackCount(5, 60, 10), 15, 30,
                    new double[]{100, 50, 45, 40, 35, 30, 15, 10, 5}, 5));
        }
    }

    @State(Scope.Benchmark)
    public static class MedianDatasource extends DataSource {
        // rover report period: 5s
        // dump duration: 100m
        // 200 instance analyze
        // stack depth range: 15, 30
        // stack duplicate rate: 3%
        // stack symbol duplicate rate: 50%, 40%, 35%, 30%, 20%, 10%, 7%, 5%, 2%
        public MedianDatasource() {
            super(generateStacks(calculateStackCount(5, 100, 200), 15, 30,
                    new double[]{50, 40, 35, 30, 20, 10, 7, 5, 2}, 3));
        }
    }

    @State(Scope.Benchmark)
    public static class HighDatasource extends DataSource {
        // rover report period: 5s
        // dump time: 2h
        // 2000 instance analyze
        // stack depth range: 15, 40
        // stack duplicate rate: 1%
        // stack symbol duplicate rate: 30%, 27%, 25%, 20%, 17%, 15%, 10%, 7%, 5%, 2%, 1%
        public HighDatasource() {
            super(generateStacks(calculateStackCount(5, 2 * 60, 2000), 15, 40,
                    new double[]{30, 27, 25, 20, 17, 15, 10, 7, 5, 2, 1}, 1));
        }
    }

    @Benchmark
    public void analyzeLowDataSource(LowDataSource lowDataSource) {
        lowDataSource.analyze();
    }

    @Benchmark
    public void analyzeMedianDataSource(MedianDatasource medianDatasource) {
        medianDatasource.analyze();
    }

    @Benchmark
    public void analyzeMaxDataSource(HighDatasource highDataSource) {
        highDataSource.analyze();
    }

}

/*
# JMH version: 1.25
# VM version: JDK 1.8.0_292, OpenJDK 64-Bit Server VM, 25.292-b10
# VM invoker: /Users/hanliu/.sdkman/candidates/java/8.0.292.hs-adpt/jre/bin/java
# VM options: <none>
# Warmup: 10 iterations, 10 s each
# Measurement: 10 iterations, 10 s each
# Timeout: 10 min per iteration
# Threads: 4 threads, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: org.apache.skywalking.oap.server.microbench.core.profiling.ebpf.EBPFProfilingAnalyzerBenchmark.analyzeLowDataSource

# Run progress: 0.00% complete, ETA 00:20:00
# Fork: 1 of 2
# Warmup Iteration   1: 2774.619 ops/s
# Warmup Iteration   2: 2652.912 ops/s
# Warmup Iteration   3: 2651.943 ops/s
# Warmup Iteration   4: 2670.755 ops/s
# Warmup Iteration   5: 2632.884 ops/s
# Warmup Iteration   6: 2597.808 ops/s
# Warmup Iteration   7: 2256.900 ops/s
# Warmup Iteration   8: 2105.842 ops/s
# Warmup Iteration   9: 2084.963 ops/s
# Warmup Iteration  10: 2142.089 ops/s
Iteration   1: 2168.913 ops/s
Iteration   2: 2161.030 ops/s
Iteration   3: 2170.136 ops/s
Iteration   4: 2161.335 ops/s
Iteration   5: 2167.978 ops/s
Iteration   6: 2154.508 ops/s
Iteration   7: 2136.985 ops/s
Iteration   8: 2107.246 ops/s
Iteration   9: 2084.855 ops/s
Iteration  10: 2071.664 ops/s

# Run progress: 16.67% complete, ETA 00:16:44
# Fork: 2 of 2
# Warmup Iteration   1: 2094.858 ops/s
# Warmup Iteration   2: 2324.678 ops/s
# Warmup Iteration   3: 2238.370 ops/s
# Warmup Iteration   4: 2252.727 ops/s
# Warmup Iteration   5: 2149.959 ops/s
# Warmup Iteration   6: 2155.332 ops/s
# Warmup Iteration   7: 2141.820 ops/s
# Warmup Iteration   8: 2154.514 ops/s
# Warmup Iteration   9: 2145.600 ops/s
# Warmup Iteration  10: 2129.701 ops/s
Iteration   1: 2157.904 ops/s
Iteration   2: 2145.461 ops/s
Iteration   3: 2155.163 ops/s
Iteration   4: 2154.556 ops/s
Iteration   5: 2161.428 ops/s
Iteration   6: 2150.353 ops/s
Iteration   7: 2161.267 ops/s
Iteration   8: 2092.811 ops/s
Iteration   9: 2059.780 ops/s
Iteration  10: 2061.371 ops/s


Result "org.apache.skywalking.oap.server.microbench.core.profiling.ebpf.EBPFProfilingAnalyzerBenchmark.analyzeLowDataSource":
  2134.237 ±(99.9%) 33.583 ops/s [Average]
  (min, avg, max) = (2059.780, 2134.237, 2170.136), stdev = 38.674
  CI (99.9%): [2100.654, 2167.820] (assumes normal distribution)


# JMH version: 1.25
# VM version: JDK 1.8.0_292, OpenJDK 64-Bit Server VM, 25.292-b10
# VM invoker: /Users/hanliu/.sdkman/candidates/java/8.0.292.hs-adpt/jre/bin/java
# VM options: <none>
# Warmup: 10 iterations, 10 s each
# Measurement: 10 iterations, 10 s each
# Timeout: 10 min per iteration
# Threads: 4 threads, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: org.apache.skywalking.oap.server.microbench.core.profiling.ebpf.EBPFProfilingAnalyzerBenchmark.analyzeMaxDataSource

# Run progress: 33.33% complete, ETA 00:13:24
# Fork: 1 of 2
# Warmup Iteration   1: 6.534 ops/s
# Warmup Iteration   2: 6.695 ops/s
# Warmup Iteration   3: 6.722 ops/s
# Warmup Iteration   4: 6.473 ops/s
# Warmup Iteration   5: 6.431 ops/s
# Warmup Iteration   6: 6.391 ops/s
# Warmup Iteration   7: 6.401 ops/s
# Warmup Iteration   8: 6.290 ops/s
# Warmup Iteration   9: 6.087 ops/s
# Warmup Iteration  10: 6.143 ops/s
Iteration   1: 5.989 ops/s
Iteration   2: 6.386 ops/s
Iteration   3: 6.397 ops/s
Iteration   4: 6.395 ops/s
Iteration   5: 6.374 ops/s
Iteration   6: 6.192 ops/s
Iteration   7: 6.111 ops/s
Iteration   8: 6.049 ops/s
Iteration   9: 6.104 ops/s
Iteration  10: 6.130 ops/s

# Run progress: 50.00% complete, ETA 00:10:20
# Fork: 2 of 2
# Warmup Iteration   1: 5.981 ops/s
# Warmup Iteration   2: 6.433 ops/s
# Warmup Iteration   3: 6.421 ops/s
# Warmup Iteration   4: 6.215 ops/s
# Warmup Iteration   5: 6.139 ops/s
# Warmup Iteration   6: 6.165 ops/s
# Warmup Iteration   7: 6.153 ops/s
# Warmup Iteration   8: 6.123 ops/s
# Warmup Iteration   9: 6.107 ops/s
# Warmup Iteration  10: 6.044 ops/s
Iteration   1: 5.869 ops/s
Iteration   2: 5.837 ops/s
Iteration   3: 5.836 ops/s
Iteration   4: 5.994 ops/s
Iteration   5: 6.187 ops/s
Iteration   6: 6.129 ops/s
Iteration   7: 6.111 ops/s
Iteration   8: 6.150 ops/s
Iteration   9: 6.154 ops/s
Iteration  10: 6.165 ops/s


Result "org.apache.skywalking.oap.server.microbench.core.profiling.ebpf.EBPFProfilingAnalyzerBenchmark.analyzeMaxDataSource":
  6.128 ±(99.9%) 0.149 ops/s [Average]
  (min, avg, max) = (5.836, 6.128, 6.397), stdev = 0.172
  CI (99.9%): [5.979, 6.277] (assumes normal distribution)


# JMH version: 1.25
# VM version: JDK 1.8.0_292, OpenJDK 64-Bit Server VM, 25.292-b10
# VM invoker: /Users/hanliu/.sdkman/candidates/java/8.0.292.hs-adpt/jre/bin/java
# VM options: <none>
# Warmup: 10 iterations, 10 s each
# Measurement: 10 iterations, 10 s each
# Timeout: 10 min per iteration
# Threads: 4 threads, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: org.apache.skywalking.oap.server.microbench.core.profiling.ebpf.EBPFProfilingAnalyzerBenchmark.analyzeMedianDataSource

# Run progress: 66.67% complete, ETA 00:06:59
# Fork: 1 of 2
# Warmup Iteration   1: 98.581 ops/s
# Warmup Iteration   2: 101.972 ops/s
# Warmup Iteration   3: 102.758 ops/s
# Warmup Iteration   4: 102.755 ops/s
# Warmup Iteration   5: 102.637 ops/s
# Warmup Iteration   6: 102.341 ops/s
# Warmup Iteration   7: 101.472 ops/s
# Warmup Iteration   8: 101.128 ops/s
# Warmup Iteration   9: 97.455 ops/s
# Warmup Iteration  10: 96.327 ops/s
Iteration   1: 95.448 ops/s
Iteration   2: 100.029 ops/s
Iteration   3: 101.103 ops/s
Iteration   4: 101.236 ops/s
Iteration   5: 100.893 ops/s
Iteration   6: 101.052 ops/s
Iteration   7: 100.859 ops/s
Iteration   8: 101.174 ops/s
Iteration   9: 101.237 ops/s
Iteration  10: 101.146 ops/s

# Run progress: 83.33% complete, ETA 00:03:28
# Fork: 2 of 2
# Warmup Iteration   1: 92.453 ops/s
# Warmup Iteration   2: 95.494 ops/s
# Warmup Iteration   3: 95.363 ops/s
# Warmup Iteration   4: 95.391 ops/s
# Warmup Iteration   5: 95.126 ops/s
# Warmup Iteration   6: 94.867 ops/s
# Warmup Iteration   7: 94.034 ops/s
# Warmup Iteration   8: 89.720 ops/s
# Warmup Iteration   9: 87.873 ops/s
# Warmup Iteration  10: 89.747 ops/s
Iteration   1: 93.948 ops/s
Iteration   2: 93.365 ops/s
Iteration   3: 94.219 ops/s
Iteration   4: 94.004 ops/s
Iteration   5: 94.352 ops/s
Iteration   6: 94.299 ops/s
Iteration   7: 94.336 ops/s
Iteration   8: 93.926 ops/s
Iteration   9: 93.592 ops/s
Iteration  10: 92.966 ops/s


Result "org.apache.skywalking.oap.server.microbench.core.profiling.ebpf.EBPFProfilingAnalyzerBenchmark.analyzeMedianDataSource":
  97.159 ±(99.9%) 3.105 ops/s [Average]
  (min, avg, max) = (92.966, 97.159, 101.237), stdev = 3.575
  CI (99.9%): [94.055, 100.264] (assumes normal distribution)


# Run complete. Total time: 00:20:43

REMEMBER: The numbers below are just data. To gain reusable insights, you need to follow up on
why the numbers are the way they are. Use profilers (see -prof, -lprof), design factorial
experiments, perform baseline and negative tests that provide experimental control, make sure
the benchmarking environment is safe on JVM/OS/HW level, ask for reviews from the domain experts.
Do not assume the numbers tell you what you want them to tell.

Benchmark                                                Mode  Cnt     Score    Error  Units
EBPFProfilingAnalyzerBenchmark.analyzeLowDataSource     thrpt   20  2134.237 ± 33.583  ops/s
EBPFProfilingAnalyzerBenchmark.analyzeMaxDataSource     thrpt   20     6.128 ±  0.149  ops/s
EBPFProfilingAnalyzerBenchmark.analyzeMedianDataSource  thrpt   20    97.159 ±  3.105  ops/s
 */
