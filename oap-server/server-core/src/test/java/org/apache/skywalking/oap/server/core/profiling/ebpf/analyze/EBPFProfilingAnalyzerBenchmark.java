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

package org.apache.skywalking.oap.server.core.profiling.ebpf.analyze;

import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingStackType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzation;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(2)
@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput})
@Threads(4)
public class EBPFProfilingAnalyzerBenchmark {

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
        public LowDataSource() {
            super(generateStacks(calculateStackCount(5, 60, 10), 15, 30,
                    new double[]{100, 50, 45, 40, 35, 30, 15, 10, 5}, 5));
        }
    }

    @State(Scope.Benchmark)
    public static class MedianDatasource extends DataSource {
        public MedianDatasource() {
            super(generateStacks(calculateStackCount(5, 100, 200), 15, 30,
                    new double[]{50, 40, 35, 30, 20, 10, 7, 5, 2}, 3));
        }
    }

    @State(Scope.Benchmark)
    public static class HighDatasource extends DataSource {
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

    @Test
    public void run() throws Exception {
        new Runner(new OptionsBuilder()
                .include(".*" + getClass().getSimpleName() + ".*")
                .jvmArgsAppend("-Xmx512m", "-Xms512m")
                .build()).run();
    }

}
