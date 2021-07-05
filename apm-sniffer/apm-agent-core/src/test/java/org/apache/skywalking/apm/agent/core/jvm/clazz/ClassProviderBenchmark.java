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

package org.apache.skywalking.apm.agent.core.jvm.clazz;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

public class ClassProviderBenchmark {

    @Benchmark
    @Fork(value = 5, warmups = 3)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @BenchmarkMode(Mode.Throughput)
    public void getThreadMetrics(Blackhole bh) {
        bh.consume(ClassProvider.INSTANCE.getClassMetrics());
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder().include(ClassProviderBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    /**
     # JMH version: 1.21
     # VM version: JDK 1.8.0_231, Java HotSpot(TM) 64-Bit Server VM, 25.231-b11
     # VM invoker: /Library/Java/JavaVirtualMachines/jdk1.8.0_231.jdk/Contents/Home/jre/bin/java
     # VM options: -javaagent:/Users/switch/Library/Application Support/JetBrains/Toolbox/apps/IDEA-U/ch-0/211.7442.40/IntelliJ IDEA.app/Contents/lib/idea_rt.jar=52931:/Users/switch/Library/Application Support/JetBrains/Toolbox/apps/IDEA-U/ch-0/211.7442.40/IntelliJ IDEA.app/Contents/bin -Dfile.encoding=UTF-8
     # Warmup: 5 iterations, 10 s each
     # Measurement: 5 iterations, 10 s each
     # Timeout: 10 min per iteration
     # Threads: 1 thread, will synchronize iterations
     # Benchmark mode: Throughput, ops/time
     *
     * Benchmark                                 Mode  Cnt        Score      Error  Units
     * ClassProviderBenchmark.getThreadMetrics  thrpt   25  6542809.978 ± 8794.520  ops/s
     */
}