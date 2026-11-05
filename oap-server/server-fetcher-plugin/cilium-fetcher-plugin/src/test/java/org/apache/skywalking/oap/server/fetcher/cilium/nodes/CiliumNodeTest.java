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

package org.apache.skywalking.oap.server.fetcher.cilium.nodes;

import java.io.Closeable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CiliumNodeTest {

    @Test
    public void shouldRunRegisteredCloseablesOnce() {
        final CiliumNode node = new CiliumNode("10.0.0.1:4244", null);
        final AtomicInteger runs = new AtomicInteger();

        assertTrue(node.addingCloseable(runs::incrementAndGet));
        node.close();
        node.close();

        assertEquals(1, runs.get());
        assertTrue(node.isClosed());
    }

    /**
     * A flow stream that starts after the node closed must not register, or nothing would
     * ever interrupt it.
     */
    @Test
    public void shouldRefuseCloseableAfterClose() {
        final CiliumNode node = new CiliumNode("10.0.0.1:4244", null);
        node.close();
        final AtomicInteger runs = new AtomicInteger();

        assertFalse(node.addingCloseable(runs::incrementAndGet));
        node.close();

        assertEquals(0, runs.get());
        assertNull(node.getObserverStub());
    }

    /**
     * A flow stream removes its interrupter when it ends. The pooled thread may then run
     * another node's stream, which this node's close() must not interrupt.
     */
    @Test
    public void shouldNotRunRemovedCloseable() {
        final CiliumNode node = new CiliumNode("10.0.0.1:4244", null);
        final AtomicInteger endedStreamRuns = new AtomicInteger();
        final AtomicInteger liveStreamRuns = new AtomicInteger();
        final Closeable endedStream = endedStreamRuns::incrementAndGet;

        assertTrue(node.addingCloseable(endedStream));
        assertTrue(node.addingCloseable(liveStreamRuns::incrementAndGet));
        node.removeCloseable(endedStream);
        node.close();

        assertEquals(0, endedStreamRuns.get());
        assertEquals(1, liveStreamRuns.get());
    }

    /**
     * While close() runs a stream's interrupter, that stream's thread cannot finish
     * deregistering, so it cannot return to its pool and pick up another node's stream
     * before the interrupt lands.
     */
    @Test
    public void shouldHoldRemovalUntilCloseFinishes() throws Exception {
        final CiliumNode node = new CiliumNode("10.0.0.1:4244", null);
        final CountDownLatch interrupting = new CountDownLatch(1);
        final CountDownLatch finishInterrupt = new CountDownLatch(1);
        // Held until the finally below releases it, so the hold cannot expire before the check.
        final Closeable interrupter = () -> {
            interrupting.countDown();
            try {
                finishInterrupt.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        assertTrue(node.addingCloseable(interrupter));

        final Thread closer = new Thread(node::close, "node-close");
        final Thread streamEnd = new Thread(() -> node.removeCloseable(interrupter), "stream-end");
        try {
            closer.start();
            assertTrue(interrupting.await(5, TimeUnit.SECONDS));
            streamEnd.start();
            awaitBlocked(streamEnd);
        } finally {
            finishInterrupt.countDown();
        }
        closer.join(5000);
        streamEnd.join(5000);
        assertFalse(closer.isAlive());
        assertFalse(streamEnd.isAlive());
    }

    private static void awaitBlocked(Thread thread) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED) {
            assertTrue(thread.isAlive(), () -> thread.getName() + " finished without waiting for close()");
            assertTrue(System.nanoTime() - deadline < 0, () -> thread.getName() + " never blocked on close()");
            Thread.sleep(1);
        }
    }
}
