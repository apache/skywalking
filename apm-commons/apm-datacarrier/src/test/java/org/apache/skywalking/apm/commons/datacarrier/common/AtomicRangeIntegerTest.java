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
     * # Warmup Iteration   1: 14187233.369 ops/s
     * # Warmup Iteration   2: 14991359.399 ops/s
     * # Warmup Iteration   3: 14681730.424 ops/s
     * Iteration   1: 15207634.686 ops/s
     * Iteration   2: 15220403.425 ops/s
     * Iteration   3: 14790811.581 ops/s
     * Iteration   4: 15038263.185 ops/s
     * Iteration   5: 15051067.987 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV1Performance":
     *   15061636.173 ±(99.9%) 668412.751 ops/s [Average]
     *   (min, avg, max) = (14790811.581, 15061636.173, 15220403.425), stdev = 173584.669
     *   CI (99.9%): [14393223.422, 15730048.923] (assumes normal distribution)
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
     * # Run progress: 33.33% complete, ETA 00:02:52
     * # Fork: 1 of 1
     * # Warmup Iteration   1: 34254277.962 ops/s
     * # Warmup Iteration   2: 35170915.203 ops/s
     * # Warmup Iteration   3: 35297868.773 ops/s
     * Iteration   1: 35222261.827 ops/s
     * Iteration   2: 35140300.524 ops/s
     * Iteration   3: 35021247.640 ops/s
     * Iteration   4: 35251568.512 ops/s
     * Iteration   5: 35461136.391 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV2Performance":
     *   35219302.979 ±(99.9%) 623877.176 ops/s [Average]
     *   (min, avg, max) = (35021247.640, 35219302.979, 35461136.391), stdev = 162018.921
     *   CI (99.9%): [34595425.803, 35843180.155] (assumes normal distribution)
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
     * # Warmup Iteration   1: 63643553.923 ops/s
     * # Warmup Iteration   2: 62395948.626 ops/s
     * # Warmup Iteration   3: 63558530.399 ops/s
     * Iteration   1: 63582808.808 ops/s
     * Iteration   2: 62405395.728 ops/s
     * Iteration   3: 62968220.187 ops/s
     * Iteration   4: 63121002.709 ops/s
     * Iteration   5: 65372094.803 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV3Performance":
     *   63489904.447 ±(99.9%) 4363169.494 ops/s [Average]
     *   (min, avg, max) = (62405395.728, 63489904.447, 65372094.803), stdev = 1133101.262
     *   CI (99.9%): [59126734.953, 67853073.941] (assumes normal distribution)
     *
     *
     * # Run complete. Total time: 00:04:17
     *
     * REMEMBER: The numbers below are just data. To gain reusable insights, you need to follow up on
     * why the numbers are the way they are. Use profilers (see -prof, -lprof), design factorial
     * experiments, perform baseline and negative tests that provide experimental control, make sure
     * the benchmarking environment is safe on JVM/OS/HW level, ask for reviews from the domain experts.
     * Do not assume the numbers tell you what you want them to tell.
     *
     * Benchmark                                                 Mode  Cnt         Score         Error  Units
     * AtomicRangeIntegerTest.testGetAndIncrementV1Performance  thrpt    5  15061636.173 ±  668412.751  ops/s
     * AtomicRangeIntegerTest.testGetAndIncrementV2Performance  thrpt    5  35219302.979 ±  623877.176  ops/s
     * AtomicRangeIntegerTest.testGetAndIncrementV3Performance  thrpt    5  63489904.447 ± 4363169.494  ops/s
     */
}
