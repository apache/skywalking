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

package org.apache.skywalking.oap.server.library.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(2)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class StringFormatGroupBenchmark {
    @Benchmark
    @Test
    public void testMatch() {
        StringFormatGroup group = new StringFormatGroup();
        group.addRule("/name/*/add", "/name/.+/add");
        Assertions.assertEquals("/name/*/add", group.format("/name/test/add").getName());

        group = new StringFormatGroup();
        group.addRule("/name/*/add/{orderId}", "/name/.+/add/.*");
        Assertions.assertEquals("/name/*/add/{orderId}", group.format("/name/test/add/12323").getName());
    }

    @Benchmark
    @Test
    public void test100Rule() {
        StringFormatGroup group = new StringFormatGroup();
        group.addRule("/name/*/add/{orderId}", "/name/.+/add/.*");
        for (int i = 0; i < 100; i++) {
            group.addRule("/name/*/add/{orderId}" + "/" + 1, "/name/.+/add/.*" + "/abc");
        }
        Assertions.assertEquals("/name/*/add/{orderId}", group.format("/name/test/add/12323").getName());
    }

    @Test
    public void run() throws Exception {
        new Runner(new OptionsBuilder()
                .include(".*" + getClass().getSimpleName() + ".*")
                .jvmArgsAppend("-Xmx512m", "-Xms512m")
                .build()).run();
    }
}
