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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitingSamplerTest {
    @Test
    void theFirstRpmCallsOfAMinuteAreKept() {
        final RateLimitingSampler sampler = new RateLimitingSampler(2);
        assertTrue(sampler.sample());
        assertTrue(sampler.sample());
        assertFalse(sampler.sample());
    }

    @Test
    void rulesNamingOneIdShareOneBudget() {
        final RateLimitingSamplers samplers = new RateLimitingSamplers();
        assertTrue(samplers.of("shared", 1).sample());
        assertFalse(samplers.of("shared", 5).sample(), "the second rule's rpm does not reset the first one's budget");
        assertTrue(samplers.of("other", 1).sample());
    }

    @Test
    void concurrentCallersShareOneBudget() throws Exception {
        final RateLimitingSampler sampler = new RateLimitingSampler(100);
        final AtomicInteger kept = new AtomicInteger();
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            final List<Future<?>> done = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                done.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 1_000; i++) {
                        if (sampler.sample()) {
                            kept.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (final Future<?> f : done) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(100, kept.get());
    }
}
