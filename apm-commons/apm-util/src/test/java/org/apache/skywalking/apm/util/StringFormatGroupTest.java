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

package org.apache.skywalking.apm.util;

import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

public class StringFormatGroupTest {
    @Benchmark
    @Test
    public void testMatch() {
        StringFormatGroup group = new StringFormatGroup();
        group.addRule("/name/*/add", "/name/.+/add");
        Assert.assertEquals("/name/*/add", group.format("/name/test/add").getName());

        group = new StringFormatGroup();
        group.addRule("/name/*/add/{orderId}", "/name/.+/add/.*");
        Assert.assertEquals("/name/*/add/{orderId}", group.format("/name/test/add/12323").getName());
    }

    @Benchmark
    @Test
    public void test100Rule() {
        StringFormatGroup group = new StringFormatGroup();
        group.addRule("/name/*/add/{orderId}", "/name/.+/add/.*");
        for (int i = 0; i < 100; i++) {
            group.addRule("/name/*/add/{orderId}" + "/" + 1, "/name/.+/add/.*" + "/abc");
        }
        Assert.assertEquals("/name/*/add/{orderId}", group.format("/name/test/add/12323").getName());
    }

    /**
     * The report below shows this pattern match performance is much about rule numbers.
     * This is a single thread test.
     *
     * @throws RunnerException
     */
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void performanceBenchmark() throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(StringFormatGroupTest.class.getSimpleName())
            .forks(1)
            .warmupIterations(0)
            .measurementIterations(5)
            .build();

        new Runner(opt).run();
    }

    /*********************************
     * # JMH version: 1.21
     * # VM version: JDK 1.8.0_91, Java HotSpot(TM) 64-Bit Server VM, 25.91-b14
     * # VM invoker: /Users/wusheng/Documents/applications/jdk1.8.0_91.jdk/Contents/Home/jre/bin/java
     * # VM options: -ea -Didea.test.cyclic.buffer.size=1048576 -javaagent:/Applications/IntelliJ IDEA.app/Contents/lib/idea_rt.jar=54841:/Applications/IntelliJ IDEA.app/Contents/bin -Dfile.encoding=UTF-8
     * # Warmup: <none>
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 1 thread, will synchronize iterations
     * # Benchmark mode: Throughput, ops/time
     * # Benchmark: org.apache.skywalking.apm.util.StringFormatGroupTest.test100Rule
     *
     * # Run progress: 0.00% complete, ETA 00:01:40
     * # Fork: 1 of 1
     * Iteration   1: 32016.496 ops/s
     * Iteration   2: 36703.873 ops/s
     * Iteration   3: 37121.543 ops/s
     * Iteration   4: 36898.225 ops/s
     * Iteration   5: 34712.564 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.util.StringFormatGroupTest.test100Rule":
     *   35490.540 ±(99.9%) 8345.368 ops/s [Average]
     *   (min, avg, max) = (32016.496, 35490.540, 37121.543), stdev = 2167.265
     *   CI (99.9%): [27145.173, 43835.908] (assumes normal distribution)
     *
     *
     * # JMH version: 1.21
     * # VM version: JDK 1.8.0_91, Java HotSpot(TM) 64-Bit Server VM, 25.91-b14
     * # VM invoker: /Users/wusheng/Documents/applications/jdk1.8.0_91.jdk/Contents/Home/jre/bin/java
     * # VM options: -ea -Didea.test.cyclic.buffer.size=1048576 -javaagent:/Applications/IntelliJ IDEA.app/Contents/lib/idea_rt.jar=54841:/Applications/IntelliJ IDEA.app/Contents/bin -Dfile.encoding=UTF-8
     * # Warmup: <none>
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 1 thread, will synchronize iterations
     * # Benchmark mode: Throughput, ops/time
     * # Benchmark: org.apache.skywalking.apm.util.StringFormatGroupTest.testMatch
     *
     * # Run progress: 50.00% complete, ETA 00:00:50
     * # Fork: 1 of 1
     * Iteration   1: 1137158.205 ops/s
     * Iteration   2: 1192936.161 ops/s
     * Iteration   3: 1218773.403 ops/s
     * Iteration   4: 1222966.452 ops/s
     * Iteration   5: 1235609.354 ops/s
     *
     *
     * Result "org.apache.skywalking.apm.util.StringFormatGroupTest.testMatch":
     *   1201488.715 ±(99.9%) 150813.461 ops/s [Average]
     *   (min, avg, max) = (1137158.205, 1201488.715, 1235609.354), stdev = 39165.777
     *   CI (99.9%): [1050675.254, 1352302.176] (assumes normal distribution)
     *
     *
     * # Run complete. Total time: 00:01:41
     *
     * REMEMBER: The numbers below are just data. To gain reusable insights, you need to follow up on
     * why the numbers are the way they are. Use profilers (see -prof, -lprof), design factorial
     * experiments, perform baseline and negative tests that provide experimental control, make sure
     * the benchmarking environment is safe on JVM/OS/HW level, ask for reviews from the domain experts.
     * Do not assume the numbers tell you what you want them to tell.
     *
     * Benchmark                           Mode  Cnt        Score        Error  Units
     * StringFormatGroupTest.test100Rule  thrpt    5    35490.540 ±   8345.368  ops/s
     * StringFormatGroupTest.testMatch    thrpt    5  1201488.715 ± 150813.461  ops/s
     *
     */
}
