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

package org.apache.skywalking.apm.agent.core.context;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ContextManagerBenchmark {
    @Benchmark
    @Fork(value = 1, warmups = 1)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.SampleTime)
    public void getKeyFromThreadLocal(Blackhole bh) {
        bh.consume(ContextManager.getRuntimeContext().get("KEY"));
    }

    @Benchmark
    @Fork(value = 1, warmups = 1)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.SampleTime)
    public void isAssignableFrom(Blackhole bh) {
        bh.consume(Map.class.isAssignableFrom(HashMap.class));
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder().include(ContextManagerBenchmark.class.getSimpleName())
                .addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }

    /**
     * # JMH version: 1.21
     * # VM version: JDK 1.8.0_292, OpenJDK 64-Bit Server VM, 25.292-b10
     * # VM invoker: /Library/Java/JavaVirtualMachines/adoptopenjdk-8.jdk/Contents/Home/jre/bin/java
     * # VM options: -javaagent:/Applications/IntelliJ IDEA.app/Contents/lib/idea_rt.jar=62459:/Applications/IntelliJ IDEA.app/Contents/bin -Dfile.encoding=UTF-8
     * # Warmup: 5 iterations, 10 s each
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 1 thread, will synchronize iterations
     * # Benchmark mode: Sampling time
     *
     * Benchmark                                                                      Mode      Cnt       Score    Error   Units
     * ContextManagerBenchmark.getKeyFromThreadLocal                                sample  1332415      48.066 ±  0.765   ns/op
     * ContextManagerBenchmark.getKeyFromThreadLocal:getKeyFromThreadLocal·p0.00    sample               11.000            ns/op
     * ContextManagerBenchmark.getKeyFromThreadLocal:getKeyFromThreadLocal·p0.50    sample               42.000            ns/op
     * ContextManagerBenchmark.getKeyFromThreadLocal:getKeyFromThreadLocal·p0.90    sample               45.000            ns/op
     * ContextManagerBenchmark.getKeyFromThreadLocal:getKeyFromThreadLocal·p0.95    sample               47.000            ns/op
     * ContextManagerBenchmark.getKeyFromThreadLocal:getKeyFromThreadLocal·p0.99    sample               93.000            ns/op
     * ContextManagerBenchmark.getKeyFromThreadLocal:getKeyFromThreadLocal·p0.999   sample              652.000            ns/op
     * ContextManagerBenchmark.getKeyFromThreadLocal:getKeyFromThreadLocal·p0.9999  sample            11236.403            ns/op
     * ContextManagerBenchmark.getKeyFromThreadLocal:getKeyFromThreadLocal·p1.00    sample           144384.000            ns/op
     * ContextManagerBenchmark.getKeyFromThreadLocal:·gc.alloc.rate                 sample        5       0.022 ±  0.010  MB/sec
     * ContextManagerBenchmark.getKeyFromThreadLocal:·gc.alloc.rate.norm            sample        5      ≈ 10⁻⁴             B/op
     * ContextManagerBenchmark.getKeyFromThreadLocal:·gc.count                      sample        5         ≈ 0           counts
     * ContextManagerBenchmark.isAssignableFrom                                     sample  1182629      45.894 ±  0.939   ns/op
     * ContextManagerBenchmark.isAssignableFrom:isAssignableFrom·p0.00              sample                7.000            ns/op
     * ContextManagerBenchmark.isAssignableFrom:isAssignableFrom·p0.50              sample               39.000            ns/op
     * ContextManagerBenchmark.isAssignableFrom:isAssignableFrom·p0.90              sample               43.000            ns/op
     * ContextManagerBenchmark.isAssignableFrom:isAssignableFrom·p0.95              sample               46.000            ns/op
     * ContextManagerBenchmark.isAssignableFrom:isAssignableFrom·p0.99              sample               89.000            ns/op
     * ContextManagerBenchmark.isAssignableFrom:isAssignableFrom·p0.999             sample              606.000            ns/op
     * ContextManagerBenchmark.isAssignableFrom:isAssignableFrom·p0.9999            sample            15904.000            ns/op
     * ContextManagerBenchmark.isAssignableFrom:isAssignableFrom·p1.00              sample           138240.000            ns/op
     * ContextManagerBenchmark.isAssignableFrom:·gc.alloc.rate                      sample        5       0.021 ±  0.007  MB/sec
     * ContextManagerBenchmark.isAssignableFrom:·gc.alloc.rate.norm                 sample        5      ≈ 10⁻⁴             B/op
     * ContextManagerBenchmark.isAssignableFrom:·gc.count                           sample        5         ≈ 0           counts
     */
}
