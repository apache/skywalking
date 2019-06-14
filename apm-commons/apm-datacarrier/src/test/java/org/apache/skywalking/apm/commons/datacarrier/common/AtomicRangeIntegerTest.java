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


package org.apache.skywalking.apm.commons.datacarrier.common;

import org.junit.Assert;
import org.junit.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Created by xin on 2017/7/14.
 */
public class AtomicRangeIntegerTest {

    private static AtomicRangeInteger ATOMIC_NEW = new AtomicRangeInteger(0, 100);
    private static AtomicRangeInteger ATOMIC_ORI = new AtomicRangeInteger(0, 100);

    @Test
    public void testGetAndIncrement() {
        AtomicRangeInteger atomicI = new AtomicRangeInteger(0, 10);
        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(i, atomicI.getAndIncrement());
        }
        Assert.assertEquals(0, atomicI.getAndIncrement());
        Assert.assertEquals(1, atomicI.get());
        Assert.assertEquals(1, atomicI.intValue());
        Assert.assertEquals(1, atomicI.longValue());
        Assert.assertEquals(1, (int)atomicI.floatValue());
        Assert.assertEquals(1, (int)atomicI.doubleValue());
    }

    @Test
    @Benchmark
    public void testOriGetAndIncrementPerformance() {
        ATOMIC_ORI.oriGetAndIncrement();
    }

    @Test
    @Benchmark
    public void testNewGetAndIncrementPerformance() {
        ATOMIC_NEW.getAndIncrement();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(AtomicRangeIntegerTest.class.getSimpleName())
                .forks(1)
                .threads(4)
                .output("/tmp/jmh.log")
                .warmupIterations(0)
                .measurementIterations(5)
                .build();

        new Runner(opt).run();
    }

    /**
     * # JMH version: 1.21
     * # VM version: JDK 1.8.0_111, Java HotSpot(TM) 64-Bit Server VM, 25.111-b14
     * # VM invoker: /Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home/jre/bin/java
     * # VM options: -Dfile.encoding=UTF-8
     * # Warmup: <none>
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 128 threads, will synchronize iterations
     * # Benchmark mode: Throughput, ops/time
     * # Benchmark: org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testNewGetAndIncrementPerformance
     *
     * # Run progress: 0.00% complete, ETA 00:01:40
     * # Fork: 1 of 1
     * Iteration   1: 35611646.554 ops/s
     * Iteration   2: 35406310.478 ops/s
     * Iteration   3: 35551443.713 ops/s
     * Iteration   4: 35482144.221 ops/s
     * Iteration   5: 35504409.888 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testNewGetAndIncrementPerformance":
     *   35511190.971 ±(99.9%) 295781.229 ops/s [Average]
     *   (min, avg, max) = (35406310.478, 35511190.971, 35611646.554), stdev = 76813.446
     *   CI (99.9%): [35215409.742, 35806972.200] (assumes normal distribution)
     *
     *
     * # JMH version: 1.21
     * # VM version: JDK 1.8.0_111, Java HotSpot(TM) 64-Bit Server VM, 25.111-b14
     * # VM invoker: /Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home/jre/bin/java
     * # VM options: -Dfile.encoding=UTF-8
     * # Warmup: <none>
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 128 threads, will synchronize iterations
     * # Benchmark mode: Throughput, ops/time
     * # Benchmark: org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testOriGetAndIncrementPerformance
     *
     * # Run progress: 50.00% complete, ETA 00:01:00
     * # Fork: 1 of 1
     * Iteration   1: 14101155.083 ops/s
     * Iteration   2: 14583704.033 ops/s
     * Iteration   3: 14535608.776 ops/s
     * Iteration   4: 14188161.484 ops/s
     * Iteration   5: 14041189.099 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testOriGetAndIncrementPerformance":
     *   14289963.695 ±(99.9%) 971336.043 ops/s [Average]
     *   (min, avg, max) = (14041189.099, 14289963.695, 14583704.033), stdev = 252252.886
     *   CI (99.9%): [13318627.652, 15261299.739] (assumes normal distribution)
     *
     *
     * # Run complete. Total time: 00:02:00
     *
     * REMEMBER: The numbers below are just data. To gain reusable insights, you need to follow up on
     * why the numbers are the way they are. Use profilers (see -prof, -lprof), design factorial
     * experiments, perform baseline and negative tests that provide experimental control, make sure
     * the benchmarking environment is safe on JVM/OS/HW level, ask for reviews from the domain experts.
     * Do not assume the numbers tell you what you want them to tell.
     *
     * Benchmark                                                  Mode  Cnt         Score        Error  Units
     * AtomicRangeIntegerTest.testNewGetAndIncrementPerformance  thrpt    5  35511190.971 ± 295781.229  ops/s
     * AtomicRangeIntegerTest.testOriGetAndIncrementPerformance  thrpt    5  14289963.695 ± 971336.043  ops/s
     */
}
