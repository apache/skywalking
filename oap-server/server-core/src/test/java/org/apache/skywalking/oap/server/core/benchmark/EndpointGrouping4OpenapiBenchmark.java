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

import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.config.group.openapi.EndpointGroupingRule4Openapi;
import org.apache.skywalking.oap.server.core.config.group.openapi.EndpointGroupingRuleReader4Openapi;
import org.junit.jupiter.api.Test;

@Slf4j
public class EndpointGrouping4OpenapiBenchmark {

    private static final String APT_TEST_DATA = "  /products1/{id}/%d:\n" + "    get:\n" + "    post:\n"
        + "  /products2/{id}/%d:\n" + "    get:\n" + "    post:\n"
        + "  /products3/{id}/%d:\n" + "    get:\n";

    private static final int WARMUP_ITERATIONS = 5000;
    private static final int MEASURE_ITERATIONS = 100_000;

    private static Map<String, String> createTestFile(final int size) {
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("paths:\n");
        for (int i = 0; i <= size; i++) {
            stringBuilder.append(String.format(APT_TEST_DATA, i, i, i));
        }
        return Collections.singletonMap("whatever", stringBuilder.toString());
    }

    @Test
    public void formatEndpointNameMatchedPaths20() {
        final EndpointGroupingRule4Openapi rule =
            new EndpointGroupingRuleReader4Openapi(createTestFile(3)).read();
        runBenchmark("paths20", rule);
    }

    @Test
    public void formatEndpointNameMatchedPaths50() {
        final EndpointGroupingRule4Openapi rule =
            new EndpointGroupingRuleReader4Openapi(createTestFile(9)).read();
        runBenchmark("paths50", rule);
    }

    @Test
    public void formatEndpointNameMatchedPaths200() {
        final EndpointGroupingRule4Openapi rule =
            new EndpointGroupingRuleReader4Openapi(createTestFile(39)).read();
        runBenchmark("paths200", rule);
    }

    private void runBenchmark(final String label, final EndpointGroupingRule4Openapi rule) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            rule.format("serviceA", "GET:/products1/123");
        }
        final long start = System.nanoTime();
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            rule.format("serviceA", "GET:/products1/123");
        }
        final long elapsed = System.nanoTime() - start;
        log.info("{}: {} ops, {} ns/op, {} ops/s",
                 label, MEASURE_ITERATIONS, elapsed / MEASURE_ITERATIONS,
                 MEASURE_ITERATIONS * 1_000_000_000L / elapsed);
    }
}
