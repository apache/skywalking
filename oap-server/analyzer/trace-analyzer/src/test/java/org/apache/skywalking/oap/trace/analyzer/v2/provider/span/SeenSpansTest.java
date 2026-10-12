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

package org.apache.skywalking.oap.trace.analyzer.v2.provider.span;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.skywalking.oap.server.core.trace.SpanAnalysisResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeenSpansTest {
    @Test
    void aRetryReadsTheVerdictOfTheFirstDelivery() {
        final SeenSpans seen = new SeenSpans(60, 10);
        assertTrue(seen.firstSight("t1", "s1"));
        assertSame(SpanAnalysisResult.CONTINUE, seen.verdictOf("t1", "s1"), "still running: the receiver decides");
        final SpanAnalysisResult dropped = SpanAnalysisResult.builder().persist(false).build();
        seen.remember("t1", "s1", dropped);
        assertFalse(seen.firstSight("t1", "s1"));
        assertSame(dropped, seen.verdictOf("t1", "s1"));
    }

    @Test
    void aWindowOfZeroRemembersNothing() {
        final SeenSpans seen = new SeenSpans(0, 10);
        assertTrue(seen.firstSight("t1", "s1"));
        assertTrue(seen.firstSight("t1", "s1"));
    }

    @Test
    void aSpanIsNewOnceInsideTheWindow() {
        final SeenSpans seen = new SeenSpans(60, 10);
        assertTrue(seen.firstSight("t1", "s1"));
        assertFalse(seen.firstSight("t1", "s1"));
        assertTrue(seen.firstSight("t1", "s2"), "another span of the trace is its own entry");
        assertTrue(seen.firstSight("t2", "s1"), "the same span id in another trace is another span");
    }

    @Test
    void theCapacityBoundsWhatIsRemembered() {
        final SeenSpans seen = new SeenSpans(60, 10);
        assertTrue(seen.firstSight("t1", "s0"));
        for (int i = 1; i <= 1000; i++) {
            assertTrue(seen.firstSight("t1", "s" + i));
        }
        // Ten entries at most, a thousand later: the first is long gone and reads as new again. A bounded set
        // trades a missed duplicate for bounded memory.
        assertTrue(seen.firstSight("t1", "s0"), "the earliest span was evicted");
        assertFalse(seen.firstSight("t1", "s0"), "and is remembered again once re-seen");
    }

    @Test
    void concurrentDeliveriesOfOneSpanCountItOnce() throws Exception {
        final SeenSpans seen = new SeenSpans(60, 100_000);
        final int threads = 8;
        final int spans = 2_000;
        final AtomicInteger firsts = new AtomicInteger();
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            final List<Future<?>> done = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                done.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < spans; i++) {
                        if (seen.firstSight("t1", "s" + i)) {
                            firsts.incrementAndGet();
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
        assertEquals(spans, firsts.get(), "every span is new to exactly one of the deliveries");
    }
}
