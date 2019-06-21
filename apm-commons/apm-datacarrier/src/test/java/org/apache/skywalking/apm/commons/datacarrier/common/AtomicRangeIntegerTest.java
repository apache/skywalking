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

    private static AtomicRangeInteger ATOMIC_V3 = new AtomicRangeInteger(0, 100);
    private static AtomicRangeIntegerV1 ATOMIC_V1 = new AtomicRangeIntegerV1(0, 100);
    private static AtomicRangeIntegerV2 ATOMIC_V2 = new AtomicRangeIntegerV2(0, 100);

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
    public void testGetAndIncrementV1Performance() {
        ATOMIC_V1.getAndIncrement();
    }

    @Test
    @Benchmark
    public void testGetAndIncrementV2Performance() {
        ATOMIC_V2.getAndIncrement();
    }

    @Test
    @Benchmark
    public void testGetAndIncrementV3Performance() {
        ATOMIC_V3.getAndIncrement();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(AtomicRangeIntegerTest.class.getSimpleName())
                .forks(1)
                .warmupIterations(3)
                .threads(128)
                .syncIterations(false)
                .output("/tmp/jmh.log")
                .measurementIterations(5)
                .build();

        new Runner(opt).run();
    }

    /**
     * # JMH version: 1.21
     * # VM version: JDK 1.8.0_111, Java HotSpot(TM) 64-Bit Server VM, 25.111-b14
     * # VM invoker: /Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home/jre/bin/java
     * # VM options: -Dfile.encoding=UTF-8
     * # Warmup: 3 iterations, 10 s each
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 128 threads, ***WARNING: Synchronize iterations are disabled!***
     * # Benchmark mode: Throughput, ops/time
     * # Benchmark: org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV1Performance
     *
     * # Run progress: 0.00% complete, ETA 00:04:00
     * # Fork: 1 of 1
     * # Warmup Iteration   1: 15557566.887 ops/s
     * # Warmup Iteration   2: 15216783.328 ops/s
     * # Warmup Iteration   3: 15358396.531 ops/s
     * Iteration   1: 15544068.859 ops/s
     * Iteration   2: 15222304.590 ops/s
     * Iteration   3: 15477987.883 ops/s
     * Iteration   4: 15313428.195 ops/s
     * Iteration   5: 15216173.993 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV1Performance":
     *   15354792.704 ±(99.9%) 575931.305 ops/s [Average]
     *   (min, avg, max) = (15216173.993, 15354792.704, 15544068.859), stdev = 149567.531
     *   CI (99.9%): [14778861.399, 15930724.009] (assumes normal distribution)
     *
     *
     * # JMH version: 1.21
     * # VM version: JDK 1.8.0_111, Java HotSpot(TM) 64-Bit Server VM, 25.111-b14
     * # VM invoker: /Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home/jre/bin/java
     * # VM options: -Dfile.encoding=UTF-8
     * # Warmup: 3 iterations, 10 s each
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 128 threads, ***WARNING: Synchronize iterations are disabled!***
     * # Benchmark mode: Throughput, ops/time
     * # Benchmark: org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV2Performance
     *
     * # Run progress: 33.33% complete, ETA 00:02:51
     * # Fork: 1 of 1
     * # Warmup Iteration   1: 39035341.765 ops/s
     * # Warmup Iteration   2: 39115740.504 ops/s
     * # Warmup Iteration   3: 38933445.873 ops/s
     * Iteration   1: 39357008.058 ops/s
     * Iteration   2: 39604401.634 ops/s
     * Iteration   3: 39148126.627 ops/s
     * Iteration   4: 38744440.251 ops/s
     * Iteration   5: 39419939.311 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV2Performance":
     *   39254783.176 ±(99.9%) 1265135.878 ops/s [Average]
     *   (min, avg, max) = (38744440.251, 39254783.176, 39604401.634), stdev = 328551.770
     *   CI (99.9%): [37989647.298, 40519919.054] (assumes normal distribution)
     *
     *
     * # JMH version: 1.21
     * # VM version: JDK 1.8.0_111, Java HotSpot(TM) 64-Bit Server VM, 25.111-b14
     * # VM invoker: /Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home/jre/bin/java
     * # VM options: -Dfile.encoding=UTF-8
     * # Warmup: 3 iterations, 10 s each
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 128 threads, ***WARNING: Synchronize iterations are disabled!***
     * # Benchmark mode: Throughput, ops/time
     * # Benchmark: org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV3Performance
     *
     * # Run progress: 66.67% complete, ETA 00:01:25
     * # Fork: 1 of 1
     * # Warmup Iteration   1: 57460686.158 ops/s
     * # Warmup Iteration   2: 57467372.926 ops/s
     * # Warmup Iteration   3: 57409919.982 ops/s
     * Iteration   1: 57821274.998 ops/s
     * Iteration   2: 57850586.140 ops/s
     * Iteration   3: 57825983.074 ops/s
     * Iteration   4: 59137108.825 ops/s
     * Iteration   5: 59021353.512 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV3Performance":
     *   58331261.310 ±(99.9%) 2634286.708 ops/s [Average]
     *   (min, avg, max) = (57821274.998, 58331261.310, 59137108.825), stdev = 684115.893
     *   CI (99.9%): [55696974.602, 60965548.018] (assumes normal distribution)
     *
     *
     * # Run complete. Total time: 00:04:16
     *
     * REMEMBER: The numbers below are just data. To gain reusable insights, you need to follow up on
     * why the numbers are the way they are. Use profilers (see -prof, -lprof), design factorial
     * experiments, perform baseline and negative tests that provide experimental control, make sure
     * the benchmarking environment is safe on JVM/OS/HW level, ask for reviews from the domain experts.
     * Do not assume the numbers tell you what you want them to tell.
     *
     * Benchmark                                                 Mode  Cnt         Score         Error  Units
     * AtomicRangeIntegerTest.testGetAndIncrementV1Performance  thrpt    5  15354792.704 ±  575931.305  ops/s
     * AtomicRangeIntegerTest.testGetAndIncrementV2Performance  thrpt    5  39254783.176 ± 1265135.878  ops/s
     * AtomicRangeIntegerTest.testGetAndIncrementV3Performance  thrpt    5  58331261.310 ± 2634286.708  ops/s
     */
}
