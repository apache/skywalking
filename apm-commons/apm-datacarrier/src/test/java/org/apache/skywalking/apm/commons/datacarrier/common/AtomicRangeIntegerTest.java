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
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by xin on 2017/7/14.
 */
public class AtomicRangeIntegerTest {

    static class AtomicRangeIntegerOri extends Number implements Serializable {
        private static final long serialVersionUID = -4099792402691141643L;
        private AtomicInteger value;
        private int startValue;
        private int endValue;

        public AtomicRangeIntegerOri(int startValue, int maxValue) {
            this.value = new AtomicInteger(startValue);
            this.startValue = startValue;
            this.endValue = maxValue - 1;
        }

        public final int getAndIncrement() {
            int current;
            int next;
            do {
                current = this.value.get();
                next = current >= this.endValue ? this.startValue : current + 1;
            }
            while (!this.value.compareAndSet(current, next));

            return current;
        }

        public final int get() {
            return this.value.get();
        }

        public int intValue() {
            return this.value.intValue();
        }

        public long longValue() {
            return this.value.longValue();
        }

        public float floatValue() {
            return this.value.floatValue();
        }

        public double doubleValue() {
            return this.value.doubleValue();
        }
    }

    private static AtomicRangeInteger ATOMIC_NEW = new AtomicRangeInteger(0, 100);
    private static AtomicRangeIntegerOri ATOMIC_ORI = new AtomicRangeIntegerOri(0, 100);

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
        ATOMIC_ORI.getAndIncrement();
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
     * # Benchmark: org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testNewGetAndIncrementPerformance
     *
     * # Run progress: 0.00% complete, ETA 00:02:40
     * # Fork: 1 of 1
     * # Warmup Iteration   1: 41358475.014 ops/s
     * # Warmup Iteration   2: 40973232.064 ops/s
     * # Warmup Iteration   3: 41310422.853 ops/s
     * Iteration   1: 41557782.370 ops/s
     * Iteration   2: 42723032.686 ops/s
     * Iteration   3: 41957321.407 ops/s
     * Iteration   4: 40708422.580 ops/s
     * Iteration   5: 40424870.574 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testNewGetAndIncrementPerformance":
     *   41474285.923 ±(99.9%) 3595500.827 ops/s [Average]
     *   (min, avg, max) = (40424870.574, 41474285.923, 42723032.686), stdev = 933740.147
     *   CI (99.9%): [37878785.096, 45069786.751] (assumes normal distribution)
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
     * # Benchmark: org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testOriGetAndIncrementPerformance
     *
     * # Run progress: 50.00% complete, ETA 00:01:25
     * # Fork: 1 of 1
     * # Warmup Iteration   1: 14169937.124 ops/s
     * # Warmup Iteration   2: 14087015.239 ops/s
     * # Warmup Iteration   3: 13955313.979 ops/s
     * Iteration   1: 13984516.590 ops/s
     * Iteration   2: 13913174.492 ops/s
     * Iteration   3: 13824113.805 ops/s
     * Iteration   4: 14886990.831 ops/s
     * Iteration   5: 14388283.816 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeIntegerTest.testOriGetAndIncrementPerformance":
     *   14199415.907 ±(99.9%) 1697559.657 ops/s [Average]
     *   (min, avg, max) = (13824113.805, 14199415.907, 14886990.831), stdev = 440850.852
     *   CI (99.9%): [12501856.250, 15896975.564] (assumes normal distribution)
     *
     *
     * # Run complete. Total time: 00:02:51
     *
     * REMEMBER: The numbers below are just data. To gain reusable insights, you need to follow up on
     * why the numbers are the way they are. Use profilers (see -prof, -lprof), design factorial
     * experiments, perform baseline and negative tests that provide experimental control, make sure
     * the benchmarking environment is safe on JVM/OS/HW level, ask for reviews from the domain experts.
     * Do not assume the numbers tell you what you want them to tell.
     *
     * Benchmark                                                  Mode  Cnt         Score         Error  Units
     * AtomicRangeIntegerTest.testNewGetAndIncrementPerformance  thrpt    5  41474285.923 ± 3595500.827  ops/s
     * AtomicRangeIntegerTest.testOriGetAndIncrementPerformance  thrpt    5  14199415.907 ± 1697559.657  ops/s
     */
}
