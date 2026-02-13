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

package org.apache.skywalking.oap.server.core.benchmark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.profiling.ebpf.analyze.EBPFProfilingAnalyzer;
import org.apache.skywalking.oap.server.core.profiling.ebpf.analyze.EBPFProfilingStack;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingStackType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzation;
import org.junit.jupiter.api.Test;

@Slf4j
public class EBPFProfilingAnalyzerBenchmark {

    private static final Random RANDOM = new Random(System.currentTimeMillis());
    private static final int SYMBOL_LENGTH = 10;
    private static final char[] SYMBOL_TABLE = "abcdefgABCDEFG1234567890_[]<>.".toCharArray();
    private static final EBPFProfilingStackType[] STACK_TYPES = new EBPFProfilingStackType[] {
            EBPFProfilingStackType.KERNEL_SPACE, EBPFProfilingStackType.USER_SPACE};

    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASURE_ITERATIONS = 5;

    private static List<EBPFProfilingStack> generateStacks(final int totalStackCount,
                                                           final int perStackMinDepth,
                                                           final int perStackMaxDepth,
                                                           final double[] stackSymbolDuplicateRate,
                                                           final double stackDuplicateRate) {
        final int uniqStackCount = (int) (100 / stackDuplicateRate);
        final List<EBPFProfilingStack> stacks = new ArrayList<>(totalStackCount);
        final StackSymbolGenerator stackSymbolGenerator =
            new StackSymbolGenerator(stackSymbolDuplicateRate, perStackMaxDepth);
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

        StackSymbolGenerator(final double[] stackSymbolDuplicateRate, final int maxDepth) {
            this.stackDepthSymbolCount = new HashMap<>();
            for (int depth = 0; depth < maxDepth; depth++) {
                double rate = stackSymbolDuplicateRate[stackSymbolDuplicateRate.length - 1];
                if (stackSymbolDuplicateRate.length > depth) {
                    rate = stackSymbolDuplicateRate[depth];
                }
                final int uniqStackCount = (int) (100 / rate);
                stackDepthSymbolCount.put(depth, uniqStackCount);
            }
            this.existingSymbolMap = new HashMap<>();
        }

        String generate(final int depth) {
            List<String> symbols = existingSymbolMap.get(depth);
            if (symbols == null) {
                symbols = new ArrayList<>();
                existingSymbolMap.put(depth, symbols);
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

    private static EBPFProfilingStack generateStack(final int stackMinDepth, final int stackMaxDepth,
                                                    final StackSymbolGenerator stackSymbolGenerator) {
        final int stackDepth = stackMinDepth + RANDOM.nextInt(stackMaxDepth - stackMinDepth);
        final List<EBPFProfilingStack.Symbol> symbols = new ArrayList<>(stackDepth);
        for (int i = 0; i < stackDepth; i++) {
            final EBPFProfilingStack.Symbol symbol = new EBPFProfilingStack.Symbol(
                    stackSymbolGenerator.generate(i), buildStackType(i, stackDepth));
            symbols.add(symbol);
        }
        final EBPFProfilingStack stack = new EBPFProfilingStack();
        stack.setDumpCount(RANDOM.nextInt(100));
        stack.setSymbols(symbols);
        return stack;
    }

    private static EBPFProfilingStackType buildStackType(final int currentDepth, final int totalDepth) {
        final int partition = totalDepth / STACK_TYPES.length;
        for (int i = 1; i <= STACK_TYPES.length; i++) {
            if (currentDepth < i * partition) {
                return STACK_TYPES[i - 1];
            }
        }
        return STACK_TYPES[STACK_TYPES.length - 1];
    }

    private static int calculateStackCount(final int stackReportPeriodSecond,
                                           final int totalTimeMinute,
                                           final int combineInstanceCount) {
        return (int) (TimeUnit.MINUTES.toSeconds(totalTimeMinute)
            / stackReportPeriodSecond * combineInstanceCount);
    }

    private static void analyze(final List<EBPFProfilingStack> stacks) {
        new EBPFProfilingAnalyzer(null, 100, 5)
            .generateTrees(new EBPFProfilingAnalyzation(), stacks.parallelStream());
    }

    @Test
    public void analyzeLowDataSource() {
        final List<EBPFProfilingStack> stacks = generateStacks(
            calculateStackCount(5, 60, 10), 15, 30,
            new double[] {100, 50, 45, 40, 35, 30, 15, 10, 5}, 5);
        runBenchmark("low", stacks);
    }

    @Test
    public void analyzeMedianDataSource() {
        final List<EBPFProfilingStack> stacks = generateStacks(
            calculateStackCount(5, 100, 200), 15, 30,
            new double[] {50, 40, 35, 30, 20, 10, 7, 5, 2}, 3);
        runBenchmark("median", stacks);
    }

    @Test
    public void analyzeHighDataSource() {
        final List<EBPFProfilingStack> stacks = generateStacks(
            calculateStackCount(5, 2 * 60, 2000), 15, 40,
            new double[] {30, 27, 25, 20, 17, 15, 10, 7, 5, 2, 1}, 1);
        runBenchmark("high", stacks);
    }

    private void runBenchmark(final String label, final List<EBPFProfilingStack> stacks) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            analyze(stacks);
        }
        final long start = System.nanoTime();
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            analyze(stacks);
        }
        final long elapsed = System.nanoTime() - start;
        log.info("{}: {} iterations, {} ms/op",
                 label, MEASURE_ITERATIONS,
                 elapsed / MEASURE_ITERATIONS / 1_000_000);
    }
}
