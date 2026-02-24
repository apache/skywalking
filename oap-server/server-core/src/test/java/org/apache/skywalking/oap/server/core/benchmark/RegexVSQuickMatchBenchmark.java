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

package org.apache.skywalking.oap.server.core.benchmark;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.config.group.EndpointGroupingRule;
import org.apache.skywalking.oap.server.core.config.group.uri.quickmatch.QuickUriGroupingRule;
import org.junit.jupiter.api.Test;

@Slf4j
public class RegexVSQuickMatchBenchmark {

    private static final int WARMUP_ITERATIONS = 5000;
    private static final int MEASURE_ITERATIONS = 100_000;

    private final EndpointGroupingRule regexRule = new EndpointGroupingRule();
    private final QuickUriGroupingRule quickRule = new QuickUriGroupingRule();

    {
        regexRule.addRule("service1", "/products/{var}", "/products/.+");
        regexRule.addRule("service1", "/products/{var}/detail", "/products/.+/detail");
        regexRule.addRule("service1", "/sales/{var}/1", "/sales/.+/1");
        regexRule.addRule("service1", "/sales/{var}/2", "/sales/.+/2");
        regexRule.addRule("service1", "/sales/{var}/3", "/sales/.+/3");
        regexRule.addRule("service1", "/sales/{var}/4", "/sales/.+/4");
        regexRule.addRule("service1", "/sales/{var}/5", "/sales/.+/5");
        regexRule.addRule("service1", "/sales/{var}/6", "/sales/.+/6");
        regexRule.addRule("service1", "/sales/{var}/7", "/sales/.+/7");
        regexRule.addRule("service1", "/sales/{var}/8", "/sales/.+/8");
        regexRule.addRule("service1", "/sales/{var}/9", "/sales/.+/9");
        regexRule.addRule("service1", "/sales/{var}/10", "/sales/.+/10");
        regexRule.addRule("service1", "/sales/{var}/11", "/sales/.+/11");
        regexRule.addRule("service1", "/employees/{var}/profile", "/employees/.+/profile");

        quickRule.addRule("service1", "/products/{var}");
        quickRule.addRule("service1", "/products/{var}/detail");
        quickRule.addRule("service1", "/sales/{var}/1");
        quickRule.addRule("service1", "/sales/{var}/2");
        quickRule.addRule("service1", "/sales/{var}/3");
        quickRule.addRule("service1", "/sales/{var}/4");
        quickRule.addRule("service1", "/sales/{var}/5");
        quickRule.addRule("service1", "/sales/{var}/6");
        quickRule.addRule("service1", "/sales/{var}/7");
        quickRule.addRule("service1", "/sales/{var}/8");
        quickRule.addRule("service1", "/sales/{var}/9");
        quickRule.addRule("service1", "/sales/{var}/10");
        quickRule.addRule("service1", "/sales/{var}/11");
        quickRule.addRule("service1", "/employees/{var}/profile");
    }

    @Test
    public void matchFirstRegex() {
        runBenchmark("matchFirstRegex",
                     () -> regexRule.format("service1", "/products/123"));
    }

    @Test
    public void matchFirstQuickUriGrouping() {
        runBenchmark("matchFirstQuick",
                     () -> quickRule.format("service1", "/products/123"));
    }

    @Test
    public void matchFourthRegex() {
        runBenchmark("matchFourthRegex",
                     () -> regexRule.format("service1", "/sales/123/2"));
    }

    @Test
    public void matchFourthQuickUriGrouping() {
        runBenchmark("matchFourthQuick",
                     () -> quickRule.format("service1", "/sales/123/2"));
    }

    @Test
    public void notMatchRegex() {
        runBenchmark("notMatchRegex",
                     () -> regexRule.format("service1", "/employees/123"));
    }

    @Test
    public void notMatchQuickUriGrouping() {
        runBenchmark("notMatchQuick",
                     () -> quickRule.format("service1", "/employees/123"));
    }

    private void runBenchmark(final String label, final Runnable action) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            action.run();
        }
        final long start = System.nanoTime();
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            action.run();
        }
        final long elapsed = System.nanoTime() - start;
        log.info("{}: {} ops, {} ns/op, {} ops/s",
                 label, MEASURE_ITERATIONS, elapsed / MEASURE_ITERATIONS,
                 MEASURE_ITERATIONS * 1_000_000_000L / elapsed);
    }
}
