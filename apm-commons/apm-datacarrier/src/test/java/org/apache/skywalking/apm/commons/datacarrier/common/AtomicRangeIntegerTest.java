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
        Assert.assertEquals(1, (int) atomicI.floatValue());
        Assert.assertEquals(1, (int) atomicI.doubleValue());
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
        Options opt = new OptionsBuilder().include(AtomicRangeIntegerTest.class.getSimpleName())
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
     * # VM options: -XX:-RestrictContended -Dfile.encoding=UTF-8
     * # Warmup: 3 iterations, 10 s each
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 128 threads, ***WARNING: Synchronize iterations are disabled!***
     * # Benchmark mode: Throughput, ops/time
     * # Benchmark: org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV1Performance
     *
     * # Run progress: 0.00% complete, ETA 00:04:00
     * # Fork: 1 of 1
     * # Warmup Iteration   1: 14087955.036 ops/s
     * # Warmup Iteration   2: 15853193.651 ops/s
     * # Warmup Iteration   3: 14242562.576 ops/s
     * Iteration   1: 13507077.199 ops/s
     * Iteration   2: 13524108.304 ops/s
     * Iteration   3: 13428875.424 ops/s
     * Iteration   4: 13442334.399 ops/s
     * Iteration   5: 13581207.442 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV1Performance":
     *   13496720.554 ±(99.9%) 240134.803 ops/s [Average]
     *   (min, avg, max) = (13428875.424, 13496720.554, 13581207.442), stdev = 62362.246
     *   CI (99.9%): [13256585.750, 13736855.357] (assumes normal distribution)
     *
     *
     * # JMH version: 1.21
     * # VM version: JDK 1.8.0_111, Java HotSpot(TM) 64-Bit Server VM, 25.111-b14
     * # VM invoker: /Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home/jre/bin/java
     * # VM options: -XX:-RestrictContended -Dfile.encoding=UTF-8
     * # Warmup: 3 iterations, 10 s each
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 128 threads, ***WARNING: Synchronize iterations are disabled!***
     * # Benchmark mode: Throughput, ops/time
     * # Benchmark: org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV2Performance
     *
     * # Run progress: 33.33% complete, ETA 00:02:52
     * # Fork: 1 of 1
     * # Warmup Iteration   1: 38963151.964 ops/s
     * # Warmup Iteration   2: 38748023.773 ops/s
     * # Warmup Iteration   3: 39049777.582 ops/s
     * Iteration   1: 39534928.550 ops/s
     * Iteration   2: 39020804.604 ops/s
     * Iteration   3: 38991508.452 ops/s
     * Iteration   4: 39025237.001 ops/s
     * Iteration   5: 39433780.645 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV2Performance":
     *   39201251.850 ±(99.9%) 1005866.969 ops/s [Average]
     *   (min, avg, max) = (38991508.452, 39201251.850, 39534928.550), stdev = 261220.458
     *   CI (99.9%): [38195384.881, 40207118.820] (assumes normal distribution)
     *
     *
     * # JMH version: 1.21
     * # VM version: JDK 1.8.0_111, Java HotSpot(TM) 64-Bit Server VM, 25.111-b14
     * # VM invoker: /Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home/jre/bin/java
     * # VM options: -XX:-RestrictContended -Dfile.encoding=UTF-8
     * # Warmup: 3 iterations, 10 s each
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 128 threads, ***WARNING: Synchronize iterations are disabled!***
     * # Benchmark mode: Throughput, ops/time
     * # Benchmark: org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV3Performance
     *
     * # Run progress: 66.67% complete, ETA 00:01:25
     * # Fork: 1 of 1
     * # Warmup Iteration   1: 45437159.014 ops/s
     * # Warmup Iteration   2: 45253129.637 ops/s
     * # Warmup Iteration   3: 45394394.135 ops/s
     * Iteration   1: 45434263.958 ops/s
     * Iteration   2: 45283522.683 ops/s
     * Iteration   3: 47116623.190 ops/s
     * Iteration   4: 46012311.703 ops/s
     * Iteration   5: 45316353.774 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testGetAndIncrementV3Performance":
     *   45832615.061 ±(99.9%) 2987464.163 ops/s [Average]
     *   (min, avg, max) = (45283522.683, 45832615.061, 47116623.190), stdev = 775834.956
     *   CI (99.9%): [42845150.898, 48820079.225] (assumes normal distribution)
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
     * AtomicRangeIntegerTest.testGetAndIncrementV1Performance  thrpt    5  13496720.554 ±  240134.803  ops/s
     * AtomicRangeIntegerTest.testGetAndIncrementV2Performance  thrpt    5  39201251.850 ± 1005866.969  ops/s
     * AtomicRangeIntegerTest.testGetAndIncrementV3Performance  thrpt    5  45832615.061 ± 2987464.163  ops/s
     */
}
