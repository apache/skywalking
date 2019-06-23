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
     * # VM options: -XX:-RestrictContended -Dfile.encoding=UTF-8
     * # Warmup: 3 iterations, 10 s each
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 16 threads, ***WARNING: Synchronize iterations are disabled!***
     * # Benchmark mode: Throughput, ops/time
     * # Benchmark: org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV1Performance
     *
     * # Run progress: 0.00% complete, ETA 00:04:00
     * # Fork: 1 of 1
     * # Warmup Iteration   1: 14704890.118 ops/s
     * # Warmup Iteration   2: 14287641.619 ops/s
     * # Warmup Iteration   3: 15458430.259 ops/s
     * Iteration   1: 13431517.653 ops/s
     * Iteration   2: 13149441.237 ops/s
     * Iteration   3: 13053355.481 ops/s
     * Iteration   4: 13765972.372 ops/s
     * Iteration   5: 13960114.205 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV1Performance":
     *   13472080.189 ±(99.9%) 1498317.926 ops/s [Average]
     *   (min, avg, max) = (13053355.481, 13472080.189, 13960114.205), stdev = 389108.407
     *   CI (99.9%): [11973762.264, 14970398.115] (assumes normal distribution)
     *
     *
     * # JMH version: 1.21
     * # VM version: JDK 1.8.0_111, Java HotSpot(TM) 64-Bit Server VM, 25.111-b14
     * # VM invoker: /Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home/jre/bin/java
     * # VM options: -XX:-RestrictContended -Dfile.encoding=UTF-8
     * # Warmup: 3 iterations, 10 s each
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 16 threads, ***WARNING: Synchronize iterations are disabled!***
     * # Benchmark mode: Throughput, ops/time
     * # Benchmark: org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV2Performance
     *
     * # Run progress: 33.33% complete, ETA 00:02:50
     * # Fork: 1 of 1
     * # Warmup Iteration   1: 35253519.221 ops/s
     * # Warmup Iteration   2: 35240129.748 ops/s
     * # Warmup Iteration   3: 35327459.365 ops/s
     * Iteration   1: 35460249.746 ops/s
     * Iteration   2: 35256890.174 ops/s
     * Iteration   3: 35198403.751 ops/s
     * Iteration   4: 35269646.854 ops/s
     * Iteration   5: 35464672.745 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV2Performance":
     *   35329972.654 ±(99.9%) 477102.640 ops/s [Average]
     *   (min, avg, max) = (35198403.751, 35329972.654, 35464672.745), stdev = 123902.041
     *   CI (99.9%): [34852870.014, 35807075.294] (assumes normal distribution)
     *
     *
     * # JMH version: 1.21
     * # VM version: JDK 1.8.0_111, Java HotSpot(TM) 64-Bit Server VM, 25.111-b14
     * # VM invoker: /Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home/jre/bin/java
     * # VM options: -XX:-RestrictContended -Dfile.encoding=UTF-8
     * # Warmup: 3 iterations, 10 s each
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 16 threads, ***WARNING: Synchronize iterations are disabled!***
     * # Benchmark mode: Throughput, ops/time
     * # Benchmark: org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV3Performance
     *
     * # Run progress: 66.67% complete, ETA 00:01:25
     * # Fork: 1 of 1
     * # Warmup Iteration   1: 49325006.351 ops/s
     * # Warmup Iteration   2: 49265786.388 ops/s
     * # Warmup Iteration   3: 49060313.310 ops/s
     * Iteration   1: 49272327.915 ops/s
     * Iteration   2: 49395508.326 ops/s
     * Iteration   3: 49150974.993 ops/s
     * Iteration   4: 48919006.868 ops/s
     * Iteration   5: 49280987.994 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV3Performance":
     *   49203761.219 ±(99.9%) 697656.947 ops/s [Average]
     *   (min, avg, max) = (48919006.868, 49203761.219, 49395508.326), stdev = 181179.294
     *   CI (99.9%): [48506104.272, 49901418.166] (assumes normal distribution)
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
     * AtomicRangeIntegerTest.testGetAndIncrementV1Performance  thrpt    5  13472080.189 ± 1498317.926  ops/s
     * AtomicRangeIntegerTest.testGetAndIncrementV2Performance  thrpt    5  35329972.654 ±  477102.640  ops/s
     * AtomicRangeIntegerTest.testGetAndIncrementV3Performance  thrpt    5  49203761.219 ±  697656.947  ops/s
     */
}
