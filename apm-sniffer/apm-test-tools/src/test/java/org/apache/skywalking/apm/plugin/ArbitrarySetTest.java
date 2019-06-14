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

package org.apache.skywalking.apm.plugin;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * # JMH version: 1.21
 * # VM version: JDK 10.0.1, Java HotSpot(TM) 64-Bit Server VM, 10.0.1+10
 * # VM invoker: /Library/Java/JavaVirtualMachines/jdk-10.0.1.jdk/Contents/Home/bin/java
 * # VM options: -javaagent:/Applications/IntelliJ IDEA.app/Contents/lib/idea_rt.jar=50729:/Applications/IntelliJ IDEA.app/Contents/bin -Dfile.encoding=UTF-8
 * # Warmup: 4 iterations, 10 s each
 * # Measurement: 5 iterations, 10 s each
 * # Timeout: 10 min per iteration
 * # Threads: 1 thread, will synchronize iterations
 * # Benchmark mode: Throughput, ops/time
 * # Benchmark: org.apache.skywalking.apm.plugin.ArbitrarySetTest.array
 * <p>
 * # Run progress: 0.00% complete, ETA 00:09:00
 * # Fork: 1 of 2
 * # Warmup Iteration   1: 1227.556 ops/ms
 * # Warmup Iteration   2: 1710.387 ops/ms
 * # Warmup Iteration   3: 1692.754 ops/ms
 * # Warmup Iteration   4: 1779.140 ops/ms
 * Iteration   1: 1833.746 ops/ms
 * Iteration   2: 1862.485 ops/ms
 * Iteration   3: 1827.150 ops/ms
 * Iteration   4: 1693.042 ops/ms
 * Iteration   5: 1643.173 ops/ms
 * <p>
 * # Run progress: 16.67% complete, ETA 00:07:39
 * # Fork: 2 of 2
 * # Warmup Iteration   1: 1455.851 ops/ms
 * # Warmup Iteration   2: 1629.368 ops/ms
 * # Warmup Iteration   3: 1729.879 ops/ms
 * # Warmup Iteration   4: 1790.962 ops/ms
 * Iteration   1: 1681.082 ops/ms
 * Iteration   2: 1557.253 ops/ms
 * Iteration   3: 1510.295 ops/ms
 * Iteration   4: 1559.166 ops/ms
 * Iteration   5: 1615.465 ops/ms
 * <p>
 * <p>
 * Result "org.apache.skywalking.apm.plugin.ArbitrarySetTest.array":
 * 1678.286 ±(99.9%) 190.386 ops/ms [Average]
 * (min, avg, max) = (1510.295, 1678.286, 1862.485), stdev = 125.929
 * CI (99.9%): [1487.900, 1868.672] (assumes normal distribution)
 * <p>
 * <p>
 * # JMH version: 1.21
 * # VM version: JDK 10.0.1, Java HotSpot(TM) 64-Bit Server VM, 10.0.1+10
 * # VM invoker: /Library/Java/JavaVirtualMachines/jdk-10.0.1.jdk/Contents/Home/bin/java
 * # VM options: -javaagent:/Applications/IntelliJ IDEA.app/Contents/lib/idea_rt.jar=50729:/Applications/IntelliJ IDEA.app/Contents/bin -Dfile.encoding=UTF-8
 * # Warmup: 4 iterations, 10 s each
 * # Measurement: 5 iterations, 10 s each
 * # Timeout: 10 min per iteration
 * # Threads: 1 thread, will synchronize iterations
 * # Benchmark mode: Throughput, ops/time
 * # Benchmark: org.apache.skywalking.apm.plugin.ArbitrarySetTest.arrayList
 * <p>
 * # Run progress: 33.33% complete, ETA 00:06:05
 * # Fork: 1 of 2
 * # Warmup Iteration   1: 575.365 ops/ms
 * # Warmup Iteration   2: 627.217 ops/ms
 * # Warmup Iteration   3: 611.638 ops/ms
 * # Warmup Iteration   4: 584.694 ops/ms
 * Iteration   1: 593.597 ops/ms
 * Iteration   2: 582.952 ops/ms
 * Iteration   3: 586.201 ops/ms
 * Iteration   4: 668.791 ops/ms
 * Iteration   5: 668.208 ops/ms
 * <p>
 * # Run progress: 50.00% complete, ETA 00:04:34
 * # Fork: 2 of 2
 * # Warmup Iteration   1: 567.132 ops/ms
 * # Warmup Iteration   2: 619.129 ops/ms
 * # Warmup Iteration   3: 719.696 ops/ms
 * # Warmup Iteration   4: 734.769 ops/ms
 * Iteration   1: 731.429 ops/ms
 * Iteration   2: 684.019 ops/ms
 * Iteration   3: 645.475 ops/ms
 * Iteration   4: 634.259 ops/ms
 * Iteration   5: 681.604 ops/ms
 * <p>
 * <p>
 * Result "org.apache.skywalking.apm.plugin.ArbitrarySetTest.arrayList":
 * 647.654 ±(99.9%) 73.776 ops/ms [Average]
 * (min, avg, max) = (582.952, 647.654, 731.429), stdev = 48.799
 * CI (99.9%): [573.877, 721.430] (assumes normal distribution)
 * <p>
 * <p>
 * # JMH version: 1.21
 * # VM version: JDK 10.0.1, Java HotSpot(TM) 64-Bit Server VM, 10.0.1+10
 * # VM invoker: /Library/Java/JavaVirtualMachines/jdk-10.0.1.jdk/Contents/Home/bin/java
 * # VM options: -javaagent:/Applications/IntelliJ IDEA.app/Contents/lib/idea_rt.jar=50729:/Applications/IntelliJ IDEA.app/Contents/bin -Dfile.encoding=UTF-8
 * # Warmup: 4 iterations, 10 s each
 * # Measurement: 5 iterations, 10 s each
 * # Timeout: 10 min per iteration
 * # Threads: 1 thread, will synchronize iterations
 * # Benchmark mode: Throughput, ops/time
 * # Benchmark: org.apache.skywalking.apm.plugin.ArbitrarySetTest.treeMap
 * <p>
 * # Run progress: 66.67% complete, ETA 00:03:02
 * # Fork: 1 of 2
 * # Warmup Iteration   1: 149.772 ops/ms
 * # Warmup Iteration   2: 164.658 ops/ms
 * # Warmup Iteration   3: 181.832 ops/ms
 * # Warmup Iteration   4: 181.535 ops/ms
 * Iteration   1: 178.571 ops/ms
 * Iteration   2: 135.320 ops/ms
 * Iteration   3: 152.644 ops/ms
 * Iteration   4: 166.896 ops/ms
 * Iteration   5: 167.542 ops/ms
 * <p>
 * # Run progress: 83.33% complete, ETA 00:01:31
 * # Fork: 2 of 2
 * # Warmup Iteration   1: 171.791 ops/ms
 * # Warmup Iteration   2: 178.640 ops/ms
 * # Warmup Iteration   3: 167.416 ops/ms
 * # Warmup Iteration   4: 171.610 ops/ms
 * Iteration   1: 179.966 ops/ms
 * Iteration   2: 187.318 ops/ms
 * Iteration   3: 183.934 ops/ms
 * Iteration   4: 181.388 ops/ms
 * Iteration   5: 186.286 ops/ms
 * <p>
 * <p>
 * Result "org.apache.skywalking.apm.plugin.ArbitrarySetTest.treeMap":
 * 171.986 ±(99.9%) 25.408 ops/ms [Average]
 * (min, avg, max) = (135.320, 171.986, 187.318), stdev = 16.806
 * CI (99.9%): [146.578, 197.394] (assumes normal distribution)
 * <p>
 * <p>
 * # Run complete. Total time: 00:09:07
 * <p>
 * REMEMBER: The numbers below are just data. To gain reusable insights, you need to follow up on
 * why the numbers are the way they are. Use profilers (see -prof, -lprof), design factorial
 * experiments, perform baseline and negative tests that provide experimental control, make sure
 * the benchmarking environment is safe on JVM/OS/HW level, ask for reviews from the domain experts.
 * Do not assume the numbers tell you what you want them to tell.
 * <p>
 * Benchmark                    Mode  Cnt     Score     Error   Units
 * ArbitrarySetTest.array      thrpt   10  1678.286 ± 190.386  ops/ms
 * ArbitrarySetTest.arrayList  thrpt   10   647.654 ±  73.776  ops/ms
 * ArbitrarySetTest.treeMap    thrpt   10   171.986 ±  25.408  ops/ms
 *
 * @author kezhenxu94
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(2)
@Warmup(iterations = 4)
@Measurement(iterations = 5)
public class ArbitrarySetTest {
    private static final Object PLACEHOLDER = new Object();

    @Benchmark
    public void arrayList() {
        ArrayList<Object> list = new ArrayList<Object>(Collections.nCopies(20, PLACEHOLDER));
        for (int i = 0; i < 100; i++) {
            int oldSize = list.size();
            if (i >= oldSize) {
                int newSize = Math.max(oldSize * 2, i);
                ArrayList<Object> newList = new ArrayList<Object>(newSize);
                newList.addAll(list);
                newList.addAll(oldSize, Collections.nCopies(newSize - oldSize, PLACEHOLDER));
                list = newList;
            }
            list.set(i, i);
        }
    }

    @Benchmark
    public void array() {
        Object[] array = new Object[20];
        Arrays.fill(array, PLACEHOLDER);
        for (int i = 1; i <= 100; i++) {
            int length = array.length;
            if (i >= length) {
                Object[] newArray = new Object[Math.max(i, length * 2)];
                System.arraycopy(array, 0, newArray, 0, length);
                array = newArray;
            }
            array[i] = i;
        }
    }

    @Benchmark
    public void treeMap() {
        final Map<Integer, Object> treeMap = new TreeMap<Integer, Object>();
        for (int i = 0; i < 100; i++) {
            treeMap.put(i, i);
        }
    }

    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder().include(ArbitrarySetTest.class.getSimpleName()).build();
        new Runner(options).run();
    }
}
