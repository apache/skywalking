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

package org.apache.skywalking.oap.server.library.util.benchmark;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.util.StringFormatGroup;
import org.junit.jupiter.api.Test;

@Slf4j
public class StringFormatGroupBenchmark {

    private static final int WARMUP_ITERATIONS = 5000;
    private static final int MEASURE_ITERATIONS = 100_000;

    @Test
    public void testMatch() {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            doMatch();
        }
        final long start = System.nanoTime();
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            doMatch();
        }
        final long elapsed = System.nanoTime() - start;
        log.info("testMatch: {} ops, {} ns/op, {} ops/s",
                 MEASURE_ITERATIONS, elapsed / MEASURE_ITERATIONS,
                 MEASURE_ITERATIONS * 1_000_000_000L / elapsed);
    }

    @Test
    public void test100Rule() {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            do100Rule();
        }
        final long start = System.nanoTime();
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            do100Rule();
        }
        final long elapsed = System.nanoTime() - start;
        log.info("test100Rule: {} ops, {} ns/op, {} ops/s",
                 MEASURE_ITERATIONS, elapsed / MEASURE_ITERATIONS,
                 MEASURE_ITERATIONS * 1_000_000_000L / elapsed);
    }

    private void doMatch() {
        StringFormatGroup group = new StringFormatGroup();
        group.addRule("/name/*/add", "/name/.+/add");
        group.format("/name/test/add");

        group = new StringFormatGroup();
        group.addRule("/name/*/add/{orderId}", "/name/.+/add/.*");
        group.format("/name/test/add/12323");
    }

    private void do100Rule() {
        final StringFormatGroup group = new StringFormatGroup();
        group.addRule("/name/*/add/{orderId}", "/name/.+/add/.*");
        for (int i = 0; i < 100; i++) {
            group.addRule("/name/*/add/{orderId}" + "/" + 1, "/name/.+/add/.*" + "/abc");
        }
        group.format("/name/test/add/12323");
    }
}
