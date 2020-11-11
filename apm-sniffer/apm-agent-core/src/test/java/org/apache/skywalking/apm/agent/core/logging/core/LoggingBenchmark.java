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

package org.apache.skywalking.apm.agent.core.logging.core;

import com.google.gson.Gson;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import java.util.concurrent.TimeUnit;

public class LoggingBenchmark {
    private static final PatternLogger PATTERN_LOGGER = new PatternLogger(LoggingBenchmark.class, PatternLogger.DEFAULT_PATTERN) {
        @Override
        protected void logger(LogLevel level, String message, Throwable e) {
            format(level, message, e);
        }
    };

    private static final JsonLogger JSON_LOGGER = new JsonLogger(LoggingBenchmark.class, new Gson()) {
        @Override
        protected void logger(LogLevel level, String message, Throwable e) {
            format(level, message, e);
        }
    };

    @Benchmark
    @Fork(value = 1, warmups = 1)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.SampleTime)
    public void jsonLogger() {
        JSON_LOGGER.info("Hello World");
    }

    @Benchmark
    @Fork(value = 1, warmups = 1)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.SampleTime)
    public void patternLogger() {
        PATTERN_LOGGER.info("Hello World");
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }

    /**
     * # JMH version: 1.21
     * # VM version: JDK 1.8.0_265, OpenJDK 64-Bit Server VM, 25.265-b01
     * # VM invoker: /Library/Java/JavaVirtualMachines/adoptopenjdk-8.jdk/Contents/Home/jre/bin/java
     * # VM options: -javaagent:/Applications/IntelliJ IDEA.app/Contents/lib/idea_rt.jar=61104:/Applications/IntelliJ IDEA.app/Contents/bin -Dfile.encoding=UTF-8
     * # Warmup: 5 iterations, 10 s each
     * # Measurement: 5 iterations, 10 s each
     * # Timeout: 10 min per iteration
     * # Threads: 1 thread, will synchronize iterations
     * # Benchmark mode: Sampling time
     *
     * Benchmark                                               Mode      Cnt        Score    Error  Units
     * LoggingBenchmark.jsonLogger                           sample  1400812     2305.088 ± 19.119  ns/op
     * LoggingBenchmark.jsonLogger:jsonLogger·p0.00          sample              1988.000           ns/op
     * LoggingBenchmark.jsonLogger:jsonLogger·p0.50          sample              2156.000           ns/op
     * LoggingBenchmark.jsonLogger:jsonLogger·p0.90          sample              2280.000           ns/op
     * LoggingBenchmark.jsonLogger:jsonLogger·p0.95          sample              2388.000           ns/op
     * LoggingBenchmark.jsonLogger:jsonLogger·p0.99          sample              3576.000           ns/op
     * LoggingBenchmark.jsonLogger:jsonLogger·p0.999         sample             18688.000           ns/op
     * LoggingBenchmark.jsonLogger:jsonLogger·p0.9999        sample             78072.717           ns/op
     * LoggingBenchmark.jsonLogger:jsonLogger·p1.00          sample           1183744.000           ns/op
     * LoggingBenchmark.patternLogger                        sample  1100999     1522.612 ± 23.720  ns/op
     * LoggingBenchmark.patternLogger:patternLogger·p0.00    sample              1270.000           ns/op
     * LoggingBenchmark.patternLogger:patternLogger·p0.50    sample              1378.000           ns/op
     * LoggingBenchmark.patternLogger:patternLogger·p0.90    sample              1452.000           ns/op
     * LoggingBenchmark.patternLogger:patternLogger·p0.95    sample              1548.000           ns/op
     * LoggingBenchmark.patternLogger:patternLogger·p0.99    sample              2188.000           ns/op
     * LoggingBenchmark.patternLogger:patternLogger·p0.999   sample             17696.000           ns/op
     * LoggingBenchmark.patternLogger:patternLogger·p0.9999  sample            589721.600           ns/op
     * LoggingBenchmark.patternLogger:patternLogger·p1.00    sample           1259520.000           ns/op
     */
}
