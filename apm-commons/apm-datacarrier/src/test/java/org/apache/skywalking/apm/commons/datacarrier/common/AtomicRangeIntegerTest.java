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
                .threads(16)
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
     * # Threads: 16 threads, ***WARNING: Synchronize iterations are disabled!***
     * # Benchmark mode: Throughput, ops/time
     * # Benchmark: org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV1Performance
     *
     * # Run progress: 0.00% complete, ETA 00:04:00
     * # Fork: 1 of 1
     * # Warmup Iteration   1: 14938579.206 ops/s
     * # Warmup Iteration   2: 14843263.866 ops/s
     * # Warmup Iteration   3: 14623129.144 ops/s
     * Iteration   1: 14855912.181 ops/s
     * Iteration   2: 14804251.834 ops/s
     * Iteration   3: 14854210.513 ops/s
     * Iteration   4: 14673149.632 ops/s
     * Iteration   5: 14883026.322 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV1Performance":
     *   14814110.096 ±(99.9%) 322581.600 ops/s [Average]
     *   (min, avg, max) = (14673149.632, 14814110.096, 14883026.322), stdev = 83773.417
     *   CI (99.9%): [14491528.496, 15136691.696] (assumes normal distribution)
     *
     *
     * # JMH version: 1.21
     * # VM version: JDK 1.8.0_111, Java HotSpot(TM) 64-Bit Server VM, 25.111-b14
     * # VM invoker: /Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home/jre/bin/java
     * # VM options: -Dfile.encoding=UTF-8
     * # Warmup: 3 iterations, 10 s each
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 16 threads, ***WARNING: Synchronize iterations are disabled!***
     * # Benchmark mode: Throughput, ops/time
     * # Benchmark: org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV2Performance
     *
     * # Run progress: 33.33% complete, ETA 00:02:51
     * # Fork: 1 of 1
     * # Warmup Iteration   1: 39748102.285 ops/s
     * # Warmup Iteration   2: 39727113.232 ops/s
     * # Warmup Iteration   3: 39552652.273 ops/s
     * Iteration   1: 39730819.190 ops/s
     * Iteration   2: 39855449.773 ops/s
     * Iteration   3: 40237887.421 ops/s
     * Iteration   4: 40243203.362 ops/s
     * Iteration   5: 39525796.753 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV2Performance":
     *   39918631.300 ±(99.9%) 1218982.399 ops/s [Average]
     *   (min, avg, max) = (39525796.753, 39918631.300, 40243203.362), stdev = 316565.858
     *   CI (99.9%): [38699648.901, 41137613.699] (assumes normal distribution)
     *
     *
     * # JMH version: 1.21
     * # VM version: JDK 1.8.0_111, Java HotSpot(TM) 64-Bit Server VM, 25.111-b14
     * # VM invoker: /Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home/jre/bin/java
     * # VM options: -Dfile.encoding=UTF-8
     * # Warmup: 3 iterations, 10 s each
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 16 threads, ***WARNING: Synchronize iterations are disabled!***
     * # Benchmark mode: Throughput, ops/time
     * # Benchmark: org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV3Performance
     *
     * # Run progress: 66.67% complete, ETA 00:01:25
     * # Fork: 1 of 1
     * # Warmup Iteration   1: 62627086.244 ops/s
     * # Warmup Iteration   2: 62851550.961 ops/s
     * # Warmup Iteration   3: 62529169.610 ops/s
     * Iteration   1: 62639962.730 ops/s
     * Iteration   2: 62670122.248 ops/s
     * Iteration   3: 62796662.787 ops/s
     * Iteration   4: 62708993.670 ops/s
     * Iteration   5: 62731060.313 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV3Performance":
     *   62709360.350 ±(99.9%) 231426.557 ops/s [Average]
     *   (min, avg, max) = (62639962.730, 62709360.350, 62796662.787), stdev = 60100.742
     *   CI (99.9%): [62477933.793, 62940786.907] (assumes normal distribution)
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
     * AtomicRangeIntegerTest.testGetAndIncrementV1Performance  thrpt    5  14814110.096 ±  322581.600  ops/s
     * AtomicRangeIntegerTest.testGetAndIncrementV2Performance  thrpt    5  39918631.300 ± 1218982.399  ops/s
     * AtomicRangeIntegerTest.testGetAndIncrementV3Performance  thrpt    5  62709360.350 ±  231426.557  ops/s
     */
}
