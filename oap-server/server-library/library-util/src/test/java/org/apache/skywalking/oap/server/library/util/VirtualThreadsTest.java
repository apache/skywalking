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

package org.apache.skywalking.oap.server.library.util;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VirtualThreadsTest {

    @Test
    public void testIsSupportedMatchesJdkVersion() {
        final int jdkVersion = Runtime.version().feature();
        final boolean expected = jdkVersion >= VirtualThreads.MINIMUM_JDK_VERSION;
        assertEquals(expected, VirtualThreads.isSupported());
    }

    @Test
    public void testVirtualThreadExecutor() throws Exception {
        if (!VirtualThreads.isSupported()) {
            return;
        }
        final ExecutorService executor = VirtualThreads.createExecutor(
            "vt-check", true, () -> Executors.newSingleThreadExecutor());
        try {
            final ThreadCapture capture = submitAndCapture(executor);
            assertTrue(capture.name.startsWith("vt:vt-check-"),
                       "Virtual thread name should start with 'vt:vt-check-', but was: " + capture.name);
            assertTrue(isVirtual(capture.thread),
                       "Thread should be virtual on JDK 25+");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testForcePlatformFallback() throws Exception {
        if (!VirtualThreads.isSupported()) {
            return;
        }
        final AtomicLong counter = new AtomicLong(0);
        final ExecutorService executor = VirtualThreads.createExecutor(
            "pt-check", false, () -> new ThreadPoolExecutor(
                2, 2, 60, TimeUnit.SECONDS, new SynchronousQueue<>(),
                r -> new Thread(r, "pt-check-" + counter.getAndIncrement())
            ));
        try {
            final ThreadCapture capture = submitAndCapture(executor);
            assertTrue(capture.name.startsWith("pt-check-"),
                       "Platform thread name should start with 'pt-check-', but was: " + capture.name);
            assertFalse(isVirtual(capture.thread),
                        "Thread should NOT be virtual when enableVirtualThreads=false");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testFallbackUsedWhenNotSupported() {
        if (VirtualThreads.isSupported()) {
            return;
        }
        final ExecutorService fallback = Executors.newSingleThreadExecutor();
        try {
            final ExecutorService result = VirtualThreads.createExecutor("test", () -> fallback);
            assertSame(fallback, result);
        } finally {
            fallback.shutdown();
        }
    }

    private ThreadCapture submitAndCapture(final ExecutorService executor) throws InterruptedException {
        final AtomicReference<Thread> threadRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        executor.submit(() -> {
            threadRef.set(Thread.currentThread());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Task did not complete in time");
        final Thread thread = threadRef.get();
        assertNotNull(thread);
        return new ThreadCapture(thread, thread.getName());
    }

    /**
     * Check Thread.isVirtual() via reflection (JDK 21+ API, compiled against JDK 11).
     */
    private static boolean isVirtual(final Thread thread) throws Exception {
        final Method isVirtual = Thread.class.getMethod("isVirtual");
        return (boolean) isVirtual.invoke(thread);
    }

    private static class ThreadCapture {
        final Thread thread;
        final String name;

        ThreadCapture(final Thread thread, final String name) {
            this.thread = thread;
            this.name = name;
        }
    }
}
