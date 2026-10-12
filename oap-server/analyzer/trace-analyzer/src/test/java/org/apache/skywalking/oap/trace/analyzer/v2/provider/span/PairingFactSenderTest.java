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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairingFactSenderTest {
    @Test
    void aStuckPeerDropsFactsInsteadOfHoldingUpTheCaller() throws Exception {
        final CountDownLatch peerStuck = new CountDownLatch(1);
        final CountDownLatch firstSendStarted = new CountDownLatch(1);
        final AtomicInteger sent = new AtomicInteger();
        final PairingFactSender sender = new PairingFactSender("tal-pairing-test", 4, fact -> {
            firstSendStarted.countDown();
            try {
                peerStuck.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sent.incrementAndGet();
        });
        try {
            sender.offer(fact());
            assertTrue(firstSendStarted.await(5, TimeUnit.SECONDS), "the sending thread took the first fact");

            final long start = System.nanoTime();
            int accepted = 0;
            for (int i = 0; i < 100; i++) {
                if (sender.offer(fact())) {
                    accepted++;
                }
            }
            final long tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue(tookMillis < 1_000, "offering never waits for the peer: " + tookMillis + " ms");
            assertTrue(accepted <= 4, "no more than the buffer is held: " + accepted);
            assertEquals(100 - accepted, sender.dropped());

            peerStuck.countDown();
            final long deadline = System.currentTimeMillis() + 5_000;
            while (sent.get() < 1 + accepted && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(1 + accepted, sent.get(), "what was held is sent once the peer recovers");
        } finally {
            peerStuck.countDown();
            sender.shutdown();
        }
    }

    private static PairingFact fact() {
        return PairingFact.server(SpanMetadata.builder().service("frontend").traceId("t1").spanId("s2")
                                              .parentSpanId("s1").build(), true);
    }
}
